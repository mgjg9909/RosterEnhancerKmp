package com.matteo.rosterenhancer.domain.payslip

import android.util.Log
import java.util.Locale
import java.util.regex.Pattern

import javax.inject.Singleton

enum class ReadMethod { GEMINI, LOCAL_OCR }

data class ExtractedPayslipData(
    val month: Int?,
    val year: Int?,
    val netPay: Double?,
    val grossPay: Double? = null,
    val readMethod: ReadMethod = ReadMethod.LOCAL_OCR
)

@Singleton
class PayslipParser constructor() {

    private val months = listOf(
        "GENNAIO", "FEBBRAIO", "MARZO", "APRILE", "MAGGIO", "GIUGNO",
        "LUGLIO", "AGOSTO", "SETTEMBRE", "OTTOBRE", "NOVEMBRE", "DICEMBRE"
    )

    // Regex con negative lookbehind: NON cattura numeri che seguono una virgola/punto (es. '25931' in '9,25931').
    // Questo evita di leggere le tariffe orarie come importi netti.
    private val flexibleAmountPattern = Pattern.compile("(?<![,.\\d])(\\d{1,5}(?:[\\.\\s]\\d{3})*(?:[,\\.]\\d{2})?)") 

    fun parse(pages: List<String>): ExtractedPayslipData {
        val fullText = pages.joinToString("\n").uppercase(Locale.getDefault())

        Log.d("PayslipParser", "======= INIZIO PARSING OCR LOCALE =======")
        Log.d("PayslipParser", "Lunghezza testo: ${fullText.length} caratteri")
        Log.d("PayslipParser", "--- TESTO COMPLETO OCR ---")
        // Logga il testo a blocchi (LogCat ha un limite per riga)
        fullText.chunked(3000).forEachIndexed { i, chunk ->
            Log.d("PayslipParser", "[CHUNK $i] $chunk")
        }
        Log.d("PayslipParser", "--- FINE TESTO OCR ---")

        val month = findMonth(fullText)
        val year = findYear(fullText)
        val netPay = findAmount(fullText, listOf(
            "NETTO A PAGARE", "NETTO IN BUSTA", "NETTO BUSTA", "NETTO DEL MESE", "TOTALE NETTO",
            "NEITO",  // OCR legge 'NETTO' come 'NEITO' nel riquadro sommario del cedolino Zucchetti
            "NETTO"
        ), isNet = true)
        val grossPay = findAmount(fullText, listOf("TOTALE COMPETENZE", "IMPONIBILE LORDO", "LORDO", "COMPETENZE"), isNet = false)

        Log.d("PayslipParser", "RISULTATO: mese=$month anno=$year netto=$netPay lordo=$grossPay")
        Log.d("PayslipParser", "======= FINE PARSING OCR LOCALE =======")

        return ExtractedPayslipData(
            month = month,
            year = year,
            netPay = netPay,
            grossPay = grossPay,
            readMethod = ReadMethod.LOCAL_OCR
        )
    }

    private fun findMonth(text: String): Int? {
        // Usa Pattern con word boundary per evitare falsi positivi (es. MAGGIO dentro MAGGIORAZIONE)
        for ((index, month) in months.withIndex()) {
            val pattern = Pattern.compile("\\b${month}\\b")
            if (pattern.matcher(text).find()) return index + 1
        }
        val numericalPattern = Pattern.compile("\\b(0?[1-9]|1[0-2])[-/\\.](202[4-9])\\b")
        val matcher = numericalPattern.matcher(text)
        if (matcher.find()) {
            return matcher.group(1)?.toIntOrNull()
        }
        return null
    }

    private fun findYear(text: String): Int? {
        val yearPattern = Pattern.compile("\\b(20[2-3][0-9])\\b")
        val matcher = yearPattern.matcher(text)
        if (matcher.find()) {
            return matcher.group(1)?.toIntOrNull()
        }
        return null
    }

    private fun findAmount(text: String, keywords: List<String>, isNet: Boolean): Double? {
        val label = if (isNet) "NETTO" else "LORDO"
        // Raccogliamo tutti gli importi plausibili e la loro posizione nel testo
        // Triple(Indice, Importo Numerico, Formattato)
        val candidates = mutableListOf<Triple<Int, Double, Boolean>>()
        val matcher = flexibleAmountPattern.matcher(text)
        while (matcher.find()) {
            val raw = matcher.group(1) ?: continue
            // Evita confusione con gli anni (se non hanno decimali)
            if (raw == "2024" || raw == "2025" || raw == "2026" || raw == "2027") continue
            
            val amount = parseFlexibleAmount(raw) ?: continue
            
            // Verifica se il numero ha separatori (virgola, punto o spazio).
            val isFormatted = !raw.all { it.isDigit() }

            // Soglia minima: il netto raramente è sotto i 700€, il lordo sotto i 900€
            val minThreshold = if (isNet) 700.0 else 900.0
            if (amount > minThreshold) {
                candidates.add(Triple(matcher.start(), amount, isFormatted))
                Log.d("PayslipParser", "[$label] Candidato trovato: raw='$raw' -> $amount (formattato=$isFormatted, idx=${matcher.start()})")
            }
        }

        Log.d("PayslipParser", "[$label] Totale candidati: ${candidates.size}")
        if (candidates.isEmpty()) return null

        // Troviamo tutte le posizioni delle parole chiave nel documento
        val keywordIndices = mutableListOf<Int>()
        for (keyword in keywords) {
            var idx = text.indexOf(keyword)
            while (idx >= 0) {
                keywordIndices.add(idx)
                Log.d("PayslipParser", "[$label] Keyword '$keyword' trovata a idx=$idx")
                idx = text.indexOf(keyword, idx + keyword.length)
            }
        }

        // FASE 1: Ricerca per prossimità (Distanza parola chiave -> Importo)
        if (keywordIndices.isNotEmpty()) {
            var bestAmount: Double? = null
            var minDistance = Int.MAX_VALUE

            for ((amountIdx, amount, _) in candidates) {
                for (kwIdx in keywordIndices) {
                    val distance = amountIdx - kwIdx
                    
                    // Vogliamo che l'importo sia preferibilmente DOPO la parola chiave nel flusso di testo.
                    // Se l'OCR ha invertito le colonne, potrebbe essere prima (distanza negativa).
                    // Aggiungiamo una penalità per distanze negative, ma non escludiamole a priori.
                    val penalty = if (distance < 0) 2000 else 0
                    val effectiveDistance = Math.abs(distance) + penalty
                    
                    // Scegliamo matematicamente il numero "più vicino" alla parola chiave
                    if (effectiveDistance < minDistance) {
                        minDistance = effectiveDistance
                        bestAmount = amount
                    }
                }
            }
            
            if (bestAmount != null) return bestAmount
        }

        // FASE 2: Fallback puro se nessuna parola chiave è stata associata a un importo
        
        // Se cerchiamo il lordo, per definizione è l'importo massimo del documento
        if (!isNet) {
            val maxFormatted = candidates.filter { it.third }.maxByOrNull { it.second }?.second
            return maxFormatted ?: candidates.maxByOrNull { it.second }?.second
        }

        // Se cerchiamo il netto senza keyword...
        // Nei cedolini italiani, il netto a pagare è quasi sempre l'ULTIMO importo rilevante stampato a fine pagina.
        // Diamo priorità all'ultimo importo "formattato" (con virgola/punto) per evitare i CAP/Codici come 25931 in fondo.
        val lastFormatted = candidates.filter { it.third }.lastOrNull()?.second
        return lastFormatted ?: candidates.lastOrNull()?.second
    }

    private fun parseFlexibleAmount(raw: String): Double? {
        var clean = raw.replace(" ", "") // Rimuove spazi (es. 1 756)
        
        if (clean.contains(".") && clean.contains(",")) {
            if (clean.indexOf(",") > clean.indexOf(".")) {
                clean = clean.replace(".", "").replace(",", ".")
            } else {
                clean = clean.replace(",", "")
            }
        } else if (clean.contains(",")) {
            // Se c'è solo la virgola, assumiamo siano decimali
            clean = clean.replace(",", ".")
        } else if (clean.contains(".")) {
            val parts = clean.split(".")
            if (parts.last().length == 3) {
                // Separatore delle migliaia (es. 1.756)
                clean = clean.replace(".", "")
            }
            // Altrimenti lasciamo il punto come decimale (es. 1756.00)
        }
        return clean.toDoubleOrNull()
    }
}




