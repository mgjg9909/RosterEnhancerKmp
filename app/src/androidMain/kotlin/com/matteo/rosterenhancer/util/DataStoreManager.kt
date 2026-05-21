package com.matteo.rosterenhancer.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import com.matteo.rosterenhancer.domain.model.GpgProfile

import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "roster_prefs")

@Singleton
class DataStoreManager constructor(
    private val context: Context
) {
    companion object {
        val SELF_MATRICOLA        = stringPreferencesKey("self_matricola")
        val ONBOARDING_DONE       = booleanPreferencesKey("onboarding_done")
        val USE_DARK_THEME        = booleanPreferencesKey("use_dark_theme")
        val FAVORITE_COLLEAGUES   = stringSetPreferencesKey("favorite_colleagues")
        val MONTHLY_HOURS_TARGET  = intPreferencesKey("monthly_hours_target")
        val USER_GENDER           = stringPreferencesKey("user_gender")
        val IS_SUPERVISOR         = booleanPreferencesKey("is_supervisor")
        val IS_FAS                = booleanPreferencesKey("is_fas")
        val HOURLY_RATE           = stringPreferencesKey("hourly_rate")
        val NIGHT_BONUS           = stringPreferencesKey("night_bonus")
        val REMINDER_MINUTES      = intPreferencesKey("reminder_minutes")
        
        // Nuovi campi per CCNL GPG
        val GPG_LEVEL             = intPreferencesKey("gpg_level")
        val GPG_SENIORITY_STEPS   = intPreferencesKey("gpg_seniority_steps")
        val GPG_PART_TIME_PERCENT = stringPreferencesKey("gpg_part_time_percent")
        val GPG_TAX_RATE          = stringPreferencesKey("gpg_tax_rate")
        val GPG_AIRPORT_INDEMNITY = stringPreferencesKey("gpg_airport_indemnity")
        val GEMINI_API_KEY         = stringPreferencesKey("gemini_api_key")

        /** Opzioni disponibili per le ore mensili contrattuali */
        val MONTHLY_HOURS_OPTIONS = listOf(24, 25, 30, 32, 40)
        const val MONTHLY_HOURS_DEFAULT = 40
    }

    val selfMatricola: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SELF_MATRICOLA] ?: ""
    }

    val isOnboardingDone: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[ONBOARDING_DONE] ?: false
    }

    val useDarkTheme: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[USE_DARK_THEME] ?: true
    }

    val favoriteColleagues: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[FAVORITE_COLLEAGUES] ?: emptySet()
    }

    val monthlyHoursTarget: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[MONTHLY_HOURS_TARGET] ?: MONTHLY_HOURS_DEFAULT
    }

    val userGender: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[USER_GENDER] ?: ""
    }

    val isSupervisor: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IS_SUPERVISOR] ?: false
    }

    val isFas: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IS_FAS] ?: false
    }
    
    val hourlyRate: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[HOURLY_RATE]?.toFloatOrNull() ?: 10.50f
    }

    val nightBonus: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[NIGHT_BONUS]?.toFloatOrNull() ?: 2.50f
    }

    val reminderMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[REMINDER_MINUTES] ?: 90
    }

    val gpgLevel: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[GPG_LEVEL] ?: 7
    }

    val gpgSenioritySteps: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[GPG_SENIORITY_STEPS] ?: 0
    }

    val gpgPartTimePercent: Flow<Double> = context.dataStore.data.map { prefs ->
        prefs[GPG_PART_TIME_PERCENT]?.toDoubleOrNull() ?: 100.0
    }

    val gpgTaxRate: Flow<Double> = context.dataStore.data.map { prefs ->
        prefs[GPG_TAX_RATE]?.toDoubleOrNull() ?: 0.15
    }

    val gpgAirportIndemnity: Flow<Double> = context.dataStore.data.map { prefs ->
        val raw: String? = prefs[GPG_AIRPORT_INDEMNITY]
        raw?.toDoubleOrNull() ?: 3.045
    }

    val gpgProfile: Flow<GpgProfile> = combine(
        gpgLevel,
        gpgSenioritySteps,
        gpgPartTimePercent,
        gpgTaxRate,
        gpgAirportIndemnity
    ) { level: Int, steps: Int, pt: Double, tax: Double, airport: Double ->
        GpgProfile(
            level = level,
            gpgSenioritySteps = steps,
            partTimePercentage = pt,
            taxRate = tax,
            airportIndemnity = airport
        )
    }

    suspend fun setSelfMatricola(matricola: String) {
        context.dataStore.edit { prefs -> prefs[SELF_MATRICOLA] = matricola }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { prefs -> prefs[ONBOARDING_DONE] = done }
    }

    suspend fun setUseDarkTheme(dark: Boolean) {
        context.dataStore.edit { prefs -> prefs[USE_DARK_THEME] = dark }
    }

    suspend fun setMonthlyHoursTarget(hours: Int) {
        context.dataStore.edit { prefs -> prefs[MONTHLY_HOURS_TARGET] = hours }
    }

    suspend fun setUserGender(gender: String) {
        context.dataStore.edit { prefs -> prefs[USER_GENDER] = gender }
    }

    suspend fun setIsSupervisor(supervisor: Boolean) {
        context.dataStore.edit { prefs -> prefs[IS_SUPERVISOR] = supervisor }
    }

    suspend fun setIsFas(fas: Boolean) {
        context.dataStore.edit { prefs -> prefs[IS_FAS] = fas }
    }

    suspend fun setHourlyRate(rate: Float) {
        context.dataStore.edit { prefs -> prefs[HOURLY_RATE] = rate.toString() }
    }

    suspend fun setNightBonus(bonus: Float) {
        context.dataStore.edit { prefs -> prefs[NIGHT_BONUS] = bonus.toString() }
    }

    suspend fun setReminderMinutes(minutes: Int) {
        context.dataStore.edit { prefs -> prefs[REMINDER_MINUTES] = minutes }
    }

    suspend fun setGpgLevel(level: Int) {
        context.dataStore.edit { prefs -> prefs[GPG_LEVEL] = level }
    }

    suspend fun setGpgSenioritySteps(steps: Int) {
        context.dataStore.edit { prefs -> prefs[GPG_SENIORITY_STEPS] = steps }
    }

    suspend fun setGpgPartTimePercent(percent: Double) {
        context.dataStore.edit { prefs -> prefs[GPG_PART_TIME_PERCENT] = percent.toString() }
    }

    suspend fun setGpgTaxRate(rate: Double) {
        context.dataStore.edit { it[GPG_TAX_RATE] = rate.toString() }
    }

    suspend fun setGpgAirportIndemnity(amount: Double) {
        context.dataStore.edit { it[GPG_AIRPORT_INDEMNITY] = amount.toString() }
    }

    suspend fun toggleFavoriteColleague(employeeId: String) {
        context.dataStore.edit { prefs ->
            val currentFavorites = prefs[FAVORITE_COLLEAGUES] ?: emptySet()
            if (currentFavorites.contains(employeeId)) {
                prefs[FAVORITE_COLLEAGUES] = currentFavorites - employeeId
            } else {
                prefs[FAVORITE_COLLEAGUES] = currentFavorites + employeeId
            }
        }
    }

    val geminiApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[GEMINI_API_KEY] ?: ""
    }

    suspend fun setGeminiApiKey(key: String) {
        context.dataStore.edit { it[GEMINI_API_KEY] = key }
    }
}



