package com.matteo.rosterenhancer.ui.screen.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matteo.rosterenhancer.data.repository.RosterRepository
import com.matteo.rosterenhancer.util.DataStoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val matricola: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isDone: Boolean = false
)

class OnboardingViewModel constructor(
    private val dataStoreManager: DataStoreManager,
    private val repository: RosterRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onMatricolaChanged(value: String) {
        _uiState.update { it.copy(matricola = value.trim(), error = null) }
    }

    fun confirm() {
        val matricola = _uiState.value.matricola.trim()
        if (matricola.isBlank()) {
            _uiState.update { it.copy(error = "Inserisci il numero di matricola") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            dataStoreManager.setSelfMatricola(matricola)
            dataStoreManager.setOnboardingDone(true)
            // Aggiorna il flag isSelf nel DB se l'utente esiste già
            repository.setSelf(matricola)
            _uiState.update { it.copy(isLoading = false, isDone = true) }
        }
    }
}

