package com.matteo.rosterenhancer.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile

actual fun createDataStore(context: Any?): DataStore<Preferences> {
    require(context is Context) { "Android context must be provided" }
    return PreferenceDataStoreFactory.create(
        produceFile = { context.filesDir.resolve("roster_prefs.preferences_pb") }
    )
}
