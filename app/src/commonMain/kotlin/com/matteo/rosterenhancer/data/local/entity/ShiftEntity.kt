package com.matteo.rosterenhancer.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@Entity(
    tableName = "shifts",
    foreignKeys = [
        ForeignKey(
            entity = MonthRosterEntity::class,
            parentColumns = ["id"],
            childColumns = ["monthRosterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("monthRosterId"),
        Index("employeeId"),
        Index(value = ["employeeName", "date"], unique = true)
    ]
)
data class ShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: String,
    val employeeName: String,
    val date: LocalDate,
    val startTime: LocalTime? = null,
    val durationHours: Int? = null,
    val endTime: LocalTime? = null,
    val role: String? = null,
    val shiftType: String,          // ShiftType.name()
    val rawCode: String = "",
    val monthRosterId: Long,
    val overtimeMinutes: Int = 0,
    val overtimeStartTime: LocalTime? = null,
    val overtimeEndTime: LocalTime? = null,
    val isHoliday: Boolean = false,
    val isManual: Boolean = false,
    val isMensaLavorata: Boolean = false,
    val notes: String = ""
)

