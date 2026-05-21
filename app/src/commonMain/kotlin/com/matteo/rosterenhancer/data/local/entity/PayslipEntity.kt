package com.matteo.rosterenhancer.data.local.entity

import androidx.room.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Entity(
    tableName = "payslips",
    indices = [Index(value = ["month", "year"], unique = true)]
)
data class PayslipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val month: Int,
    val year: Int,
    val netPay: Double,
    val grossPay: Double = 0.0,
    val rawText: String = "",
    val fileName: String = "",
    val uploadDate: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    val wasCalibrated: Boolean = false,
    val filePath: String? = null
)

@Entity(tableName = "learning_logs")
data class LearningLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    val month: Int,
    val year: Int,
    val message: String,
    val diffAmount: Double = 0.0,
    val parameterChanged: String? = null,
    val oldValue: String? = null,
    val newValue: String? = null
)

