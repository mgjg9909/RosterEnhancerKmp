package com.matteo.rosterenhancer.util

import com.matteo.rosterenhancer.domain.model.Shift

expect object NotificationScheduler {
    suspend fun scheduleShiftReminders(context: Any?, shifts: List<Shift>)
    fun showShiftNotification(context: Any?, title: String, message: String)
}

