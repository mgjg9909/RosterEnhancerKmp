package com.matteo.rosterenhancer.ui.components
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

import com.matteo.rosterenhancer.util.plusMinutes
import com.matteo.rosterenhancer.util.format

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.matteo.rosterenhancer.domain.model.Shift
import com.matteo.rosterenhancer.domain.model.ShiftNote
import com.matteo.rosterenhancer.ui.components.WheelPicker
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.matteo.rosterenhancer.util.DateTimeFormatter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftNoteBottomSheet(
    shift: Shift,
    existingNote: ShiftNote?,
    colleagues: List<Shift> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (noteText: String, extraMinutes: Int, startTime: LocalTime?, endTime: LocalTime?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var text by remember { mutableStateOf(existingNote?.note ?: "") }
    
    val initialOtTotal = (existingNote?.extraMinutes?.takeIf { it > 0 }) ?: shift.overtimeMinutes
    var hasOvertime by remember { mutableStateOf(initialOtTotal > 0) }
    
    val otStart = shift.overtimeStartTime ?: shift.endTime ?: LocalTime(15, 0)
    val otEnd = shift.overtimeEndTime ?: otStart.plusMinutes(initialOtTotal.toLong())
    
    var otStartHour by remember { mutableStateOf(otStart.hour.toString().padStart(2, '0')) }
    var otStartMin by remember { mutableStateOf(otStart.minute.toString().padStart(2, '0')) }
    
    var otEndHour by remember { mutableStateOf(otEnd.hour.toString().padStart(2, '0')) }
    var otEndMin by remember { mutableStateOf(otEnd.minute.toString().padStart(2, '0')) }

    val hoursList = (0..23).map { it.toString().padStart(2, '0') }
    val minsList = listOf("00", "15", "30", "45")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Note Turno del ${shift.date.dayOfMonth}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Chiudi")
                }
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                placeholder = { Text("Appunti, anomalie, mancato badge...") },
                shape = RoundedCornerShape(12.dp)
            )

            if (colleagues.isNotEmpty()) {
                val groupName = com.matteo.rosterenhancer.util.RoleGroups.normalize(shift.role)
                val title = if (groupName == "PAX" || groupName == "PAM" || groupName == "PAF") {
                    "Inizia il turno con te"
                } else {
                    "Colleghi del giorno"
                }
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = title.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Black
                    )
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        items(colleagues) { colShift ->
                            val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
                            val startStr = colShift.startTime?.format(timeFormatter) ?: "--:--"
                            val endStr = colShift.endTime?.format(timeFormatter) ?: "--:--"
                            
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = colShift.employeeName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "$startStr - $endStr",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = colShift.role ?: "—",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

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
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
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
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                WheelPicker(items = hoursList, selectedItem = otEndHour, onItemSelected = { otEndHour = it }, modifier = Modifier.width(50.dp))
                                Text(":", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 4.dp))
                                WheelPicker(items = minsList, selectedItem = otEndMin, onItemSelected = { otEndMin = it }, modifier = Modifier.width(50.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (existingNote != null && (existingNote.note.isNotBlank() || existingNote.extraMinutes > 0)) {
                    OutlinedButton(
                        onClick = { onSave("", 0, null, null) }, // Azzerare tutto elimina la nota dal database per logica del repo
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Elimina")
                    }
                }
                
                Button(
                    onClick = { 
                        val otTotalMinutes: Int
                        val finalStartTime: LocalTime?
                        val finalEndTime: LocalTime?

                        if (hasOvertime) {
                            val sH = otStartHour.toIntOrNull() ?: 0
                            val sM = otStartMin.toIntOrNull() ?: 0
                            val eH = otEndHour.toIntOrNull() ?: 0
                            val eM = otEndMin.toIntOrNull() ?: 0
                            
                            val startTotal = sH * 60 + sM
                            var endTotal = eH * 60 + eM
                            if (endTotal < startTotal) endTotal += 24 * 60
                            
                            otTotalMinutes = endTotal - startTotal
                            finalStartTime = LocalTime(sH, sM)
                            finalEndTime = LocalTime(eH, eM)
                        } else {
                            otTotalMinutes = 0
                            finalStartTime = null
                            finalEndTime = null
                        }
                        onSave(text, otTotalMinutes, finalStartTime, finalEndTime) 
                    },
                    modifier = Modifier.weight(if (existingNote != null) 1f else 2f)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Salva")
                }
            }
        }
    }
}








