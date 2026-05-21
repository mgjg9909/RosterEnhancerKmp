package com.matteo.rosterenhancer.util

fun getMonthName(month: Int): String {
    val months = listOf(
        "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
        "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre"
    )
    return if (month in 1..12) months[month - 1] else "Mese $month"
}

