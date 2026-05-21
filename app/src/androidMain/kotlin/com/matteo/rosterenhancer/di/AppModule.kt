package com.matteo.rosterenhancer.di

import org.koin.dsl.module
import org.koin.core.module.dsl.singleOf
import android.content.Context
import androidx.room.Room
import com.matteo.rosterenhancer.data.local.RosterDatabase
import com.matteo.rosterenhancer.data.local.dao.*
import com.matteo.rosterenhancer.data.repository.RosterRepository
import com.matteo.rosterenhancer.util.DataStoreManager
import com.matteo.rosterenhancer.util.NotificationScheduler
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage

import androidx.sqlite.driver.bundled.BundledSQLiteDriver

val appModule = module {
    single {
        val context = get<Context>()
        val dbFile = context.getDatabasePath("roster_database.db")
        Room.databaseBuilder<RosterDatabase>(
            context = context,
            name = dbFile.absolutePath
        )
        .setDriver(BundledSQLiteDriver())
        .addMigrations(RosterDatabase.MIGRATION_8_9, RosterDatabase.MIGRATION_9_10)
        .fallbackToDestructiveMigration(true)
        .build()
    }

    single { get<RosterDatabase>().employeeDao() }
    single { get<RosterDatabase>().shiftDao() }
    single { get<RosterDatabase>().monthRosterDao() }
    single { get<RosterDatabase>().shiftNoteDao() }
    single { get<RosterDatabase>().payslipDao() }

    single {
        HttpClient(OkHttp) {
            install(HttpCookies) {
                storage = AcceptAllCookiesStorage()
            }
            followRedirects = true
        }
    }

    singleOf(::DataStoreManager)
    singleOf(::RosterRepository)
}



