package com.matteo.rosterenhancer.util

import android.content.Context
import androidx.work.*
import com.matteo.rosterenhancer.domain.model.Shift
import com.matteo.rosterenhancer.domain.model.ShiftType
import com.matteo.rosterenhancer.worker.ShiftReminderWorker
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.datetime.toInstant
import java.util.concurrent.TimeUnit

actual object NotificationScheduler {

    actual suspend fun scheduleShiftReminders(context: Any?, shifts: List<Shift>) {
        val androidContext = context as? Context ?: return
        val workManager = WorkManager.getInstance(androidContext)
        
        workManager.cancelAllWorkByTag("SHIFT_REMINDER")

        val dataStoreManager = com.matteo.rosterenhancer.util.DataStoreManager(
            com.matteo.rosterenhancer.util.createDataStore(androidContext)
        )
        val reminderMinutes = dataStoreManager.reminderMinutes.first()
        
        if (reminderMinutes == -1) {
            return
        }

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        val upcomingWorkShifts = shifts.filter {
            it.shiftType == ShiftType.WORK && it.startTime != null
        }.mapNotNull { shift ->
            val shiftStartDateTime = shift.date.atTime(shift.startTime!!)
            if (shiftStartDateTime.isAfter(now)) {
                shift to shiftStartDateTime
            } else null
        }

        for ((shift, startDateTime) in upcomingWorkShifts) {
            val notifyTime = startDateTime.minusMinutes(reminderMinutes.toLong())
            
            if (notifyTime.isBefore(now)) continue

            // Math to get delay in milliseconds
            val systemTimeZone = TimeZone.currentSystemDefault()
            val delayInMillis = notifyTime.toInstant(systemTimeZone).toEpochMilliseconds() -
                                now.toInstant(systemTimeZone).toEpochMilliseconds()

            val inputData = Data.Builder()
                .putString("SHIFT_ID", shift.id.toString())
                .putString("SHIFT_START", shift.startTime.toString())
                .putString("SHIFT_ROLE", shift.role ?: "Turno Base")
                .build()

            val workRequest = OneTimeWorkRequestBuilder<ShiftReminderWorker>()
                .setInitialDelay(delayInMillis, TimeUnit.MILLISECONDS)
                .addTag("SHIFT_REMINDER")
                .addTag("SHIFT_${shift.id}")
                .setInputData(inputData)
                .build()

            workManager.enqueueUniqueWork(
                "Shift_${shift.id}",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    actual fun showShiftNotification(context: Any?, title: String, message: String) {
        val androidContext = context as? Context ?: return
        val notificationManager = androidContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "shift_reminders"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Promemoria e Variazioni Turni",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifiche pre-turno e variazioni roster"
                enableLights(true)
                lightColor = android.graphics.Color.CYAN
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = androidx.core.app.NotificationCompat.Builder(androidContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) 
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(message.hashCode(), notification)
    }
}
