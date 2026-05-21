package com.matteo.rosterenhancer.domain.payslip

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

import javax.inject.Singleton

/** Risultato dell'analisi Gemini: o i dati estratti, o il motivo dell'errore. */
sealed class GeminiResult {
    data class Success(val data: ExtractedPayslipData) : GeminiResult()
    data class Failure(val reason: String) : GeminiResult()
}

@Singleton
class GeminiPayslipAnalyzer constructor() {

    // Usiamo gemini-3.1-flash-lite: confermato disponibile per la tua chiave
    private val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent"

    private val client = HttpClient(OkHttp) {
        install(io.ktor.client.plugins.HttpTimeout) {
            requestTimeoutMillis = 60_000 // 60 secondi
            connectTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
        }
    }

    /**
     * Analizza le pagine di un cedolino usando Gemini Vision tramite REST API v1 diretta.
     * Restituisce GeminiResult.Success o GeminiResult.Failure con il motivo esatto.
     */
    suspend fun analyze(pages: List<Bitmap>, apiKey: String): GeminiResult {
        if (apiKey.isBlank()) {
            return GeminiResult.Failure("Chiave API non configurata nelle impostazioni.")
        }

        return withContext(Dispatchers.IO) {
            try {
                // Costruiamo il body JSON manualmente
                val parts = JSONArray()

                // Aggiungiamo le immagini come base64 (ridimensionate per ridurre i token)
                for (bitmap in pages.take(2)) { // max 2 pagine invece di 3
                    val scaled = scaleBitmap(bitmap, maxWidth = 1024)
                    val baos = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 65, baos) // qualità 65 per upload più veloce
                    val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

                    val imageData = JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", base64)
                    }
                    parts.put(JSONObject().apply { put("inlineData", imageData) })
                }

                // Aggiungiamo il prompt testuale
                parts.put(JSONObject().apply { put("text", PROMPT) })

                val requestBody = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply { put("parts", parts) })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0)
                    })
                }.toString()

                Log.d("GeminiAnalyzer", "Chiamata REST v1: $apiUrl")

                // Chiamata HTTP POST diretta all'endpoint v1
                val response = client.post("$apiUrl?key=$apiKey") {
                    headers {
                        append(HttpHeaders.ContentType, "application/json")
                    }
                    setBody(requestBody.toByteArray(Charsets.UTF_8))
                }

                val responseText = response.bodyAsText()
                val statusCode = response.status.value
                Log.d("GeminiAnalyzer", "HTTP Status: $statusCode")

                if (statusCode !in 200..299) {
                    return@withContext GeminiResult.Failure("HTTP $statusCode: $responseText")
                }

                // Navighiamo la struttura JSON della risposta Gemini REST
                val responseJson = JSONObject(responseText)
                val candidates = responseJson.optJSONArray("candidates")
                    ?: return@withContext GeminiResult.Failure("Campo 'candidates' mancante nella risposta")

                val rawJson = candidates
                    .optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?: return@withContext GeminiResult.Failure("Testo JSON non trovato nella risposta Gemini")

                Log.d("GeminiAnalyzer", "Risposta Gemini grezza: $rawJson")

                val cleanJson = rawJson
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                val json = JSONObject(cleanJson)

                val result = ExtractedPayslipData(
                    month = if (json.isNull("mese")) null else json.optInt("mese").takeIf { it in 1..12 },
                    year = if (json.isNull("anno")) null else json.optInt("anno").takeIf { it > 2000 },
                    netPay = if (json.isNull("netto")) null else json.optDouble("netto").takeIf { !it.isNaN() && it > 0 },
                    grossPay = if (json.isNull("lordo")) null else json.optDouble("lordo").takeIf { !it.isNaN() && it > 0 },
                    readMethod = ReadMethod.GEMINI
                )
                Log.d("GeminiAnalyzer", "RISULTATO GEMINI OK: mese=${result.month} anno=${result.year} netto=${result.netPay} lordo=${result.grossPay}")
                GeminiResult.Success(result)

            } catch (t: Throwable) {
                val reason = "${t.javaClass.simpleName}: ${t.message}"
                Log.e("GeminiAnalyzer", "Errore REST -> fallback OCR locale. Motivo: $reason")
                GeminiResult.Failure(reason)
            }
        }
    }

    companion object {
        private const val PROMPT = """Sei un assistente esperto in cedolini paga italiani.
Analizza attentamente il documento e rispondi SOLO con un JSON nel seguente formato, senza testo aggiuntivo:
{"mese":<numero intero 1-12, null se non trovato>,"anno":<anno a 4 cifre, null se non trovato>,"netto":<importo netto a pagare come numero decimale, null se non trovato>,"lordo":<importo lordo come numero decimale, null se non trovato>}

REGOLE PER IL NETTO: Cerca "NETTO A PAGARE", "NETTO IN BUSTA", "TOTALE NETTO". L'importo finale bonificato al dipendente, tipicamente tra 1000 e 3000 euro. Ignora anticipi, trattenute, addizionali.
REGOLE PER IL LORDO: Cerca "TOTALE COMPETENZE", "IMPONIBILE LORDO". Sempre maggiore del netto.
IMPORTANTISSIMO: restituisci SOLO il JSON, nessun altro testo."""

        fun scaleBitmap(bitmap: Bitmap, maxWidth: Int): Bitmap {
            if (bitmap.width <= maxWidth) return bitmap
            val scale = maxWidth.toFloat() / bitmap.width
            val newHeight = (bitmap.height * scale).toInt()
            return Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true)
        }
    }
}




