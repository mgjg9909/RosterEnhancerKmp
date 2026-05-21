package com.matteo.rosterenhancer.util

import com.matteo.rosterenhancer.domain.model.Shift
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import kotlinx.cinterop.ExperimentalForeignApi

actual object NotificationScheduler {

    actual suspend fun scheduleShiftReminders(context: Any?, shifts: List<Shift>) {
        // Notifiche locali programmate su iOS
        // Possiamo svuotarle e ri-programmarle in futuro se necessario, o delegare alla pipeline iOS nativa.
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removeAllPendingNotificationRequests()
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun showShiftNotification(context: Any?, title: String, message: String) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(message)
        }
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(1.0, false)
        val request = UNNotificationRequest.requestWithIdentifier(message.hashCode().toString(), content, trigger)
        center.addNotificationRequest(request, null)
    }
}
