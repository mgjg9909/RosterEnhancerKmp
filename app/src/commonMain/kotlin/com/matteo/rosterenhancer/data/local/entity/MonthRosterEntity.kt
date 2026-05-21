package com.matteo.rosterenhancer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Clock

@Entity(tableName = "month_rosters")
data class MonthRosterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val month: Int,
    val year: Int,
    val importedAt: Long = Clock.System.now().toEpochMilliseconds(),
    val fileName: String = "",
    val employeeCount: Int = 0
)

