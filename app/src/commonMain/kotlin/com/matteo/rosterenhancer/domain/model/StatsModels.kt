package com.matteo.rosterenhancer.domain.model

import kotlinx.datetime.LocalDate

data class TopCompanion(
    val id: String,
    val name: String,
    val sharedHours: Int,
    val sharedDays: Int
)

data class SalaryTrendPoint(
    val monthName: String,
    val netSalary: Double,
    val month: Int,
    val year: Int
)

data class StatsUiState(
    val totalHours: Int = 0,
    val workDays: Int = 0,
    val restDays: Int = 0,
    val absentDays: Int = 0,
    // Distribuzione per fascia oraria
    val morningShifts: Int = 0,     // 03-06
    val centraleShifts: Int = 0,    // 07-11
    val afternoonShifts: Int = 0,   // 12-18
    val nightShifts: Int = 0,       // 19-02
    val roleDistribution: Map<String, Int> = emptyMap(),
    val topCompanions: List<TopCompanion> = emptyList(),
    val salaryTrend: List<SalaryTrendPoint> = emptyList(),
    val monthMaxHours: Int = 160,
    // Ore per settimana (label "Sett 1" -> ore)
    val weeklyHours: List<Pair<String, Int>> = emptyList(),
    // Heatmap: data -> tipo turno ("WORK","REST","OFF","ABSENT",...)
    val monthShiftMap: Map<LocalDate, String> = emptyMap(),
    val isLoading: Boolean = true
)
