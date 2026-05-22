package com.matteo.rosterenhancer.ui.screen.salary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matteo.rosterenhancer.data.local.dao.PayslipDao
import com.matteo.rosterenhancer.data.local.entity.LearningLogEntity
import com.matteo.rosterenhancer.data.local.entity.PayslipEntity
import com.matteo.rosterenhancer.domain.payslip.CalibrationResult
import com.matteo.rosterenhancer.domain.payslip.PayslipProcessor

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch




class PayslipViewModel constructor(
    private val payslipDao: PayslipDao,
    private val calibrationManager: PayslipProcessor
) : ViewModel() {

    private val _uiState = MutableStateFlow(PayslipUiState())
    val uiState: StateFlow<PayslipUiState> = _uiState.asStateFlow()

    val payslips: StateFlow<List<PayslipEntity>> = payslipDao.getAllPayslips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val learningLogs: StateFlow<List<LearningLogEntity>> = payslipDao.getAllLearningLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun processPayslip(fileBytes: ByteArray, fileName: String, isPdf: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, lastResult = null) }
            
            try {
                val result = calibrationManager.processNewPayslip(fileBytes, fileName, isPdf)
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        lastResult = if (result is CalibrationResult.Success) result else null,
                        error = if (result is CalibrationResult.Error) result.message else null
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Errore sconosciuto") }
            }
        }
    }

    fun applyCalibration(result: CalibrationResult.Success) {
        viewModelScope.launch {
            calibrationManager.applyCalibration(result.delta, result.data.month ?: 0, result.data.year ?: 0)
            _uiState.update { it.copy(lastResult = null) }
        }
    }

    fun dismissResult() {
        _uiState.update { it.copy(lastResult = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun deletePayslip(payslip: PayslipEntity) {
        viewModelScope.launch {
            payslip.filePath?.let { path ->
                try {
                    calibrationManager.deleteFile(path)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            payslipDao.deletePayslip(payslip)
        }
    }

    fun deleteLearningLog(log: LearningLogEntity) {
        viewModelScope.launch {
            payslipDao.deleteLearningLog(log)
            // Se c'è un cedolino associato, resettiamo il flag wasCalibrated
            payslipDao.getPayslipForMonth(log.month, log.year)?.let { payslip ->
                payslipDao.insertPayslip(payslip.copy(wasCalibrated = false))
            }
        }
    }
}

data class PayslipUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastResult: CalibrationResult.Success? = null
)




