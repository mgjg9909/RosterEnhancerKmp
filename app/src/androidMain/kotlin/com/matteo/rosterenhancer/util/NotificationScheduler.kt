package com.matteo.rosterenhancer.util

import kotlinx.datetime.toLocalDateTime
import com.matteo.rosterenhancer.util.*
import kotlinx.datetime.periodUntil
import com.matteo.rosterenhancer.util.atZone
import com.matteo.rosterenhancer.util.atTime
import com.matteo.rosterenhancer.util.minusMinutes
import com.matteo.rosterenhancer.util.isAfter
import com.matteo.rosterenhancer.util.isBefore

import android.content.Context
import androidx.work.*
import com.matteo.rosterenhancer.domain.model.Shift
import com.matteo.rosterenhancer.domain.model.ShiftType
import com.matteo.rosterenhancer.worker.ShiftReminderWorker
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

object NotificationScheduler {

    // Di default avvisiamo 90 minuti prima
    suspend fun scheduleShiftReminders(context: Context, shifts: List<Shift>) {
        val workManager = WorkManager.getInstance(context)
        
        // Pialla tutte le notifiche programmate in precedenza per non avere accavallamenti
        workManager.cancelAllWorkByTag("SHIFT_REMINDER")

        val dataStoreManager = com.matteo.rosterenhancer.util.DataStoreManager(context)
        val reminderMinutes = dataStoreManager.reminderMinutes.first()
        
        if (reminderMinutes == -1) {
            return // Notifiche disabilitate
        }

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        // Scegliamo solo i turni LAVORATIVI nel futuro
        val upcomingWorkShifts = shifts.filter {
            it.shiftType == ShiftType.WORK && it.startTime != null && it.date != null
        }.mapNotNull { shift ->
            val shiftStartDateTime = shift.date.atTime(shift.startTime!!)
            // Se il turno ha superato la mezzanotte rispetto all'inizio del mese? Qui diamo per scontato che inizi oggi.
            if (shiftStartDateTime.isAfter(now)) {
                shift to shiftStartDateTime
            } else null
        }

        for ((shift, startDateTime) in upcomingWorkShifts) {
            val notifyTime = startDateTime.minusMinutes(reminderMinutes.toLong())
            
            // Se l'orario di notifica è nel passato, skippa (il turno inizia tra poco)
            if (notifyTime.isBefore(now)) continue

            val delayInMillis = notifyTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() -
                                now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

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

    fun showShiftNotification(context: Context, title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
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

        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
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






