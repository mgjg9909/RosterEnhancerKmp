package com.matteo.rosterenhancer.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi

data class DailySummary(
    val shift: Shift,
    val totalHours: Double,
    val manualOvertimeHours: Double = 0.0,
    val manualOvertimePay: Double = 0.0,
    val nightHours: Double,
    val sundayHours: Double = 0.0,
    val holidayHours: Double = 0.0,
    val basePay: Double,
    val airportIndemnityPay: Double = 0.0,
    val nightBonusPay: Double,
    val sundayBonusPay: Double,
    val holidayBonusPay: Double,
    val presenceIndemnity: Double,
    val restBonusPay: Double = 0.0,
    val mensaPay: Double = 0.0,
    val totalGrossPay: Double,
    val totalNetPay: Double
)

data class MonthlySummary(
    val month: Int,
    val year: Int,
    val totalHours: Double,
    val ordinaryHours: Double,
    val overtimeTier1Hours: Double,
    val overtimeTier2Hours: Double,
    val manualOvertimeHours: Double = 0.0,
    val autoOvertimeHours: Double = 0.0,
    val monthlyThreshold: Double = 173.0,
    val overtimePay: Double,
    val totalNightBonus: Double = 0.0,
    val totalSundayBonus: Double = 0.0,
    val totalHolidayBonus: Double = 0.0,
    val totalPresenceIndemnity: Double = 0.0,
    val totalRestBonusPay: Double = 0.0,
    val totalMensaPay: Double = 0.0,
    val dailySummaries: List<DailySummary>,
    val totalGrossPay: Double,
    val estimatedNetPay: Double,
    val basePayOrdinario: Double = 0.0,
    val accrual13th: Double = 0.0,
    val accrual14th: Double = 0.0,
    val totalAccruals: Double = 0.0,
    val realNetPay: Double? = null
)

data class AccrualsSummary(
    val cumulativeTfr: Double = 0.0,
    val cumulative13th: Double = 0.0,
    val cumulative14th: Double = 0.0,
    val tfrLastResetDate: LocalDateTime? = null,
    val thirteenthLastResetDate: LocalDateTime? = null,
    val fourteenthLastResetDate: LocalDateTime? = null
)

@OptIn(ExperimentalUuidApi::class)
data class PayslipMetadata(
    val id: String = Uuid.random().toString(),
    val month: Int,
    val year: Int,
    val netPay: Double,
    val filename: String,
    val importDate: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    val isAiExtracted: Boolean = true
)

data class EarningsProjection(
    val earnedSoFar: Double,
    val estimatedFuture: Double,
    val totalProjected: Double,
    val progressPercent: Float
)

data class SalaryUiState(
    val currentMonth: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    val summary: MonthlySummary? = null,
    val accrualsSummary: AccrualsSummary? = null,
    val projection: EarningsProjection? = null,
    val payslipHistory: List<PayslipMetadata> = emptyList(),
    val profile: GpgProfile = GpgProfile(),
    val isLoading: Boolean = true
)

data class GpgProfile(
    val level: Int = 7,
    val gpgSenioritySteps: Int = 0,
    val partTimePercentage: Double = 100.0,
    val taxRate: Double = 0.15,
    val airportIndemnity: Double = 3.045,
    val hireDate: LocalDateTime? = null
)

