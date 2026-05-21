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
import com.matteo.rosterenhancer.domain.model.StatsUiState
import com.matteo.rosterenhancer.domain.model.TopCompanion
import com.matteo.rosterenhancer.domain.model.SalaryTrendPoint

/**
 * Fasce orarie aziendali:
 *  🌅 Mattina    → 03:00 – 06:59
 *  ☀️ Centrale   → 07:00 – 11:59
 *  🌆 Pomeriggio → 12:00 – 18:59
 *  🌙 Notte      → 19:00 – 02:59
 */


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


