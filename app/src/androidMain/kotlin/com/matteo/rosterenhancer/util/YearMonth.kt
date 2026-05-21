package com.matteo.rosterenhancer.util

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

data class YearMonth(val year: Int, val month: Int) : Comparable<YearMonth> {
    
    fun plusMonths(months: Int): YearMonth {
        var newMonth = month + months
        var newYear = year
        while (newMonth > 12) {
            newMonth -= 12
            newYear++
        }
        while (newMonth < 1) {
            newMonth += 12
            newYear--
        }
        return YearMonth(newYear, newMonth)
    }

    fun minusMonths(months: Int): YearMonth {
        return plusMonths(-months)
    }
    
    val lengthOfMonth: Int
        get() = when (month) {
            2 -> if (isLeapYear(year)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        
    fun atDay(day: Int): LocalDate {
        return LocalDate(year, month, day)
    }

    fun atEndOfMonth(): LocalDate {
        return LocalDate(year, month, lengthOfMonth)
    }

    override fun compareTo(other: YearMonth): Int {
        if (this.year != other.year) {
            return this.year - other.year
        }
        return this.month - other.month
    }
    
    override fun toString(): String {
        return "$year-${month.toString().padStart(2, '0')}"
    }

    companion object {
        fun now(): YearMonth {
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            return YearMonth(today.year, today.monthNumber)
        }

        fun of(year: Int, month: Int): YearMonth {
            return YearMonth(year, month)
        }
        
        fun parse(text: String): YearMonth {
            val parts = text.split("-")
            return YearMonth(parts[0].toInt(), parts[1].toInt())
        }

        private fun isLeapYear(year: Int): Boolean {
            return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        }
    }
}

val LocalDate.yearMonth: YearMonth
    get() = YearMonth(this.year, this.monthNumber)
