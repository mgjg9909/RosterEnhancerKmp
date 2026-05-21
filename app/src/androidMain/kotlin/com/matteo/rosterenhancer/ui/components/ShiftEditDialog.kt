package com.matteo.rosterenhancer.ui.components

import kotlinx.datetime.todayIn
import com.matteo.rosterenhancer.util.*
import kotlinx.datetime.periodUntil
import com.matteo.rosterenhancer.util.plusHours
import com.matteo.rosterenhancer.util.plusMinutes

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.matteo.rosterenhancer.domain.model.Shift
import com.matteo.rosterenhancer.domain.model.ShiftType
import com.matteo.rosterenhancer.ui.screen.salary.WheelPicker
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.LocalTime
import com.matteo.rosterenhancer.util.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftEditDialog(
    shift: Shift? = null,
    initialDate: LocalDate? = null,
    currentMonth: YearMonth? = null,
    onDismiss: () -> Unit,
    onConfirm: (Shift) -> Unit
) {
    val isEdit = shift != null
    val targetDate = shift?.date ?: initialDate ?: Clock.System.todayIn(TimeZone.currentSystemDefault())
    
    // Stato per Data (solo se creazione libera e currentMonth fornito)
    var dayOfMonth by remember { 
        mutableStateOf(targetDate.dayOfMonth.toString()) 
    }
    val daysList = remember(currentMonth) { 
        currentMonth?.let { (1..it.lengthOfMonth).map { d -> d.toString() } } ?: emptyList() 
    }

    // Stato per Orario Inizio
    var startHour by remember { mutableStateOf(shift?.startTime?.hour?.toString()?.padStart(2, '0') ?: "07") }
    var startMin by remember { mutableStateOf(shift?.startTime?.minute?.toString()?.padStart(2, '0') ?: "00") }
    
    // Stato per Straordinari
    val initialOvertimeTotal = shift?.overtimeMinutes ?: 0
    var hasOvertime by remember { mutableStateOf(initialOvertimeTotal > 0) }
    
    val otStart = shift?.overtimeStartTime ?: shift?.endTime ?: LocalTime(15, 0)
    val otEnd = shift?.overtimeEndTime ?: otStart.plusMinutes(initialOvertimeTotal.toLong())
    
    var otStartHour by remember { mutableStateOf(otStart.hour.toString().padStart(2, '0')) }
    var otStartMin by remember { mutableStateOf(otStart.minute.toString().padStart(2, '0')) }
    
    var otEndHour by remember { mutableStateOf(otEnd.hour.toString().padStart(2, '0')) }
    var otEndMin by remember { mutableStateOf(otEnd.minute.toString().padStart(2, '0')) }

    var notes by remember { mutableStateOf(shift?.notes ?: "") }

    // Stato per Mansione e Durata
    var role by remember { mutableStateOf(shift?.role ?: "PAX") }
    var duration by remember { mutableStateOf(shift?.durationHours?.toString() ?: "8") }
    var type by remember { mutableStateOf(shift?.shiftType ?: ShiftType.WORK) }
    var isMensa by remember { mutableStateOf(shift?.isMensaLavorata ?: false) }
    
    val hoursList = (0..23).map { it.toString().padStart(2, '0') }
    val minsList = listOf("00", "15", "30", "45")
    val overtimeHoursList = (0..12).map { it.toString() }
    val rolesList = remember { listOf("PAX", "PAM", "BAG", "BAG_S", "SEF", "SEM", "PEM", "TAM", "TAF", "SPV") }
    val durationOptions = listOf("3", "5", "8")

    val isWorkRelated = type == ShiftType.WORK || type == ShiftType.INTERVENTO || 
                        type == ShiftType.MANCATO_R1 || type == ShiftType.MANCATO_R2

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 750.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Header
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isEdit) Icons.Default.Settings else Icons.Default.Add, 
                            null, 
                            tint = MaterialTheme.colorScheme.onPrimaryContainer, 
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (isEdit) "Modifica Turno" else "Aggiungi Turno", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    val dateLabel = if (isEdit || currentMonth == null) {
                        "${targetDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ITALIAN).capitalize()} ${targetDate.dayOfMonth} ${targetDate.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN).capitalize()}"
                    } else {
                        "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN).capitalize()} ${currentMonth.year}"
                    }
                    Text(text = dateLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(24.dp))

                // Content
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Tipologia
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Tipologia:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        val allowedTypes = listOf(
                            ShiftType.WORK,
                            ShiftType.REST_1,
                            ShiftType.REST_2,
                            ShiftType.ABSENT,
                            ShiftType.MANCATO_R1,
                            ShiftType.MANCATO_R2,
                            ShiftType.INTERVENTO
                        )
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            allowedTypes.forEach { t ->
                                val label = when(t) {
                                    ShiftType.WORK -> "Ordinario"
                                    ShiftType.REST_1 -> "R1"
                                    ShiftType.REST_2 -> "R2"
                                    ShiftType.ABSENT -> "Assente"
                                    ShiftType.MANCATO_R1 -> "Mancato R1"
                                    ShiftType.MANCATO_R2 -> "Mancato R2"
                                    ShiftType.INTERVENTO -> "Intervento"
                                    else -> t.name
                                }
                                FilterChip(
                                    selected = type == t,
                                    onClick = { type = t },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    // Selezione Giorno (solo creazione libera)
                    if (!isEdit && currentMonth != null) {
                         Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Giorno:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
                                WheelPicker(items = daysList, selectedItem = dayOfMonth, onItemSelected = { dayOfMonth = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                            }
                        }
                    }

                    if (isWorkRelated) {
                        // Orario Inizio
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Orario Inizio:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
                                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    WheelPicker(items = hoursList, selectedItem = startHour, onItemSelected = { startHour = it }, modifier = Modifier.width(60.dp))
                                    Text(":", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 8.dp))
                                    WheelPicker(items = minsList, selectedItem = startMin, onItemSelected = { startMin = it }, modifier = Modifier.width(60.dp))
                                }
                            }
                        }

                        // Durata
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Durata:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                durationOptions.forEach { d ->
                                    FilterChip(
                                        selected = duration == d,
                                        onClick = { duration = d },
                                        label = { Text("${d}h") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // Mansione
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Mansione:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rolesList.forEach { r ->
                                    FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r) })
                                }
                            }
                        }

                        // Straordinari & Note
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Prolungo / Straordinario", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Switch(checked = hasOvertime, onCheckedChange = { hasOvertime = it })
                            }
                            
                            if (hasOvertime) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // DA
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Dalle:", style = MaterialTheme.typography.labelMedium)
                                        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
                                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                WheelPicker(items = hoursList, selectedItem = otStartHour, onItemSelected = { otStartHour = it }, modifier = Modifier.width(50.dp))
                                                Text(":", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 4.dp))
                                                WheelPicker(items = minsList, selectedItem = otStartMin, onItemSelected = { otStartMin = it }, modifier = Modifier.width(50.dp))
                                            }
                                        }
                                    }
                                    
                                    // A
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Alle:", style = MaterialTheme.typography.labelMedium)
                                        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
                                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                WheelPicker(items = hoursList, selectedItem = otEndHour, onItemSelected = { otEndHour = it }, modifier = Modifier.width(50.dp))
                                                Text(":", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 4.dp))
                                                WheelPicker(items = minsList, selectedItem = otEndMin, onItemSelected = { otEndMin = it }, modifier = Modifier.width(50.dp))
                                            }
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                OutlinedTextField(
                                    value = notes,
                                    onValueChange = { notes = it },
                                    label = { Text("Note (opzionale)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }

                        // Mensa
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                             Column(modifier = Modifier.weight(1f)) {
                                Text("Pausa Mensa Retribuita", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text("Indennità presenze +45 min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = isMensa, onCheckedChange = { isMensa = it })
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Actions
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                        Text("Annulla")
                    }
                    Button(
                        onClick = {
                            val h = startHour.toIntOrNull() ?: 8
                            val m = startMin.toIntOrNull() ?: 0
                            val sTime = LocalTime(h, m)
                            val dur = duration.toIntOrNull() ?: 0
                            val otTotalMinutes = if (hasOvertime) {
                                val sH = otStartHour.toIntOrNull() ?: 0
                                val sM = otStartMin.toIntOrNull() ?: 0
                                val eH = otEndHour.toIntOrNull() ?: 0
                                val eM = otEndMin.toIntOrNull() ?: 0
                                
                                val startTotal = sH * 60 + sM
                                var endTotal = eH * 60 + eM
                                if (endTotal < startTotal) endTotal += 24 * 60
                                
                                endTotal - startTotal
                            } else 0
                            
                            val finalDate = if (!isEdit && currentMonth != null) {
                                currentMonth.atDay(dayOfMonth.toIntOrNull() ?: 1)
                            } else {
                                targetDate
                            }

                            val otStartTime = if (isWorkRelated && hasOvertime) {
                                val sOtH = otStartHour.toIntOrNull() ?: 0
                                val sOtM = otStartMin.toIntOrNull() ?: 0
                                LocalTime(sOtH, sOtM)
                            } else null
                            
                            val otEndTime = if (isWorkRelated && hasOvertime) {
                                val eOtH = otEndHour.toIntOrNull() ?: 0
                                val eOtM = otEndMin.toIntOrNull() ?: 0
                                LocalTime(eOtH, eOtM)
                            } else null

                            val result = (shift ?: Shift(
                                date = finalDate,
                                employeeId = "",
                                employeeName = "",
                                isManual = true,
                                shiftType = type
                            )).copy(
                                date = finalDate,
                                startTime = if (isWorkRelated) sTime else null,
                                durationHours = if (isWorkRelated) dur else 0,
                                endTime = if (isWorkRelated) sTime.plusHours(dur.toLong()) else null,
                                role = if (isWorkRelated) role else null,
                                shiftType = type,
                                isMensaLavorata = if (isWorkRelated) isMensa else false,
                                overtimeMinutes = if (isWorkRelated) otTotalMinutes else 0,
                                overtimeStartTime = otStartTime,
                                overtimeEndTime = otEndTime,
                                notes = if (isWorkRelated) notes.trim() else "",
                                isManual = true
                            )
                            onConfirm(result)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Salva", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun String.capitalize() = replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }








