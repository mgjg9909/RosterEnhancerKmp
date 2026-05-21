package com.matteo.rosterenhancer.domain.parser

import com.matteo.rosterenhancer.domain.model.Employee
import com.matteo.rosterenhancer.domain.model.Shift

/**
 * Shared data class for the result of an XLSX parse operation.
 * Defined in commonMain so RosterRepository can reference it.
 * The actual XlsxParser implementation lives in androidMain.
 */
data class ParseResult(
    val employees: List<Employee>,
    val shifts: List<Shift>,
    val month: Int,
    val year: Int,
    val fileName: String = "",
    val debugInfo: String = ""
)
