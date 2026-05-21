package com.matteo.rosterenhancer.ui.screen.calendar

import com.matteo.rosterenhancer.util.format

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matteo.rosterenhancer.domain.model.Shift
import com.matteo.rosterenhancer.domain.model.ShiftType
import com.matteo.rosterenhancer.domain.usecase.RestSwapCandidate
import com.matteo.rosterenhancer.domain.usecase.SwapCandidate
import com.matteo.rosterenhancer.ui.component.HapticStyle
import com.matteo.rosterenhancer.ui.component.bouncyClick
import com.matteo.rosterenhancer.ui.components.GlassCard
import com.matteo.rosterenhancer.ui.theme.*
import kotlinx.datetime.LocalDate
import com.matteo.rosterenhancer.util.*
import kotlinx.datetime.periodUntil
import com.matteo.rosterenhancer.util.DateTimeFormatter
import com.matteo.rosterenhancer.util.Locale

@Composable
fun MyShiftSwapBanner(
    shift: Shift,
    onClickSwap: () -> Unit
) {
    val color = workShiftColor(shift.startTime?.hour)
    
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .bouncyClick(hapticStyle = HapticStyle.LIGHT) { onClickSwap() },
        cornerRadius = 24.dp,
        containerColor = color,
        glassAlpha = 0.15f,
        borderAlpha = 0.3f
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CAMBIO TURNO",
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = shift.timeRange,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black
                )
                val role = shift.role?.takeIf { it.isNotBlank() }
                if (role != null) {
                    Text(
                        text = role,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = "Cambio Turno",
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftSwapBottomSheet(
    candidates: List<SwapCandidate>,
    targetDate: LocalDate,
    onDismiss: () -> Unit,
    onCandidateClick: (SwapCandidate) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
        ) {
            Text(
                "Matchmaker",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black
            )
            Text(
                "Scambi idonei e verificati dall'algoritmo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            if (candidates.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Nessun collega idoneo trovato.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 600.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    itemsIndexed(candidates, key = { _, it -> it.proposedShift.id }) { index, candidate ->
                        // Staggered entrance
                        val itemVisible = remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(index * 40L)
                            itemVisible.value = true
                        }
                        
                        AnimatedVisibility(
                            visible = itemVisible.value,
                            enter = slideInVertically(initialOffsetY = { 30 }) + fadeIn(animationSpec = tween(300)),
                            label = "candidate_$index"
                        ) {
                            CandidateSwapCard(candidate, onClick = { onCandidateClick(candidate) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CandidateSwapCard(
    candidate: SwapCandidate,
    onClick: () -> Unit
) {
    val shift = candidate.proposedShift
    val color = if (shift.shiftType == ShiftType.WORK) workShiftColor(shift.startTime?.hour) else ShiftRest1
    val isEnabled = candidate.validationError == null
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClick(enabled = isEnabled) { onClick() }
            .alpha(if (isEnabled) 1f else 0.6f),
        cornerRadius = 28.dp,
        containerColor = if (candidate.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        glassAlpha = if (candidate.isFavorite) 0.15f else (if (isDark) 0.1f else 0.05f),
        borderAlpha = if (candidate.isFavorite) 0.5f else 0.1f
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val nameNumRegex = remember { Regex("^(.+?)\\s+(\\d{3,8})\\s*$") }
                    val match = remember(candidate.employeeName) { nameNumRegex.find(candidate.employeeName) }
                    val cleanName = match?.groupValues?.get(1)?.trim() ?: candidate.employeeName
                    val cleanId = (match?.groupValues?.get(2)?.trim() ?: candidate.employeeId).replace("group_", "")

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            cleanName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = color
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = cleanName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Matricola $cleanId",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                
                if (candidate.isFavorite) {
                    Surface(
                        shape = CircleShape,
                        color = color.copy(alpha = 0.1f),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Star, null, tint = color, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("BEST", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = color)
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(20.dp))

            // Timeline-style Comparison
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniDayBadge("IERI", candidate.yesterdayShift, modifier = Modifier.weight(1f))
                MiniDayBadge("OGGI", candidate.proposedShift, isTarget = true, modifier = Modifier.weight(1.2f))
                MiniDayBadge("DOMANI", candidate.tomorrowShift, modifier = Modifier.weight(1f))
            }

            if (!isEnabled) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Block, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = candidate.validationError ?: "Incompatibile",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniDayBadge(label: String, shift: Shift?, modifier: Modifier = Modifier, isTarget: Boolean = false) {
    val color = when (shift?.shiftType) {
        ShiftType.WORK           -> workShiftColor(shift.startTime?.hour)
        ShiftType.REST_1         -> ShiftRest1
        ShiftType.REST_2         -> ShiftRest2
        ShiftType.DAY_OFF        -> ShiftDayOff
        else                     -> MaterialTheme.colorScheme.outline
    }
    
    val timeLabel = when {
        shift == null -> "--"
        shift.shiftType == ShiftType.WORK -> {
            val time = shift.timeRange.replace(" ", "")
            val role = shift.role?.take(3) ?: ""
            if (role.isNotEmpty()) "$time\n($role)" else time
        }
        else -> when (shift.shiftType) {
            ShiftType.REST_1 -> "R1"
            ShiftType.REST_2 -> "R2"
            ShiftType.DAY_OFF -> "RO"
            ShiftType.ABSENT -> "ASS"
            ShiftType.PARENTAL_LEAVE -> "CP"
            ShiftType.HOLIDAY -> "FES"
            else -> shift.shiftType.name.take(3)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isTarget) color.copy(alpha = 0.1f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
            .then(if (isTarget) Modifier.border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(16.dp)) else Modifier)
            .padding(horizontal = 4.dp, vertical = 10.dp)
    ) {
        Text(
            text = label, 
            style = MaterialTheme.typography.labelSmall, 
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            color = if (isTarget) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = timeLabel, 
            style = MaterialTheme.typography.labelSmall, 
            fontSize = 11.sp,
            lineHeight = 12.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Black,
            color = if (isTarget) color else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun RestSwapCandidateCard(
    candidate: RestSwapCandidate
) {
    val shift = candidate.theirShiftOnMyRestDate
    val color = workShiftColor(shift.startTime?.hour)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClick { /* Proponi */ },
        cornerRadius = 28.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        glassAlpha = if (isDark) 0.1f else 0.05f
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val nameNumRegex = remember { Regex("^(.+?)\\s+(\\d{3,8})\\s*$") }
                val match = remember(candidate.employeeName) { nameNumRegex.find(candidate.employeeName) }
                val cleanName = match?.groupValues?.get(1)?.trim() ?: candidate.employeeName
                val cleanId = (match?.groupValues?.get(2)?.trim() ?: candidate.employeeId).replace("group_", "")

                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(cleanName.take(1).uppercase(), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(cleanName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold)
                    Text(
                        text = "Matricola $cleanId",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                ) {
                    Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("GUADAGNI", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32), fontWeight = FontWeight.Black, fontSize = 8.sp)
                        Text("RIPOSO", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        Text(candidate.myWorkDate.format(DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                
                Icon(Icons.Default.SwapHoriz, null, Modifier.padding(horizontal = 8.dp).size(24.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                ) {
                    Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("CEDERAI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 8.sp)
                        Text("RIPOSO", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        Text(candidate.myRestDate.format(DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            
            Spacer(Modifier.height(4.dp))
        }
    }
}






