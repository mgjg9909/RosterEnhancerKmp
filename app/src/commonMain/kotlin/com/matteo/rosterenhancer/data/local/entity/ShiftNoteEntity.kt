package com.matteo.rosterenhancer.data.local.entity

import androidx.room.Entity
import kotlinx.datetime.LocalDate

/**
 * Tabella separata per le annotazioni sui turni.
 * La chiave primaria è (employeeName + date) e NON è legata al ShiftEntity tramite FK,
 * così sopravvive ai re-import del roster senza essere cancellata.
 */
@Entity(
    tableName = "shift_notes",
    primaryKeys = ["employeeName", "date"]
)
data class ShiftNoteEntity(
    val employeeName: String,
    val date: LocalDate,
    val note: String = "",
    val extraMinutes: Int = 0  // Minuti di prolungamento del turno (es. 30 = +0:30h)
)


