package com.matteo.rosterenhancer.domain.parser

import android.util.Log
import com.matteo.rosterenhancer.domain.model.Employee
import com.matteo.rosterenhancer.domain.model.Shift
import com.matteo.rosterenhancer.domain.model.ShiftType
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import com.matteo.rosterenhancer.util.now
import com.matteo.rosterenhancer.util.monthValue
import com.matteo.rosterenhancer.util.plusHours
import com.matteo.rosterenhancer.util.plusDays
import com.matteo.rosterenhancer.domain.parser.ParseResult

private const val TAG = "XlsxParser"

/**
 * Parser XLSX leggero senza Apache POI.
 * Gestisce:
 *  - Date come numeri seriali Excel (es. 46112 = 1 apr 2026)
 *  - Date come numeri semplici (1, 2, 3… 31)
 *  - Stringhe condivise (t="s") e inline (t="inlineStr")
 *  - Rilevamento automatico della riga header con le date
 *  - Nome + matricola in colonna separata o stessa cella con \n
 */
@Singleton
class XlsxParser @Inject constructor() {

    fun parse(inputStream: InputStream, monthRosterId: Long = 0): ParseResult {
        // Leggi tutto il file ZIP in memoria
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(inputStream.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        Log.d(TAG, "ZIP entries: ${entries.keys}")

        // 1. Shared strings
        val sharedStrings = entries["xl/sharedStrings.xml"]
            ?.let { parseSharedStrings(it.inputStream()) }
            ?: emptyList()
        Log.d(TAG, "Shared strings count: ${sharedStrings.size}, first 5: ${sharedStrings.take(5)}")

        // 2. Trova tutti i fogli disponibili
        val sheetKeys = entries.keys
            .filter { it.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }
            .sorted()
        
        Log.d(TAG, "Sheet keys found: $sheetKeys")
        if (sheetKeys.isEmpty()) {
            return ParseResult(emptyList(), emptyList(), 0, 0,
                debugInfo = "Nessun foglio trovato nel file. Entries: ${entries.keys}")
        }

        val allEmployees = mutableListOf<Employee>()
        val allShifts = mutableListOf<Shift>()
        var mainMonth = 0
        var mainYear = 0
        val debugBuilder = StringBuilder()
        debugBuilder.appendLine("Entries ZIP: ${entries.keys.joinToString()}")
        debugBuilder.appendLine("Shared strings: ${sharedStrings.size}")
        debugBuilder.appendLine("Fogli trovati: ${sheetKeys.size}")

        for (sheetKey in sheetKeys) {
            Log.d(TAG, "Parsing sheet: $sheetKey")
            val rawRows = parseSheet(entries[sheetKey]!!.inputStream(), sharedStrings)
            if (rawRows.isEmpty()) continue
            
            val sheetResult = buildRoster(rawRows, monthRosterId, "Sheet: $sheetKey")
            
            // Unione intelligente: evita duplicati di dipendenti
            sheetResult.employees.forEach { emp ->
                if (allEmployees.none { it.fullName == emp.fullName }) {
                    allEmployees.add(emp)
                }
            }
            allShifts.addAll(sheetResult.shifts)
            
            if (mainMonth == 0) {
                mainMonth = sheetResult.month
                mainYear = sheetResult.year
            }
            debugBuilder.appendLine("--- Foglio $sheetKey ---")
            debugBuilder.appendLine(sheetResult.debugInfo)
        }

        Log.d(TAG, "Combined Result: ${allEmployees.size} employees, ${allShifts.size} shifts")
        
        return ParseResult(
            employees = allEmployees,
            shifts = allShifts,
            month = mainMonth,
            year = mainYear,
            debugInfo = debugBuilder.toString()
        )
    }

    // ─── Shared Strings ──────────────────────────────────────────────────────

    private fun parseSharedStrings(inputStream: InputStream): List<String> {
        val strings = mutableListOf<String>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var inT = false
        val currentText = StringBuilder()
        var inSi = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> { inSi = true; currentText.clear() }
                    "t"  -> { inT = true }
                }
                XmlPullParser.TEXT -> if (inT) currentText.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t"  -> inT = false
                    "si" -> { if (inSi) strings.add(currentText.toString().trim()); inSi = false }
                }
            }
            eventType = parser.next()
        }
        return strings
    }

    // ─── Sheet Parser ─────────────────────────────────────────────────────────

    /**
     * Parsa xl/worksheets/sheet1.xml.
     * Restituisce una matrice [riga][colonna] di stringhe.
     * Le celle vuote sono stringhe vuote, non null.
     */
    private fun parseSheet(inputStream: InputStream, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<MutableList<String>>()
        var currentRow = mutableListOf<String>()
        var currentRowIdx = -1
        var currentCellType = ""
        var currentCellRef = ""
        var inValue = false
        var inInlineStr = false
        val currentValue = StringBuilder()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> {
                        currentRow = mutableListOf()
                        currentRowIdx = parser.getAttributeValue(null, "r")?.toIntOrNull() ?: (rows.size + 1)
                    }
                    "c" -> {
                        currentCellType = parser.getAttributeValue(null, "t") ?: ""
                        currentCellRef = parser.getAttributeValue(null, "r") ?: ""
                        inValue = false
                        inInlineStr = false
                        currentValue.clear()
                    }
                    "v" -> { inValue = true; currentValue.clear() }
                    "t" -> if (currentCellType == "inlineStr" || inInlineStr) {
                        inInlineStr = true; currentValue.clear()
                    }
                    "is" -> inInlineStr = true
                }
                XmlPullParser.TEXT -> {
                    if (inValue || inInlineStr) currentValue.append(parser.text)
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "c" -> {
                        val colIdx = cellRefToColIndex(currentCellRef)
                        // Riempi gap con stringhe vuote
                        while (currentRow.size < colIdx) currentRow.add("")
                        val value = when {
                            currentCellType == "s" -> {
                                val idx = currentValue.toString().toIntOrNull() ?: -1
                                if (idx in sharedStrings.indices) sharedStrings[idx] else ""
                            }
                            currentCellType == "b" ->
                                if (currentValue.toString() == "1") "TRUE" else "FALSE"
                            currentCellType == "inlineStr" || inInlineStr ->
                                currentValue.toString().trim()
                            else -> currentValue.toString().trim()
                        }
                        currentRow.add(value)
                        inValue = false
                        inInlineStr = false
                    }
                    "row" -> {
                        if (currentRow.isNotEmpty()) rows.add(currentRow)
                    }
                }
            }
            eventType = parser.next()
        }
        return rows
    }

    // ─── Roster Builder ───────────────────────────────────────────────────────

    private fun buildRoster(rawRows: List<List<String>>, monthRosterId: Long, debug: String): ParseResult {
        if (rawRows.isEmpty()) return ParseResult(emptyList(), emptyList(), 0, 0, debugInfo = "Nessuna riga")

        val employees = mutableListOf<Employee>()
        val shifts = mutableListOf<Shift>()
        
        var currentDates = mutableMapOf<Int, LocalDate>()
        var lastDay = 0
        var currentMonth = 0
        var currentYear = 0
        
        // Stato per il rilevamento del nome
        var lastDetectedName: String? = null

        for (rowIdx in rawRows.indices) {
            val row = rawRows[rowIdx]
            if (row.isEmpty()) continue

            // 1. Controlla se questa riga è un header di date
            val headerScore = row.count { cell ->
                val num = cell.trim().toDoubleOrNull()
                num != null && (num in 1.0..31.0 || num > 40000.0)
            }

            if (headerScore >= 5) {
                // Nuova riga date trovata!
                val newDates = mutableMapOf<Int, LocalDate>()
                
                // Cerca metadati (Mese/Anno) nelle 3 righe sopra l'header se non li abbiamo ancora
                if (currentMonth == 0) {
                    val (m, y) = detectMonthYearFromTopRows(rawRows.subList((rowIdx - 3).coerceAtLeast(0), rowIdx))
                    currentMonth = m ?: LocalDate.now().monthValue
                    currentYear = y ?: LocalDate.now().year
                }

                var sheetLastDay = 0
                for (colIdx in row.indices) {
                    val cellValue = row[colIdx].trim()
                    val numMatch = Regex("\\b(0?[1-9]|[12][0-9]|3[01])(?:\\.0)?\\b").find(cellValue)
                    if (numMatch != null) {
                        val intVal = numMatch.groupValues[1].toInt()
                        
                        // Cambio mese
                        if (sheetLastDay >= 28 && intVal < 10) {
                            currentMonth++
                            if (currentMonth > 12) { currentMonth = 1; currentYear++ }
                        }
                        
                        try {
                            newDates[colIdx] = LocalDate(currentYear, currentMonth, intVal)
                            sheetLastDay = intVal
                        } catch (_: Exception) {}
                    }
                }
                
                if (newDates.isNotEmpty()) {
                    currentDates = newDates
                    lastDay = sheetLastDay
                    continue // Passa alla riga successiva per leggere i turni
                }
            }

            // 2. Se abbiamo delle date attive, prova a leggere la riga come dipendente + turni
            if (currentDates.isNotEmpty()) {
                val col0 = row.getOrNull(0)?.trim() ?: ""
                val col1 = row.getOrNull(1)?.trim() ?: ""
                if (col0.isBlank() && col1.isBlank()) continue

                var (fullName, matricola) = extractNameAndMatricola(row, rowIdx)
                fullName = fullName.uppercase().trim()

                val hasShifts = currentDates.keys.any { (row.getOrNull(it)?.trim() ?: "").isNotBlank() }
                
                // Gestione nomi su righe diverse
                if (!hasShifts && fullName.length > 5 && !fullName.all { it.isDigit() }) {
                    lastDetectedName = fullName
                    continue
                }
                if (hasShifts && (fullName.startsWith("ID_") || fullName.all { it.isDigit() })) {
                    if (lastDetectedName != null) {
                        if (fullName.all { it.isDigit() }) matricola = fullName
                        fullName = lastDetectedName
                    }
                }

                if (fullName.isNotBlank()) {
                    if (employees.none { it.fullName == fullName }) {
                        employees.add(Employee(id = matricola, fullName = fullName))
                    }
                    
                    for ((colIdx, date) in currentDates) {
                        val cellValue = row.getOrNull(colIdx)?.trim() ?: ""
                        if (cellValue.isBlank()) continue
                        
                        shifts.add(parseShiftCell(cellValue, matricola, fullName, date, monthRosterId))
                    }
                }
            }
        }

        val resultMonth = employees.firstOrNull()?.let { emp -> shifts.find { it.employeeName == emp.fullName }?.date?.monthValue } ?: currentMonth
        val resultYear = employees.firstOrNull()?.let { emp -> shifts.find { it.employeeName == emp.fullName }?.date?.year } ?: currentYear

        return ParseResult(
            employees = employees,
            shifts = shifts,
            month = resultMonth,
            year = resultYear,
            debugInfo = "$debug\nTurni: ${shifts.size}, Dipendenti: ${employees.size}"
        )
    }

    private fun monthName(m: Int) = when(m) {
        1 -> "Gennaio"; 2 -> "Febbraio"; 3 -> "Marzo"; 4 -> "Aprile";
        5 -> "Maggio"; 6 -> "Giugno"; 7 -> "Luglio"; 8 -> "Agosto";
        9 -> "Settembre"; 10 -> "Ottobre"; 11 -> "Novembre"; 12 -> "Dicembre";
        else -> "Mese $m"
    }

    /**
     * Trova l'indice della riga che contiene i numeri dei giorni (header date).
     */
    private fun findHeaderRow(rows: List<List<String>>): Int {
        var bestRowIdx = -1
        var bestScore = 0

        for ((idx, row) in rows.withIndex()) {
            if (row.isEmpty()) continue
            var score = 0
            for (cell in row) {
                // Prova a convertire in numero per gestire 30.0, 31.0
                val num = cell.trim().toDoubleOrNull()
                if (num != null) {
                    if (num in 1.0..31.0 || num > 40000.0) {
                        score++
                    }
                }
            }
            if (score > bestScore) {
                bestScore = score
                bestRowIdx = idx
            }
            // Non ci fermiamo a 7, vogliamo la riga con il punteggio massimo
        }

        return if (bestScore >= 3) bestRowIdx else -1
    }

    /**
     * Scansiona le righe sopra la tabella per cercare nomi di mesi e anni.
     */
    private fun detectMonthYearFromTopRows(rows: List<List<String>>): Pair<Int?, Int?> {
        val monthNames = listOf(
            listOf("GENNAIO", "JANUARY", "GEN"),
            listOf("FEBBRAIO", "FEBRUARY", "FEB"),
            listOf("MARZO", "MARCH", "MAR"),
            listOf("APRILE", "APRIL", "APR"),
            listOf("MAGGIO", "MAY", "MAG"),
            listOf("GIUGNO", "JUNE", "GIU"),
            listOf("LUGLIO", "JULY", "LUG"),
            listOf("AGOSTO", "AUGUST", "AGO"),
            listOf("SETTEMBRE", "SEPTEMBER", "SET"),
            listOf("OTTOBRE", "OCTOBER", "OTT"),
            listOf("NOVEMBRE", "NOVEMBER", "NOV"),
            listOf("DICEMBRE", "DECEMBER", "DIC")
        )

        var month: Int? = null
        var year: Int? = null

        for (row in rows) {
            for (cell in row) {
                val upperCell = cell.uppercase().trim()
                if (upperCell.isBlank()) continue

                // Cerca il mese
                if (month == null) {
                    for ((index, names) in monthNames.withIndex()) {
                        if (names.any { upperCell.contains(it) }) {
                            month = index + 1
                            break
                        }
                    }
                }

                // Cerca l'anno (una sequenza di 4 cifre tra 2024 e 2028)
                if (year == null) {
                    val yearMatch = Regex("202[4-8]").find(upperCell)
                    if (yearMatch != null) {
                        // Accettiamo l'anno solo se la cella contiene altro testo 
                        // o se abbiamo già trovato il mese, per non confonderlo con le matricole
                        if (upperCell.length > 4 || month != null) {
                            year = yearMatch.value.toInt()
                        }
                    }
                }
            }
        }
        return Pair(month, year)
    }

    /**
     * Estrae nome e matricola da una riga.
     * Gestisce vari formati:
     *  - Col 0 = "ROSSI MARIO\n12345" (stessa cella con newline)
     *  - Col 0 = "ROSSI MARIO", Col 1 = "12345"
     *  - Col 0 = "ROSSI MARIO", Col 1 = NON numerico (matricola assente)
     */
    private fun extractNameAndMatricola(row: List<String>, rowIdx: Int): Pair<String, String> {
        val firstCell = row.getOrNull(0)?.trim() ?: ""

        // Controlla se il nome contiene un newline (es. "ROSSI MARIO\n12345")
        if (firstCell.contains('\n')) {
            val parts = firstCell.split('\n')
            val name = parts[0].trim()
            val matricola = parts.getOrNull(1)?.trim()?.filter { it.isDigit() } ?: rowIdx.toString()
            if (name.isNotBlank()) return Pair(name, matricola.ifBlank { rowIdx.toString() })
        }

        // Col 1 = matricola numerica?
        val col1 = row.getOrNull(1)?.trim() ?: ""
        if (col1.isNotBlank() && col1.all { it.isDigit() } && col1.length in 3..8) {
            return Pair(firstCell, col1)
        }

        // Col 0 finisce con numeri? (es. "ROSSI MARIO 12345")
        val nameNumRegex = Regex("^(.+?)\\s+(\\d{3,8})\\s*$")
        val match = nameNumRegex.find(firstCell)
        if (match != null) {
            return Pair(match.groupValues[1].trim(), match.groupValues[2])
        }

        // Fallback: usa il rowIdx come matricola
        return Pair(firstCell, rowIdx.toString())
    }

    // ─── Shift Cell Parser ────────────────────────────────────────────────────

    /**
     * Decodifica la cella turno.
     * Formato: [3 cifre ora][1 cifra durata] [MANSIONE]
     *   - Cifra 1+2 = ore (00-23)
     *   - Cifra 3   = minuti: 0→:00, 3→:30
     *   - Cifra 4   = durata in ore
     * Es: "0338 BAG" → 03:30, 8h, mansione BAG → fine 11:30
     */
    fun parseShiftCell(
        rawCode: String,
        employeeId: String,
        employeeName: String,
        date: LocalDate,
        monthRosterId: Long = 0
    ): Shift {
        val nameNorm = employeeName.uppercase().trim()
        val code = rawCode.trim().uppercase()

        val shiftType = when {
            code == "R1" || code.startsWith("R1 ") || code.startsWith("R1\n") -> ShiftType.REST_1
            code == "R2" || code.startsWith("R2 ") || code.startsWith("R2\n") -> ShiftType.REST_2
            code == "RO" || code.startsWith("RO ") || code.startsWith("RO\n") -> ShiftType.DAY_OFF
            code.startsWith("ASSENTE") -> ShiftType.ABSENT
            code == "CP" || code.startsWith("CP ") -> ShiftType.PARENTAL_LEAVE
            // Turno lavorativo: contiene una sequenza di 3 o 4 cifre
            Regex(".*\\d{3,4}.*").find(code) != null -> ShiftType.WORK
            else -> ShiftType.OTHER
        }

        if (shiftType != ShiftType.WORK) {
            return Shift(
                employeeId = employeeId, employeeName = nameNorm, date = date,
                shiftType = shiftType, rawCode = rawCode, monthRosterId = monthRosterId
            )
        }

        return try {
            val workMatch = Regex("\\d{3,4}").find(code) ?: throw Exception("Turno non riconosciuto")
            val timeCode = workMatch.value
            
            // La mansione è ciò che rimane nel testo, escludendo il codice numerico
            val role = code.replace(timeCode, "")
                .replace("[", "").replace("]", "")
                .trim().split(" ", "\n", "-").firstOrNull { it.length >= 2 } ?: ""

            val (hour, minute, duration) = when (timeCode.length) {
                4 -> {
                    val h = timeCode.substring(0, 2).toInt()
                    val m = if (timeCode[2] == '3') 30 else 0
                    val d = timeCode[3].digitToInt()
                    Triple(h, m, d)
                }
                3 -> {
                    val h = timeCode.substring(0, 1).toInt()
                    val m = if (timeCode[1] == '3') 30 else 0
                    val d = timeCode[2].digitToInt()
                    Triple(h, m, d)
                }
                else -> Triple(0, 0, 0)
            }

            val startTime = LocalTime(hour.coerceIn(0, 23), minute)
            val endTime   = startTime.plusHours(duration.toLong())

            Log.d(TAG, "DEBUG PARSER: Code=$code -> TimeCode=$timeCode -> H=$hour, M=$minute, Dur=$duration -> Range: $startTime-$endTime")

            Shift(
                employeeId = employeeId, employeeName = nameNorm, date = date,
                startTime = startTime, durationHours = duration, endTime = endTime,
                role = role, shiftType = ShiftType.WORK, rawCode = rawCode,
                monthRosterId = monthRosterId
            )
        } catch (e: Exception) {
            Log.w(TAG, "DEBUG PARSER ERROR: Failed to parse code $code: ${e.message}")
            Shift(
                employeeId = employeeId, employeeName = nameNorm, date = date,
                shiftType = ShiftType.OTHER, rawCode = rawCode, monthRosterId = monthRosterId
            )
        }
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    /** Converte riferimento cella Excel (es. "A1", "AB3") all'indice colonna 0-based */
    private fun cellRefToColIndex(ref: String): Int {
        val colStr = ref.takeWhile { it.isLetter() }
        if (colStr.isEmpty()) return 0
        var index = 0
        for (c in colStr) index = index * 26 + (c.uppercaseChar() - 'A' + 1)
        return index - 1
    }

    /**
     * Converte un numero seriale Excel in LocalDate.
     * Base: 30 dicembre 1899 (compatibilità con il bug di Lotus 1-2-3).
     */
    private fun excelSerialToDate(serial: Int): LocalDate? = try {
        LocalDate(1899, 12, 30).plusDays(serial.toLong())
            .takeIf { it.year in 2000..2100 }
    } catch (_: Exception) { null }
}

