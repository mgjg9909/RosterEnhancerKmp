package com.matteo.rosterenhancer.data.repository

import kotlinx.datetime.todayIn
import com.matteo.rosterenhancer.util.*
import kotlinx.datetime.periodUntil
import com.matteo.rosterenhancer.util.withDayOfMonth
import com.matteo.rosterenhancer.util.minusDays
import com.matteo.rosterenhancer.util.isBefore
import com.matteo.rosterenhancer.util.monthValue

import com.matteo.rosterenhancer.data.local.dao.EmployeeDao
import com.matteo.rosterenhancer.data.local.dao.MonthRosterDao
import com.matteo.rosterenhancer.data.local.dao.ShiftDao
import com.matteo.rosterenhancer.data.local.dao.ShiftNoteDao
import com.matteo.rosterenhancer.data.local.entity.EmployeeEntity
import com.matteo.rosterenhancer.data.local.entity.MonthRosterEntity
import com.matteo.rosterenhancer.data.local.entity.ShiftEntity
import com.matteo.rosterenhancer.data.local.entity.ShiftNoteEntity
import com.matteo.rosterenhancer.domain.model.Employee
import com.matteo.rosterenhancer.domain.model.Shift
import com.matteo.rosterenhancer.domain.model.ShiftNote
import com.matteo.rosterenhancer.domain.model.ShiftType
import com.matteo.rosterenhancer.domain.parser.XlsxParser
import com.matteo.rosterenhancer.data.network.RosterScraper
import com.matteo.rosterenhancer.data.security.CredentialsManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.LocalTime

import javax.inject.Singleton
import android.util.Log
import android.content.Context


import com.matteo.rosterenhancer.util.DataStoreManager

@Singleton
class RosterRepository constructor(
    private val context: Context,
    private val employeeDao: EmployeeDao,
    private val shiftDao: ShiftDao,
    private val monthRosterDao: MonthRosterDao,
    private val shiftNoteDao: ShiftNoteDao,
    private val scraper: RosterScraper,
    private val credentialsManager: CredentialsManager,
    private val dataStoreManager: DataStoreManager
) {

    // ─── Employees ───────────────────────────────────────────────────────────

    fun getAllEmployees(): Flow<List<Employee>> =
        employeeDao.getAllEmployees().map { list -> list.map { it.toDomain() } }

    fun searchEmployees(query: String): Flow<List<Employee>> =
        employeeDao.searchEmployees(query).map { list -> list.map { it.toDomain() } }

    suspend fun getSelf(): Employee? = employeeDao.getSelf()?.toDomain()

    suspend fun setSelf(matricola: String) {
        employeeDao.clearSelf()
        employeeDao.setSelf(matricola)
        dataStoreManager.setSelfMatricola(matricola)
        Log.d("RosterRepository", "Identità Self impostata su matricola: $matricola (DB + DataStore)")
    }

    suspend fun hasEmployees(): Boolean = employeeDao.count() > 0

    // ─── Shifts ──────────────────────────────────────────────────────────────

    fun getShiftsForDate(date: LocalDate): Flow<List<Shift>> =
        shiftDao.getShiftsForDate(date).map { list -> list.map { it.toDomain() } }

    fun getShiftsForEmployeeInRange(employeeId: String, from: LocalDate, to: LocalDate): Flow<List<Shift>> =
        shiftDao.getShiftsForEmployeeInRange(employeeId, from, to).map { list -> list.map { it.toDomain() } }

    fun getAllShiftsForEmployee(employeeId: String): Flow<List<Shift>> =
        shiftDao.getAllShiftsForEmployee(employeeId).map { list -> list.map { it.toDomain() } }

    fun getAllWorkShiftsInRange(from: LocalDate, to: LocalDate): Flow<List<Shift>> =
        shiftDao.getAllWorkShiftsInRange(from, to).map { list -> list.map { it.toDomain() } }

    fun getShiftsWorkingNow(date: LocalDate, currentTime: LocalTime): Flow<List<Shift>> {
        val yesterday = date.minusDays(1)
        return shiftDao.getShiftsWorkingNow(date, yesterday, currentTime).map { list -> list.map { it.toDomain() } }
    }

    fun getEmployeesOffToday(date: LocalDate): Flow<List<Shift>> =
        shiftDao.getEmployeesOffToday(date).map { list -> list.map { it.toDomain() } }

    suspend fun getShiftsInRangeAsync(from: LocalDate, to: LocalDate): List<Shift> =
        shiftDao.getShiftsInRangeAsync(from, to).map { it.toDomain() }

    suspend fun getTotalHoursInMonth(employeeId: String, month: Int, year: Int): Int {
        val from = LocalDate(year, month, 1)
        val to = from.withDayOfMonth(from.lengthOfMonth)
        return shiftDao.getTotalHoursForEmployee(employeeId, from, to) ?: 0
    }

    suspend fun countShiftsByType(employeeId: String, type: ShiftType, month: Int, year: Int): Int {
        val from = LocalDate(year, month, 1)
        val to = from.withDayOfMonth(from.lengthOfMonth)
        return shiftDao.countShiftsByType(employeeId, type.name, from, to)
    }

    suspend fun getAllRoles(): List<String> = shiftDao.getAllRoles()

    // ─── Note ─────────────────────────────────────────────────────────────────

    fun getNoteForShift(name: String, date: LocalDate): Flow<ShiftNote?> =
        shiftNoteDao.getNoteForShift(name, date).map { it?.toDomain() }

    fun getNotesInRangeFlow(from: LocalDate, to: LocalDate): Flow<List<ShiftNote>> =
        shiftNoteDao.getNotesInRangeFlow(from, to).map { list -> list.map { it.toDomain() } }

    suspend fun upsertNote(name: String, date: LocalDate, note: String, extraMinutes: Int) {
        if (note.isBlank() && extraMinutes == 0) {
            shiftNoteDao.delete(name, date)
        } else {
            shiftNoteDao.upsert(ShiftNoteEntity(name, date, note, extraMinutes))
        }
    }

    // ─── Rosters ─────────────────────────────────────────────────────────────

    fun getAllRosters(): Flow<List<MonthRosterEntity>> = monthRosterDao.getAllRosters()

    suspend fun getLatestRoster(): MonthRosterEntity? = monthRosterDao.getLatestRoster()

    suspend fun deleteRoster(roster: MonthRosterEntity) = monthRosterDao.delete(roster)

    suspend fun getOrCreateRosterId(month: Int, year: Int): Long {
        val existing = monthRosterDao.getRosterForMonth(month, year)
        return if (existing != null) {
            existing.id
        } else {
            monthRosterDao.insert(
                MonthRosterEntity(
                    month = month,
                    year = year,
                    fileName = "Inserimento Manuale",
                    employeeCount = 0
                )
            )
        }
    }

    suspend fun upsertManualShift(shift: Shift) {
        // 1. Ottieni matricola dal DataStore come fonte di verità
        val matricola = dataStoreManager.selfMatricola.first()
        
        // 2. Assicurati che l'utente esista nel DB e sia segnato come "Self"
        var self = if (matricola.isNotBlank()) employeeDao.getById(matricola) else null
        
        if (self == null && matricola.isNotBlank()) {
            // Crea record minimo se manca
            self = EmployeeEntity(id = matricola, fullName = "IO", isSelf = true)
            employeeDao.insertAll(listOf(self))
        } else if (self != null && !self.isSelf) {
            employeeDao.clearSelf()
            employeeDao.setSelf(matricola)
        }

        val finalEmployeeId = self?.id ?: shift.employeeId
        val finalEmployeeName = self?.fullName ?: shift.employeeName

        val rosterId = getOrCreateRosterId(shift.date.monthValue, shift.date.year)
        val autoHoliday = com.matteo.rosterenhancer.util.ItalianHolidayUtils.isHoliday(shift.date)
        
        val entity = shift.toEntity().copy(
            employeeId = finalEmployeeId,
            employeeName = finalEmployeeName,
            monthRosterId = rosterId,
            isHoliday = shift.isHoliday || autoHoliday,
            isManual = true
        )
        shiftDao.upsertShift(entity)
    }

    // ─── Import ──────────────────────────────────────────────────────────────

    /**
     * Importa il risultato del parsing nel database.
     * STRATEGIA: UPSERT — preserva le note (ShiftNoteEntity) anche se il roster viene re-importato.
     * I turni vengono aggiornati via INSERT OR REPLACE (usando il vincolo UNIQUE su employeeName+date).
     */
    suspend fun importRoster(
        parseResult: XlsxParser.ParseResult,
        fileName: String
    ): Result<String> = runCatching {
        processRosterData(
            shifts = parseResult.shifts,
            employees = parseResult.employees,
            sourceName = fileName
        )
    }

    /**
     * Sincronizza i turni dal portale web.
     */
    suspend fun syncWithWeb(): Result<String> = runCatching {
        val user = credentialsManager.getUsername() ?: throw Exception("Credenziali mancanti. Configurale nelle Impostazioni > Integrazione Cloud.")
        val pwd = credentialsManager.getPassword() ?: throw Exception("Credenziali mancanti. Configurale nelle Impostazioni > Integrazione Cloud.")

        // 1. Login
        scraper.login(user, pwd).getOrThrow()

        // 2. Scarica turni personali (obbligatorio — lancia eccezione se fallisce)
        val myShifts = scraper.fetchMyRoster().getOrElse { err ->
            throw Exception("Impossibile scaricare i turni personali: ${err.localizedMessage}")
        }

        // 3. Scarica turni gruppo (opzionale — usa lista vuota se fallisce)
        val groupShifts = scraper.fetchGroupRoster().getOrElse { err ->
            Log.w("RosterRepository", "Turni gruppo non disponibili: ${err.message}")
            emptyList<Shift>()
        }

        val allShifts = myShifts + groupShifts
        
        // Se anche i turni personali sono vuoti, il portale non ha restituito dati leggibili
        if (myShifts.isEmpty()) {
            throw Exception("Nessun turno personale trovato. Il portale potrebbe aver cambiato formato HTML — controlla i log di debug.")
        }

        // 4. Allineamento Identità Automatica: se abbiamo una matricola reale, impostiamola come Self
        val myFirstShift = myShifts.firstOrNull()
        val myName = myFirstShift?.employeeName
        val myId = myFirstShift?.employeeId
        
        if (!myId.isNullOrBlank() && myId != "self" && myId.all { it.isDigit() }) {
            Log.d("RosterRepository", "Auto-allineamento identità: impostazione matricola $myId come Self")
            setSelf(myId)
        }

        // 5. Crea lista dipendenti unica
        val employees = allShifts.groupBy { it.employeeName }.map { (name, shifts) ->
            val firstShift = shifts.first()
            val shiftId = firstShift.employeeId
            
            val finalId = when {
                name == myName -> myId ?: "self"
                shiftId == "group" -> "group_$name"
                else -> shiftId
            }
            Employee(id = finalId, fullName = name)
        }

        // 6. Processa l'inserimento nel database locale
        processRosterData(
            shifts = allShifts,
            employees = employees,
            sourceName = "Sincronizzazione Cloud"
        )

        // 7. Messaggio di successo con dettagli utili
        val groupInfo = if (groupShifts.isNotEmpty()) " + ${groupShifts.size} turni gruppo" else ""
        "Sincronizzato: ${myShifts.size} turni personali$groupInfo"
    }

    private suspend fun processRosterData(
        shifts: List<Shift>,
        employees: List<Employee>,
        sourceName: String
    ): String {
        // 1. VALIDAZIONE
        if (shifts.isEmpty()) {
            throw Exception("Nessun turno da importare!")
        }

        // 2. Mappa dei Roster per mese/anno (per gestire import multi-mese)
        val rosterIds = mutableMapOf<Pair<Int, Int>, Long>()
        
        // 3. Inserisci/Aggiorna dipendenti
        val selfEntry = employeeDao.getSelf()
        val selfId = selfEntry?.id
        val selfName = selfEntry?.fullName
        
        val employeeEntities = employees.map { emp ->
            val isCurrentSelf = emp.id == "self" || emp.id == selfId || emp.fullName.uppercase().trim() == selfName?.uppercase()?.trim()
            EmployeeEntity(
                id = emp.id,
                fullName = emp.fullName.uppercase().trim(),
                isSelf = isCurrentSelf
            )
        }
        employeeDao.insertAll(employeeEntities)

        // 4. Trasformazione in Entity e salvataggio
        val myMatricola = selfId
        val myName = selfName
        
        // [DIAGNOSTICA] Mappa per priorità: i turni personali devono vincere su quelli di gruppo
        val prioritizedShifts = mutableMapOf<Pair<String, LocalDate>, ShiftEntity>()

        shifts.forEach { shift ->
            val monthYear = shift.date.monthValue to shift.date.year
            val rId = rosterIds.getOrPut(monthYear) {
                val existing = monthRosterDao.getRosterForMonth(monthYear.first, monthYear.second)
                if (existing != null) {
                    monthRosterDao.update(existing.copy(fileName = sourceName, employeeCount = employees.size))
                    existing.id
                } else {
                    monthRosterDao.insert(MonthRosterEntity(
                        month = monthYear.first,
                        year = monthYear.second,
                        fileName = sourceName,
                        employeeCount = employees.size
                    ))
                }
            }

            val isThisShiftMe = (myMatricola != null && shift.employeeId == myMatricola) || 
                               (myName != null && shift.employeeName.uppercase().trim() == myName.uppercase().trim())
            
            val finalEmployeeId = if (isThisShiftMe && myMatricola != null) myMatricola else shift.employeeId
            val key = finalEmployeeId to shift.date
            
            val entity = shift.toEntity().copy(
                employeeId = finalEmployeeId,
                monthRosterId = rId,
                isManual = false
            )

            // Se è un turno personale, sovrascrivi sempre. Se è di gruppo, inserisci solo se non c'è già nulla.
            if (isThisShiftMe) {
                prioritizedShifts[key] = entity
            } else if (!prioritizedShifts.containsKey(key)) {
                prioritizedShifts[key] = entity
            }
        }
        
        val shiftEntities = prioritizedShifts.values.toList()

        // ── STRATEGIA "MERGE INTELLIGENTE" ───────────────────────────────────
        // Regole:
        //  1. Turni con data < oggi → MAI toccarli (cronologia storica intatta)
        //  2. Turni con data >= oggi → aggiorna solo se qualcosa è effettivamente cambiato
        //     (tipo turno, orario inizio, orario fine, ruolo)
        //  3. Colleghi: stessa logica, ma basata su (employeeId, date)
        // ─────────────────────────────────────────────────────────────────────

        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        var inserted = 0
        var updated = 0
        var skipped = 0

        shiftEntities.forEach { incoming ->
            // Regola 1: non toccare il passato
            if (incoming.date.isBefore(today)) {
                skipped++
                return@forEach
            }

            // Leggi il turno esistente per questo dipendente/data
            val existing = shiftDao.getShiftForEmployee(incoming.employeeId, incoming.date)

            if (existing == null) {
                // Nuovo turno → inserisci
                shiftDao.upsertShift(incoming)
                inserted++
            } else {
                // Turno già presente → aggiorna SOLO se qualcosa è cambiato
                val changed = existing.shiftType != incoming.shiftType ||
                              existing.startTime != incoming.startTime ||
                              existing.endTime != incoming.endTime ||
                              existing.durationHours != incoming.durationHours ||
                              existing.role != incoming.role ||
                              existing.rawCode != incoming.rawCode

                if (changed) {
                    // Preserva overtimeMinutes e isManual già esistenti, aggiorna il resto
                    shiftDao.upsertShift(incoming.copy(
                        id = existing.id,
                        overtimeMinutes = existing.overtimeMinutes,
                        overtimeStartTime = existing.overtimeStartTime,
                        overtimeEndTime = existing.overtimeEndTime,
                        isManual = existing.isManual,
                        isMensaLavorata = existing.isMensaLavorata,
                        notes = existing.notes
                    ))
                    updated++
                } else {
                    skipped++
                }
            }
        }

        Log.d("RosterRepository", "Import completato: $inserted inseriti, $updated aggiornati, $skipped saltati (totale elaborati: ${shiftEntities.size})")
        return "Roster aggiornato ($inserted nuovi, $updated modificati)"
    }

    suspend fun deleteShift(shift: Shift) {
        shiftDao.delete(shift.toEntity())
    }

    // ─── Mappers ─────────────────────────────────────────────────────────────

    private fun EmployeeEntity.toDomain() = Employee(
        id = id,
        fullName = fullName,
        isSelf = isSelf
    )

    private fun ShiftEntity.toDomain() = Shift(
        id = id,
        employeeId = employeeId,
        employeeName = employeeName,
        date = date,
        startTime = startTime,
        durationHours = durationHours,
        endTime = endTime,
        role = role,
        shiftType = ShiftType.valueOf(shiftType),
        rawCode = rawCode,
        monthRosterId = monthRosterId,
        overtimeMinutes = overtimeMinutes,
        overtimeStartTime = overtimeStartTime,
        overtimeEndTime = overtimeEndTime,
        isHoliday = isHoliday,
        isManual = isManual,
        isMensaLavorata = isMensaLavorata,
        notes = notes
    )

    private fun Shift.toEntity() = ShiftEntity(
        id = id,
        employeeId = employeeId,
        employeeName = employeeName,
        date = date,
        startTime = startTime,
        durationHours = durationHours,
        endTime = endTime,
        role = role,
        shiftType = shiftType.name,
        rawCode = rawCode,
        monthRosterId = monthRosterId,
        overtimeMinutes = overtimeMinutes,
        overtimeStartTime = overtimeStartTime,
        overtimeEndTime = overtimeEndTime,
        isHoliday = isHoliday,
        isManual = isManual,
        isMensaLavorata = isMensaLavorata,
        notes = notes
    )

    private fun ShiftNoteEntity.toDomain() = ShiftNote(
        employeeName = employeeName,
        date = date,
        note = note,
        extraMinutes = extraMinutes
    )
}







