package com.matteo.rosterenhancer.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matteo.rosterenhancer.data.local.entity.MonthRosterEntity
import com.matteo.rosterenhancer.data.repository.RosterRepository
import com.matteo.rosterenhancer.data.security.CredentialsManager
import com.matteo.rosterenhancer.util.DataStoreManager


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


data class SettingsUiState(
    val currentMatricola: String = "",
    val newMatricola: String = "",
    val useDarkTheme: Boolean = true,
    val monthlyHoursTarget: Int = DataStoreManager.MONTHLY_HOURS_DEFAULT,
    val userGender: String = "",
    val isSupervisor: Boolean = false,
    val isFas: Boolean = false,
    val rosters: List<MonthRosterEntity> = emptyList(),
    val saved: Boolean = false,
    val reminderMinutes: Int = 90,
    // Cloud Sync
    val cloudUser: String = "",
    val cloudPass: String = "",
    val isSyncing: Boolean = false,
    val syncStatus: String? = null,
    val hourlyRate: String = "10.50",
    val nightBonus: String = "2.50",
    // GPG Profile
    val gpgLevel: Int = 4,
    val gpgSenioritySteps: Int = 0,
    val gpgPartTime: String = "100.0",
    val gpgTaxRate: String = "15.0",
    // AI Integration
    val geminiApiKey: String = ""
)


class SettingsViewModel constructor(
    private val dataStoreManager: DataStoreManager,
    private val repository: RosterRepository,
    private val credentialsManager: CredentialsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Carica credenziali esistenti su IO per evitare ANR con EncryptedSharedPreferences
            val user = withContext(Dispatchers.IO) { credentialsManager.getUsername() ?: "" }
            val pass = withContext(Dispatchers.IO) { credentialsManager.getPassword() ?: "" }
            _uiState.update { it.copy(cloudUser = user, cloudPass = pass) }
        }

        viewModelScope.launch {
            // [DIAGNOSTICA] Semplificazione del combine per evitare stalli
            dataStoreManager.selfMatricola.onEach { matricola ->
                _uiState.update { it.copy(
                    currentMatricola = matricola,
                    newMatricola = if (it.newMatricola.isBlank()) matricola else it.newMatricola
                )}
            }.launchIn(viewModelScope)

            dataStoreManager.useDarkTheme.onEach { dark ->
                _uiState.update { it.copy(useDarkTheme = dark) }
            }.launchIn(viewModelScope)

            dataStoreManager.geminiApiKey.onEach { key ->
                _uiState.update { it.copy(geminiApiKey = key) }
            }.launchIn(viewModelScope)

            // Carichiamo i roster separatamente
            repository.getAllRosters().onEach { list ->
                _uiState.update { it.copy(rosters = list) }
            }.launchIn(viewModelScope)

            // Gli altri parametri meno critici li carichiamo in un unico blocco protetto
            combine(
                dataStoreManager.monthlyHoursTarget,
                dataStoreManager.userGender,
                dataStoreManager.isSupervisor,
                dataStoreManager.isFas,
                dataStoreManager.gpgLevel,
                dataStoreManager.gpgSenioritySteps,
                dataStoreManager.gpgPartTimePercent,
                dataStoreManager.gpgTaxRate
            ) { values ->
                _uiState.update { it.copy(
                    monthlyHoursTarget = values[0] as Int,
                    userGender = values[1] as String,
                    isSupervisor = values[2] as Boolean,
                    isFas = values[3] as Boolean,
                    gpgLevel = values[4] as Int,
                    gpgSenioritySteps = values[5] as Int,
                    gpgPartTime = (values[6] as Double).toString(),
                    gpgTaxRate = ((values[7] as Double) * 100).toString()
                )}
            }.catch { e ->
                println("SettingsViewModel" + ": " + "Errore in combine secondario" + " - " + e.message)
            }.launchIn(viewModelScope)
        }
    }

    fun onCloudUserChanged(value: String) {
        _uiState.update { it.copy(cloudUser = value, syncStatus = null) }
    }

    fun onCloudPassChanged(value: String) {
        _uiState.update { it.copy(cloudPass = value, syncStatus = null) }
    }

    fun saveCloudCredentials() {
        viewModelScope.launch {
            credentialsManager.saveCredentials(_uiState.value.cloudUser, _uiState.value.cloudPass)
            _uiState.update { it.copy(syncStatus = "Credenziali salvate correttamente.") }
        }
    }

    private suspend fun saveCloudCredentialsSuspend() {
        credentialsManager.saveCredentials(_uiState.value.cloudUser, _uiState.value.cloudPass)
        _uiState.update { it.copy(syncStatus = "Credenziali salvate correttamente.") }
    }

    fun syncNow() {
        if (_uiState.value.cloudUser.isBlank() || _uiState.value.cloudPass.isBlank()) {
            _uiState.update { it.copy(syncStatus = "Inserire username e password.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, syncStatus = "Inizializzazione sincronizzazione...") }
            
            // Prima salviamo le credenziali
            saveCloudCredentialsSuspend()
            
            repository.syncWithWeb()
                .onSuccess { msg ->
                    _uiState.update { it.copy(isSyncing = false, syncStatus = msg) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isSyncing = false, syncStatus = "Errore: ${err.localizedMessage}") }
                }
        }
    }

    fun onMatricolaChanged(value: String) {
        _uiState.update { it.copy(newMatricola = value, saved = false) }
    }

    fun saveMatricola() {
        val matricola = _uiState.value.newMatricola.trim()
        if (matricola.isBlank()) return
        viewModelScope.launch {
            dataStoreManager.setSelfMatricola(matricola)
            repository.setSelf(matricola)
            _uiState.update { it.copy(currentMatricola = matricola, saved = true) }
        }
    }

    fun toggleTheme(dark: Boolean) {
        viewModelScope.launch { dataStoreManager.setUseDarkTheme(dark) }
    }

    fun setMonthlyHours(hours: Int) {
        viewModelScope.launch { dataStoreManager.setMonthlyHoursTarget(hours) }
    }

    fun deleteRoster(roster: MonthRosterEntity) {
        viewModelScope.launch { repository.deleteRoster(roster) }
    }

    fun setReminderMinutes(minutes: Int) {
        viewModelScope.launch { dataStoreManager.setReminderMinutes(minutes) }
    }

    fun setGender(gender: String) {
        viewModelScope.launch { dataStoreManager.setUserGender(gender) }
    }

    fun setSupervisor(isSuper: Boolean) {
        viewModelScope.launch { 
            dataStoreManager.setIsSupervisor(isSuper) 
            if (isSuper) {
                // Mutua esclusione con FAS
                dataStoreManager.setIsFas(false)
            }
        }
    }

    fun setFas(isFas: Boolean) {
        viewModelScope.launch { 
            dataStoreManager.setIsFas(isFas)
            if (isFas) {
                // Mutua esclusione con SPV
                dataStoreManager.setIsSupervisor(false)
            }
        }
    }

    fun onHourlyRateChanged(value: String) {
        _uiState.update { it.copy(hourlyRate = value) }
        value.toFloatOrNull()?.let {
            viewModelScope.launch { dataStoreManager.setHourlyRate(it) }
        }
    }

    fun onNightBonusChanged(value: String) {
        _uiState.update { it.copy(nightBonus = value) }
        value.toFloatOrNull()?.let {
            viewModelScope.launch { dataStoreManager.setNightBonus(it) }
        }
    }

    fun setGpgLevel(level: Int) {
        viewModelScope.launch { dataStoreManager.setGpgLevel(level) }
    }

    fun setGpgSenioritySteps(steps: Int) {
        viewModelScope.launch { dataStoreManager.setGpgSenioritySteps(steps) }
    }

    fun onGpgPartTimeChanged(value: String) {
        _uiState.update { it.copy(gpgPartTime = value) }
        value.toDoubleOrNull()?.let {
            viewModelScope.launch { dataStoreManager.setGpgPartTimePercent(it) }
        }
    }

    fun onGpgTaxRateChanged(value: String) {
        _uiState.update { it.copy(gpgTaxRate = value) }
        value.toDoubleOrNull()?.let {
            viewModelScope.launch { dataStoreManager.setGpgTaxRate(it / 100.0) }
        }
    }

    fun onGeminiApiKeyChanged(value: String) {
        _uiState.update { it.copy(geminiApiKey = value) }
    }

    fun saveGeminiApiKey() {
        val key = _uiState.value.geminiApiKey.trim()
        viewModelScope.launch { dataStoreManager.setGeminiApiKey(key) }
    }
}




