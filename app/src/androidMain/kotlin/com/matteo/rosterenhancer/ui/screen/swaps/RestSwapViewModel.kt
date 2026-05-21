package com.matteo.rosterenhancer.ui.screen.swaps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matteo.rosterenhancer.data.repository.RosterRepository
import com.matteo.rosterenhancer.domain.model.Shift
import com.matteo.rosterenhancer.domain.model.ShiftType
import com.matteo.rosterenhancer.domain.usecase.FindRestSwapCandidatesUseCase
import com.matteo.rosterenhancer.domain.usecase.RestSwapCandidate
import com.matteo.rosterenhancer.util.DataStoreManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import com.matteo.rosterenhancer.util.plusDays

data class RestSwapUiState(
    val myWorkShiftsForMonth: List<Shift> = emptyList(),
    val myRestDaysForMonth: List<Shift> = emptyList(),
    val selectedWorkShiftToDrop: Shift? = null,
    val selectedRestDayToWork: Shift? = null,
    val candidates: List<RestSwapCandidate>? = null,
    val isSearching: Boolean = false,
    val selfName: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
class RestSwapViewModel constructor(
    private val repository: RosterRepository,
    private val dataStoreManager: DataStoreManager,
    private val findRestSwapCandidatesUseCase: FindRestSwapCandidatesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(RestSwapUiState())
    val uiState: StateFlow<RestSwapUiState> = _uiState.asStateFlow()

    private var selfMatricola: String = ""
    private var isSupervisor: Boolean = false
    private var isFas: Boolean = false
    private var userGender: String = "M"

    init { loadBaseData() }

    private fun loadBaseData() {
        // Load one-shot data (identity, preferences) safely
        viewModelScope.launch {
            try {
                val self = repository.getSelf()
                selfMatricola = self?.id ?: ""
                _uiState.update { it.copy(selfName = self?.fullName ?: "") }

                isSupervisor = dataStoreManager.isSupervisor.first()
                isFas = dataStoreManager.isFas.first()
                userGender = dataStoreManager.userGender.first()
            } catch (e: Exception) {
                android.util.Log.e("RestSwapViewModel", "Errore caricamento dati base", e)
            }
        }

        // Observe shifts reactively — uses flatMapLatest so it auto-cancels on resubscription
        dataStoreManager.selfMatricola
            .flatMapLatest { matricola ->
                if (matricola.isBlank()) flowOf(emptyList())
                else {
                    selfMatricola = matricola
                    val now = Clock.System.todayIn(TimeZone.currentSystemDefault())
                    val to = now.plusDays(60)
                    repository.getShiftsForEmployeeInRange(matricola, now, to)
                }
            }
            .onEach { shifts ->
                val worked = shifts.filter { it.shiftType == ShiftType.WORK }
                val rests  = shifts.filter {
                    it.shiftType == ShiftType.REST_1 ||
                    it.shiftType == ShiftType.REST_2 ||
                    it.shiftType == ShiftType.DAY_OFF
                }
                _uiState.update {
                    it.copy(
                        myWorkShiftsForMonth = worked.sortedBy { s -> s.date },
                        myRestDaysForMonth   = rests.sortedBy  { s -> s.date }
                    )
                }
            }
            .catch { e -> android.util.Log.e("RestSwapViewModel", "Errore osservazione turni", e) }
            .launchIn(viewModelScope)
    }

    fun selectWorkShiftToDrop(shift: Shift?) {
        _uiState.update { it.copy(selectedWorkShiftToDrop = shift, candidates = null) }
        checkSearch()
    }

    fun selectRestDayToWork(shift: Shift?) {
        _uiState.update { it.copy(selectedRestDayToWork = shift, candidates = null) }
        checkSearch()
    }

    private fun checkSearch() {
        val state = _uiState.value
        val dropShift   = state.selectedWorkShiftToDrop
        val pickupRest  = state.selectedRestDayToWork
        if (dropShift != null && pickupRest != null) {
            searchCandidates(dropShift, pickupRest)
        }
    }

    private fun searchCandidates(myShiftToDrop: Shift, myRestDayToWork: Shift) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }

            val list = findRestSwapCandidatesUseCase(
                myWorkDate     = myShiftToDrop.date,
                myRestDate     = myRestDayToWork.date,
                myShiftToDrop  = myShiftToDrop,
                selfEmployeeId = selfMatricola,
                userGender     = userGender,
                isSupervisor   = isSupervisor,
                isFas          = isFas
            )

            _uiState.update { it.copy(isSearching = false, candidates = list) }
        }
    }
}



