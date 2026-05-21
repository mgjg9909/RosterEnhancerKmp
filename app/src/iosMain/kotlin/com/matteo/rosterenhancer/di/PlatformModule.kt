package com.matteo.rosterenhancer.di

import org.koin.core.module.Module
import org.koin.dsl.module
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.room.Room
import com.matteo.rosterenhancer.data.local.RosterDatabase
import com.matteo.rosterenhancer.data.local.instantiateImpl
import com.matteo.rosterenhancer.util.createDataStore
import platform.Foundation.NSHomeDirectory
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import com.matteo.rosterenhancer.data.network.IRosterScraper
import com.matteo.rosterenhancer.data.security.CredentialsManager
import com.matteo.rosterenhancer.domain.model.Shift

actual val platformModule: Module = module {
    single {
        val dbFilePath = NSHomeDirectory() + "/roster_database.db"
        Room.databaseBuilder<RosterDatabase>(
            name = dbFilePath,
            factory = { RosterDatabase::class.instantiateImpl() }
        )
        .setDriver(BundledSQLiteDriver())
        .fallbackToDestructiveMigration(true)
        .build()
    }

    single {
        createDataStore()
    }

    single {
        HttpClient(Darwin) {
            install(HttpCookies) {
                storage = AcceptAllCookiesStorage()
            }
            engine {
                configureRequest {
                    setAllowsCellularAccess(true)
                }
            }
        }
    }
    
    // Stubs for missing iOS implementations required by RosterRepository
    single<IRosterScraper> {
        object : IRosterScraper {
            override suspend fun login(user: String, pass: String): Result<String> = Result.failure(Exception("Not implemented on iOS yet"))
            override suspend fun fetchMyRoster(): Result<List<Shift>> = Result.failure(Exception("Not implemented on iOS yet"))
            override suspend fun fetchGroupRoster(): Result<List<Shift>> = Result.failure(Exception("Not implemented on iOS yet"))
        }
    }
    
    single<CredentialsManager> {
        object : CredentialsManager {
            override fun saveCredentials(user: String, pass: String) {}
            override fun getCredentials(): Pair<String, String>? = null
            override fun clearCredentials() {}
        }
    }
}
