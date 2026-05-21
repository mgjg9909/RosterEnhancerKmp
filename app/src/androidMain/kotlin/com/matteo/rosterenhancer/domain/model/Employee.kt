package com.matteo.rosterenhancer.domain.model

data class Employee(
    val id: String,         // Numero di matricola (es. "4213")
    val fullName: String,   // Nome completo (es. "DALL'OSSO LINDA")
    val isSelf: Boolean = false
)

