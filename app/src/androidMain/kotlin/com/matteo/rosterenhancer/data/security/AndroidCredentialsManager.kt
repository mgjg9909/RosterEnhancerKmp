package com.matteo.rosterenhancer.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidCredentialsManager(
    private val context: Context
) : CredentialsManager {
    private val TAG = "CredentialsManager"

    private val sharedPreferences: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "secret_shared_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Keystore non disponibile, uso fallback non cifrato.", e)
            context.getSharedPreferences("secure_credentials_fallback", Context.MODE_PRIVATE)
        }
    }

    override suspend fun saveCredentials(username: String, password: String): Unit =
        withContext(Dispatchers.IO) {
            sharedPreferences.edit()
                .putString("username", username)
                .putString("password", password)
                .apply()
        }

    override suspend fun getUsername(): String? =
        withContext(Dispatchers.IO) {
            sharedPreferences.getString("username", null)
        }

    override suspend fun getPassword(): String? =
        withContext(Dispatchers.IO) {
            sharedPreferences.getString("password", null)
        }

    override suspend fun hasCredentials(): Boolean =
        withContext(Dispatchers.IO) {
            !sharedPreferences.getString("username", null).isNullOrBlank() &&
            !sharedPreferences.getString("password", null).isNullOrBlank()
        }

    override suspend fun clearCredentials(): Unit =
        withContext(Dispatchers.IO) {
            sharedPreferences.edit().clear().apply()
        }
}
