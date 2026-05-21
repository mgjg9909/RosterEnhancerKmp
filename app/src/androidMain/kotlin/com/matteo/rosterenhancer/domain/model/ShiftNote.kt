package com.matteo.rosterenhancer.domain.model

import kotlinx.datetime.LocalDate

data class ShiftNote(
    val employeeName: String,
    val date: LocalDate,
    val note: String = "",
    val extraMinutes: Int = 0
) {
    /** Es. extraMinutes=90 → "+1h 30m" */
    val extraTimeLabel: String get() {
        if (extraMinutes <= 0) return ""
        val h = extraMinutes / 60
        val m = extraMinutes % 60
        return if (h > 0 && m > 0) "+${h}h ${m}m"
        else if (h > 0) "+${h}h"
        else "+${m}m"
    }
}


