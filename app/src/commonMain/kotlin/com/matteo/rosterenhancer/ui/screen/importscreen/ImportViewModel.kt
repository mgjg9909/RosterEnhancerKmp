package com.matteo.rosterenhancer.ui.screen.importscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matteo.rosterenhancer.data.repository.RosterRepository
import com.matteo.rosterenhancer.domain.parser.XlsxParser
import com.matteo.rosterenhancer.domain.parser.ParseResult
import com.matteo.rosterenhancer.util.DataStoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class ImportState {
    object Idle : ImportState()
    object Loading : ImportState()
    data class Preview(val result: ParseResult, val fileName: String) : ImportState()
    data class Success(val month: Int, val year: Int, val shiftCount: Int) : ImportState()
    data class Error(val message: String) : ImportState()
}

class ImportViewModel constructor(
    private val repository: RosterRepository,
    private val xlsxParser: XlsxParser,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    fun parseFile(fileBytes: ByteArray, fileName: String) {
        viewModelScope.launch {
            _state.value = ImportState.Loading
            try {
                val result = xlsxParser.parse(fileBytes)
                if (result.employees.isEmpty() || result.shifts.isEmpty()) {
                    _state.value = ImportState.Error(
                        "Nessun dato trovato nel file.\n\nDEBUG:\n${result.debugInfo}"
                    )
                } else {
                    _state.value = ImportState.Preview(result, fileName)
                }
            } catch (e: Exception) {
                _state.value = ImportState.Error("Errore nel parsing: ${e.message}")
            }
        }
    }

    fun confirmImport() {
        val currentState = _state.value
        if (currentState !is ImportState.Preview) return

        viewModelScope.launch {
            _state.value = ImportState.Loading
            val result = repository.importRoster(currentState.result, currentState.fileName)
            if (result.isSuccess) {
                // Aggiorna isSelf se l'utente ha già impostato la matricola
                val matricola = dataStoreManager.selfMatricola.first()
                if (matricola.isNotBlank()) {
                    repository.setSelf(matricola)
                }
                _state.value = ImportState.Success(
                    month = currentState.result.month,
                    year = currentState.result.year,
                    shiftCount = currentState.result.shifts.size
                )
            } else {
                _state.value = ImportState.Error(
                    result.exceptionOrNull()?.message ?: "Errore durante il salvataggio"
                )
            }
        }
    }

    fun reset() { _state.value = ImportState.Idle }
}

