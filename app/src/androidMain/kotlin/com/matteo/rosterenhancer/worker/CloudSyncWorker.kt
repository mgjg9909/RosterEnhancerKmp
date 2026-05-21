package com.matteo.rosterenhancer.worker

import android.content.Context
import android.util.Log

import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.matteo.rosterenhancer.data.repository.RosterRepository


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class CloudSyncWorker constructor(
     appContext: Context,
     workerParams: WorkerParameters,
    private val repository: RosterRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d("CloudSyncWorker", "Inizio sincronizzazione in background...")
            
            // Il repository lancia la Sincronizzazione, e gestisce internamente 
            // il confronto dei turni vecchi/nuovi e il dispatch delle Notifiche push!
            val result = repository.syncWithWeb()
            
            if (result.isSuccess) {
                Log.d("CloudSyncWorker", "Esito sync background: ${result.getOrNull()}")
                Result.success()
            } else {
                Log.e("CloudSyncWorker", "Sync background fallita", result.exceptionOrNull())
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("CloudSyncWorker", "Eccezione catastrofica in sync", e)
            Result.failure()
        }
    }
}



