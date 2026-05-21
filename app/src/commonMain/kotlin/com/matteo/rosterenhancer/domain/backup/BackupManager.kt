package com.matteo.rosterenhancer.domain.backup

import com.matteo.rosterenhancer.domain.model.Shift
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Serializable
data class BackupData(
    val version: Int = 1,
    val exportDate: String,
    val shifts: List<Shift>
)

class BackupManager {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Converte una lista di turni in una stringa JSON formattata.
     */
    fun exportBackup(shifts: List<Shift>): String {
        val data = BackupData(
            exportDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString(),
            shifts = shifts
        )
        return json.encodeToString(data)
    }

    /**
     * Parsa una stringa JSON e restituisce la lista di turni.
     * Lancia eccezione se il formato non è valido o la versione è incompatibile.
     */
    fun importBackup(jsonString: String): List<Shift> {
        val data = json.decodeFromString<BackupData>(jsonString)
        if (data.version > 1) {
            throw Exception("Spiacenti, questo file di backup è stato generato con una versione più recente dell'app (${data.version}) e non può essere importato.")
        }
        return data.shifts
    }
}

