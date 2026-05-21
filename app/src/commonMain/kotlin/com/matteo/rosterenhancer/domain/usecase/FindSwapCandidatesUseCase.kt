package com.matteo.rosterenhancer.domain.usecase

import com.matteo.rosterenhancer.data.repository.RosterRepository
import com.matteo.rosterenhancer.domain.model.Shift
import com.matteo.rosterenhancer.domain.model.ShiftType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import com.matteo.rosterenhancer.util.*
import com.matteo.rosterenhancer.util.Duration

data class SwapCandidate(
    val employeeId: String,
    val employeeName: String,
    val proposedShift: Shift,
    val isFavorite: Boolean = false,
    val sharedHoursWithUser: Int = 0, // Estensione futura (Fase 3 info)
    val yesterdayShift: Shift? = null,
    val tomorrowShift: Shift? = null,
    val validationError: String? = null
)

class FindSwapCandidatesUseCase(
    private val repository: RosterRepository
) {
    /**
     * Trova tutti i colleghi che lavorano nella data specificata (e che hanno un turno DIVERSO dal mio)
     * effettuando la validazione incrociata delle 11 ore di riposo.
     * 
     * @param targetDate La data del cambio (oggi, domani, ecc.)
     * @param myShift Il turno che IO devo fare in quella data (che voglio cedere)
     * @param selfEmployeeId La mia matricola
     * @return Lista di candidati validi con cui posso effettuare lo scambio in regola.
     */
    suspend operator fun invoke(
        targetDate: LocalDate,
        myShift: Shift,
        selfEmployeeId: String,
        userGender: String,
        isSupervisor: Boolean,
        isFas: Boolean
    ): List<SwapCandidate> {
        // Se il mio turno non è lavorativo, non posso scambiarlo con un altro turno
        if (myShift.shiftType != ShiftType.WORK || myShift.startTime == null || myShift.endTime == null) {
            return emptyList()
        }

        // Il mio turno, convertito in DateTime esatti
        val myStartDT = targetDate.atTime(myShift.startTime)
        val myEndDT = if (myShift.endTime <= myShift.startTime) targetDate.plusDays(1).atTime(myShift.endTime) else targetDate.atTime(myShift.endTime)

        // 1. Estraiamo tutti i turni del database nel range esteso: [D-2, D-1, Oggi, D+1, D+2]
        // Serve per vedere se ci sono riposi o turni di lavoro che influenzano le 35 ore.
        val windowShifts: List<Shift> = repository.getShiftsInRangeAsync(
            from = targetDate.minusDays(2),
            to = targetDate.plusDays(2)
        )

        // Mappiamo i turni per dipendente, normalizzando l'ID per evitare duplicati
        val nameNumRegex = Regex("^(.+?)\\s+(\\d{3,8})\\s*$")
        val shiftsByEmployee = windowShifts.groupBy { shift ->
            val match = nameNumRegex.find(shift.employeeName)
            (match?.groupValues?.get(2)?.trim() ?: shift.employeeId).replace("group_", "")
        }

        // Timeline dell'utente
        val myTimeline = shiftsByEmployee[selfEmployeeId] ?: emptyList()
        println("DEBUG_SEARCH: Starting search for ${shiftsByEmployee.size} potential colleagues")
        
        val validCandidates = mutableListOf<SwapCandidate>()

        for ((colleagueId, theirTimeline) in shiftsByEmployee) {
            if (colleagueId == selfEmployeeId) continue
            
            val theirShiftToday = theirTimeline.firstOrNull { it.date == targetDate }

            // Loro devono avere un turno LAVORATIVO oggi per lo swap della stessa giornata
            if (theirShiftToday == null || theirShiftToday.shiftType != ShiftType.WORK) continue
            if (theirShiftToday.startTime == null || theirShiftToday.endTime == null) continue

            // Non ha senso scambiare lo stesso identico turno
            if (theirShiftToday.startTime == myShift.startTime && theirShiftToday.endTime == myShift.endTime) continue
            
            // --- VINCOLO DURATA ---
            // Si può scambiare solo con colleghi che lavorano le STESSE ORE (es. 8h con 8h, 3h con 3h)
            if (theirShiftToday.durationHours != myShift.durationHours) {
                println("DEBUG_SEARCH: Skipping ${theirShiftToday.employeeName} - Duration mismatch (${theirShiftToday.durationHours}h vs ${myShift.durationHours}h)")
                continue
            }
            
            // --- IDENTITÀ & RESTRIZIONI DI RUOLO ---
            val theirRole = theirShiftToday.role ?: ""
            val upperRole = theirRole.uppercase()

            if (isFas) {
                // I FAS cambiano solo con FAS e COF
                if (upperRole != "FAS" && upperRole != "COF") {
                    println("DEBUG_SEARCH: Skipping ${theirShiftToday.employeeName} - FAS Role mismatch ($upperRole)")
                    continue
                }
            } else if (isSupervisor) {
                // I SPV cambiano solo con il loro cluster
                val spvRoles = setOf("SPV", "MBS", "BAG_S", "PES", "SBH")
                if (!spvRoles.contains(upperRole)) {
                    println("DEBUG_SEARCH: Skipping ${theirShiftToday.employeeName} - SPV Role mismatch ($upperRole)")
                    continue
                }
            } else {
                // Normale operatore. Escludiamo i ruoli esclusivi FAS/SPV per simmetria
                val spvFasRoles = setOf("SPV", "MBS", "BAG_S", "PES", "SBH", "FAS", "COF")
                if (spvFasRoles.contains(upperRole)) {
                    println("DEBUG_SEARCH: Skipping ${theirShiftToday.employeeName} - Normal operator cannot swap with Special role ($upperRole)")
                    continue
                }

                // Check di Genere
                if (userGender.uppercase() == "M") {
                    if (upperRole.endsWith("F")) {
                        println("DEBUG_SEARCH: Skipping ${theirShiftToday.employeeName} - Gender mismatch (Male vs Female role)")
                        continue
                    }
                } else if (userGender.uppercase() == "F") {
                    if (upperRole.endsWith("M")) {
                        println("DEBUG_SEARCH: Skipping ${theirShiftToday.employeeName} - Gender mismatch (Female vs Male role)")
                        continue
                    }
                }
            }
            
            val theirStartDT = targetDate.atTime(theirShiftToday.startTime)
            val theirEndDT = if (theirShiftToday.endTime <= theirShiftToday.startTime) targetDate.plusDays(1).atTime(theirShiftToday.endTime) else targetDate.atTime(theirShiftToday.endTime)

            // --- TEST 1: IO posso fare IL LORO orario? ---
            var errorCause = validateRestRules(
                proposedStart = theirStartDT,
                proposedEnd = theirEndDT,
                timeline = myTimeline,
                targetDate = targetDate,
                isMyTimeline = true
            )

            // --- TEST 2: LORO possono fare IL MIO orario? ---
            if (errorCause == null) {
                errorCause = validateRestRules(
                    proposedStart = myStartDT,
                    proposedEnd = myEndDT,
                    timeline = theirTimeline,
                    targetDate = targetDate,
                    isMyTimeline = false
                )
            }

            if (errorCause != null) {
                println("DEBUG_SEARCH: Skipping ${theirShiftToday.employeeName} - Validation error: $errorCause")
                continue
            }

            println("DEBUG_SEARCH: Found valid candidate: ${theirShiftToday.employeeName}")

            val theirYesterday = theirTimeline.firstOrNull { it.date == targetDate.minusDays(1) }
            val theirTomorrow = theirTimeline.firstOrNull { it.date == targetDate.plusDays(1) }

            // Pulizia nome per visualizzazione corretta (ID già pulito dal raggruppamento)
            val match = nameNumRegex.find(theirShiftToday.employeeName)
            val cleanName = match?.groupValues?.get(1)?.trim() ?: theirShiftToday.employeeName
            val cleanId = colleagueId 

            validCandidates.add(
                SwapCandidate(
                    employeeId = cleanId,
                    employeeName = cleanName,
                    proposedShift = theirShiftToday,
                    yesterdayShift = theirYesterday,
                    tomorrowShift = theirTomorrow,
                    validationError = null
                )
            )
        }

        println("DEBUG_SEARCH: Search finished. Found ${validCandidates.size} candidates (including those with errors)")
        return validCandidates
    }

    /**
     * Verifica la regola delle 11 ore (riposo giornaliero) e delle 35 ore (riposo con riposo settimanale).
     */
    private fun validateRestRules(
        proposedStart: LocalDateTime, 
        proposedEnd: LocalDateTime, 
        timeline: List<Shift>, 
        targetDate: LocalDate,
        isMyTimeline: Boolean
    ): String? {
        // 1. Controllo verso il PASSATO (Fine ultimo turno -> Inizio proposto)
        val prevWork = timeline.filter { 
            it.date < targetDate && it.shiftType == ShiftType.WORK && it.endTime != null && it.startTime != null 
        }.maxByOrNull { it.date }

        if (prevWork != null) {
            val yEnd = if (prevWork.endTime!! <= prevWork.startTime!!) 
                           prevWork.date.plusDays(1).atTime(prevWork.endTime)
                       else 
                           prevWork.date.atTime(prevWork.endTime)

            // C'è un riposo (R1, R2, RO) tra il turno precedente e oggi?
            val hasRestBetween = timeline.any { 
                it.date > prevWork.date && it.date < targetDate && 
                (it.shiftType == ShiftType.REST_1 || it.shiftType == ShiftType.REST_2 || it.shiftType == ShiftType.DAY_OFF)
            }

            val gapMinutes = Duration.between(yEnd, proposedStart).toMinutes()
            val required = if (hasRestBetween) 35 else 11
            val requiredMinutes = required * 60L
            if (gapMinutes < requiredMinutes) {
                return if (isMyTimeline) "NON PUOI fare questo turno (viola $required ore di riposo dal tuo turno precedente)."
                       else "IL COLLEGA non può fare il tuo turno (viola $required ore di riposo dal suo turno precedente)."
            }
        }

        // 2. Controllo verso il FUTURO (Fine proposto -> Inizio turno domani/dopodomani)
        val nextWork = timeline.filter { 
            it.date > targetDate && it.shiftType == ShiftType.WORK && it.startTime != null && it.endTime != null 
        }.minByOrNull { it.date }

        if (nextWork != null) {
            val tomStart = nextWork.date.atTime(nextWork.startTime!!)

            // C'è un riposo tra oggi e il turno successivo?
            val hasRestBetween = timeline.any { 
                it.date > targetDate && it.date < nextWork.date && 
                (it.shiftType == ShiftType.REST_1 || it.shiftType == ShiftType.REST_2 || it.shiftType == ShiftType.DAY_OFF)
            }

            val gapMinutes = Duration.between(proposedEnd, tomStart).toMinutes()
            val required = if (hasRestBetween) 35 else 11
            val requiredMinutes = required * 60L
            if (gapMinutes < requiredMinutes) {
                return if (isMyTimeline) "NON PUOI fare questo turno (viola $required ore di riposo al tuo turno successivo)."
                       else "IL COLLEGA non può fare il tuo turno (viola $required ore di riposo al suo turno successivo)."
            }
        }

        return null
    }
}


