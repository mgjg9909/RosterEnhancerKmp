package com.matteo.rosterenhancer.util

import com.matteo.rosterenhancer.util.plusDays
import com.matteo.rosterenhancer.util.monthValue

import kotlinx.datetime.LocalDate
import com.matteo.rosterenhancer.util.*
import kotlinx.datetime.periodUntil

object ItalianHolidayUtils {

    /**
     * Verifica se una data è una festività nazionale italiana.
     */
    fun isHoliday(date: LocalDate): Boolean {
        val day = date.dayOfMonth
        val month = date.monthValue
        val year = date.year

        // Festività fisse
        if (month == 1 && day == 1) return true   // Capodanno
        if (month == 1 && day == 6) return true   // Epifania
        if (month == 4 && day == 25) return true  // Liberazione
        if (month == 5 && day == 1) return true   // Lavoro
        if (month == 6 && day == 2) return true   // Repubblica
        if (month == 8 && day == 15) return true  // Ferragosto
        if (month == 11 && day == 1) return true  // Ognissanti
        if (month == 12 && day == 8) return true  // Immacolata
        if (month == 12 && day == 25) return true // Natale
        if (month == 12 && day == 26) return true // S. Stefano

        // Pasqua e Pasquetta
        val easter = getEasterDate(year)
        if (date == easter || date == easter.plusDays(1)) return true

        return false
    }

    /**
     * Calcola la data della Pasqua (Algoritmo di Gauss).
     */
    private fun getEasterDate(year: Int): LocalDate {
        val a = year % 19
        val b = year % 4
        val c = year % 7
        val m = 24
        val n = 5
        val d = (19 * a + m) % 30
        val e = (2 * b + 4 * c + 6 * d + n) % 7
        
        var day = 22 + d + e
        var month = 3

        if (day > 31) {
            day -= 31
            month = 4
        }
        
        // Casi particolari dell'algoritmo
        if (month == 4 && day == 26) day = 19
        if (month == 4 && day == 25 && d == 28 && e == 6 && a > 10) day = 18

        return LocalDate(year, month, day)
    }
}





