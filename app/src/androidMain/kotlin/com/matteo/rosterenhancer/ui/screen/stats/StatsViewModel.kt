package com.matteo.rosterenhancer.ui.screen.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matteo.rosterenhancer.data.repository.RosterRepository
import com.matteo.rosterenhancer.domain.model.Shift
import com.matteo.rosterenhancer.domain.model.ShiftType
import com.matteo.rosterenhancer.util.DataStoreManager
import com.matteo.rosterenhancer.util.RoleGroups
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import com.matteo.rosterenhancer.util.*
import com.matteo.rosterenhancer.domain.usecase.GetMonthlyStatsUseCase

/**
 * Fasce orarie aziendali:
 *  🌅 Mattina    → 03:00 – 06:59
 *  ☀️ Centrale   → 07:00 – 11:59
 *  🌆 Pomeriggio → 12:00 – 18:59
 *  🌙 Notte      → 19:00 – 02:59
 */
data class TopCompanion(
    val id: String,
    val name: String,
    val sharedHours: Int,
    val sharedDays: Int
)

data class SalaryTrendPoint(
    val monthName: String,
    val netSalary: Double,
    val month: Int,
    val year: Int
)

data class StatsUiState(
    val totalHours: Int = 0,
    val workDays: Int = 0,
    val restDays: Int = 0,
    val absentDays: Int = 0,
    // Distribuzione per fascia oraria
    val morningShifts: Int = 0,     // 03-06
    val centraleShifts: Int = 0,    // 07-11
    val afternoonShifts: Int = 0,   // 12-18
    val nightShifts: Int = 0,       // 19-02
    val roleDistribution: Map<String, Int> = emptyMap(),
    val topCompanions: List<TopCompanion> = emptyList(),
    val salaryTrend: List<SalaryTrendPoint> = emptyList(),
    val monthMaxHours: Int = 160,
    // Ore per settimana (label "Sett 1" -> ore)
    val weeklyHours: List<Pair<String, Int>> = emptyList(),
    // Heatmap: data -> tipo turno ("WORK","REST","OFF","ABSENT",...)
    val monthShiftMap: Map<LocalDate, String> = emptyMap(),
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel constructor(
    private val repository: RosterRepository,
    private val dataStoreManager: DataStoreManager,
    private val getMonthlyStatsUseCase: GetMonthlyStatsUseCase
) : ViewModel() {

    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

    val uiState: StateFlow<StatsUiState> = dataStoreManager.selfMatricola
        .flatMapLatest { matricola ->
            if (matricola.isBlank()) flowOf(StatsUiState(isLoading = false))
            else {
                val fromD = today.withDayOfMonth(1)
                val toD = today.withDayOfMonth(today.lengthOfMonth)
                
                // Ultimi 6 mesi per il grafico
                val sixMonthsAgo = today.minusMonths(5L).withDayOfMonth(1)
                
                combine(
                    repository.getShiftsForEmployeeInRange(matricola, fromD, toD),
                    repository.getShiftsForEmployeeInRange(matricola, sixMonthsAgo, toD),
                    repository.getAllWorkShiftsInRange(fromD, toD),
                    dataStoreManager.monthlyHoursTarget,
                    dataStoreManager.gpgProfile
                ) { currentMyShifts: List<com.matteo.rosterenhancer.domain.model.Shift>, 
                    historicalMyShifts: List<com.matteo.rosterenhancer.domain.model.Shift>, 
                    allWorkShifts: List<com.matteo.rosterenhancer.domain.model.Shift>, 
                    monthTarget: Int, 
                    profile: com.matteo.rosterenhancer.domain.model.GpgProfile ->
                    getMonthlyStatsUseCase.execute(currentMyShifts, historicalMyShifts, allWorkShifts, monthTarget, matricola, profile)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUiState())


}


