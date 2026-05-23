package com.matteo.rosterenhancer.di

import org.koin.core.module.Module
import org.koin.dsl.module
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.room.Room
import com.matteo.rosterenhancer.data.local.RosterDatabase
import com.matteo.rosterenhancer.util.createDataStore
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSFileManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import com.matteo.rosterenhancer.data.network.IRosterScraper
import com.matteo.rosterenhancer.data.security.CredentialsManager
import com.matteo.rosterenhancer.domain.model.Shift
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

import platform.Foundation.NSHomeDirectory

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual val platformModule: Module = module {
    single {
        val dbFilePath = NSHomeDirectory() + "/Documents/roster_database.db"
        Room.databaseBuilder<RosterDatabase>(
            name = dbFilePath,
            factory = { com.matteo.rosterenhancer.data.local.RosterDatabaseConstructor.initialize() }
        )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
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
        }
    }
    
    // Stubs for missing iOS implementations required by RosterRepository
    single<com.matteo.rosterenhancer.domain.parser.XlsxParser> {
        object : com.matteo.rosterenhancer.domain.parser.XlsxParser {
            override suspend fun parse(fileBytes: ByteArray): com.matteo.rosterenhancer.domain.parser.ParseResult = com.matteo.rosterenhancer.domain.parser.ParseResult(emptyList(), emptyList(), 1, 2024, "", "Not implemented on iOS")
            override fun parseShiftCell(rawCode: String, employeeId: String, employeeName: String, date: kotlinx.datetime.LocalDate, monthRosterId: Long): com.matteo.rosterenhancer.domain.model.Shift = throw Exception("Not implemented on iOS")
        }
    }
    
    single<com.matteo.rosterenhancer.domain.payslip.PayslipProcessor> {
        object : com.matteo.rosterenhancer.domain.payslip.PayslipProcessor {
            override suspend fun processNewPayslip(fileBytes: ByteArray, fileName: String, isPdf: Boolean): com.matteo.rosterenhancer.domain.payslip.CalibrationResult = throw Exception("Not implemented")
            override suspend fun applyCalibration(delta: Double, month: Int, year: Int) {}
            override fun deleteFile(filePath: String) {}
        }
    }

    single<IRosterScraper> {
        object : IRosterScraper {
            override suspend fun login(user: String, pass: String): Result<String> = Result.failure(Exception("Not implemented on iOS yet"))
            override suspend fun fetchMyRoster(): Result<List<Shift>> = Result.failure(Exception("Not implemented on iOS yet"))
            override suspend fun fetchGroupRoster(): Result<List<Shift>> = Result.failure(Exception("Not implemented on iOS yet"))
        }
    }
    
    single<CredentialsManager> {
        object : CredentialsManager {
            override suspend fun saveCredentials(username: String, password: String) {}
            override suspend fun getUsername(): String? = null
            override suspend fun getPassword(): String? = null
            override suspend fun hasCredentials(): Boolean = false
            override suspend fun clearCredentials() {}
        }
    }
}