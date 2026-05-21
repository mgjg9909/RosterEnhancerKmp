package com.matteo.rosterenhancer.domain.payslip

enum class ReadMethod { GEMINI, LOCAL_OCR }

data class ExtractedPayslipData(
    val month: Int?,
    val year: Int?,
    val netPay: Double?,
    val grossPay: Double? = null,
    val readMethod: ReadMethod = ReadMethod.LOCAL_OCR
)

sealed class CalibrationResult {
    data class Success(
        val data: ExtractedPayslipData,
        val delta: Double,
        val theoreticalNet: Double = 0.0,
        val message: String? = null,
        val geminiFailReason: String? = null
    ) : CalibrationResult()
    data class Error(val message: String) : CalibrationResult()
}
