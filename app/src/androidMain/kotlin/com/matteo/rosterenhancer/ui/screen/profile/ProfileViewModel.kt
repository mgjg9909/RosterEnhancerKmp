package com.matteo.rosterenhancer.ui.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matteo.rosterenhancer.domain.model.GpgProfile
import com.matteo.rosterenhancer.util.DataStoreManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime

class ProfileViewModel constructor(
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    val profile: StateFlow<GpgProfile> = combine(
        dataStoreManager.gpgLevel,
        dataStoreManager.gpgSenioritySteps,
        dataStoreManager.gpgPartTimePercent,
        dataStoreManager.gpgTaxRate,
        dataStoreManager.gpgAirportIndemnity
    ) { level, steps, pt, tax, airportIndemnity ->
        GpgProfile(level, steps, pt, tax, airportIndemnity = airportIndemnity)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GpgProfile())

    fun updateLevel(level: Int) {
        viewModelScope.launch {
            dataStoreManager.setGpgLevel(level)
        }
    }

    fun updateSenioritySteps(gpgSenioritySteps: Int) {
        viewModelScope.launch {
            dataStoreManager.setGpgSenioritySteps(gpgSenioritySteps)
        }
    }

    fun updatePartTime(percent: Double) {
        viewModelScope.launch {
            dataStoreManager.setGpgPartTimePercent(percent)
        }
    }

    fun updateTaxRate(rate: Double) {
        viewModelScope.launch {
            dataStoreManager.setGpgTaxRate(rate)
        }
    }

    fun updateAirportIndemnity(amount: Double) {
        viewModelScope.launch {
            dataStoreManager.setGpgAirportIndemnity(amount)
        }
    }
}

