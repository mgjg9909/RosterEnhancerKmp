package com.matteo.rosterenhancer.domain.usecase

import com.matteo.rosterenhancer.data.repository.RosterRepository
import com.matteo.rosterenhancer.domain.model.Shift
import com.matteo.rosterenhancer.domain.model.ShiftType
import com.matteo.rosterenhancer.util.Duration
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.daysUntil
import com.matteo.rosterenhancer.util.*

data class RestSwapCandidate(
    val employeeId: String,
    val employeeName: String,
    val myWorkDate: LocalDate,
    val myRestDate: LocalDate,
    val theirShiftOnMyRestDate: Shift,
    val disqualificationReason: String? = null
)

class FindRestSwapCandidatesUseCase(
    private val repository: RosterRepository
) {
    /**
     * Trova i colleghi idonei per uno Scambio di Riposo.
     * 
     * Logica a specchio:
     * - IO voglio riposare nel `myWorkDate` (dove attualmente lavoro il `myShiftToDrop`).
     * - IO mi offro di lavorare nel `myRestDate` (dove attualmente riposo).
     * -> CERCO UN COLLEGA CHE:
     *    1. È di riposo nel `myWorkDate`.
     *    2. Lavora nel `myRestDate`.
     *    3. Il cui turno nel `myRestDate` dura quanto il mio `myShiftToDrop`.
     *    4. Nessuno dei due viola la regola delle 11 ore.
     *    5. Nessuno dei due supera i 6 giorni consecutivi di lavoro.
     *    6. Rispetta regole FAS/SPV/Genere identiche allo swap classico.
     */
    suspend operator fun invoke(
        myWorkDate: LocalDate,         // Giorno 1 (Mio Turno)
        myRestDate: LocalDate,         // Giorno 2 (Mio Riposo)
        myShiftToDrop: Shift,          // Il turno che cedo nel Giorno 1
        selfEmployeeId: String,
        userGender: String,
        isSupervisor: Boolean,
        isFas: Boolean
    ): List<RestSwapCandidate> {
        
        // 1. Estraiamo una "Timeline Larga" per noi e tutti i colleghi per calcolare i 6 giorni.
        // I 6 giorni si calcolano coprendo da (MinDate - 6) a (MaxDate + 6)
        val minDate = if (myWorkDate < myRestDate) myWorkDate else myRestDate
        val maxDate = if (myWorkDate > myRestDate) myWorkDate else myRestDate

        val windowShifts: List<Shift> = repository.getShiftsInRangeAsync(
            from = minDate.minusDays(7),
            to = maxDate.plusDays(7)
        )

        // Mappiamo i turni per dipendente, normalizzando l'ID per evitare duplicati
        val nameNumRegex = Regex("^(.+?)\\s+(\\d{3,8})\\s*$")
        val shiftsByEmployee = windowShifts.groupBy { shift ->
            val match = nameNumRegex.find(shift.employeeName)
            (match?.groupValues?.get(2)?.trim() ?: shift.employeeId).replace("group_", "")
        }
        val myTimeline = shiftsByEmployee[selfEmployeeId] ?: emptyList()

        val candidates = mutableListOf<RestSwapCandidate>()

        for ((colleagueId, theirTimeline) in shiftsByEmployee) {
            if (colleagueId == selfEmployeeId) continue

            // Loro devono riposare nel myWorkDate e lavorare nel myRestDate
            val theirShiftOnMyWorkDate = theirTimeline.firstOrNull { it.date == myWorkDate }
            val theirShiftOnMyRestDate = theirTimeline.firstOrNull { it.date == myRestDate }

            // Verifica Condizione di Base 1: Loro riposano quando io voglio riposare?
            if (theirShiftOnMyWorkDate?.shiftType == ShiftType.WORK) continue // Hanno già un turno
            
            // Verifica Condizione di Base 2: Loro lavorano quando io mi offro?
            if (theirShiftOnMyRestDate == null || theirShiftOnMyRestDate.shiftType != ShiftType.WORK) continue
            if (theirShiftOnMyRestDate.startTime == null || theirShiftOnMyRestDate.endTime == null) continue

            // Verifica Durata Ore
            if (theirShiftOnMyRestDate.durationHours != myShiftToDrop.durationHours) {
                // Non mostriamo chi ha orari diversi, altrimenti la lista esplode
                continue 
            }

            // --- REGOLE IDENTITARIE ---
            val theirRole = theirShiftOnMyRestDate.role ?: ""
            val upperRole = theirRole.uppercase()
            var invalidIdentity = false

            if (isFas) {
                if (upperRole != "FAS" && upperRole != "COF") invalidIdentity = true
            } else if (isSupervisor) {
                val spvRoles = setOf("SPV", "MBS", "BAG_S", "PES", "SBH")
                if (!spvRoles.contains(upperRole)) invalidIdentity = true
            } else {
                val spvFasRoles = setOf("SPV", "MBS", "BAG_S", "PES", "SBH", "FAS", "COF")
                if (spvFasRoles.contains(upperRole)) invalidIdentity = true

                if (userGender.uppercase() == "M" && upperRole.endsWith("F")) invalidIdentity = true
                if (userGender.uppercase() == "F" && upperRole.endsWith("M")) invalidIdentity = true
            }
            if (invalidIdentity) continue // Identità sbagliata = non visibile 


            // --- VINCOLI LEGALI (Generano la Disqualification) ---
            var disqualificationReason: String? = null

            // IO accetto un turno il myRestDate, quindi il myRestDate diventa LAVORO,
            // e io NON lavoro nel myWorkDate.
            val myHypotheticalTimeline = myTimeline.filter { it.date != myWorkDate && it.date != myRestDate }.toMutableList()
            myHypotheticalTimeline.add(theirShiftOnMyRestDate.copy(date = myRestDate))

            val myError = validateTimeline(myHypotheticalTimeline, "Tu")
            if (myError != null) {
                disqualificationReason = myError
            } else {
                // LORO accettano un turno nel myWorkDate (diventa LAVORO), 
                // e NON lavorano nel myRestDate
                val theirHypotheticalTimeline = theirTimeline.filter { it.date != myRestDate && it.date != myWorkDate }.toMutableList()
                theirHypotheticalTimeline.add(myShiftToDrop.copy(date = myWorkDate))

                val theirError = validateTimeline(theirHypotheticalTimeline, "Il collega")
                if (theirError != null) {
                    disqualificationReason = theirError
                }
            }

            // Pulizia nome per visualizzazione corretta (ID già pulito dal raggruppamento)
            val match = nameNumRegex.find(theirShiftOnMyRestDate.employeeName)
            val cleanName = match?.groupValues?.get(1)?.trim() ?: theirShiftOnMyRestDate.employeeName
            val cleanId = colleagueId 

            candidates.add(
                RestSwapCandidate(
                    employeeId = cleanId,
                    employeeName = cleanName,
                    myWorkDate = myWorkDate,
                    myRestDate = myRestDate,
                    theirShiftOnMyRestDate = theirShiftOnMyRestDate,
                    disqualificationReason = disqualificationReason
                )
            )
        }

        // Ritorniamo prima i validi, poi gli invalidi
        return candidates.sortedBy { it.disqualificationReason != null }
    }

    private fun getEndDT(date: LocalDate, shift: Shift): LocalDateTime {
        return if (shift.endTime!! <= shift.startTime!!) {
            date.plusDays(1).atTime(shift.endTime)
        } else {
            date.atTime(shift.endTime)
        }
    }

    private fun validateTimeline(timeline: List<Shift>, userNameDesc: String): String? {
        val workShifts = timeline.filter { it.shiftType == ShiftType.WORK && it.startTime != null && it.endTime != null }
            .sortedBy { it.date }

        // 1. Controllo 6 giorni consecutivi
        var consecutiveWork = 0
        var previousShiftDate: LocalDate? = null

        for (shift in workShifts) {
            if (previousShiftDate == null || previousShiftDate.plusDays(1) == shift.date) {
                consecutiveWork++
            } else {
                consecutiveWork = 1 
            }
            
            if (consecutiveWork > 6) {
                return "$userNameDesc avrebbe ${consecutiveWork} gg lavorativi consecutivi"
            }
            previousShiftDate = shift.date
        }

        // 2. Controllo Stacco Orario (11h o 35+h)
        for (i in 0 until workShifts.size - 1) {
            val currentShift = workShifts[i]
            val nextShift = workShifts[i + 1]

            val daysBetween = currentShift.date.daysUntil(nextShift.date) - 1
            if (daysBetween >= 0) {
                val requiredGap = 11 + (24 * daysBetween)
                val currentEnd = getEndDT(currentShift.date, currentShift)
                val nextStart = nextShift.date.atTime(nextShift.startTime!!)
                
                val actualGap = Duration.between(currentEnd, nextStart).toHours()
                
                if (actualGap < requiredGap) {
                    if (daysBetween == 0) return "$userNameDesc non ha le 11h minime di stacco"
                    return "$userNameDesc non avrebbe le ${requiredGap}h di riposo (ne ha solo ${actualGap}h)"
                }
            }
        }

        return null
    }
}


