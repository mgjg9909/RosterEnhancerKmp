package com.matteo.rosterenhancer.domain.model

import kotlinx.serialization.Serializable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@Serializable
data class Shift(
    val id: Long = 0,
    val employeeId: String,
    val employeeName: String,
    val date: LocalDate,
    val startTime: LocalTime? = null,
    val durationHours: Int? = null,
    val endTime: LocalTime? = null,
    val role: String? = null,           // Mansione: BAG, SPV, PAX, ecc.
    val shiftType: ShiftType,
    val rawCode: String = "",           // Codice originale dal Excel per debug
    val monthRosterId: Long = 0,
    val overtimeMinutes: Int = 0,
    val overtimeStartTime: LocalTime? = null,
    val overtimeEndTime: LocalTime? = null,
    val isHoliday: Boolean = false,
    val isManual: Boolean = false,
    val isMensaLavorata: Boolean = false,
    val notes: String = ""
) {
    /** Ore lavorate in questo turno (0 se non è un turno lavorativo) */
    val workedHours: Int get() = if (shiftType == ShiftType.WORK) durationHours ?: 0 else 0

    /** True se il turno inizia prima delle 6:00 */
    val isNightShift: Boolean get() = startTime?.let { it.hour < 6 } ?: false

    /** Descrizione leggibile dell'orario */
    val timeRange: String get() {
        if (startTime == null || endTime == null) return ""
        val startStr = "${startTime.hour.toString().padStart(2, '0')}:${startTime.minute.toString().padStart(2, '0')}"
        val endStr = "${endTime.hour.toString().padStart(2, '0')}:${endTime.minute.toString().padStart(2, '0')}"
        return "$startStr → $endStr"
    }

    companion object {
        fun fromEntity(entity: com.matteo.rosterenhancer.data.local.entity.ShiftEntity): Shift {
            return Shift(
                id = entity.id,
                employeeId = entity.employeeId,
                employeeName = entity.employeeName,
                date = entity.date,
                startTime = entity.startTime,
                durationHours = entity.durationHours,
                endTime = entity.endTime,
                role = entity.role,
                shiftType = try { ShiftType.valueOf(entity.shiftType) } catch (e: Exception) { ShiftType.OTHER },
                rawCode = entity.rawCode,
                monthRosterId = entity.monthRosterId,
                overtimeMinutes = entity.overtimeMinutes,
                overtimeStartTime = entity.overtimeStartTime,
                overtimeEndTime = entity.overtimeEndTime,
                isHoliday = entity.isHoliday,
                isManual = entity.isManual,
                isMensaLavorata = entity.isMensaLavorata,
                notes = entity.notes
            )
        }
    }
}


