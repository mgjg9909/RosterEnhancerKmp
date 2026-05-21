package com.matteo.rosterenhancer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "employees")
data class EmployeeEntity(
    @PrimaryKey val id: String,     // Matricola
    val fullName: String,
    val isSelf: Boolean = false
)

