package com.matteo.rosterenhancer.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat

import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters




class ShiftReminderWorker constructor(
     appContext: Context,
     workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val shiftId = inputData.getString("SHIFT_ID") ?: return Result.failure()
        val shiftStart = inputData.getString("SHIFT_START") ?: "Tra poco"
        val shiftRole = inputData.getString("SHIFT_ROLE") ?: "un turno"

        com.matteo.rosterenhancer.util.NotificationScheduler.showShiftNotification(applicationContext, "Turno in avvicinamento! ⏳", "Inizia alle $shiftStart ($shiftRole). Verifica l'app CCE o preparati!")
        return Result.success()
    }
}



