package com.matteo.rosterenhancer.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import javax.inject.Singleton

/**
 * Gestisce le credenziali in modo sicuro.
 *
 * IMPORTANTE: tutte le funzioni pubbliche sono suspend e girano su Dispatchers.IO.
 * NON chiamare da main thread — causerebbe ANR su Samsung con hw-backed Keystore.
 */
@Singleton
class CredentialsManager constructor(
    private val context: Context
) {
    private val TAG = "CredentialsManager"

    // Inizializzazione lazy ANCHE qui, MA accessibile solo tramite funzioni suspend
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

    /** Salva le credenziali. Deve essere chiamata su Dispatchers.IO. */
    suspend fun saveCredentials(username: String, password: String) =
        withContext(Dispatchers.IO) {
            sharedPreferences.edit()
                .putString("username", username)
                .putString("password", password)
                .apply()
        }

    /** Legge lo username. Deve essere chiamata su Dispatchers.IO. */
    suspend fun getUsername(): String? =
        withContext(Dispatchers.IO) {
            sharedPreferences.getString("username", null)
        }

    /** Legge la password. Deve essere chiamata su Dispatchers.IO. */
    suspend fun getPassword(): String? =
        withContext(Dispatchers.IO) {
            sharedPreferences.getString("password", null)
        }

    /** Controlla se le credenziali sono presenti. Deve essere chiamata su Dispatchers.IO. */
    suspend fun hasCredentials(): Boolean =
        withContext(Dispatchers.IO) {
            !sharedPreferences.getString("username", null).isNullOrBlank() &&
            !sharedPreferences.getString("password", null).isNullOrBlank()
        }

    /** Cancella le credenziali. Deve essere chiamata su Dispatchers.IO. */
    suspend fun clearCredentials() =
        withContext(Dispatchers.IO) {
            sharedPreferences.edit().clear().apply()
        }
}



