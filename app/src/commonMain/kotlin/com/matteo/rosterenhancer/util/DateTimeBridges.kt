package com.matteo.rosterenhancer.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.plus
import kotlinx.datetime.minus
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Clock
import kotlinx.datetime.todayIn
import kotlinx.datetime.DayOfWeek

private val UTC = TimeZone.UTC

// Extensions on Companion objects to simulate java.time static calls
fun LocalDate.Companion.now(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
fun LocalTime.Companion.now(): LocalTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
fun LocalDateTime.Companion.now(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

fun LocalDateTime.Companion.of(date: LocalDate, time: LocalTime): LocalDateTime =
    LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, time.hour, time.minute, time.second, time.nanosecond)

fun LocalTime.Companion.of(hour: Int, minute: Int): LocalTime = LocalTime(hour, minute)
fun LocalTime.Companion.of(hour: Int, minute: Int, second: Int): LocalTime = LocalTime(hour, minute, second)
val LocalTime.Companion.MIN: LocalTime get() = LocalTime(0, 0, 0, 0)
val LocalTime.Companion.MAX: LocalTime get() = LocalTime(23, 59, 59, 999999999)

fun DayOfWeek.getDisplayName(style: TextStyle, locale: Locale): String {
    return ItalianDaysOfWeek[this.ordinal]
}


// Extensions for Java-like API on Kotlin types
val LocalDate.monthValue: Int get() = this.monthNumber
val LocalDateTime.monthValue: Int get() = this.monthNumber
val YearMonth.monthValue: Int get() = this.month

fun LocalDateTime.toLocalDate(): LocalDate = this.date
fun LocalDateTime.toLocalTime(): LocalTime = this.time

fun LocalDate.atTime(hour: Int, minute: Int): LocalDateTime =
    LocalDateTime(this.year, this.monthNumber, this.dayOfMonth, hour, minute)

fun LocalDate.atTime(time: LocalTime): LocalDateTime =
    LocalDateTime(this.year, this.monthNumber, this.dayOfMonth, time.hour, time.minute, time.second, time.nanosecond)

fun LocalDateTime.isAfter(other: LocalDateTime): Boolean = this > other
fun LocalDateTime.isBefore(other: LocalDateTime): Boolean = this < other

fun LocalDateTime.plusMinutes(minutes: Long): LocalDateTime {
    val instant = this.toInstant(UTC)
    val newInstant = instant.plus(minutes, DateTimeUnit.MINUTE)
    return newInstant.toLocalDateTime(UTC)
}

fun LocalDateTime.minusMinutes(minutes: Long): LocalDateTime = plusMinutes(-minutes)

fun LocalDateTime.plusHours(hours: Long): LocalDateTime {
    val instant = this.toInstant(UTC)
    val newInstant = instant.plus(hours, DateTimeUnit.HOUR)
    return newInstant.toLocalDateTime(UTC)
}

fun LocalDateTime.minusHours(hours: Long): LocalDateTime = plusHours(-hours)

fun LocalDateTime.plusDays(days: Long): LocalDateTime {
    val newDate = this.date.plus(days.toInt(), DateTimeUnit.DAY)
    return LocalDateTime(newDate.year, newDate.monthNumber, newDate.dayOfMonth, this.time.hour, this.time.minute, this.time.second, this.time.nanosecond)
}

fun LocalDate.isAfter(other: LocalDate): Boolean = this > other
fun LocalDate.isBefore(other: LocalDate): Boolean = this < other

fun LocalDate.plusDays(days: Long): LocalDate {
    return this.plus(days.toInt(), DateTimeUnit.DAY)
}

fun LocalDate.minusDays(days: Long): LocalDate {
    return this.minus(days.toInt(), DateTimeUnit.DAY)
}

fun LocalDate.plusMonths(months: Long): LocalDate {
    return this.plus(months.toInt(), DateTimeUnit.MONTH)
}

fun LocalDate.minusMonths(months: Long): LocalDate {
    return this.minus(months.toInt(), DateTimeUnit.MONTH)
}

fun LocalDate.withDayOfMonth(day: Int): LocalDate =
    LocalDate(this.year, this.monthNumber, day)

val LocalDate.lengthOfMonth: Int
    get() = when (this.monthNumber) {
        2 -> if (isLeapYear(this.year)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

private fun isLeapYear(year: Int): Boolean {
    return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
}

fun LocalTime.isAfter(other: LocalTime): Boolean = this > other
fun LocalTime.isBefore(other: LocalTime): Boolean = this < other

fun LocalTime.plusMinutes(minutes: Long): LocalTime {
    val totalMinutes = (this.hour * 60 + this.minute + minutes) % 1440
    val positiveMinutes = if (totalMinutes < 0) totalMinutes + 1440 else totalMinutes
    val newHour = (positiveMinutes / 60).toInt()
    val newMinute = (positiveMinutes % 60).toInt()
    return LocalTime(newHour, newMinute, this.second, this.nanosecond)
}

fun LocalTime.minusMinutes(minutes: Long): LocalTime = plusMinutes(-minutes)

fun LocalTime.plusHours(hours: Long): LocalTime = plusMinutes(hours * 60)

fun LocalTime.minusHours(hours: Long): LocalTime = plusMinutes(-hours * 60)

fun LocalTime.toSecondOfDay(): Int = this.hour * 3600 + this.minute * 60 + this.second

// Custom KmpDuration that replaces java.time.Duration
class Duration private constructor(val kotlinDuration: kotlin.time.Duration) {
    fun toMinutes(): Long = kotlinDuration.inWholeMinutes
    fun toHours(): Long = kotlinDuration.inWholeHours
    fun toDays(): Long = kotlinDuration.inWholeDays
    fun toMillis(): Long = kotlinDuration.inWholeMilliseconds
    
    fun plus(other: Duration): Duration {
        return Duration(this.kotlinDuration + other.kotlinDuration)
    }

    companion object {
        val ZERO = Duration(kotlin.time.Duration.ZERO)

        fun between(start: LocalDateTime, end: LocalDateTime): Duration {
            val startInstant = start.toInstant(UTC)
            val endInstant = end.toInstant(UTC)
            return Duration(endInstant - startInstant)
        }

        fun between(start: LocalTime, end: LocalTime): Duration {
            val startSec = start.hour * 3600 + start.minute * 60 + start.second
            val endSec = end.hour * 3600 + end.minute * 60 + end.second
            val diffSec = endSec - startSec
            return Duration(kotlin.time.Duration.Companion.parse("${diffSec}s"))
        }
    }
}

// Italian Locale Mock / Bridges
enum class TextStyle { FULL, FULL_STANDALONE, SHORT, NARROW }

class Locale private constructor() {
    companion object {
        val ITALIAN = Locale()
    }
}

class DateTimeFormatter private constructor(val pattern: String) {
    companion object {
        fun ofPattern(pattern: String): DateTimeFormatter = DateTimeFormatter(pattern)
        fun ofPattern(pattern: String, locale: Locale?): DateTimeFormatter = DateTimeFormatter(pattern)
    }
}

// Formatting helpers in Italian
private val ItalianMonths = listOf(
    "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
    "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre"
)

private val ItalianMonthsShort = listOf(
    "Gen", "Feb", "Mar", "Apr", "Mag", "Giu",
    "Lug", "Ago", "Set", "Ott", "Nov", "Dic"
)

private val ItalianDaysOfWeek = listOf(
    "Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica"
)

private val ItalianDaysOfWeekShort = listOf(
    "Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom"
)

private fun formatPattern(date: LocalDate?, time: LocalTime?, dateTime: LocalDateTime?, pattern: String): String {
    var result = pattern
    
    val year = date?.year ?: dateTime?.year ?: 0
    val month = date?.monthNumber ?: dateTime?.monthNumber ?: 1
    val day = date?.dayOfMonth ?: dateTime?.dayOfMonth ?: 1
    val dayOfWeek = date?.dayOfWeek?.ordinal?.plus(1) ?: dateTime?.dayOfWeek?.ordinal?.plus(1) ?: 1
    
    val hour = time?.hour ?: dateTime?.hour ?: 0
    val minute = time?.minute ?: dateTime?.minute ?: 0
    val second = time?.second ?: dateTime?.second ?: 0
    
    if (result.contains("EEEE")) {
        result = result.replace("EEEE", ItalianDaysOfWeek[dayOfWeek - 1])
    } else if (result.contains("EEE")) {
        result = result.replace("EEE", ItalianDaysOfWeekShort[dayOfWeek - 1])
    }
    
    if (result.contains("MMMM")) {
        result = result.replace("MMMM", ItalianMonths[month - 1])
    } else if (result.contains("MMM")) {
        result = result.replace("MMM", ItalianMonthsShort[month - 1])
    } else if (result.contains("MM")) {
        result = result.replace("MM", month.toString().padStart(2, '0'))
    } else if (result.contains("M")) {
        result = result.replace("M", month.toString())
    }
    
    if (result.contains("dd")) {
        result = result.replace("dd", day.toString().padStart(2, '0'))
    } else if (result.contains("d")) {
        result = result.replace("d", day.toString())
    }
    
    if (result.contains("yyyy")) {
        result = result.replace("yyyy", year.toString())
    }
    
    if (result.contains("HH")) {
        result = result.replace("HH", hour.toString().padStart(2, '0'))
    }
    if (result.contains("mm")) {
        result = result.replace("mm", minute.toString().padStart(2, '0'))
    }
    if (result.contains("ss")) {
        result = result.replace("ss", second.toString().padStart(2, '0'))
    }
    
    return result
}

fun LocalDate.format(formatter: DateTimeFormatter): String = formatPattern(this, null, null, formatter.pattern)
fun LocalTime.format(formatter: DateTimeFormatter): String = formatPattern(null, this, null, formatter.pattern)
fun LocalDateTime.format(formatter: DateTimeFormatter): String = formatPattern(this.date, this.time, this, formatter.pattern)

fun Int.getDisplayName(style: TextStyle, locale: Locale): String {
    return if (this in 1..12) ItalianMonths[this - 1] else "Mese $this"
}

fun Month.getDisplayName(style: TextStyle, locale: Locale): String {
    return if (style == TextStyle.SHORT) ItalianMonthsShort[this.ordinal] else ItalianMonths[this.ordinal]
}

/** Unambiguous Italian display name for Month (avoids JVM shadowing) */
fun Month.italianDisplayName(short: Boolean = false): String =
    if (short) ItalianMonthsShort[this.ordinal] else ItalianMonths[this.ordinal]

/** Unambiguous Italian display name for DayOfWeek (avoids JVM shadowing) */
fun DayOfWeek.italianDisplayName(short: Boolean = false): String =
    if (short) ItalianDaysOfWeekShort[this.ordinal] else ItalianDaysOfWeek[this.ordinal]

