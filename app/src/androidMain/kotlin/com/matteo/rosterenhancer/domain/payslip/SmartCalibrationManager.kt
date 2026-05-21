package com.matteo.rosterenhancer.domain.payslip

import com.matteo.rosterenhancer.util.minusDays
import com.matteo.rosterenhancer.util.format

import android.content.Context
import android.net.Uri
import com.matteo.rosterenhancer.data.local.dao.PayslipDao
import com.matteo.rosterenhancer.data.local.dao.ShiftDao
import com.matteo.rosterenhancer.data.local.entity.LearningLogEntity
import com.matteo.rosterenhancer.data.local.entity.PayslipEntity
import com.matteo.rosterenhancer.domain.calculator.SalaryCalculator
import com.matteo.rosterenhancer.domain.model.GpgProfile
import com.matteo.rosterenhancer.domain.model.Shift
import com.matteo.rosterenhancer.util.DataStoreManager
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import com.matteo.rosterenhancer.util.*
import kotlinx.datetime.periodUntil
import kotlinx.datetime.LocalDateTime
import java.io.File
import java.io.FileOutputStream

import javax.inject.Singleton


import kotlin.math.abs

@Singleton
class SmartCalibrationManager constructor(
    private val context: Context,
    private val ocrManager: PayslipOcrManager,
    private val parser: PayslipParser,
    private val geminiAnalyzer: GeminiPayslipAnalyzer,
    private val payslipDao: PayslipDao,
    private val shiftDao: ShiftDao,
    private val dataStoreManager: DataStoreManager
) {
    /**
     * Analizza un file, estrae i dati e calcola le discrepanze
     */
    suspend fun processNewPayslip(uri: Uri, isPdf: Boolean): CalibrationResult {
        // Ottieni le pagine come testo OCR (per il parser regex) e come bitmap (per Gemini Vision)
        val pages = if (isPdf) ocrManager.analyzePdf(uri) else listOf(ocrManager.analyzeImage(uri))
        val bitmapPages = if (isPdf) ocrManager.getPdfBitmaps(uri) else listOf(ocrManager.getImageBitmap(uri)).filterNotNull()

        // Prova prima con Gemini (se la chiave è disponibile)
        val apiKey = dataStoreManager.geminiApiKey.first()
        var geminiFailReason: String? = null
        val extracted = if (apiKey.isNotBlank()) {
            val geminiResult = geminiAnalyzer.analyze(bitmapPages, apiKey)
            when (geminiResult) {
                is GeminiResult.Success -> geminiResult.data
                is GeminiResult.Failure -> {
                    geminiFailReason = geminiResult.reason
                    android.util.Log.w("SmartCalibrationManager", "Gemini fallito, uso OCR locale. Motivo: $geminiFailReason")
                    parser.parse(pages)
                }
            }
        } else {
            geminiFailReason = "Chiave API non inserita nelle impostazioni."
            parser.parse(pages)
        }

        if (extracted.month == null || extracted.year == null || extracted.netPay == null) {
            return CalibrationResult.Error("Impossibile estrarre i dati fondamentali (Mese, Anno o Netto) dal documento.")
        }

        val startDate = LocalDate(extracted.year, extracted.month, 1)
        val endDate = startDate.plusMonths(1).minusDays(1)
        
        // Salva il file localmente per visualizzazioni future
        val localPath = saveFileLocally(uri, extracted.month, extracted.year, isPdf)

        // Crea l'entità cedolino e salvala subito
        val rawText = pages.joinToString("\n")
        
        // DEBUG: Scrive il testo OCR su un file per analisi
        try {
            val debugFile = File(context.getExternalFilesDir(null), "ocr_debug.txt")
            debugFile.writeText(rawText)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val entity = PayslipEntity(
            month = extracted.month,
            year = extracted.year,
            netPay = extracted.netPay,
            grossPay = extracted.grossPay ?: 0.0,
            rawText = rawText,
            fileName = uri.lastPathSegment ?: "cedolino_${extracted.month}_${extracted.year}",
            filePath = localPath
        )
        payslipDao.insertPayslip(entity)

        val matricola = dataStoreManager.selfMatricola.first()
        val shiftEntities = shiftDao.getMyShiftsInRangeAsync(matricola, startDate, endDate)
        
        if (shiftEntities.isEmpty()) {
            return CalibrationResult.Success(
                data = extracted,
                delta = 0.0,
                geminiFailReason = geminiFailReason,
                message = "Cedolino letto e salvato in archivio correttamente, ma non ho trovato turni salvati per ${extracted.month}/${extracted.year} con cui fare la calibrazione."
            )
        }

        // Calcola il riepilogo teorico con le impostazioni attuali
        val profile = getCurrentProfile()
        val calculator = SalaryCalculator(profile)
        val shifts = shiftEntities.map { Shift.fromEntity(it) }
        
        val summary = calculator.calculateMonthlySummary(extracted.month, extracted.year, shifts)
        val totalTheoreticalNet = summary.estimatedNetPay

        val delta = extracted.netPay - totalTheoreticalNet

        return CalibrationResult.Success(extracted, delta, totalTheoreticalNet, geminiFailReason = geminiFailReason)
    }

    suspend fun applyCalibration(delta: Double, month: Int, year: Int) {
        val profile = getCurrentProfile()
        // Algoritmo semplice: se il netto reale è diverso, aggiustiamo la taxRate stimata
        // Delta = NetReal - NetTheo -> Se positivo, le tasse erano stimate troppo alte.
        // TaxRate = 1 - (Net / Gross)
        // Per ora facciamo un aggiustamento empirico o salviamo solo il log
        
        val message = if (delta > 1.0) {
            "Rilevato netto reale superiore alla stima di €${"%.2f".format(delta)}. Le tue detrazioni fiscali potrebbero essere più alte del previsto."
        } else if (delta < -1.0) {
            "Rilevato netto reale inferiore alla stima di €${"%.2f".format(abs(delta))}. Ho suggerito un aumento dell'aliquota fiscale stimata."
        } else {
            "Combacia perfettamente con le mie stime!"
        }

        payslipDao.insertLearningLog(LearningLogEntity(
            month = month,
            year = year,
            message = message,
            diffAmount = delta
        ))

        // Segna il cedolino come calibrato
        payslipDao.getPayslipForMonth(month, year)?.let { payslip ->
            payslipDao.insertPayslip(payslip.copy(wasCalibrated = true))
        }
    }

    private suspend fun getCurrentProfile(): GpgProfile {
        return GpgProfile(
            level = dataStoreManager.gpgLevel.first(),
            gpgSenioritySteps = dataStoreManager.gpgSenioritySteps.first(),
            partTimePercentage = dataStoreManager.gpgPartTimePercent.first(),
            taxRate = dataStoreManager.gpgTaxRate.first(),
            airportIndemnity = dataStoreManager.gpgAirportIndemnity.first()
        )
    }

    private fun saveFileLocally(uri: Uri, month: Int, year: Int, isPdf: Boolean): String? {
        return try {
            val extension = if (isPdf) "pdf" else "jpg"
            val fileName = "payslip_${year}_${month}_${System.currentTimeMillis()}.$extension"
            val dir = File(context.filesDir, "payslips")
            if (!dir.exists()) dir.mkdirs()
            
            val file = File(dir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

sealed class CalibrationResult {
    data class Success(
        val data: ExtractedPayslipData,
        val delta: Double,
        val theoreticalNet: Double = 0.0,
        val message: String? = null,
        val geminiFailReason: String? = null  // non-null solo se Gemini ha fallito
    ) : CalibrationResult()
    data class Error(val message: String) : CalibrationResult()
}






