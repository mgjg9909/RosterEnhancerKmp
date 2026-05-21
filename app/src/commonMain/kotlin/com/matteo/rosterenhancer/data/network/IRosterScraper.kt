package com.matteo.rosterenhancer.data.network

import com.matteo.rosterenhancer.domain.model.Shift

/**
 * Interface for the web roster scraper.
 * Defined in commonMain so RosterRepository can reference it.
 * The actual implementation lives in androidMain/RosterScraper.kt.
 */
interface IRosterScraper {
    suspend fun login(user: String, pass: String): Result<String>
    suspend fun fetchMyRoster(): Result<List<Shift>>
    suspend fun fetchGroupRoster(): Result<List<Shift>>
}
