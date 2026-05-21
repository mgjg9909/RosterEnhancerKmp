package com.matteo.rosterenhancer.ui.screen.swaps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matteo.rosterenhancer.data.repository.RosterRepository
import com.matteo.rosterenhancer.domain.model.Shift
import com.matteo.rosterenhancer.domain.model.ShiftType
import com.matteo.rosterenhancer.domain.usecase.FindSwapCandidatesUseCase
import com.matteo.rosterenhancer.domain.usecase.SwapCandidate
import com.matteo.rosterenhancer.util.DataStoreManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import com.matteo.rosterenhancer.util.plusDays

data class ShiftSwapUiState(
    val upcomingShifts: List<Shift> = emptyList(),
    val selectedShift: Shift? = null,
    val candidates: List<SwapCandidate>? = null,
    val isSearching: Boolean = false,
    val selfName: String = "",
    val favorites: Set<String> = emptySet()
)

@OptIn(ExperimentalCoroutinesApi::class)
class ShiftSwapViewModel constructor(
    private val repository: RosterRepository,
    private val dataStoreManager: DataStoreManager,
    private val findSwapCandidatesUseCase: FindSwapCandidatesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShiftSwapUiState())
    val uiState: StateFlow<ShiftSwapUiState> = _uiState.asStateFlow()

    private var selfMatricola: String = ""
    private var isSupervisor: Boolean = false
    private var isFas: Boolean = false
    private var userGender: String = "M"

    init { loadBaseData() }

    private fun loadBaseData() {
        // Load one-shot preferences safely
        viewModelScope.launch {
            try {
                val self = repository.getSelf()
                selfMatricola = self?.id ?: ""
                _uiState.update { it.copy(selfName = self?.fullName ?: "") }

                isSupervisor = dataStoreManager.isSupervisor.first()
                isFas        = dataStoreManager.isFas.first()
                userGender   = dataStoreManager.userGender.first()
            } catch (e: Exception) {
                println("ShiftSwapViewModel" + ": " + "Errore caricamento dati base" + " - " + e.message)
            }
        }

        // Observe shifts reactively — flatMapLatest auto-cancels on resubscription
        dataStoreManager.selfMatricola
            .flatMapLatest { matricola ->
                if (matricola.isBlank()) flowOf(emptyList())
                else {
                    selfMatricola = matricola
                    val now = Clock.System.todayIn(TimeZone.currentSystemDefault())
                    val endOfNextMonth = now.plusDays(60)
                    repository.getShiftsForEmployeeInRange(matricola, now, endOfNextMonth)
                }
            }
            .onEach { shifts ->
                val workedShifts = shifts
                    .filter { it.shiftType == ShiftType.WORK }
                    .sortedBy { it.date }
                _uiState.update { it.copy(upcomingShifts = workedShifts) }
            }
            .catch { e -> println("ShiftSwapViewModel" + ": " + "Errore osservazione turni" + " - " + e.message) }
            .launchIn(viewModelScope)
    }

    fun selectShift(shift: Shift?) {
        _uiState.update { it.copy(selectedShift = shift, candidates = null) }
        if (shift != null) searchCandidates(shift)
    }

    private fun searchCandidates(myShift: Shift) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }

            val favorites = dataStoreManager.favoriteColleagues.first()

            val list = findSwapCandidatesUseCase(
                targetDate     = myShift.date,
                myShift        = myShift,
                selfEmployeeId = selfMatricola,
                userGender     = userGender,
                isSupervisor   = isSupervisor,
                isFas          = isFas
            ).map { candidate ->
                candidate.copy(isFavorite = favorites.contains(candidate.employeeId))
            }.sortedWith(
                compareBy<SwapCandidate> { !it.isFavorite }
                    .thenBy { it.employeeName }
            )

            _uiState.update { it.copy(isSearching = false, candidates = list) }
        }
    }
}



