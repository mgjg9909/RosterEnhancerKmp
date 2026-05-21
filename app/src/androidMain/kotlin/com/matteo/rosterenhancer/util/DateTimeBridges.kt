package com.matteo.rosterenhancer.util

import kotlinx.datetime.LocalDate
import com.matteo.rosterenhancer.util.*
import kotlinx.datetime.periodUntil
import kotlinx.datetime.LocalTime
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import java.time.format.DateTimeFormatter

// Conversions
fun LocalDate.toJava(): java.time.LocalDate = java.time.LocalDate.of(this.year, this.monthNumber, this.dayOfMonth)
fun LocalTime.toJava(): java.time.LocalTime = java.time.LocalTime.of(this.hour, this.minute, this.second, this.nanosecond)
fun LocalDateTime.toJava(): java.time.LocalDateTime = java.time.LocalDateTime.of(this.year, this.monthNumber, this.dayOfMonth, this.hour, this.minute, this.second, this.nanosecond)

fun java.time.LocalDate.toKotlin(): LocalDate = LocalDate(this.year, this.monthValue, this.dayOfMonth)
fun java.time.LocalTime.toKotlin(): LocalTime = LocalTime(this.hour, this.minute, this.second, this.getNano())
fun java.time.LocalDateTime.toKotlin(): LocalDateTime = LocalDateTime(this.year, this.monthValue, this.dayOfMonth, this.hour, this.minute, this.second, this.getNano())

// Extensions for Java-like API on Kotlin types (Android only)
val LocalDate.monthValue: Int get() = this.monthNumber
val LocalDateTime.monthValue: Int get() = this.monthNumber
val YearMonth.monthValue: Int get() = this.month

fun LocalDateTime.toLocalDate(): LocalDate = this.date
fun LocalDateTime.toLocalTime(): LocalTime = this.time

fun LocalDate.atTime(hour: Int, minute: Int): LocalDateTime = LocalDateTime(this.year, this.monthNumber, this.dayOfMonth, hour, minute)
fun LocalDate.atTime(time: LocalTime): LocalDateTime = LocalDateTime(this.year, this.monthNumber, this.dayOfMonth, time.hour, time.minute, time.second, time.nanosecond)

fun LocalDateTime.isAfter(other: LocalDateTime): Boolean = this.toJava().isAfter(other.toJava())
fun LocalDateTime.isBefore(other: LocalDateTime): Boolean = this.toJava().isBefore(other.toJava())
fun LocalDateTime.minusMinutes(minutes: Long): LocalDateTime = this.toJava().minusMinutes(minutes).toKotlin()
fun LocalDateTime.plusMinutes(minutes: Long): LocalDateTime = this.toJava().plusMinutes(minutes).toKotlin()
fun LocalDateTime.plusDays(days: Long): LocalDateTime = this.toJava().plusDays(days).toKotlin()
fun LocalDateTime.minusHours(hours: Long): LocalDateTime = this.toJava().minusHours(hours).toKotlin()
fun LocalDateTime.plusHours(hours: Long): LocalDateTime = this.toJava().plusHours(hours).toKotlin()
fun LocalDateTime.atZone(zoneId: java.time.ZoneId): java.time.ZonedDateTime = this.toJava().atZone(zoneId)

fun LocalDate.isAfter(other: LocalDate): Boolean = this.toJava().isAfter(other.toJava())
fun LocalDate.isBefore(other: LocalDate): Boolean = this.toJava().isBefore(other.toJava())
fun LocalDate.plusDays(days: Long): LocalDate = this.toJava().plusDays(days).toKotlin()
fun LocalDate.minusDays(days: Long): LocalDate = this.toJava().minusDays(days).toKotlin()
fun LocalDate.plusMonths(months: Long): LocalDate = this.toJava().plusMonths(months).toKotlin()
fun LocalDate.minusMonths(months: Long): LocalDate = this.toJava().minusMonths(months).toKotlin()
fun LocalDate.withDayOfMonth(day: Int): LocalDate = this.toJava().withDayOfMonth(day).toKotlin()
val LocalDate.lengthOfMonth: Int get() = this.toJava().lengthOfMonth()

fun LocalTime.isAfter(other: LocalTime): Boolean = this.toJava().isAfter(other.toJava())
fun LocalTime.isBefore(other: LocalTime): Boolean = this.toJava().isBefore(other.toJava())
fun LocalTime.minusHours(hours: Long): LocalTime = this.toJava().minusHours(hours).toKotlin()
fun LocalTime.plusHours(hours: Long): LocalTime = this.toJava().plusHours(hours).toKotlin()
fun LocalTime.minusMinutes(minutes: Long): LocalTime = this.toJava().minusMinutes(minutes).toKotlin()
fun LocalTime.plusMinutes(minutes: Long): LocalTime = this.toJava().plusMinutes(minutes).toKotlin()
fun LocalTime.toSecondOfDay(): Int = this.toJava().toSecondOfDay()

fun Int.getDisplayName(style: java.time.format.TextStyle, locale: java.util.Locale): String {
    return java.time.Month.of(this).getDisplayName(style, locale)
}

fun Month.getDisplayName(style: java.time.format.TextStyle, locale: java.util.Locale): String {
    return java.time.Month.of(this.ordinal + 1).getDisplayName(style, locale)
}

// Formatters
fun LocalDate.format(formatter: DateTimeFormatter): String = this.toJava().format(formatter)
fun LocalTime.format(formatter: DateTimeFormatter): String = this.toJava().format(formatter)
fun LocalDateTime.format(formatter: DateTimeFormatter): String = this.toJava().format(formatter)







