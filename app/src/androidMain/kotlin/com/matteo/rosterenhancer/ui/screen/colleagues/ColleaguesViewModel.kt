package com.matteo.rosterenhancer.ui.screen.colleagues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matteo.rosterenhancer.data.repository.RosterRepository
import com.matteo.rosterenhancer.domain.model.Shift
import com.matteo.rosterenhancer.util.RoleGroups
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
import com.matteo.rosterenhancer.util.DataStoreManager

enum class ColleagueFilter { WORKING_NOW, ALL, WORKING_TODAY, ON_REST }

@OptIn(ExperimentalCoroutinesApi::class)
class ColleaguesViewModel constructor(
    private val repository: RosterRepository,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filter = MutableStateFlow(ColleagueFilter.WORKING_NOW)
    val filter: StateFlow<ColleagueFilter> = _filter.asStateFlow()

    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

    val todayAndYesterdayShifts: StateFlow<List<Shift>> = combine(
        repository.getShiftsForDate(Clock.System.todayIn(TimeZone.currentSystemDefault()).minusDays(1)),
        repository.getShiftsForDate(Clock.System.todayIn(TimeZone.currentSystemDefault()))
    ) { yesterday, tod ->
        yesterday + tod
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteColleagues: StateFlow<Set<String>> = dataStoreManager.favoriteColleagues
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val filteredShifts: StateFlow<List<Shift>> = combine(
        todayAndYesterdayShifts, _searchQuery, _filter, favoriteColleagues
    ) { shifts, query, filter, favorites ->
        val nowDT = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val today = nowDT.toLocalDate()

        shifts.filter { shift ->
            // Filtra fuori i turni di ieri a meno che non stiamo cercando "WORKING_NOW"
            if (filter != ColleagueFilter.WORKING_NOW && shift.date != today) {
                return@filter false
            }

            val matchesFilter = when (filter) {
                ColleagueFilter.ALL -> shift.date == today
                ColleagueFilter.WORKING_TODAY -> shift.date == today && shift.shiftType.name == "WORK"
                ColleagueFilter.WORKING_NOW -> {
                    if (shift.startTime != null && shift.endTime != null && shift.shiftType.name == "WORK") {
                        val startDT = shift.date.atTime(shift.startTime)
                        val endDT = if (shift.endTime <= shift.startTime) {
                            shift.date.plusDays(1).atTime(shift.endTime)
                        } else {
                            shift.date.atTime(shift.endTime)
                        }
                        !(nowDT < startDT) && !(nowDT > endDT)
                    } else {
                        false
                    }
                }
                ColleagueFilter.ON_REST -> {
                    shift.date == today && (shift.shiftType.name.startsWith("REST") || shift.shiftType.name == "DAY_OFF")
                }
            }

            if (!matchesFilter) return@filter false

            val matchesSearch = query.isBlank() ||
                shift.employeeName.contains(query, ignoreCase = true) ||
                shift.role?.contains(query, ignoreCase = true) == true ||
                RoleGroups.normalize(shift.role).contains(query, ignoreCase = true)

            matchesSearch
        }
        .distinctBy { it.employeeId }
        .sortedWith(compareBy({ !favorites.contains(it.employeeId) }, { it.startTime ?: kotlinx.datetime.LocalTime(23, 59, 59, 999999999) }, { it.employeeName }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchChanged(query: String) { _searchQuery.value = query }
    fun onFilterChanged(filter: ColleagueFilter) { _filter.value = filter }
    
    fun toggleFavorite(employeeId: String) {
        viewModelScope.launch {
            dataStoreManager.toggleFavoriteColleague(employeeId)
        }
    }
}



