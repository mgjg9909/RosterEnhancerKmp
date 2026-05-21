package com.matteo.rosterenhancer.data.security

import platform.Foundation.NSUserDefaults

class IosCredentialsManager : CredentialsManager {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun saveCredentials(username: String, password: String) {
        defaults.setObject(username, "username")
        defaults.setObject(password, "password")
        defaults.synchronize()
    }

    override suspend fun getUsername(): String? {
        return defaults.stringForKey("username")
    }

    override suspend fun getPassword(): String? {
        return defaults.stringForKey("password")
    }

    override suspend fun hasCredentials(): Boolean {
        return !getUsername().isNullOrBlank() && !getPassword().isNullOrBlank()
    }

    override suspend fun clearCredentials() {
        defaults.removeObjectForKey("username")
        defaults.removeObjectForKey("password")
        defaults.synchronize()
    }
}
