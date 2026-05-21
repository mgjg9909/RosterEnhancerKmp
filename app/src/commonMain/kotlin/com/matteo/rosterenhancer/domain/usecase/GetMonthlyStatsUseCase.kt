package com.matteo.rosterenhancer.domain.usecase

import com.matteo.rosterenhancer.data.repository.RosterRepository
import com.matteo.rosterenhancer.domain.model.Shift
import com.matteo.rosterenhancer.domain.model.ShiftType
import com.matteo.rosterenhancer.util.RoleGroups
import com.matteo.rosterenhancer.domain.model.StatsUiState
import com.matteo.rosterenhancer.domain.model.TopCompanion
import com.matteo.rosterenhancer.domain.model.SalaryTrendPoint
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import com.matteo.rosterenhancer.util.*

import com.matteo.rosterenhancer.util.TextStyle
import com.matteo.rosterenhancer.util.Locale

class GetMonthlyStatsUseCase(
    private val repository: RosterRepository
) {
    fun execute(
        myShifts: List<Shift>,
        historicalShifts: List<Shift>,
        allWorkShifts: List<Shift>,
        monthTarget: Int,
        selfMatricola: String,
        profile: com.matteo.rosterenhancer.domain.model.GpgProfile
    ): StatsUiState {
        val workShifts = myShifts.filter { it.shiftType == ShiftType.WORK }
        val totalHours = workShifts.sumOf { it.durationHours ?: 0 }

        // Fasce orarie
        var morning = 0; var centrale = 0; var afternoon = 0; var night = 0
        for (shift in workShifts) {
            val h = shift.startTime?.hour ?: continue
            when {
                h >= 19 || h < 3  -> night++
                h in 3 until 7    -> morning++
                h in 7 until 12   -> centrale++
                else              -> afternoon++
            }
        }

        val roleMap = workShifts
            .groupBy { RoleGroups.normalize(it.role) }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
            .take(8)
            .associate { it.toPair() }

        // Podio Compagni
        val top3 = calculateTopCompanions(workShifts, allWorkShifts, selfMatricola)

        // Trend Salariale
        val trend = calculateSalaryTrend(profile, historicalShifts)

        // Target Mensile
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val weeksInMonth = YearMonth(today.year, today.monthNumber).lengthOfMonth / 7.0
        val monthlyTarget = (monthTarget * weeksInMonth).toInt().coerceAtLeast(1)

        // Ore per settimana
        val today2 = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val firstOfMonth = LocalDate(today2.year, today2.monthNumber, 1)
        val endOfMonth = YearMonth(today2.year, today2.monthNumber).atEndOfMonth()
        val weeklyHours = mutableListOf<Pair<String, Int>>()
        var weekStart = firstOfMonth
        var weekNum = 1
        while (weekStart.monthNumber == firstOfMonth.monthNumber) {
            val endOfWeek = weekStart.plusDays(6L)
            val weekEnd = if (endOfWeek > endOfMonth) endOfMonth else endOfWeek
            val hours = workShifts
                .filter { it.date >= weekStart && it.date <= weekEnd }
                .sumOf { it.durationHours ?: 0 }
            weeklyHours.add("Sett $weekNum" to hours)
            weekStart = weekStart.plusDays(7L)
            weekNum++
        }

        // Heatmap: mappa data -> categoria turno
        val monthShiftMap = myShifts.associate { shift ->
            shift.date to when (shift.shiftType) {
                ShiftType.WORK          -> "WORK"
                ShiftType.REST_1,
                ShiftType.REST_2        -> "REST"
                ShiftType.DAY_OFF       -> "OFF"
                ShiftType.ABSENT        -> "ABSENT"
                ShiftType.HOLIDAY       -> "HOLIDAY"
                ShiftType.PARENTAL_LEAVE-> "PARENTAL"
                else                    -> "OTHER"
            }
        }

        return StatsUiState(
            totalHours = totalHours,
            workDays = workShifts.size,
            restDays = myShifts.count {
                it.shiftType == ShiftType.REST_1 ||
                it.shiftType == ShiftType.REST_2 ||
                it.shiftType == ShiftType.DAY_OFF
            },
            absentDays = myShifts.count { it.shiftType == ShiftType.ABSENT },
            morningShifts = morning,
            centraleShifts = centrale,
            afternoonShifts = afternoon,
            nightShifts = night,
            roleDistribution = roleMap,
            topCompanions = top3,
            salaryTrend = trend,
            monthMaxHours = monthlyTarget,
            weeklyHours = weeklyHours,
            monthShiftMap = monthShiftMap,
            isLoading = false
        )
    }

    private fun calculateTopCompanions(myWorkShifts: List<Shift>, allWorkShifts: List<Shift>, selfMatricola: String): List<TopCompanion> {
        val myWorkShiftsByDate = myWorkShifts.associateBy { it.date }
        val overlaps = mutableMapOf<String, OverlapData>()

        for (shift in allWorkShifts) {
            if (shift.employeeId == selfMatricola) continue
            val myShift = myWorkShiftsByDate[shift.date] ?: continue
            
            val s1 = shift.startTime ?: continue
            val e1 = shift.endTime ?: continue
            val s2 = myShift.startTime ?: continue
            val e2 = myShift.endTime ?: continue
            
            val startDt1 = shift.date.atTime(s1)
            val endDt1 = if (e1 <= s1) shift.date.plusDays(1).atTime(e1) else shift.date.atTime(e1)
            
            val startDt2 = myShift.date.atTime(s2)
            val endDt2 = if (e2 <= s2) myShift.date.plusDays(1).atTime(e2) else myShift.date.atTime(e2)
            
            val overlapStart = if (startDt1 > startDt2) startDt1 else startDt2
            val overlapEnd = if (endDt1 < endDt2) endDt1 else endDt2
            
            if (overlapStart < overlapEnd) {
                val mins = Duration.between(overlapStart, overlapEnd).toMinutes()
                val data = overlaps.getOrPut(shift.employeeId) { OverlapData(name = shift.employeeName) }
                data.minutes += mins
                data.days += 1
            }
        }
        
        return overlaps.entries
            .sortedByDescending { it.value.minutes }
            .take(3)
            .map { 
                TopCompanion(id = it.key, name = it.value.name, sharedHours = (it.value.minutes / 60.0).toInt(), sharedDays = it.value.days) 
            }
    }

    private fun calculateSalaryTrend(profile: com.matteo.rosterenhancer.domain.model.GpgProfile, historicalShifts: List<Shift>): List<SalaryTrendPoint> {
        val calculator = com.matteo.rosterenhancer.domain.calculator.SalaryCalculator(profile)
        val trend = mutableListOf<SalaryTrendPoint>()
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        
        val last6Months = (0..5).map { today.minusMonths(it.toLong()) }.reversed()
        for (mDate in last6Months) {
            val monthShifts = historicalShifts.filter { it.date.monthNumber == mDate.monthNumber && it.date.year == mDate.year }
            val summary = calculator.calculateMonthlySummary(mDate.monthNumber, mDate.year, monthShifts)
            trend.add(
                SalaryTrendPoint(
                    monthName = mDate.month.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).uppercase(),
                    netSalary = summary.estimatedNetPay,
                    month = mDate.monthNumber,
                    year = mDate.year
                )
            )
        }
        return trend
    }

    private class OverlapData(var name: String = "", var minutes: Long = 0L, var days: Int = 0)
}



