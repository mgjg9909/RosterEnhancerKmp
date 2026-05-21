package com.matteo.rosterenhancer.domain.calculator

import com.matteo.rosterenhancer.domain.model.*
import kotlinx.datetime.DayOfWeek
import com.matteo.rosterenhancer.util.Duration
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalDate
import com.matteo.rosterenhancer.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * Calcolatore stipendio conforme al CCNL Trasporto Aereo - Gestori Aeroportuali.
 * Implementa il calcolo lordo → netto a cascata con tutte le voci retributive
 * e le trattenute fiscali/previdenziali italiane.
 *
 * Costanti basate su: Livello 7, Full-Time (100%), anno fiscale 2024.
 */
class SalaryCalculator(
    private val profile: GpgProfile
) {

    // ─── Paga oraria base dal CCNL ───────────────────────────────────────────
    // La paga oraria reale viene calcolata da CcnlTables in base a livello + scatti.
    // Per livello 7 senza scatti: 1601.86 / 173 = 9.25879... ≈ 9.15335 (base di calcolo CCNL)
    private val dynamicBaseMonthly = CcnlTables.mensilitaBase2024[profile.level]!! +
        (CcnlTables.scattiAnzianitaMensili[profile.level]!! * profile.gpgSenioritySteps.coerceIn(0, 6))

    // Paga mensile scalata per part-time
    private val monthlyBasePay = dynamicBaseMonthly * (profile.partTimePercentage / 100.0)

    companion object {

        // ── Stipendio base mensile lordo (Livello 7, Full-Time) ──────────────
        const val BASE_MONTHLY_PAY = 1601.86

        // ── Base oraria di calcolo per le MAGGIORAZIONI (non è la paga oraria!) ──
        // Usato esclusivamente per moltiplicare le maggiorazioni (notturno, domenicale, etc.)
        const val CALC_HOURLY_BASE = 9.15335

        // ── Indennità giornaliere fisse (per ogni giorno lavorato) ──────────
        const val INDENNITA_GIORNALIERA   = 3.68    // Indennità giornaliera
        const val INCREMENTO              = 3.70    // Incremento contrattuale
        const val INDENNITA_TURNO         = 0.26    // Indennità di turno
        const val INDENNITA_CAMPO         = 0.21    // Indennità di campo
        const val TOTAL_DAILY_INDEMNITY   = INDENNITA_GIORNALIERA + INCREMENTO + INDENNITA_TURNO + INDENNITA_CAMPO // 7.85

        // ── Quota mensa (se permesso mensa goduto) ───────────────────────────
        const val MENSA_PAY = 9.25931

        // ── Maggiorazioni: percentuali CCNL espresse in €/h assoluti ────────
        // Notturno (20:00-08:00): +50% della base di calcolo
        const val NIGHT_BONUS_PER_HOUR    = CALC_HOURLY_BASE * 0.50  // 4.57668 €/h
        // Domenicale: +10% della base di calcolo
        const val SUNDAY_BONUS_PER_HOUR   = CALC_HOURLY_BASE * 0.10  // 0.91534 €/h
        // Festivo diurno: +45% della base di calcolo (la tariffa si somma al normale)
        const val HOLIDAY_BONUS_PER_HOUR  = CALC_HOURLY_BASE * 0.45  // 4.11901 €/h

        // ── Straordinari ─────────────────────────────────────────────────────
        // Soglia mensile standard di ore ordinarie
        const val MONTHLY_HOURS_THRESHOLD = 173.0
        // Straordinario diurno feriale: 125% della paga oraria (calcolata sulla base mensile/173)
        const val OVERTIME_MULTIPLIER     = 1.25

        // ── Mancato Riposo (solo se presenti nel CCNL) ───────────────────────
        const val R1_MULTIPLIER = 0.30
        const val R2_MULTIPLIER = 0.40

        // ════════════════════════════════════════════════════════════════════════
        // ALGORITMO LORDO → NETTO (a cascata)
        // ════════════════════════════════════════════════════════════════════════

        // ── INPS (quota dipendente) ───────────────────────────────────────────
        // Somma delle aliquote: FPLD 9.19% + CIG 0.30% + Fondo Solidarietà Aereo 0.167% + FIS 0.267%
        const val INPS_RATE = 0.09924  // 9.924%

        // ── IRPEF - Scaglioni 2024 ───────────────────────────────────────────
        // Per redditi fino a 28.000€: aliquota 23%
        // (semplificato: dipendente aeroportuale L7 FT è tipicamente in primo scaglione)
        const val IRPEF_RATE_FIRST_BRACKET = 0.23

        // ── Detrazione lavoro dipendente (Art. 13 TUIR) ──────────────────────
        // Per redditi 15.000€-28.000€ annui:
        // Formula: 1.910 + 1.190 * (28.000 - reddito) / 13.000
        // Costanti per calcolo mensile
        const val DETR_LAVDIP_15K_28K_BASE = 1910.0    // Detrazione base annua €
        const val DETR_LAVDIP_15K_28K_VAR  = 1190.0    // Quota variabile annua €
        const val DETR_LAVDIP_REDDITO_MAX  = 28000.0
        const val DETR_LAVDIP_REDDITO_15K  = 15000.0

        // ── Addizionali locali (Emilia-Romagna, Sala Bolognese) ──────────────
        // Addizionale Regionale Emilia-Romagna 2024: 2.33% (mensile = /12)
        const val ADDIZIONALE_REGIONALE = 0.0233
        // Addizionale Comunale Sala Bolognese: 0.8% (mensile = /12)
        const val ADDIZIONALE_COMUNALE  = 0.008
    }

    // ─── Paga oraria ordinaria (per il calcolo della paga base quotidiana) ───
    // Questa è la paga oraria effettiva basata sul contratto e sugli scatti
    private val baseHourlyPay: Double get() = monthlyBasePay / CcnlTables.MONTHLY_DIVISOR

    fun getBaseMonthlyHoursThreshold(month: Int, year: Int): Double {
        val daysInMonth = YearMonth.of(year, month).lengthOfMonth
        val workingDays = daysInMonth - 8 // 8 riposi mensili fissi
        return (workingDays * 8.0) * (profile.partTimePercentage / 100.0)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // CALCOLO MENSILE
    // ════════════════════════════════════════════════════════════════════════════

    fun calculateMonthlySummary(month: Int, year: Int, shifts: List<Shift>): MonthlySummary {
        val dailySummaries = shifts.map { calculateDailySummary(it) }

        val totalHours          = dailySummaries.sumOf { it.totalHours }
        val manualOvertimeHours = dailySummaries.sumOf { it.manualOvertimeHours }
        val manualOvertimePay   = dailySummaries.sumOf { it.manualOvertimePay }
        val ordinaryWorkedHours = totalHours - manualOvertimeHours

        val totalNightBonus       = dailySummaries.sumOf { it.nightBonusPay }
        val totalSundayBonus      = dailySummaries.sumOf { it.sundayBonusPay }
        val totalHolidayBonus     = dailySummaries.sumOf { it.holidayBonusPay }
        val totalPresenceIndemnity = dailySummaries.sumOf { it.presenceIndemnity }
        val totalMensaPay         = dailySummaries.sumOf { it.mensaPay }
        val totalRestBonus        = dailySummaries.sumOf { it.restBonusPay }

        val baseHoursThreshold = getBaseMonthlyHoursThreshold(month, year)
        val ordinaryHours      = ordinaryWorkedHours.coerceAtMost(baseHoursThreshold)
        val extraHours         = max(0.0, ordinaryWorkedHours - baseHoursThreshold)

        // Straordinario automatico oltre soglia mensile (125% come da CCNL)
        val autoOvertimePay    = extraHours * baseHourlyPay * OVERTIME_MULTIPLIER
        val totalOvertimePay   = manualOvertimePay + autoOvertimePay

        val basePayOrdinario   = ordinaryHours * baseHourlyPay

        // Accruals (Ratei 13° e 14°) — basati sullo stipendio base mensile
        val accrual13th  = monthlyBasePay / 12.0
        val accrual14th  = monthlyBasePay / 12.0
        val totalAccruals = accrual13th + accrual14th

        // ── Separazione Lordo per Detassazione (Agevolato al 10%) ───────────
        // In Italia i turni (notturno, festivo, domenica) e gli straordinari
        // godono dell'imposta sostitutiva al 10%.
        val grossAgevolato = totalOvertimePay + totalNightBonus + totalSundayBonus + totalHolidayBonus + totalRestBonus
        
        // Il resto (paga base, indennità fisse, mensa) va a tassazione ordinaria IRPEF
        val grossOrdinario = basePayOrdinario + totalPresenceIndemnity + totalMensaPay

        val totalGrossPay = grossOrdinario + grossAgevolato

        // ── Netto reale con cascata fiscale avanzata ─────────────────────────
        val estimatedNetPay = calculateAdvancedNet(grossOrdinario, grossAgevolato)

        return MonthlySummary(
            month              = month,
            year               = year,
            totalHours         = totalHours,
            ordinaryHours      = ordinaryHours,
            overtimeTier1Hours = manualOvertimeHours + extraHours,
            overtimeTier2Hours = 0.0,
            manualOvertimeHours = manualOvertimeHours,
            autoOvertimeHours  = extraHours,
            monthlyThreshold   = baseHoursThreshold,
            overtimePay        = totalOvertimePay,
            totalNightBonus    = totalNightBonus,
            totalSundayBonus   = totalSundayBonus,
            totalHolidayBonus  = totalHolidayBonus,
            totalPresenceIndemnity = totalPresenceIndemnity,
            totalRestBonusPay  = totalRestBonus,
            totalMensaPay      = totalMensaPay,
            dailySummaries     = dailySummaries,
            totalGrossPay      = totalGrossPay,
            estimatedNetPay    = estimatedNetPay,
            basePayOrdinario   = basePayOrdinario,
            accrual13th        = accrual13th,
            accrual14th        = accrual14th,
            totalAccruals      = totalAccruals
        )
    }

    // ════════════════════════════════════════════════════════════════════════════
    // CALCOLO LORDO → NETTO A CASCATA (AVANZATO CON BONUS GOVERNATIVI)
    // ════════════════════════════════════════════════════════════════════════════

    /**
     * Calcola il netto applicando le regole reali italiane:
     * 1. Detassazione 10% (Imposta sostitutiva) sui turni e straordinari.
     * 2. Esonero IVS (Taglio del cuneo fiscale del 6% o 7%).
     */
    fun calculateAdvancedNet(grossOrdinario: Double, grossAgevolato: Double): Double {
        val totalGross = grossOrdinario + grossAgevolato

        // ── STEP 1: INPS (Trattenuta standard) ──────────────────────────────
        // L'INPS si calcola sull'intero lordo
        val inpsDeduction = totalGross * INPS_RATE
        
        // Ripartiamo l'INPS proporzionalmente per capire l'imponibile IRPEF
        val inpsOrdinario = inpsDeduction * (grossOrdinario / totalGross.coerceAtLeast(1.0))
        val inpsAgevolato = inpsDeduction * (grossAgevolato / totalGross.coerceAtLeast(1.0))

        val imponibileIrpef = grossOrdinario - inpsOrdinario
        val imponibileSostitutiva = grossAgevolato - inpsAgevolato

        // ── STEP 2: IRPEF Ordinaria (23%) + Detrazioni ──────────────────────
        val annualImponibile = imponibileIrpef * 12.0
        val irpefLordaAnnua  = annualImponibile * IRPEF_RATE_FIRST_BRACKET

        val detrazione = if (annualImponibile <= 15000.0) {
            1880.0
        } else if (annualImponibile <= 28000.0) {
            DETR_LAVDIP_15K_28K_BASE +
                DETR_LAVDIP_15K_28K_VAR * ((DETR_LAVDIP_REDDITO_MAX - annualImponibile) /
                    (DETR_LAVDIP_REDDITO_MAX - DETR_LAVDIP_REDDITO_15K))
        } else {
            0.0
        }

        val irpefNettaAnnua  = max(0.0, irpefLordaAnnua - detrazione)
        val irpefNettaMensile = irpefNettaAnnua / 12.0

        // ── STEP 3: Imposta Sostitutiva 10% (Detassazione Turni) ────────────
        val impostaSostitutiva = imponibileSostitutiva * 0.10

        // ── STEP 4: Addizionali Locali (solo su imponibile IRPEF) ───────────
        val addRegionale = (annualImponibile * ADDIZIONALE_REGIONALE) / 12.0
        val addComunale  = (annualImponibile * ADDIZIONALE_COMUNALE) / 12.0

        // ── STEP 5: Esonero IVS (Taglio cuneo fiscale - Bonus netto) ────────
        // Sotto i 1923€ lordi/mese -> bonus 7%
        // Sotto i 2692€ lordi/mese -> bonus 6%
        val esoneroIvs = when {
            totalGross <= 1923.0 -> totalGross * 0.07
            totalGross <= 2692.0 -> totalGross * 0.06
            else -> 0.0
        }

        // ── RISULTATO FINALE ────────────────────────────────────────────────
        return totalGross - 
               inpsDeduction - 
               irpefNettaMensile - 
               impostaSostitutiva - 
               addRegionale - 
               addComunale + 
               esoneroIvs
    }

    /**
     * Fallback per mantenere la compatibilità con le chiamate esterne
     */
    fun calculateNetFromGross(grossMonthly: Double): Double {
        // Approssima che il 20% del lordo sia detassabile se non abbiamo i dettagli
        val agevolato = grossMonthly * 0.20
        val ordinario = grossMonthly - agevolato
        return calculateAdvancedNet(ordinario, agevolato)
    }

    // ════════════════════════════════════════════════════════════════════════════
    // CALCOLO GIORNALIERO
    // ════════════════════════════════════════════════════════════════════════════

    fun calculateDailySummary(shift: Shift): DailySummary {
        val isWorkingShift = shift.shiftType == ShiftType.WORK ||
            shift.shiftType == ShiftType.INTERVENTO ||
            shift.shiftType == ShiftType.MANCATO_R1 ||
            shift.shiftType == ShiftType.MANCATO_R2

        if (!isWorkingShift) {
            return DailySummary(
                shift           = shift,
                totalHours      = 0.0,
                nightHours      = 0.0,
                basePay         = 0.0,
                nightBonusPay   = 0.0,
                sundayBonusPay  = 0.0,
                holidayBonusPay = 0.0,
                presenceIndemnity = 0.0,
                totalGrossPay   = 0.0,
                totalNetPay     = 0.0
            )
        }

        val startTime = shift.startTime?.let { LocalDateTime(shift.date, it) }
            ?: LocalDateTime.now()
        val endTime = shift.endTime?.let {
            var dt = LocalDateTime(shift.date, it)
            if (dt < startTime) dt = dt.plusDays(1L)
            dt
        } ?: startTime.plusHours(shift.durationHours?.toLong() ?: 0L)

        val rawDurationHours = Duration.between(startTime, endTime).toMinutes() / 60.0

        // INTERVENTO: ore valgono doppio
        val ordinaryDurationHours = if (shift.shiftType == ShiftType.INTERVENTO)
            rawDurationHours * 2.0 else rawDurationHours

        val manualOvertimeHours = shift.overtimeMinutes / 60.0
        val workingTotalHours   = ordinaryDurationHours + manualOvertimeHours

        // ── Paga base delle ore ordinarie ────────────────────────────────────
        val basePay = ordinaryDurationHours * baseHourlyPay

        // ── Straordinario manuale (125%) ──────────────────────────────────────
        val manualOvertimePay = manualOvertimeHours * baseHourlyPay * OVERTIME_MULTIPLIER

        // ── Indennità giornaliere fisse (CCNL esatto) ─────────────────────────
        val presenceIndemnity = TOTAL_DAILY_INDEMNITY  // 7.85 €/giorno fisso

        // ── Mensa (solo se flaggata) ───────────────────────────────────────────
        val mensaPay = if (shift.isMensaLavorata) MENSA_PAY else 0.0

        // ── Maggiorazione Notturna (ore 20:00-08:00) ─────────────────────────
        // Usa CALC_HOURLY_BASE (9.15335) × 50% = 4.57668 €/h
        val overtimeStartTime = endTime
        val overtimeEndTime   = endTime.plusMinutes(shift.overtimeMinutes.toLong())
        val ordinaryNightHours = calculateOverlapHours(startTime, endTime, 20, 8)
        val overtimeNightHours = calculateOverlapHours(overtimeStartTime, overtimeEndTime, 20, 8)
        val nightHours        = ordinaryNightHours + overtimeNightHours
        val nightBonusPay     = nightHours * NIGHT_BONUS_PER_HOUR

        // ── Maggiorazione Domenicale (+10% CALC_HOURLY_BASE) ─────────────────
        val isSundayShift  = shift.date.dayOfWeek == DayOfWeek.SUNDAY || shift.shiftType == ShiftType.MANCATO_R2
        val sundayBonusPay = if (isSundayShift) workingTotalHours * SUNDAY_BONUS_PER_HOUR else 0.0

        // ── Maggiorazione Festiva (+45% CALC_HOURLY_BASE) ────────────────────
        // Non si cumula con il domenicale
        val isActualHolidayBonus = shift.isHoliday && !isSundayShift
        val holidayBonusPay      = if (isActualHolidayBonus) workingTotalHours * HOLIDAY_BONUS_PER_HOUR else 0.0

        // ── Mancato Riposo (R1/R2) ────────────────────────────────────────────
        val restBonusPay = when (shift.shiftType) {
            ShiftType.MANCATO_R1 -> ordinaryDurationHours * baseHourlyPay * R1_MULTIPLIER
            ShiftType.MANCATO_R2 -> ordinaryDurationHours * baseHourlyPay * R2_MULTIPLIER
            else -> 0.0
        }

        // ── Indennità aeroportuale (fissa per turno dal profilo) ──────────────
        val airportIndemnityPay = profile.airportIndemnity

        // ── Lordo giornaliero — separato per tipo fiscale ─────────────────────
        // Componenti "ordinarie" (IRPEF 23%)
        val grossOrdinarioDay = basePay + presenceIndemnity + mensaPay + airportIndemnityPay
        // Componenti "agevolate" (Imposta Sostitutiva 10%)
        val grossAgevolatoDay = manualOvertimePay + nightBonusPay + sundayBonusPay + holidayBonusPay + restBonusPay

        val totalGrossPay = grossOrdinarioDay + grossAgevolatoDay

        // ── Netto giornaliero con motore fiscale avanzato ─────────────────────
        val totalNetPay = calculateAdvancedNet(grossOrdinarioDay, grossAgevolatoDay)

        return DailySummary(
            shift             = shift,
            totalHours        = workingTotalHours,
            manualOvertimeHours = manualOvertimeHours,
            manualOvertimePay = manualOvertimePay,
            nightHours        = nightHours,
            sundayHours       = if (isSundayShift) workingTotalHours else 0.0,
            holidayHours      = if (shift.isHoliday) workingTotalHours else 0.0,
            basePay           = basePay,
            airportIndemnityPay = airportIndemnityPay,
            nightBonusPay     = nightBonusPay,
            sundayBonusPay    = sundayBonusPay,
            holidayBonusPay   = holidayBonusPay,
            presenceIndemnity = presenceIndemnity,
            restBonusPay      = restBonusPay,
            mensaPay          = mensaPay,
            totalGrossPay     = totalGrossPay,
            totalNetPay       = totalNetPay
        )
    }

    // ════════════════════════════════════════════════════════════════════════════
    // UTILITY: Calcolo ore di sovrapposizione con una fascia oraria
    // ════════════════════════════════════════════════════════════════════════════

    private fun calculateOverlapHours(
        start: LocalDateTime,
        end: LocalDateTime,
        hourStart: Int,
        hourEnd: Int
    ): Double {
        if (Duration.between(start, end).toMinutes() <= 0) return 0.0
        var overlapMinutes = 0L
        var dayStart = start.toLocalDate()
        val dayEnd   = end.toLocalDate()

        while (dayStart <= dayEnd) {
            val dayBase = LocalDateTime(dayStart, kotlinx.datetime.LocalTime(0, 0))
            if (hourEnd < 24) {
                overlapMinutes += overlapMinutes(start, end, dayBase, dayBase.plusHours(hourEnd.toLong()))
            }
            if (hourStart > 0) {
                overlapMinutes += overlapMinutes(start, end, dayBase.plusHours(hourStart.toLong()), dayBase.plusHours(24L))
            }
            dayStart = dayStart.plusDays(1L)
        }
        return overlapMinutes / 60.0
    }

    private fun overlapMinutes(
        aStart: LocalDateTime, aEnd: LocalDateTime,
        bStart: LocalDateTime, bEnd: LocalDateTime
    ): Long {
        val from = if (aStart > bStart) aStart else bStart
        val to   = if (aEnd < bEnd) aEnd else bEnd
        return if (from < to) Duration.between(from, to).toMinutes() else 0L
    }
}


