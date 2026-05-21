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
import com.matteo.rosterenhancer.domain.parser.XlsxParser

import androidx.sqlite.driver.bundled.BundledSQLiteDriver

import org.koin.core.module.Module
import com.matteo.rosterenhancer.util.createDataStore

actual val platformModule: Module = module {
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

    single {
        createDataStore(get<Context>())
    }

    single {
        HttpClient(OkHttp) {
            install(HttpCookies) {
                storage = AcceptAllCookiesStorage()
            }
            followRedirects = true
        }
    }

    singleOf(::XlsxParser)
    single<com.matteo.rosterenhancer.data.network.IRosterScraper> {
        com.matteo.rosterenhancer.data.network.RosterScraper(get(), get(), get())
    }
    single<com.matteo.rosterenhancer.data.security.CredentialsManager> {
        com.matteo.rosterenhancer.data.security.AndroidCredentialsManager(get())
    }
}
