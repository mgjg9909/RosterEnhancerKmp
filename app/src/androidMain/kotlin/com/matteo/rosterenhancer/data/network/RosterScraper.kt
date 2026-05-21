package com.matteo.rosterenhancer.data.network

import kotlinx.datetime.todayIn
import com.matteo.rosterenhancer.util.*
import kotlinx.datetime.periodUntil
import com.matteo.rosterenhancer.util.plusDays
import com.matteo.rosterenhancer.util.monthValue
import com.matteo.rosterenhancer.util.format

import android.util.Log
import com.matteo.rosterenhancer.data.local.dao.EmployeeDao
import com.matteo.rosterenhancer.data.local.entity.EmployeeEntity
import com.matteo.rosterenhancer.domain.model.Shift
import com.matteo.rosterenhancer.domain.parser.XlsxParser
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import org.jsoup.Jsoup
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import java.time.format.DateTimeFormatter

import javax.inject.Singleton

@Singleton
class RosterScraper constructor(
    private val client: HttpClient,
    private val employeeDao: EmployeeDao,
    private val xlsxParser: XlsxParser
) {
    // Aggiornamento URL dopo refactor del portale (Versione 3.2.0.3)
    private val baseUrl = "https://turni.bologna-airport.it/RosterEnhancer"
    private val TAG = "RosterScraper"

    suspend fun login(user: String, pass: String): Result<String> = runCatching {
        Log.d(TAG, "Inizializzazione sessione (warm-up)...")
        client.get("${baseUrl}accessService") {
            header(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        }

        val response = client.submitForm(
            url = "$baseUrl/login",
            formParameters = parameters {
                append("loginCredentials", user)
                append("loginPWD", pass)
                append("loginType", "LDAP")
            }
        ) {
            header(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            header(HttpHeaders.Referrer, "$baseUrl/accessService")
        }

        val cookies = client.cookies(baseUrl)
        Log.d(TAG, "Cookie rilevati dopo login: ${cookies.joinToString { "${it.name}=${it.value.take(5)}..." }}")
        
        Log.d(TAG, "Login response status: ${response.status.value}")
        
        val body = response.bodyAsText()
        if (response.status.value == 200 && (body.contains("personalService") || body.contains("groupService") || body.contains("index.jsp") || body.contains("Gruppo"))) {
            Log.d(TAG, "Login effettuato con successo (rilevato contenuto post-login)")
            "indexService" 
        } else if (response.status.value == 302 || response.status.value == 200) {
            // Se 302 o 200 (se Ktor ha seguito il redirect), visitiamo comunque la home per attivare la sessione
            val location = response.headers["Location"] ?: "index.jsp"
            Log.d(TAG, "Login effettuato (Redirect/Home: $location). Attivazione sessione...")
            
            val activationResponse = client.get("$baseUrl/index.jsp") {
                header(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                header(HttpHeaders.Referrer, "$baseUrl/accessService")
            }
            val activationBody = activationResponse.bodyAsText()
            Log.d(TAG, "Attivazione sessione status: ${activationResponse.status.value}, body length: ${activationBody.length}")
            if (activationBody.length > 500) {
                Log.d(TAG, "Anteprima Home: ${activationBody.take(500).replace("\n", " ")}")
            }
            "indexService"
        } else {
            throw Exception("Login fallito con stato: ${response.status}")
        }
    }

    suspend fun fetchMyRoster(): Result<List<Shift>> = runCatching {
        Log.d(TAG, "Richiesta utente.jsp (AJAX)...")
        val response = client.get("$baseUrl/utente.jsp") {
            header(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            header(HttpHeaders.Referrer, "$baseUrl/index.jsp")
            header("X-Requested-With", "XMLHttpRequest")
            url {
                parameters.append("recNumFrom", "1")
                parameters.append("recNumTo", "100")
            }
        }
        val html = response.bodyAsText()
        Log.d(TAG, "fetchMyRoster utente.jsp status: ${response.status.value}, body length: ${html.length}")
        
        // DIAGNOSTICA: Stampiamo l'inizio dell'HTML per verificare le classi
        if (html.length > 100) {
            Log.d(TAG, "HTML PREVIEW: ${html.take(2000).replace("\n", " ")}")
        }

        val doc = Jsoup.parse(html)
        val shifts = parsePersonalRosterHtml(doc)
        
        Log.d(TAG, "Parsing roster personale completato. Turni trovati: ${shifts.size}")
        
        if (shifts.isNotEmpty()) {
            val firstShift = shifts.first()
            employeeDao.insertAll(listOf(EmployeeEntity(
                id = firstShift.employeeId,
                fullName = firstShift.employeeName,
                isSelf = true
            )))
        }

        validateShifts(shifts)
        shifts
    }

    suspend fun fetchGroupRoster(): Result<List<Shift>> = runCatching {
        val allShifts = mutableListOf<Shift>()
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val referenceDateStr = today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

        // --- Sonda iniziale: leggo recordCountGiorni dall'HTML del server ---
        // Uso una richiesta leggera (1 dipendente) per scoprire quanti giorni ha il server.
        val probeResponse = client.get("$baseUrl/gruppo.jsp") {
            header(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            header(HttpHeaders.Referrer, "$baseUrl/index.jsp")
            header("X-Requested-With", "XMLHttpRequest")
            url {
                parameters.append("dataRiferimento", referenceDateStr)
                parameters.append("recNumUtentiFrom", "1")
                parameters.append("recNumUtentiTo", "1")   // Solo 1 dipendente → risposta leggera
                parameters.append("recNumGiorniFrom", "1")
                parameters.append("recNumGiorniTo", "1")
                parameters.append("adminGroupShiftDataToDisplay", "0")
            }
        }
        val probeHtml = probeResponse.bodyAsText()

        // <input id="recordCountGiorni-tabs-2" value="38" type="hidden">
        val totalDays = Regex("""id="recordCountGiorni[^"]*"\s+value="(\d+)"""")
            .find(probeHtml)?.groupValues?.get(1)?.toIntOrNull() ?: 38
        android.util.Log.e(TAG, "recordCountGiorni dal server: $totalDays")

        // Il server restituisce 7 giorni per richiesta. Step di 7 per copertura continua.
        val dayWindows = (1..totalDays step 7).map { from -> from to (from + 6) }
        android.util.Log.e(TAG, "Finestre da scaricare: ${dayWindows.size} (${dayWindows.first()} .. ${dayWindows.last()})")

        for ((giorniFrom, giorniTo) in dayWindows) {
            // Data reale della prima colonna di questa finestra
            val windowStartDate = today.plusDays((giorniFrom - 1).toLong())
            android.util.Log.e(TAG, "Finestra giorni $giorniFrom-$giorniTo → parte dal $windowStartDate")

            var empFrom = 1
            var empTo = 150
            var hasMoreEmployees = true
            var batchCount = 0

            while (hasMoreEmployees && batchCount < 10) {
                batchCount++
                val response = client.get("$baseUrl/gruppo.jsp") {
                    header(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    header(HttpHeaders.Referrer, "$baseUrl/index.jsp")
                    header("X-Requested-With", "XMLHttpRequest")
                    url {
                        parameters.append("dataRiferimento", referenceDateStr)
                        parameters.append("recNumUtentiFrom", empFrom.toString())
                        parameters.append("recNumUtentiTo", empTo.toString())
                        parameters.append("recNumGiorniFrom", giorniFrom.toString())
                        parameters.append("recNumGiorniTo", giorniTo.toString())
                        parameters.append("adminGroupShiftDataToDisplay", "0")
                    }
                }

                val html = response.bodyAsText()
                android.util.Log.d(TAG, "Batch emp=$empFrom-$empTo giorni=$giorniFrom-$giorniTo: status=${response.status.value} len=${html.length}")

                // Preview solo per il primo batch della prima finestra
                if (giorniFrom == 1 && batchCount == 1 && html.length > 100) {
                    android.util.Log.e(TAG, "HTML GRUPPO PREVIEW:\n${html.take(3000).replace("\n", " ").replace("\r", "")}")
                }

                if (response.status.value != 200 || !html.contains("<table", ignoreCase = true)) {
                    hasMoreEmployees = false
                } else {
                    val pageShifts = parseRosterHtml(html, isGroup = true, baseDateForBlock = windowStartDate)
                    android.util.Log.e(TAG, "Parsed giorni=$giorniFrom-$giorniTo emp=$empFrom-$empTo: ${pageShifts.size} turni")
                    if (pageShifts.isEmpty()) {
                        hasMoreEmployees = false
                    } else {
                        allShifts.addAll(pageShifts)
                        empFrom += 150
                        empTo += 150
                    }
                }
            }
        }

        val finalShifts = allShifts.distinctBy { it.employeeName + it.date.toString() + (it.startTime?.toString() ?: "") }
        android.util.Log.e(TAG, "fetchGroupRoster COMPLETO: ${finalShifts.size} turni unici")

        if (finalShifts.isEmpty()) {
            throw Exception("Nessun turno trovato nel roster di gruppo.")
        }

        validateShifts(finalShifts)
        finalShifts
    }

    private suspend fun parseRosterHtml(
        html: String, 
        isGroup: Boolean = false, 
        baseDateForBlock: LocalDate? = null
    ): List<Shift> {
        val doc = Jsoup.parse(html)
        
        if (!isGroup) {
            // Per il roster personale, usiamo ESCLUSIVAMENTE il parser della lista (utente.jsp)
            return parsePersonalRosterHtml(doc)
        }
        
        // Per il roster di gruppo, usiamo il parser della griglia (gruppo.jsp)
        return parseGridRosterHtml(doc, isGroup, baseDateForBlock)
    }

    private suspend fun parseGridRosterHtml(
        doc: org.jsoup.nodes.Document,
        isGroup: Boolean,
        baseDateForBlock: LocalDate?
    ): List<Shift> {
        val shifts = mutableListOf<Shift>()
        val profile = employeeDao.getSelf()
        
        val fullText = doc.text().uppercase()
        var detectedMonth = baseDateForBlock?.monthValue ?: Clock.System.todayIn(TimeZone.currentSystemDefault()).monthValue
        var detectedYear = baseDateForBlock?.year ?: Clock.System.todayIn(TimeZone.currentSystemDefault()).year
        
        val yearMatch = Regex("202[4-9]").find(fullText)
        if (yearMatch != null) { detectedYear = yearMatch.value.toInt() }

        // Mese e anno: usiamo baseDateForBlock come fonte primaria (calcolato con precisione dal chiamante).
        // Solo se è null, proviamo a leggere il mese dal testo HTML (fallback).
        if (baseDateForBlock == null) {
            val italianMonths = listOf("GENNAIO","FEBBRAIO","MARZO","APRILE","MAGGIO","GIUGNO",
                "LUGLIO","AGOSTO","SETTEMBRE","OTTOBRE","NOVEMBRE","DICEMBRE")
            for ((idx, monthName) in italianMonths.withIndex()) {
                if (fullText.contains(monthName)) {
                    detectedMonth = idx + 1
                    android.util.Log.e(TAG, "Mese letto dall'HTML (fallback): $monthName -> mese $detectedMonth/$detectedYear")
                    break
                }
            }
        } else {
            android.util.Log.d(TAG, "Mese da baseDateForBlock: $detectedMonth/$detectedYear (inizio finestra: $baseDateForBlock)")
        }

        val rows = doc.select("tr")
        var headerDateRowIdx = -1
        val colToDateMap = mutableMapOf<Int, LocalDate>()

        for (i in rows.indices) {
            val row = rows[i]
            val cells = row.select("td, th")
            var score = 0
            val rowColToDate = mutableMapOf<Int, LocalDate>()
            
            var lastDay = 0
            var currentMonth = detectedMonth
            var currentYear = detectedYear
            
            cells.forEachIndexed { colIdx, cell ->
                val text = cell.text().trim()
                // Cerchiamo un numero isolato (es. "24" o "VEN 24") che rappresenti il giorno
                val dayMatch = Regex("\\b(\\d{1,2})\\b").find(text)
                val day = dayMatch?.groupValues?.get(1)?.toIntOrNull()
                
                if (day != null && day in 1..31) {
                    // Verifichiamo che la cella non sia eccessivamente lunga (es. una riga di testo descrittivo)
                    if (text.length <= 25) {
                        score++
                        if (lastDay >= 25 && day < 10) {
                            currentMonth++
                            if (currentMonth > 12) {
                                currentMonth = 1
                                currentYear++
                            }
                        }
                        try {
                            rowColToDate[colIdx] = LocalDate(currentYear, currentMonth, day)
                            lastDay = day
                        } catch (_: Exception) {}
                    }
                }
            }
            // Alzo la soglia a 7 per essere più flessibili
            if (score >= 7) {
                headerDateRowIdx = i
                colToDateMap.putAll(rowColToDate)
                android.util.Log.e(TAG, "!!! HEADER IDENTIFICATO RIGA $i !!! (Score: $score, TotColonne: ${cells.size})")
                // Log di TUTTE le celle per vedere la struttura completa
                cells.forEachIndexed { idx, cell ->
                    val t = cell.text().trim()
                    if (t.isNotBlank()) android.util.Log.d(TAG, "  CELL[$idx] len=${t.length} -> '$t' -> mappato=${rowColToDate.containsKey(idx)}")
                }
                break
            } else if (score > 0) {
                android.util.Log.v(TAG, "Riga $i scartata come header (Score insufficiente: $score)")
            }
        }

        if (headerDateRowIdx == -1) {
            Log.e(TAG, "Impossibile trovare la griglia dei turni nell'HTML")
            return emptyList()
        }

        for (i in (headerDateRowIdx + 1) until rows.size) {
            val row = rows[i]
            val cells = row.select("td, th")
            if (cells.size <= (colToDateMap.keys.maxOrNull() ?: 0)) continue

            val nameCell = cells[0].text().trim()
            if (nameCell.isBlank()) continue
            if (nameCell.length < 5 && !nameCell.any { it.isDigit() }) continue
            if (nameCell.equals("SECURITY", ignoreCase = true)) continue

            // [SICUREZZA] Verifichiamo subito se questa riga è dell'utente
            val nameNumRegex = Regex("^(.+?)\\s+(\\d{3,8})\\s*$")
            val nameMatch = nameNumRegex.find(nameCell)
            val extractedName = nameMatch?.groupValues?.get(1)?.trim() ?: nameCell
            val rawId = nameMatch?.groupValues?.get(2)?.trim() ?: extractedName.uppercase().replace(" ", "_")
            val normalizedRawId = rawId.trimStart('0')
            val myNormalizedId = profile?.id?.trimStart('0')
            
            val isMe = (myNormalizedId != null && normalizedRawId == myNormalizedId) || 
                       (profile != null && extractedName.uppercase().contains(profile.fullName.uppercase().trim()))
            
            if (isMe) {
                android.util.Log.d(TAG, "Salto la riga personale '$extractedName' nel roster di GRUPPO per evitare inquinamento dati.")
                continue 
            }

            val rowRole = row.select("th[colspan]").firstOrNull()?.text()?.trim()

            colToDateMap.forEach { (colIdx, date) ->
                if (colIdx >= cells.size) return@forEach
                val cellValue = cells[colIdx].text().trim()
                if (cellValue.isNotBlank() && cellValue != "-") {
                    try {
                        val rawCodeWithRole = if (rowRole != null && !cellValue.contains(rowRole, ignoreCase = true)) {
                            "$cellValue $rowRole"
                        } else {
                            cellValue
                        }

                        val shift = xlsxParser.parseShiftCell(
                            rawCode = rawCodeWithRole,
                            employeeId = "group_$rawId",
                            employeeName = extractedName.uppercase(),
                            date = date.toJava(),
                            monthRosterId = 0
                        )
                        shifts.add(shift)
                    } catch (e: Exception) {
                        Log.w(TAG, "Errore parsing cella HTML: $cellValue per $extractedName il $date", e)
                    }
                }
            }
        }
        return shifts
    }

    private suspend fun parsePersonalRosterHtml(doc: org.jsoup.nodes.Document): List<Shift> {
        val shifts = mutableListOf<Shift>()
        val months = listOf("GENNAIO", "FEBBRAIO", "MARZO", "APRILE", "MAGGIO", "GIUGNO", "LUGLIO", "AGOSTO", "SETTEMBRE", "OTTOBRE", "NOVEMBRE", "DICEMBRE")
        
        // ESTRAZIONE IDENTITÀ: Prendiamo matricola e nome reale dal portale (provando più selettori)
        val identityCells = doc.select(".utenteTab td, .utenteTab table td, div.utenteTab table tr td")
        val realName = identityCells.getOrNull(0)?.text()?.trim() ?: ""
        val realMatricola = identityCells.getOrNull(1)?.text()?.trim() ?: ""
        
        android.util.Log.e("RosterScraper", "!!! IDENTITÀ PORTALE RILEVATA: Name='$realName', Matr='$realMatricola' !!!")

        if (realName.isBlank() || realMatricola.isBlank()) {
            Log.e(TAG, "ATTENZIONE: Identità non rilevata correttamente! (Name: '$realName', Matr: '$realMatricola')")
        } else {
            Log.d(TAG, "Identità rilevata con successo: $realName ($realMatricola)")
        }

        // SELEZIONE BLINDATA: Proviamo prima la tabella specifica, poi tutte le tabelle
        var rows = doc.select("table.turniDipendenteTable tr")
        if (rows.isEmpty()) {
            rows = doc.select("table tr")
        }

        var currentMonth = Clock.System.todayIn(TimeZone.currentSystemDefault()).monthValue
        var currentYear = Clock.System.todayIn(TimeZone.currentSystemDefault()).year
        var lastDetectedDay: Int? = null
        
        for (row in rows) {
            val text = row.text().uppercase()
            val allCells = row.select("td, th")
            
            // LOG DI DIAGNOSTICA: Vediamo cosa stiamo leggendo riga per riga
            if (text.isNotBlank()) Log.v(TAG, "[RIGA] $text")

            // FILTRO ANTI-MALIK: Se la riga contiene un ID dipendente (es. 4 cifre finali), è una riga di gruppo
            // Escludiamo gli anni (2024-2030) e i mesi per evitare falsi positivi
            val colleagueIdMatch = Regex("[A-Z]{3,}\\s?\\d{4}").find(text)
            if (colleagueIdMatch != null) {
                val matchValue = colleagueIdMatch.value
                val foundId = matchValue.takeLast(4)
                val isYear = foundId.toIntOrNull() in 2024..2030
                val isMonth = months.any { text.contains(it) }
                
                val isOurRow = (realMatricola.isNotBlank() && foundId == realMatricola) || 
                              (realName.isNotBlank() && text.contains(realName.uppercase())) ||
                              isYear || isMonth
                
                if (!isOurRow) {
                    // NON BREAKKARE PIÙ! Salta e basta, per evitare blocchi a metà mese
                    continue
                } else if (!isYear && !isMonth) {
                    Log.d(TAG, "Rilevato nostro ID o Nome ($foundId) in riga: OK")
                }
            }

            // FILTRO INTESTAZIONI: Se la riga contiene una sequenza di giorni (es. 23 24 25), scartala
            val isHeaderSequence = Regex("\\d{1,2}\\s+\\d{1,2}\\s+\\d{1,2}").containsMatchIn(text)
            if (isHeaderSequence) {
                if (text.length > 5) Log.d(TAG, "Riga scartata (intestazione giorni): ${text.take(30)}...")
                continue
            }

            // CRITERI DI ARRESTO (Muro di mattoni disattivato per debug)
            val hasGroupSignals = allCells.any { cell -> 
                val cls = cell.className()
                cls == "cellaTurnoGruppo" || cls == "turnoconfermato" || 
                cls == "turnodafirmare" || cls == "turnononconfermato"
            }
            if (hasGroupSignals) {
                val parentTable = row.closest("table")
                if (parentTable != null && !parentTable.hasClass("turniDipendenteTable")) {
                    Log.d(TAG, "Rilevato segnale gruppo in tabella esterna ($text). Salto riga.")
                    continue
                }
            }

            // Cerchiamo il mese/anno per aggiornare il contesto
            months.forEachIndexed { index, m ->
                if (text.contains(m)) {
                    currentMonth = index + 1
                    val yearMatch = Regex("202[4-9]").find(text)
                    if (yearMatch != null) {
                        currentYear = yearMatch.value.toInt()
                    }
                    lastDetectedDay = null
                    Log.d(TAG, "Contesto temporale aggiornato: $currentMonth/$currentYear")
                }
            }

            // ESTRAZIONE RIGA PERSONALE (Nuova Struttura: DATA (VEN 24) | TURNO (1103) | FUNZIONE (PAX) | ... | NOTE)
            // Cerchiamo il giorno nella prima o seconda cella
            val firstCellText = allCells.getOrNull(0)?.text()?.trim() ?: ""
            val secondCellText = allCells.getOrNull(1)?.text()?.trim() ?: ""
            
            // Cerchiamo il numero del giorno (es. "24" da "VEN 24")
            // Usiamo i word boundaries (\\b) per evitare di prendere pezzi dell'anno (es. il "20" di "2026")
            val dayMatchFirst = Regex("\\b(\\d{1,2})\\b").find(firstCellText)
            val dayMatchSecond = if (dayMatchFirst == null) Regex("\\b(\\d{1,2})\\b").find(secondCellText) else null
            
            val day = dayMatchFirst?.groupValues?.get(1)?.toIntOrNull() 
                      ?: dayMatchSecond?.groupValues?.get(1)?.toIntOrNull()
            
            if (day != null) {
                // Rollover mese se il giorno è molto più piccolo dell'ultimo (es. da 30 a 1)
                if (lastDetectedDay != null && day < lastDetectedDay!! - 15) {
                    currentMonth++
                    if (currentMonth > 12) {
                        currentMonth = 1
                        currentYear++
                    }
                    Log.d(TAG, "Passaggio al mese successivo rilevato: $currentMonth/$currentYear")
                }
                
                try {
                    val date = LocalDate(currentYear, currentMonth, day)
                    lastDetectedDay = day

                    // Il codice turno è ora nella cella index 1 (se il giorno era in index 0)
                    // o index 2 (se il giorno era separato). Proviamo a cercarlo.
                    val rawCode = if (dayMatchFirst != null) {
                        allCells.getOrNull(1)?.text()?.trim() ?: ""
                    } else {
                        allCells.getOrNull(2)?.text()?.trim() ?: ""
                    }
                    
                    if (rawCode.isNotBlank() && rawCode != "-" && !rawCode.all { it.isLetter() && it !in listOf('R', 'F') }) {
                        // La mansione è nella cella successiva al codice
                        val codeIdx = if (dayMatchFirst != null) 1 else 2
                        val role = allCells.getOrNull(codeIdx + 1)?.text()?.trim() ?: ""
                        
                        // Le note sono verso la fine della riga
                        val note = allCells.lastOrNull()?.text()?.trim() ?: ""
                        
                        val shift = xlsxParser.parseShiftCell(
                            rawCode = rawCode,
                            employeeId = realMatricola, 
                            employeeName = realName,
                            date = date.toJava(),
                            monthRosterId = 0
                        ).copy(role = role, notes = note)
                        
                        shifts.add(shift)
                        Log.d(TAG, "[SALVATO] Giorno $day: $rawCode $role (${shift.startTime}-${shift.endTime})")
                        if (note.isNotBlank() && note != role) Log.d(TAG, "   └─ Note: $note")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Errore riga $day: ${e.message}")
                }
            }
        }
        Log.d(TAG, "Parsing completato: ${shifts.size} turni totali per $realName")
        return shifts
    }

    private fun validateShifts(shifts: List<Shift>) {
        shifts.forEach { shift ->
            if (shift.date.year < 2024) {
                Log.e(TAG, "RILEVATO TURNO CON ANNO ERRATO: ${shift.date} per ${shift.employeeName}")
            }
        }
    }
}





