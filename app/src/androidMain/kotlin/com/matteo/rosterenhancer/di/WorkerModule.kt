package com.matteo.rosterenhancer.di

import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.dsl.module
import com.matteo.rosterenhancer.worker.CloudSyncWorker
import com.matteo.rosterenhancer.worker.ShiftReminderWorker

val workerModule = module {
    workerOf(::CloudSyncWorker)
    workerOf(::ShiftReminderWorker)
}



