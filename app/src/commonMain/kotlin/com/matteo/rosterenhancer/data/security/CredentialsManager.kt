package com.matteo.rosterenhancer.data.security

interface CredentialsManager {
    suspend fun saveCredentials(username: String, password: String)
    suspend fun getUsername(): String?
    suspend fun getPassword(): String?
    suspend fun hasCredentials(): Boolean
    suspend fun clearCredentials()
}
