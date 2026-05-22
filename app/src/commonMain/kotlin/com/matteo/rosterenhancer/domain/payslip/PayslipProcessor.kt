package com.matteo.rosterenhancer.domain.payslip

interface PayslipProcessor {
    suspend fun processNewPayslip(fileBytes: ByteArray, fileName: String, isPdf: Boolean): CalibrationResult
    suspend fun applyCalibration(delta: Double, month: Int, year: Int)
    fun deleteFile(filePath: String)
}
