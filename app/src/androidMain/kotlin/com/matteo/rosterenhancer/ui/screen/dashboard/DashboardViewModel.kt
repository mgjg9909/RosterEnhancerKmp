package com.matteo.rosterenhancer.ui.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matteo.rosterenhancer.data.repository.RosterRepository
import com.matteo.rosterenhancer.domain.model.Shift
import com.matteo.rosterenhancer.domain.model.ShiftType
import com.matteo.rosterenhancer.util.DataStoreManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import com.matteo.rosterenhancer.util.*

import kotlinx.datetime.LocalTime
import kotlinx.coroutines.delay

data class DashboardUiState(
    val selfName: String = "",
    val selfId: String = "",
    val todayShift: Shift? = null,
    val upcomingShifts: List<Shift> = emptyList(), // domani + resto del mese
    val monthHours: Int = 0,
    val monthMaxHours: Int = 160,
    val nextRestDate: LocalDate? = null,
    val hasRoster: Boolean = false,
    val isLoading: Boolean = true,
    val notesByDate: Map<LocalDate, com.matteo.rosterenhancer.domain.model.ShiftNote> = emptyMap(),
    val colleaguesInShift: List<Shift> = emptyList(),
    val isLoadingColleagues: Boolean = true,
    val estimatedEarnings: Double = 0.0,
    val todayEarnings: Double = 0.0,
    val roleCounts: Map<String, Int> = emptyMap(),
    val topColleagues: List<Pair<String, Int>> = emptyList(),
    val allShiftsOfMonth: List<Shift> = emptyList(),
    val isSyncing: Boolean = false,
    val syncResultMessage: String? = null,
    val selectedColleagueName: String? = null,
    val sharedShifts: List<Pair<Shift, Shift>> = emptyList() // Pair(Il mio turno, Turno del collega)
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel constructor(
    private val repository: RosterRepository,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    val useDarkTheme: Flow<Boolean> = dataStoreManager.useDarkTheme
    val isOnboardingDone: Flow<Boolean> = dataStoreManager.isOnboardingDone

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    private val tomorrow = today.plusDays(1)
    private val firstOfMonth = LocalDate(today.year, today.monthNumber, 1)
    private val endOfMonth = today.toJava().withDayOfMonth(today.toJava().lengthOfMonth()).toKotlin()
    private val endOfUpcomingRange = today.toJava().plusMonths(1).withDayOfMonth(today.toJava().plusMonths(1).lengthOfMonth()).toKotlin()
    
    // Memorizziamo temporaneamente tutti i turni di tutti per calcolare i dettagli on-demand
    private var allWorkShiftsOfMonth: List<Shift> = emptyList()

    init { 
        loadDashboard()
        observeColleaguesInShift()
    }

    fun syncWithIntranet() {
        if (_uiState.value.isSyncing) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, syncResultMessage = null) }
            val result = repository.syncWithWeb()
            
            _uiState.update { it.copy(
                isSyncing = false,
                syncResultMessage = result.getOrNull() ?: result.exceptionOrNull()?.message ?: "Errore sconosciuto"
            ) }
        }
    }

    fun clearSyncMessage() {
        _uiState.update { it.copy(syncResultMessage = null) }
    }

    fun selectColleagueForSharedShifts(name: String?) {
        if (name == null) {
            _uiState.update { it.copy(selectedColleagueName = null, sharedShifts = emptyList()) }
            return
        }

        val myAllShifts = _uiState.value.allShiftsOfMonth
        val shared = mutableListOf<Pair<Shift, Shift>>()

        // Prendiamo tutti i turni di lavoro del collega per il mese
        val colleagueWorkShifts = allWorkShiftsOfMonth.filter { it.employeeName == name }

        colleagueWorkShifts.forEach { colShift ->
            val myShiftOnSameDay = myAllShifts.firstOrNull { it.date == colShift.date }
                ?: Shift(
                    date = colShift.date,
                    shiftType = ShiftType.OTHER,
                    employeeName = _uiState.value.selfName,
                    employeeId = _uiState.value.selfId
                )
            
            shared.add(myShiftOnSameDay to colShift)
        }

        _uiState.update { it.copy(
            selectedColleagueName = name,
            sharedShifts = shared.sortedBy { it.first.date }
        ) }
    }

    private fun observeColleaguesInShift() {
        viewModelScope.launch {
            delay(2000) // Attende che la UI sia stabile prima del primo caricamento
            while(true) {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
                val currentDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
                val matricola = dataStoreManager.selfMatricola.first()
                
                repository.getShiftsWorkingNow(currentDate, now).first().let { allWorking ->
                    val others = allWorking.filter { 
                        it.employeeId != matricola && it.employeeId != "self" && 
                        it.employeeName.uppercase() != _uiState.value.selfName.uppercase()
                    }
                    
                    if (com.matteo.rosterenhancer.BuildConfig.DEBUG)
                        android.util.Log.d("DashboardViewModel", "Colleghi in turno: trovati ${allWorking.size}, filtrati ${others.size} (Nome Self: '${_uiState.value.selfName}')")
                    
                    _uiState.update { it.copy(
                        colleaguesInShift = others,
                        isLoadingColleagues = false
                    ) }
                }
                delay(60000) // Refresh ogni minuto
            }
        }
    }

    private fun loadDashboard() {
        if (com.matteo.rosterenhancer.BuildConfig.DEBUG) android.util.Log.d("DashboardViewModel", "loadDashboard() avviato")
        viewModelScope.launch {
            dataStoreManager.selfMatricola.flatMapLatest { matricola ->
                combine(
                    repository.getShiftsForEmployeeInRange(matricola.ifBlank { "self" }, firstOfMonth, endOfMonth),
                    repository.getNotesInRangeFlow(from = firstOfMonth, to = endOfMonth),
                    dataStoreManager.monthlyHoursTarget,
                    dataStoreManager.gpgProfile
                ) { allShifts, monthNotes, monthTarget, profile -> 
                    val calculator = com.matteo.rosterenhancer.domain.calculator.SalaryCalculator(profile)
                    val summary = calculator.calculateMonthlySummary(today.monthNumber, today.year, allShifts)
                    
                    val todayShift = allShifts.firstOrNull { it.date == Clock.System.todayIn(TimeZone.currentSystemDefault()) }
                    val todayEarnings = todayShift?.let { calculator.calculateDailySummary(it).totalNetPay } ?: 0.0

                    // Fetch upcoming shifts synchronously within the flow mapping
                    val upcoming = repository.getShiftsForEmployeeInRange(matricola.ifBlank { "self" }, tomorrow, endOfUpcomingRange).first()
                    
                    // Statistiche Ruoli (PAX, MER, BAG)
                    val stats = allShifts.filter { it.shiftType == ShiftType.WORK }
                        .map { com.matteo.rosterenhancer.util.RoleGroups.normalize(it.role) }
                        .groupingBy { it }
                        .eachCount()
                    
                    // Statistiche Colleghi (Top 3)
                    val allWorkShifts = repository.getAllWorkShiftsInRange(firstOfMonth, endOfMonth).first()
                    allWorkShiftsOfMonth = allWorkShifts // Salviamo per uso futuro
                    
                    val myWorkShifts = allShifts.filter { it.shiftType == ShiftType.WORK }
                    val colleagueCounts = mutableMapOf<String, Int>()
                    
                    myWorkShifts.forEach { myShift ->
                        val myStart = myShift.startTime ?: return@forEach
                        val myEnd = myShift.endTime ?: return@forEach
                        
                        allWorkShifts.filter { it.employeeId != matricola && it.date == myShift.date }.forEach { colleagueShift ->
                            val colStart = colleagueShift.startTime ?: return@forEach
                            val colEnd = colleagueShift.endTime ?: return@forEach
                            
                            // Verifica sovrapposizione (almeno 1 ora insieme)
                            val overlapStart = if ((myStart > colStart)) myStart else colStart
                            val overlapEnd = if ((myEnd < colEnd)) myEnd else colEnd
                            
                            if ((overlapEnd > overlapStart)) {
                                colleagueCounts[colleagueShift.employeeName] = (colleagueCounts[colleagueShift.employeeName] ?: 0) + 1
                            }
                        }
                    }
                    val topColleagues = colleagueCounts.toList().sortedByDescending { it.second }.take(3)

                    DataBundle(
                        allShifts, upcoming, monthNotes, monthTarget, 
                        summary.estimatedNetPay, todayEarnings,
                        stats,
                        topColleagues,
                        calculator.getBaseMonthlyHoursThreshold(today.monthNumber, today.year).toInt()
                    )
                }.flowOn(kotlinx.coroutines.Dispatchers.Default)
            }.catch { e ->
                android.util.Log.e("DashboardViewModel", "Dashboard: ERRORE nel flusso", e)
                _uiState.update { it.copy(isLoading = false) }
            }.collect { bundle ->
                val allShifts = bundle.allShifts
                val upcoming = bundle.upcomingShifts
                val monthNotes = bundle.monthNotes
                val earnings = bundle.earnings
                val todayEarnings = bundle.todayEarnings

                val todayShift = allShifts.firstOrNull { it.date == today }

                val nextRest = upcoming.firstOrNull {
                    it.shiftType == ShiftType.REST_1 || it.shiftType == ShiftType.REST_2
                }?.date

                val hours = allShifts
                    .filter { it.date.monthNumber == today.monthNumber && it.date.year == today.year }
                    .sumOf { it.workedHours }

                val weeksInMonth = today.toJava().lengthOfMonth() / 7.0

                val self = repository.getSelf()
                val notesMap = monthNotes.filter { it.employeeName.uppercase().trim() == self?.fullName?.uppercase()?.trim() }
                                         .associateBy { it.date }

                // Aggiornamento incrementale: solo i campi effettivamente cambiati
                // causano recomposizioni localizzate invece di rifare tutta la UI
                _uiState.update { current ->
                    current.copy(
                        selfName = self?.fullName ?: "Utente",
                        selfId = self?.id ?: "4363",
                        todayShift = todayShift,
                        upcomingShifts = upcoming,
                        monthHours = hours,
                        monthMaxHours = bundle.maxHoursThreshold,
                        nextRestDate = nextRest,
                        hasRoster = allShifts.isNotEmpty(),
                        isLoading = false,
                        notesByDate = notesMap,
                        estimatedEarnings = earnings,
                        todayEarnings = todayEarnings,
                        roleCounts = bundle.roleCounts,
                        topColleagues = bundle.topColleagues,
                        allShiftsOfMonth = bundle.allShifts
                    )
                }
            }
        }
    }

    private data class DataBundle(
        val allShifts: List<Shift>,
        val upcomingShifts: List<Shift>,
        val monthNotes: List<com.matteo.rosterenhancer.domain.model.ShiftNote>,
        val monthTarget: Int,
        val earnings: Double,
        val todayEarnings: Double,
        val roleCounts: Map<String, Int>,
        val topColleagues: List<Pair<String, Int>>,
        val maxHoursThreshold: Int
    )
    fun saveShiftNote(shift: Shift, text: String, extraMinutes: Int, otStart: java.time.LocalTime?, otEnd: java.time.LocalTime?) {
        viewModelScope.launch {
            // Salva nella tabella separata delle note (retrocompatibilità / storico)
            repository.upsertNote(shift.employeeName, shift.date, text, extraMinutes)
            
            // Aggiorna anche il turno vero e proprio per il calcolo dello stipendio
            val updatedShift = shift.copy(
                overtimeMinutes = extraMinutes,
                overtimeStartTime = otStart?.toKotlin(),
                overtimeEndTime = otEnd?.toKotlin(),
                notes = text.trim()
            )
            repository.upsertManualShift(updatedShift)
        }
    }

    private fun getExtendedMacroGroup(rawRole: String?): String {
        if (rawRole == null) return "—"
        val role = rawRole.trim().uppercase()
        val group = com.matteo.rosterenhancer.util.RoleGroups.normalize(role)
        // La macro-mansione estesa raggruppa logicamente anche SBH dentro BAG per questa specifica vista
        if (group == "BAG" || role == "SBH") return "BAG_EXT"
        return group
    }

    fun getColleaguesForShift(myShift: Shift): List<Shift> {
        val myExtGroup = getExtendedMacroGroup(myShift.role)
        val selfId = _uiState.value.selfId
        val selfName = _uiState.value.selfName.uppercase()
        
        // Filtra tutti i colleghi che lavorano nello stesso giorno, escludendo l'utente stesso
        val colleaguesThatDay = allWorkShiftsOfMonth.filter { 
            it.date == myShift.date && 
            it.employeeId != selfId && 
            it.employeeId != "self" &&
            it.employeeName.uppercase() != selfName &&
            it.shiftType == ShiftType.WORK
        }

        return if (myExtGroup == "PAX") {
            // Regola per PAX: Stesso gruppo macro (PAX, PAM, PAF) e stesso ESATTO orario di inizio
            colleaguesThatDay.filter { 
                getExtendedMacroGroup(it.role) == "PAX" && 
                it.startTime == myShift.startTime 
            }.sortedBy { it.employeeName }
        } else {
            // Regola per gli altri: Stesso gruppo macro esteso, ordinati per orario di inizio
            colleaguesThatDay.filter { 
                getExtendedMacroGroup(it.role) == myExtGroup 
            }.sortedWith(compareBy({ it.startTime }, { it.employeeName }))
        }
    }
}



