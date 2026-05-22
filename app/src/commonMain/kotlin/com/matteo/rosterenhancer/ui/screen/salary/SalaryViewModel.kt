package com.matteo.rosterenhancer.ui.screen.salary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matteo.rosterenhancer.data.repository.RosterRepository
import com.matteo.rosterenhancer.domain.calculator.SalaryCalculator
import com.matteo.rosterenhancer.domain.model.*
import com.matteo.rosterenhancer.util.DataStoreManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import com.matteo.rosterenhancer.util.*


@OptIn(ExperimentalCoroutinesApi::class)
class SalaryViewModel constructor(
    private val repository: RosterRepository,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val _currentMonth = MutableStateFlow(Clock.System.todayIn(TimeZone.currentSystemDefault()))
    val currentMonth = _currentMonth.asStateFlow()

    // Profilo GPG caricato dal DataStore
    private val gpgProfile: Flow<GpgProfile> = combine(
        dataStoreManager.gpgLevel,
        dataStoreManager.gpgSenioritySteps,
        dataStoreManager.gpgPartTimePercent,
        dataStoreManager.gpgTaxRate,
        dataStoreManager.gpgAirportIndemnity
    ) { level, steps, pt, tax, airport ->
        GpgProfile(level, steps, pt, tax, airportIndemnity = airport)
    }

    val uiState: StateFlow<SalaryUiState> = combine(
        dataStoreManager.selfMatricola,
        _currentMonth,
        gpgProfile
    ) { matricola, month, profile ->
        Triple<String, kotlinx.datetime.LocalDate, com.matteo.rosterenhancer.domain.model.GpgProfile>(matricola, month, profile)
    }.flatMapLatest { (matricola, month, profile) ->
        if (matricola.isBlank()) flowOf(SalaryUiState(isLoading = false))
        else {
            val fromD = month.withDayOfMonth(1)
            val toD = month.withDayOfMonth(month.lengthOfMonth)
            
            val flow: kotlinx.coroutines.flow.Flow<SalaryUiState> = combine(
                repository.getShiftsForEmployeeInRange(matricola, fromD, toD),
                repository.getAllShiftsForEmployee(matricola)
            ) { monthShifts, allShifts ->
                val calculator = SalaryCalculator(profile)
                val summary = calculator.calculateMonthlySummary(month.monthNumber, month.year, monthShifts)
                
                val projection = calculateProjection(monthShifts, calculator)
                val accruals = calculateAccruals(allShifts, calculator)
                
                SalaryUiState(
                    currentMonth = month,
                    summary = summary,
                    accrualsSummary = accruals,
                    projection = projection,
                    profile = profile,
                    isLoading = false
                )
            }.flowOn(kotlinx.coroutines.Dispatchers.Default)
            flow
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SalaryUiState())

    private fun calculateProjection(shifts: List<Shift>, calculator: SalaryCalculator): EarningsProjection {
        val now = kotlinx.datetime.Clock.System.todayIn(TimeZone.currentSystemDefault())
        val currentMonth = kotlinx.datetime.Clock.System.todayIn(TimeZone.currentSystemDefault())

        // Passato: tutti i turni già avvenuti (Cloud + Manuali)
        val pastShifts = shifts.filter { it.date < now }
        // Futuro: SOLO Cloud Sync (isManual = false) — programmazione ufficiale
        val futureShifts = shifts.filter { it.date >= now && !it.isManual }

        // Calcolo a livello MENSILE per evitare distorsioni fiscali da scala
        // (l'Esonero IVS e l'IRPEF si applicano diversamente su importi giornalieri vs mensili)
        val earnedSoFar = if (pastShifts.isEmpty()) 0.0 else {
            calculator.calculateMonthlySummary(currentMonth.monthNumber, currentMonth.year, pastShifts).estimatedNetPay
        }
        val estimatedFuture = if (futureShifts.isEmpty()) 0.0 else {
            calculator.calculateMonthlySummary(currentMonth.monthNumber, currentMonth.year, futureShifts).estimatedNetPay
        }

        val totalProjected = earnedSoFar + estimatedFuture

        // Progresso basato sul totale dei turni ufficiali del mese
        val officialShiftsCount = shifts.count { !it.isManual }
        val progress = if (officialShiftsCount > 0) {
            pastShifts.count { !it.isManual }.toFloat() / officialShiftsCount.toFloat()
        } else {
            if (shifts.isNotEmpty()) pastShifts.size.toFloat() / shifts.size.toFloat() else 0f
        }

        return EarningsProjection(earnedSoFar, estimatedFuture, totalProjected, progress)
    }

    fun nextMonth() {
        _currentMonth.update { it.plusMonths(1L) }
    }

    fun prevMonth() {
        _currentMonth.update { it.minusMonths(1L) }
    }

    fun addManualShift(
        date: kotlinx.datetime.LocalDate,
        startTime: String,
        duration: Int,
        role: String,
        shiftType: com.matteo.rosterenhancer.domain.model.ShiftType = com.matteo.rosterenhancer.domain.model.ShiftType.WORK,
        isMensa: Boolean = false,
        overtimeMinutes: Int = 0,
        otStart: kotlinx.datetime.LocalTime? = null,
        otEnd: kotlinx.datetime.LocalTime? = null
    ) {
        viewModelScope.launch {
            val h = try { kotlinx.datetime.LocalTime.parse(startTime) } catch (e: Exception) { kotlinx.datetime.LocalTime(8, 0) }
            val newShift = com.matteo.rosterenhancer.domain.model.Shift(
                employeeId = "", // Gestito dal repository
                employeeName = "", // Gestito dal repository
                date = date,
                startTime = h,
                durationHours = duration,
                endTime = kotlinx.datetime.LocalTime((h.hour + duration) % 24, h.minute),
                role = role,
                shiftType = shiftType,
                isManual = true,
                isMensaLavorata = isMensa,
                overtimeMinutes = overtimeMinutes,
                overtimeStartTime = otStart,
                overtimeEndTime = otEnd
            )
            repository.upsertManualShift(newShift)
        }
    }

    fun updateShift(shift: com.matteo.rosterenhancer.domain.model.Shift) {
        viewModelScope.launch {
            // Un update manuale segna sempre il turno come "Manual" per protezione
            repository.upsertManualShift(shift.copy(isManual = true))
        }
    }

    fun deleteShift(shift: com.matteo.rosterenhancer.domain.model.Shift) {
        viewModelScope.launch {
            repository.deleteShift(shift)
        }
    }

    private fun calculateAccruals(shifts: List<Shift>, calculator: SalaryCalculator): AccrualsSummary {
        var tfr = 0.0
        var thirteenth = 0.0
        var fourteenth = 0.0
        
        // Raggruppiamo i turni per mese/anno per calcolare i ratei mensili
        val grouped = shifts.groupBy { Pair(it.date.year, it.date.monthNumber) }
        
        grouped.forEach { (month, monthShifts) ->
            val summary = calculator.calculateMonthlySummary(month.second, month.first, monthShifts)
            tfr += (summary.totalGrossPay / 13.5)
            thirteenth += summary.accrual13th
            fourteenth += summary.accrual14th
        }
        
        return AccrualsSummary(
            cumulativeTfr = tfr,
            cumulative13th = thirteenth,
            cumulative14th = fourteenth
        )
    }
}



