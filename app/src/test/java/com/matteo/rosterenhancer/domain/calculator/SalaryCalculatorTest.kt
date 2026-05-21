package com.matteo.rosterenhancer.domain.calculator

import com.matteo.rosterenhancer.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class SalaryCalculatorTest {

    private lateinit var calculator: SalaryCalculator
    private val standardProfile = GpgProfile(
        level = 4, // Livello 4 per test
        gpgSenioritySteps = 2,
        partTimePercentage = 100.0,
        taxRate = 0.15,
        airportIndemnity = 3.045
    )

    @Before
    fun setup() {
        calculator = SalaryCalculator(standardProfile)
    }

    @Test
    fun `calculateDailySummary - WORK - 8 hours day shift`() {
        // Giorno lavorativo normale (lunedi), 07:00-15:00 (8h)
        val shift = Shift(
            employeeId = "TEST",
            employeeName = "Test User",
            date = LocalDate(2026, 4, 13), // Lunedì
            startTime = LocalTime(7, 0),
            durationHours = 8,
            shiftType = ShiftType.WORK,
            isManual = true
        )

        val summary = calculator.calculateDailySummary(shift)

        assertEquals(8.0, summary.totalHours, 0.01)
        assertEquals(1.0, summary.nightHours, 0.01) // 07:00-08:00 è notturno (fascia 20-08)
        assertEquals(0.0, summary.sundayBonusPay, 0.01)
        assertEquals(0.0, summary.holidayBonusPay, 0.01)
    }

    @Test
    fun `calculateDailySummary - WORK - Night Shift overlap`() {
        // Turno 22:00 - 06:00 (8h totali, 22:00-06:00 sono 8h notturne 20-08)
        val shift = Shift(
            employeeId = "TEST",
            employeeName = "Test User",
            date = LocalDate(2026, 4, 13),
            startTime = LocalTime(22, 0),
            durationHours = 8,
            shiftType = ShiftType.WORK,
            isManual = true
        )

        val summary = calculator.calculateDailySummary(shift)

        assertEquals(8.0, summary.totalHours, 0.01)
        assertEquals(8.0, summary.nightHours, 0.01) // Tutto il turno è tra le 20 e le 08
    }

    @Test
    fun `calculateDailySummary - MANCATO_R1 - Verify multiplier`() {
        // Mancato R1 (maggiorazione 30% sulle ore ordinarie)
        val shift = Shift(
            employeeId = "TEST",
            employeeName = "Test User",
            date = LocalDate(2026, 4, 14), // Martedì
            startTime = LocalTime(7, 0),
            durationHours = 8,
            shiftType = ShiftType.MANCATO_R1,
            isManual = true
        )

        val summary = calculator.calculateDailySummary(shift)
        
        // La paga base in DailySummary include già il restBonusPay
        // restBonusPay = duration * hourlyPay * 0.30
        
        val hourlyPay = CcnlTables.calcolaPagaOrariaBase(standardProfile.level, standardProfile.gpgSenioritySteps)
        val expectedRestBonus = 8.0 * hourlyPay * 0.30
        
        assertEquals(expectedRestBonus, summary.restBonusPay, 0.01)
    }

    @Test
    fun `calculateDailySummary - INTERVENTO - Verify double hours`() {
        // Intervento: ore ordinarie raddoppiate
        val shift = Shift(
            employeeId = "TEST",
            employeeName = "Test User",
            date = LocalDate(2026, 4, 15),
            startTime = LocalTime(7, 0),
            durationHours = 3,
            shiftType = ShiftType.INTERVENTO,
            isManual = true
        )

        val summary = calculator.calculateDailySummary(shift)
        
        // 3h lavorate -> 6h contate come totalHours (ordinaryDurationHours * 2)
        assertEquals(6.0, summary.totalHours, 0.01)
    }

    @Test
    fun `calculateDailySummary - MENSA - Verify 45 min pay`() {
        // Mensa lavorata (45 min extra pay)
        val shift = Shift(
            employeeId = "TEST",
            employeeName = "Test User",
            date = LocalDate(2026, 4, 16),
            startTime = LocalTime(7, 0),
            durationHours = 8,
            shiftType = ShiftType.WORK,
            isMensaLavorata = true,
            isManual = true
        )

        val summary = calculator.calculateDailySummary(shift)
        
        val hourlyPay = CcnlTables.calcolaPagaOrariaBase(standardProfile.level, standardProfile.gpgSenioritySteps)
        val expectedMensaPay = (45.0 / 60.0) * hourlyPay
        
        assertEquals(expectedMensaPay, summary.mensaPay, 0.01)
    }
}

