package com.matteo.rosterenhancer.ui.screen.calendar

import kotlinx.datetime.todayIn
import com.matteo.rosterenhancer.util.*
import kotlinx.datetime.periodUntil
import com.matteo.rosterenhancer.util.withDayOfMonth
import com.matteo.rosterenhancer.util.minusDays
import com.matteo.rosterenhancer.util.plusDays
import com.matteo.rosterenhancer.util.isAfter
import com.matteo.rosterenhancer.util.monthValue
import com.matteo.rosterenhancer.util.format

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matteo.rosterenhancer.ui.components.GlassCard
import com.matteo.rosterenhancer.domain.model.Shift
import com.matteo.rosterenhancer.domain.model.ShiftNote
import com.matteo.rosterenhancer.domain.model.ShiftType
import com.matteo.rosterenhancer.ui.theme.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import com.matteo.rosterenhancer.util.YearMonth
import com.matteo.rosterenhancer.util.DateTimeFormatter
import com.matteo.rosterenhancer.util.TextStyle
import com.matteo.rosterenhancer.util.Locale

@Composable
fun CollapsibleCalendarHeader(
    currentMonth: YearMonth,
    selectedDate: LocalDate?,
    shiftsByDate: Map<LocalDate, Shift>,
    onDateSelected: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val daysOfWeek = listOf("L", "M", "M", "G", "V", "S", "D")
    
    Card(
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: Mese + Frecce + Toggle Espansione
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPreviousMonth) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Mese precedente", tint = MaterialTheme.colorScheme.primary)
                }
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onExpandedChange(!isExpanded) }) {
                    Text(
                        text = "${currentMonth.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.ITALIAN).replaceFirstChar { it.uppercase() }} ${currentMonth.year}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle calendario",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                IconButton(onClick = onNextMonth) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Mese successivo", tint = MaterialTheme.colorScheme.primary)
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Intestazione giorni settimana
            Row(modifier = Modifier.fillMaxWidth()) {
                for (day in daysOfWeek) {
                    Text(day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))

            // Griglia giorni
            AnimatedContent(
                targetState = isExpanded,
                transitionSpec = {
                    expandVertically() togetherWith shrinkVertically()
                },
                label = "calendar_expansion"
            ) { expanded ->
                if (expanded) {
                    // Vista Mensile
                    val firstDay = currentMonth.atDay(1)
                    val startOffset = (firstDay.dayOfWeek.ordinal + 1 - 1) % 7
                    val rows = ((startOffset + currentMonth.lengthOfMonth) + 6) / 7
                    
                    Column {
                        for (row in 0 until rows) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                for (col in 0 until 7) {
                                    val dayNumber = row * 7 + col - startOffset + 1
                                    if (dayNumber < 1 || dayNumber > currentMonth.lengthOfMonth) {
                                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                                    } else {
                                        val date = currentMonth.atDay(dayNumber)
                                        DayCell(
                                            date = date,
                                            shift = shiftsByDate[date],
                                            isSelected = date == selectedDate,
                                            onDateSelected = onDateSelected,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Vista Settimanale
                    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                    val targetDate = selectedDate ?: today
                    
                    // Assicuriamoci che targetDate sia nel mese corrente
                    val dateToShow = if (targetDate.year == currentMonth.year && targetDate.monthNumber == currentMonth.month) {
                        targetDate
                    } else {
                        currentMonth.atDay(1)
                    }
                    
                    val startOfWeek = dateToShow.minusDays((dateToShow.dayOfWeek.ordinal + 1 - 1).toLong())
                    
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (i in 0 until 7) {
                            val date = startOfWeek.plusDays(i.toLong())
                            if (date.monthNumber == currentMonth.month) {
                                DayCell(
                                    date = date,
                                    shift = shiftsByDate[date],
                                    isSelected = date == selectedDate,
                                    onDateSelected = onDateSelected,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    shift: Shift?,
    isSelected: Boolean,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val isToday = date == Clock.System.todayIn(TimeZone.currentSystemDefault())
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    
    val dotColor = getAgendaShiftDotColor(shift)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable { onDateSelected(date) },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally, 
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                textAlign = TextAlign.Center,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
            )
            if (dotColor != null) {
                Spacer(Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            } else {
                // Mantiene lo spazio anche se non c'è il pallino per evitare saltelli nel testo
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

private fun getAgendaShiftDotColor(shift: Shift?): Color? {
    if (shift == null) return null
    return when (shift.shiftType) {
        ShiftType.WORK -> {
            when (com.matteo.rosterenhancer.util.RoleGroups.normalize(shift.role)) {
                "PAX" -> BluePrimary
                "BAG" -> ShiftNight
                "MER" -> ShiftAfternoon
                "TAG" -> Color(0xFF673AB7)
                "SER" -> SuccessGreen
                else -> Color(0xFF2196F3)
            }
        }
        ShiftType.REST_1, ShiftType.REST_2, ShiftType.DAY_OFF -> Color.Transparent
        ShiftType.ABSENT, ShiftType.PARENTAL_LEAVE -> ShiftAbsent
        else -> ShiftOther
    }
}

@Composable
fun AgendaShiftItem(
    shift: Shift,
    note: ShiftNote?,
    onClick: () -> Unit
) {
    val isWork = shift.shiftType == ShiftType.WORK
    val shiftColor = getAgendaShiftDotColor(shift) ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Data e Giorno (Sinistra)
        Column(
            modifier = Modifier.width(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = shift.date.format(DateTimeFormatter.ofPattern("EEE", Locale.ITALIAN)).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = shift.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Linea verticale colorata
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(shiftColor)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Dettagli Turno (Destra)
        Column(modifier = Modifier.weight(1f)) {
            if (isWork) {
                Text(
                    text = shift.timeRange,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = shift.role ?: "Turno",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (note != null || shift.overtimeMinutes > 0) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Outlined.Notes,
                            contentDescription = "Nota presente",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                Text(
                    text = when (shift.shiftType) {
                        ShiftType.REST_1 -> "Riposo"
                        ShiftType.REST_2 -> "Riposo"
                        ShiftType.DAY_OFF -> "Libero"
                        ShiftType.ABSENT -> "Assente"
                        ShiftType.PARENTAL_LEAVE -> "Congedo"
                        ShiftType.HOLIDAY -> "Festività"
                        else -> "Altro"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusColleaguesBottomSheet(
    shift: Shift,
    note: ShiftNote?,
    dayShifts: List<Shift>,
    onDismiss: () -> Unit,
    onEditNote: () -> Unit
) {
    val colleaguesInSameShift = remember(shift, dayShifts) {
        if (shift.shiftType != ShiftType.WORK || shift.startTime == null || shift.endTime == null) {
            emptyList()
        } else {
            dayShifts.filter {
                it.employeeId != shift.employeeId && 
                it.shiftType == ShiftType.WORK && 
                it.startTime != null && it.endTime != null &&
                !(it.endTime!! <= shift.startTime || it.startTime >= shift.endTime!!)
            }.sortedBy { it.startTime }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Dettaglio Turno", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(16.dp))
            
            AgendaShiftItem(shift = shift, note = note, onClick = {})
            
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Note e Straordinari", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onEditNote) { Text("Modifica") }
            }
            
            if (note?.note?.isNotBlank() == true || shift.overtimeMinutes > 0) {
                val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (note?.note?.isNotBlank() == true) {
                            Text(note.note, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (shift.overtimeMinutes > 0) {
                            if (note?.note?.isNotBlank() == true) Spacer(Modifier.height(4.dp))
                            Text("Straordinario: +${shift.overtimeMinutes} min", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Text("Nessuna nota presente.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            Spacer(Modifier.height(24.dp))

            Text("Colleghi in sovrapposizione", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            if (colleaguesInSameShift.isEmpty()) {
                Text("Nessun collega in turno in questa fascia oraria.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val listState = rememberLazyListState()
                val haptic = LocalHapticFeedback.current

                // Feedback aptico durante lo scroll
                LaunchedEffect(listState.isScrollInProgress) {
                    if (listState.isScrollInProgress) {
                        snapshotFlow { listState.firstVisibleItemIndex }
                            .collect { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    }
                }

                androidx.compose.foundation.lazy.LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 100.dp) // Spazio di sicurezza per la Bottom Nav
                ) {
                    items(colleaguesInSameShift.size) { index ->
                        val colleague = colleaguesInSameShift[index]
                        val colleagueColor = if (colleague.shiftType == ShiftType.WORK) workShiftColor(colleague.startTime?.hour) else MaterialTheme.colorScheme.outline
                        
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 20.dp,
                            borderAlpha = 0.2f,
                            glassAlpha = 0.4f
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Barra di stato verticale
                                Box(modifier = Modifier.fillMaxHeight().width(4.dp).background(colleagueColor.copy(alpha = 0.6f)))

                                Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(colleagueColor.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            colleague.employeeName.take(2).uppercase(), 
                                            style = MaterialTheme.typography.titleSmall, 
                                            color = colleagueColor, 
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column {
                                        Text(colleague.employeeName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold)
                                        val roleStr = colleague.role?.let { " • $it" } ?: ""
                                        Text(
                                            "${colleague.timeRange}$roleStr", 
                                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), 
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
fun DateRibbon(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current

    // Feedback aptico durante lo scroll (tick)
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .collect {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
        }
    }
    
    // Genera l'intero mese corrente + 7 giorni prima e dopo
    val dates = remember {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val start = today.withDayOfMonth(1).minusDays(7)
        val end = today.withDayOfMonth(today.lengthOfMonth).plusDays(7)
        
        val list = mutableListOf<LocalDate>()
        var curr = start
        while (!curr.isAfter(end)) {
            list.add(curr)
            curr = curr.plusDays(1)
        }
        list
    }
    
    // Auto-scroll rimosso per stabilità visiva su richiesta utente
    // Eseguiamo il posizionamento solo all'avvio (LaunchedEffect(Unit))
    LaunchedEffect(Unit) {
        val index = dates.indexOf(selectedDate).takeIf { it >= 0 } ?: return@LaunchedEffect
        listState.scrollToItem(index)
    }
    
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dates.forEachIndexed { index, date ->
            // Separatore Mese
            if (index == 0 || date.month != dates[index - 1].month) {
                item(key = "month_${date.monthValue}_${date.year}") {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = date.format(DateTimeFormatter.ofPattern("MMM", Locale.ITALIAN)).uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
                    }
                }
            }
            
            item(key = date.toString()) {
                val isSelected = date == selectedDate
                val isToday = date == Clock.System.todayIn(TimeZone.currentSystemDefault())
                
                val scale by animateFloatAsState(if (isSelected) 1.05f else 1f, label = "scale")
                
                GlassCard(
                    modifier = Modifier
                        .width(64.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clickable(
                            interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) { onDateSelected(date) },
                    cornerRadius = 20.dp,
                    borderAlpha = if (isSelected) 0.6f else 0.15f,
                    glassAlpha = if (isSelected) 0.25f else if (isToday) 0.08f else 0.3f
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = date.format(DateTimeFormatter.ofPattern("EEE", Locale.ITALIAN)).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            fontSize = 9.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = if (isSelected || isToday) FontWeight.Black else FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MyShiftBentoCard(
    shift: Shift?,
    selectedDate: LocalDate,
    onClick: () -> Unit
) {
    val isToday = selectedDate == Clock.System.todayIn(TimeZone.currentSystemDefault())
    val dateText = if (isToday) "OGGI" else selectedDate.format(DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)).uppercase()
    val color = if (shift?.shiftType == ShiftType.WORK) workShiftColor(shift.startTime?.hour) else MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(enabled = shift != null) { onClick() },
        cornerRadius = 32.dp,
        borderAlpha = 0.6f, // Bordo più marcato per risaltare
        glassAlpha = if (isDark) 0.3f else 0.45f,
        containerColor = color // Tinta di vetro colorato decisa
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isToday) Icons.Default.FlashOn else Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = if (isDark) Color.White else color.copy(alpha = 0.8f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "IL TUO TURNO • $dateText",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.1.sp
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (shift == null || shift.shiftType != ShiftType.WORK) {
                    val statusText = if (shift == null) "Nessun turno" else shift.shiftType.name.replace("_", " ").lowercase().capitalize()
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = shift.timeRange,
                        style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = shift.role ?: "In Servizio",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ColleagueHubCard(
    shift: Shift,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit
) {
    val color = if (shift.shiftType == ShiftType.WORK) workShiftColor(shift.startTime?.hour) else shiftTypeColor(shift.shiftType.name)

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        borderAlpha = 0.25f,
        glassAlpha = 0.2f,
        containerColor = color // Tinta di vetro colorato
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shift.employeeName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                val infoText = if (shift.shiftType == ShiftType.WORK) {
                    val roleStr = shift.role?.let { " • $it" } ?: ""
                    "${shift.timeRange}$roleStr"
                } else {
                    shift.shiftType.name.replace("_", " ").lowercase().capitalize()
                }
                Text(
                    text = infoText,
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
            }
            
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isFavorite) color.copy(alpha = 0.1f) else Color.Transparent)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = null,
                    tint = if (isFavorite) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun GhostEmptyCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.1f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(androidx.compose.material.icons.Icons.Default.Coffee, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text("Nessun collega operativo", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}










