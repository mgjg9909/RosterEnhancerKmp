package com.matteo.rosterenhancer.ui.screen.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.matteo.rosterenhancer.util.formatDecimal
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.blur
import com.matteo.rosterenhancer.ui.components.GlassCard
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import com.matteo.rosterenhancer.domain.model.Shift
import com.matteo.rosterenhancer.domain.model.ShiftType
import com.matteo.rosterenhancer.ui.theme.SuccessGreen
import com.matteo.rosterenhancer.ui.theme.*
import com.matteo.rosterenhancer.util.NotificationScheduler
import kotlinx.coroutines.delay
import com.matteo.rosterenhancer.util.Duration
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import com.matteo.rosterenhancer.util.DateTimeFormatter
import com.matteo.rosterenhancer.util.TextStyle
import com.matteo.rosterenhancer.util.Locale
import com.matteo.rosterenhancer.util.format
import com.matteo.rosterenhancer.util.now
import com.matteo.rosterenhancer.util.plusMinutes
import androidx.compose.ui.text.style.TextAlign
import com.matteo.rosterenhancer.domain.model.ShiftNote
import com.matteo.rosterenhancer.ui.components.ShiftNoteBottomSheet
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToCalendar: () -> Unit,
    onNavigateToColleagues: () -> Unit,
    onNavigateToSalary: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToProfile: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel()
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val uiState by viewModel.uiState.collectAsState()
    val today = LocalDate.now()
    
    var selectedShiftForNote by remember { mutableStateOf<Shift?>(null) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }




    // Gestione feedback sincronizzazione
    LaunchedEffect(uiState.syncResultMessage) {
        uiState.syncResultMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSyncMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        val hour = LocalTime.now().hour
                        val (greeting, emoji) = when (hour) {
                            in 5..11 -> "Buongiorno" to "Ôÿò"
                            in 12..17 -> "Buon pomeriggio" to "ÔÿÇ´©Å"
                            in 18..22 -> "Buonasera" to "­ƒîÖ"
                            else -> "Buonanotte" to "­ƒÿ┤"
                        }
                        Text(
                            text = "$greeting, ${uiState.selfName.split(" ").lastOrNull() ?: ""}! $emoji",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = uiState.selfId,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = today.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN))
                                    .replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                actions = {
                    Row(modifier = Modifier.padding(end = 8.dp)) {
                        val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
                        val rotation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "sync_rotation_anim"
                        )

                        IconButton(
                            onClick = { viewModel.syncWithIntranet() },
                            enabled = !uiState.isSyncing,
                            modifier = Modifier
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FileUpload,
                                contentDescription = "Sincronizza roster",
                                tint = if (uiState.isSyncing) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.graphicsLayer {
                                    rotationZ = if (uiState.isSyncing) rotation else 180f
                                }
                            )
                        }
                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier
                        ) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Impostazioni")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding(), bottom = 112.dp)
                .graphicsLayer { alpha = 0.99f }
                .drawWithContent {
                    drawContent()
                    // Leggiamo scrollState.value nella draw phase: invalida solo il disegno, mai la composizione
                    val maxScroll = scrollState.maxValue.toFloat()
                    val fadeAmount = if (maxScroll > 0f) {
                        val remaining = maxScroll - scrollState.value.toFloat()
                        (remaining / 400f).coerceIn(0f, 1f)
                    } else 0f
                    
                    // Disegniamo sempre il rettangolo per evitare cambi strutturali nel render tree che causano sfarfallii.
                    // Modifichiamo solo l'alpha del colore in basso.
                    drawRect(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            0.85f to Color.Black,
                            1.0f to Color.Black.copy(alpha = 1f - fadeAmount)
                        ),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                    )
                }
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val currentTime = LocalTime.now()
            val todayShift = uiState.todayShift
            
            val isTodayFinished = todayShift?.let { shift ->
                if (shift.shiftType != ShiftType.WORK) return@let true
                
                val otMinutes = maxOf(shift.overtimeMinutes, uiState.notesByDate[shift.date]?.extraMinutes ?: 0)
                val end = shift.endTime?.plusMinutes(otMinutes.toLong()) ?: LocalTime(23, 59, 59)
                val runsOvernight = end <= (shift.startTime ?: LocalTime(0, 0))
                if (runsOvernight) {
                    currentTime > end && currentTime < (shift.startTime ?: LocalTime(0, 0)) && currentTime.hour > 12
                } else {
                    currentTime > end
                }
            } ?: true

            val displayShift = if (isTodayFinished) {
                uiState.upcomingShifts.firstOrNull { it.shiftType == ShiftType.WORK }
            } else {
                todayShift
            }
            val isUpcoming = isTodayFinished && displayShift != null && displayShift.date != LocalDate.now()

            if (!uiState.hasRoster && !uiState.isLoading) {
                NoRosterCard(onImport = onNavigateToImport)
            } else {
                val nextWorkShift = if (todayShift != null && todayShift.shiftType == ShiftType.WORK && !isTodayFinished) {
                    uiState.upcomingShifts.firstOrNull { it.shiftType == ShiftType.WORK }
                } else {
                    displayShift
                }

                Row(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Column: Today's Shift & Today's Earnings
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            SmallTodayShiftCard(shift = todayShift)
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            SmallTodayEarningsCard(amount = uiState.todayEarnings)
                        }
                    }

                    // Right Side: Next Work Shift (Big Card)
                    Box(modifier = Modifier.weight(1.8f).fillMaxHeight()) {
                        val isActuallyUpcoming = nextWorkShift?.let { 
                            val now = LocalDateTime.now()
                            val start = LocalDateTime(it.date, it.startTime ?: LocalTime(0, 0))
                            now < start
                        } ?: true

                        TodayShiftCard(
                            shift = nextWorkShift,
                            isUpcoming = isActuallyUpcoming,
                            note = nextWorkShift?.let { uiState.notesByDate[it.date] },
                            onClick = { nextWorkShift?.let { selectedShiftForNote = it } }
                        )
                    }
                }

                // Sezione PROSSIMI TURNI spostata qui sopra
                UpcomingShiftsSection(
                    shifts = uiState.upcomingShifts,
                    notes = uiState.notesByDate,
                    onShiftClick = { selectedShiftForNote = it }
                )

                if (uiState.colleaguesInShift.isNotEmpty() || uiState.isLoadingColleagues) {
                    LiveColleaguesWidget(
                        colleagues = uiState.colleaguesInShift,
                        isLoading = uiState.isLoadingColleagues,
                        onColleagueClick = { viewModel.selectColleagueForSharedShifts(it) }
                    )
                }

                // Nuova Sezione: Statistiche Performance del Mese (Heatmap)
                MonthPerformanceWidget(
                    roleCounts = uiState.roleCounts,
                    topColleagues = uiState.topColleagues,
                    allShifts = uiState.allShiftsOfMonth,
                    onColleagueClick = { viewModel.selectColleagueForSharedShifts(it) }
                )

                // Monthly Earnings Card
                EarningBentoCard(
                    amount = uiState.estimatedEarnings,
                    onClick = onNavigateToSalary
                )

                CompactMonthMetrics(
                    worked = uiState.monthHours,
                    max = uiState.monthMaxHours
                )

                uiState.nextRestDate?.let { date ->
                    NextRestCard(date = date)
                }
                
            }
        }
    }

    selectedShiftForNote?.let { shift ->
        val colleaguesForShift = remember(shift) { viewModel.getColleaguesForShift(shift) }
        ShiftNoteBottomSheet(
            shift = shift,
            existingNote = uiState.notesByDate[shift.date],
            colleagues = colleaguesForShift,
            onDismiss = { selectedShiftForNote = null },
            onSave = { text, mins, start, end ->
                viewModel.saveShiftNote(shift, text, mins, start, end)
                selectedShiftForNote = null
            }
        )
    }

    // Stato locale per gestire l'animazione di chiusura del Dialog
    var isClosingOverlay by remember { androidx.compose.runtime.mutableStateOf(false) }
    var displayedColleague by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var displayedShifts by remember { androidx.compose.runtime.mutableStateOf<List<Pair<Shift, Shift>>>(emptyList()) }

    LaunchedEffect(uiState.selectedColleagueName) {
        if (uiState.selectedColleagueName != null) {
            displayedColleague = uiState.selectedColleagueName
            displayedShifts = uiState.sharedShifts
            isClosingOverlay = false
        } else if (displayedColleague != null) {
            isClosingOverlay = true
            kotlinx.coroutines.delay(300) // Aspetta la fine dell'animazione di exit con margine
            displayedColleague = null
        }
    }

    if (displayedColleague != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { viewModel.selectColleagueForSharedShifts(null) },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = !isClosingOverlay,
                enter = androidx.compose.animation.scaleIn(initialScale = 0.85f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.75f, stiffness = 400f)) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(200)),
                exit = androidx.compose.animation.scaleOut(targetScale = 0.9f, animationSpec = androidx.compose.animation.core.tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing)) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(200)),
                modifier = Modifier.fillMaxSize()
            ) {
                ColleagueShiftsOverlay(
                    colleagueName = displayedColleague!!,
                    sharedShifts = displayedShifts,
                    onDismiss = { viewModel.selectColleagueForSharedShifts(null) }
                )
            }
        }
    }
}

@Composable
private fun TodayShiftCard(
    shift: Shift?,
    isUpcoming: Boolean = false,
    note: ShiftNote? = null,
    onClick: () -> Unit = {}
) {
    val shiftColor = if (isUpcoming) MaterialTheme.colorScheme.secondary else shiftColor(shift)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    GlassCard(
        modifier = Modifier
            .fillMaxSize(),
        onClick = onClick,
        shape = RoundedCornerShape(32.dp),
        containerColor = shiftColor,
        glassAlpha = if (isDark) 0.15f else 0.08f,
        borderAlpha = 0.35f // Pi├╣ marcato per coerenza
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isUpcoming) "PROSSIMO" else "OGGI",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                        color = shiftColor,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShiftBadge(shift = shift, color = shiftColor)
                        if (isUpcoming) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(0.5.dp, shiftColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    shift?.date?.format(DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)) ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = shiftColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (shift?.shiftType == ShiftType.WORK && shift.startTime != null && shift.endTime != null) {
                    Text(
                        text = shift.timeRange,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Black
                    )
                    // Countdown isolato: si ricompone solo lui ogni secondo, non tutta la card
                    ShiftCountdownBox(shift = shift, isUpcoming = isUpcoming, note = note, isDark = isDark)

                    // Step 4: Micro-Timeline Progress
                    if (!isUpcoming && shift != null && shift.shiftType == ShiftType.WORK) {
                        ShiftProgressTimeline(shift = shift)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${shift.durationHours} ore lavorative  ÔÇó  ${shift.role ?: ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val otMinutes = maxOf(shift.overtimeMinutes, note?.extraMinutes ?: 0)
                            if (otMinutes > 0) {
                                Badge(containerColor = MaterialTheme.colorScheme.primary) { 
                                    val h = otMinutes / 60
                                    val m = otMinutes % 60
                                    val label = if (h > 0 && m > 0) "+${h}h ${m}m" else if (h > 0) "+${h}h" else "+${m}m"
                                    Text(label, color = MaterialTheme.colorScheme.onPrimary) 
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                            
                            PremiumNoteButton(
                                onClick = onClick,
                                hasNote = note?.note?.isNotBlank() == true || shift.notes.isNotBlank()
                            )
                        }
                    }
                } else {
                    Text(
                        text = shiftTypeLabel(shift?.shiftType),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Composable isolato per il countdown del turno.
 * Contiene tutto il timer e lo stato temporale: solo questo si ricompone ogni secondo,
 * lasciando TodayShiftCard completamente stabile durante lo scroll.
 */
@Composable
private fun ShiftCountdownBox(shift: Shift, isUpcoming: Boolean, note: ShiftNote?, isDark: Boolean, isSmall: Boolean = false) {
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    var currentDate by remember { mutableStateOf(LocalDate.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = LocalTime.now()
            currentDate = LocalDate.now()
        }
    }

    val otMinutes = maxOf(shift.overtimeMinutes, note?.extraMinutes ?: 0)

    val countdownText = remember(currentTime, currentDate) {
        if (isUpcoming) {
            val shiftDateTime = LocalDateTime(shift.date, shift.startTime ?: LocalTime(0, 0))
            val nowDateTime = LocalDateTime(currentDate, currentTime)
            if (nowDateTime > shiftDateTime) return@remember if (isSmall) "Inizio" else "Inizia a breve"
            val duration = com.matteo.rosterenhancer.util.Duration.between(nowDateTime, shiftDateTime)
            val days = duration.toDays()
            val hours = duration.toHours() % 24
            val minutes = duration.toMinutes() % 60
            val seconds = (duration.toMillis() / 1000) % 60
            
            if (isSmall) {
                if (days > 0) "Inizia ${days}g"
                else "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
            } else {
                if (days > 0) "Inizia tra ${days}g ${hours}h ${minutes}m"
                else "Inizia tra ${hours.toString().padStart(2, '0')}h ${minutes.toString().padStart(2, '0')}m ${seconds.toString().padStart(2, '0')}s"
            }
        } else {
            val start = shift.startTime ?: return@remember null
            val end = (shift.endTime ?: return@remember null).plusMinutes(otMinutes.toLong())
            val runsOvernight = end <= start
            val isWorking = if (runsOvernight) currentTime > start || currentTime < end
                            else currentTime >= start && currentTime <= end
            val isFinished = if (runsOvernight) currentTime > end && currentTime < start && currentTime.hour > 12
                             else currentTime > end
            when {
                isWorking -> {
                    val d = if (currentTime < end) Duration.between(currentTime, end)
                            else Duration.between(currentTime, LocalTime(23, 59, 59)).plus(Duration.between(LocalTime(0, 0), end))
                    
                    if (isSmall) "${d.toHours().toString().padStart(2, '0')}:${(d.toMinutes() % 60).toString().padStart(2, '0')}:${((d.toMillis() / 1000) % 60).toString().padStart(2, '0')}"
                    else "Fine tra ${d.toHours().toString().padStart(2, '0')}h ${(d.toMinutes() % 60).toString().padStart(2, '0')}m ${((d.toMillis() / 1000) % 60).toString().padStart(2, '0')}s"
                }
                !isFinished && currentTime < start -> {
                    val d = Duration.between(currentTime, start)
                    if (isSmall) "${d.toHours().toString().padStart(2, '0')}:${(d.toMinutes() % 60).toString().padStart(2, '0')}:${((d.toMillis() / 1000) % 60).toString().padStart(2, '0')}"
                    else "Inizia tra ${d.toHours().toString().padStart(2, '0')}h ${(d.toMinutes() % 60).toString().padStart(2, '0')}m ${((d.toMillis() / 1000) % 60).toString().padStart(2, '0')}s"
                }
                else -> if (isSmall) "Finito" else "Turno concluso"
            }
        }
    }

    countdownText?.let { text ->
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(if (isSmall) 8.dp else 12.dp))
                .padding(horizontal = if (isSmall) 4.dp else 8.dp, vertical = if (isSmall) 2.dp else 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val start = shift.startTime
                val end = shift.endTime?.plusMinutes(otMinutes.toLong())
                val runsOvernight = end != null && start != null && end <= start
                val isWorking = if (start != null && end != null) {
                    if (runsOvernight) currentTime > start || currentTime < end
                    else currentTime >= start && currentTime <= end
                } else false

                if (isWorking) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
                        label = "pulse_alpha"
                    )
                    Box(Modifier.size(if (isSmall) 8.dp else 10.dp).clip(CircleShape).background(SuccessGreen.copy(alpha = alpha)))
                    Spacer(Modifier.width(if (isSmall) 6.dp else 10.dp))
                } else if (isUpcoming || (start != null && currentTime < start)) {
                    Box(Modifier.size(if (isSmall) 8.dp else 10.dp).clip(CircleShape).background(Color(0xFFFFB300)))
                    Spacer(Modifier.width(if (isSmall) 6.dp else 10.dp))
                }

                AnimatedCounterText(
                    text = text,
                    style = if (isSmall) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    color = if (text.startsWith("Fine")) SuccessGreen else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
fun AnimatedCounterText(text: String, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle, color: Color) {
    Row(modifier = modifier) {
        text.forEachIndexed { index, char ->
            if (char.isDigit()) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        slideInVertically { height -> height } + fadeIn() togetherWith
                                slideOutVertically { height -> -height } + fadeOut()
                    },
                    label = "counter_$index"
                ) { charState ->
                    Text(text = charState.toString(), style = style, color = color, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(text = char.toString(), style = style, color = color, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun UpcomingShiftTimelineItem(
    shift: Shift,
    note: ShiftNote? = null,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onClick: () -> Unit = {}
) {
    val color = shiftColor(shift)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Timeline Column (Line + Dot)
        Column(
            modifier = Modifier.width(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top line
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(8.dp)
                    .background(if (isFirst) Color.Transparent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            )
            
            // Dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f))
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
            }
            
            // Bottom line
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .weight(1f)
                    .heightIn(min = 12.dp)
                    .background(if (isLast) Color.Transparent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            )
        }

        Spacer(Modifier.width(12.dp))

        // Content
        GlassCard(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            glassAlpha = if (isDark) 0.15f else 0.12f,
            borderAlpha = 0.4f, // Molto pi├╣ definito
            onClick = onClick
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = shift.date.format(DateTimeFormatter.ofPattern("EEEE d", Locale.ITALIAN))
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (shift.shiftType == ShiftType.WORK) {
                            "${shift.timeRange} ÔÇó ${shift.role ?: "Turno"}"
                        } else {
                            shiftTypeLabel(shift.shiftType)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    if (note != null || shift.overtimeMinutes > 0) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Outlined.Notes,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = color.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedDashboardBackground(todayShift: Shift?) {
    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val shiftColor = shiftColor(todayShift)
    
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val bgColor = MaterialTheme.colorScheme.background
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(color = bgColor)
        
        // Orb 1: Grande e diffuso (Colore primario)
        val centerColor = shiftColor.copy(alpha = 0.15f)
        val edgeColor = Color.Transparent
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(centerColor, edgeColor),
                center = center.copy(
                    x = center.x + (size.width * 0.4f * kotlin.math.cos((kotlin.math.PI / 180.0 * phase.toDouble())).toFloat()),
                    y = center.y + (size.height * 0.3f * kotlin.math.sin((kotlin.math.PI / 180.0 * phase.toDouble())).toFloat())
                ),
                radius = size.minDimension * 1.2f
            )
        )
        
        // Orb 2: Pi├╣ piccolo e veloce (o sfasato)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(centerColor.copy(alpha = 0.10f), edgeColor),
                center = center.copy(
                    x = center.x + (size.width * 0.5f * kotlin.math.cos((kotlin.math.PI / 180.0 * phase.toDouble() + 180)).toFloat()),
                    y = center.y + (size.height * 0.4f * kotlin.math.sin((kotlin.math.PI / 180.0 * phase.toDouble() + 90)).toFloat())
                ),
                radius = size.minDimension * 0.9f
            )
        )
    }
}

@Composable
private fun CompactMonthMetrics(worked: Int, max: Int) {
    val progress = if (max > 0) (worked.toFloat() / max).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "progress"
    )
    
    val overTarget = worked > max
    val color = if (overTarget) SuccessGreen else MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (!isDark) androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.15f)) else null
    ) {
        Column(
            modifier = Modifier
                .background(color.copy(alpha = if (isDark) 0.2f else 0.05f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = color
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Ore mensili",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "$worked / $max h",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(color.copy(alpha = 0.7f), color)
                            )
                        )
                )
            }
            
            if (overTarget) {
                Text(
                    text = "Hai superato il monte ore di ${worked - max}h",
                    style = MaterialTheme.typography.labelSmall,
                    color = SuccessGreen,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun NextRestCard(date: LocalDate) {
    val today = LocalDate.now()
    val daysDiff = date.toEpochDays() - today.toEpochDays()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.45f else 0.05f)), 
        elevation = CardDefaults.cardElevation(0.dp),
        border = if (!isDark) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) else null
    ) {
        Row(modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ShiftRest1.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Weekend, contentDescription = null, tint = ShiftRest1, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Prossimo riposo", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                Text(
                    date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN))
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            if (daysDiff <= 7) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ShiftRest1.copy(alpha = 0.1f),
                    contentColor = ShiftRest1
                ) {
                    Text(
                        if (daysDiff == 0) "Oggi!" else "tra $daysDiff gg",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun NoRosterCard(onImport: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(if (isDark) 0.dp else 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.5f else 0.8f)).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Icon(Icons.Outlined.FileUpload, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Iniziamo?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold)
                Text("Importa il file Excel del roster per sbloccare le statistiche e il calendario.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = onImport,
                modifier = Modifier,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Importa ora")
            }
        }
    }
}

@Composable
private fun LiveColleaguesWidget(
    colleagues: List<Shift>,
    isLoading: Boolean,
    onColleagueClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isLoading) MaterialTheme.colorScheme.outlineVariant else SuccessGreen)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "IN TURNO ORA",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            if (!isLoading) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "(${colleagues.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        val haptic = LocalHapticFeedback.current
        val listState = rememberLazyListState()
        
        // Feedback aptico al cambio di elemento
        LaunchedEffect(listState.firstVisibleItemIndex) {
            if (listState.isScrollInProgress) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isLoading) {
                items(3) { ColleagueLoadingCard() }
            } else {
                items(
                    items = colleagues,
                    key = { it.employeeId + it.date.toString() } // Chiave univoca per stabilit├á
                ) { colleague ->
                    ColleagueLiveCard(
                        colleague = colleague,
                        onClick = { onColleagueClick(colleague.employeeName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ColleagueLoadingCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha * 0.5f)),
        modifier = Modifier.width(140.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(Modifier.fillMaxWidth(0.8f).height(10.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(4.dp)))
            Box(Modifier.fillMaxWidth(0.5f).height(8.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(4.dp)))
            Box(Modifier.fillMaxWidth(0.6f).height(8.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
private fun ColleagueLiveCard(
    colleague: Shift,
    onClick: () -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    
    val isDark = remember(surfaceColor) { surfaceColor.luminance() < 0.5f }
    val color = remember(colleague.startTime) { workShiftColor(colleague.startTime?.hour) }
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "scale"
    )

    val borderAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.8f else 0.1f,
        animationSpec = tween(150),
        label = "border_alpha"
    )

    val borderWidth by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isPressed) 2.dp else 1.dp,
        animationSpec = tween(150),
        label = "border_width"
    )
    
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(0.dp),
        border = androidx.compose.foundation.BorderStroke(borderWidth, if (isPressed) color.copy(alpha = borderAlpha) else onSurface.copy(alpha = borderAlpha)),
        modifier = Modifier
            .width(160.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null, // No standard ripple
                onClick = onClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(onSurface.copy(alpha = if (isDark) 0.12f else 0.04f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = colleague.employeeName,
                style = MaterialTheme.typography.bodyMedium,
                color = onSurface,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = colleague.timeRange,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = color,
                maxLines = 1
            )
            Text(
                text = colleague.role ?: "Turno",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun getInitials(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts.last().take(1)).uppercase()
    }
}

@Composable
private fun EarningBentoCard(amount: Double, onClick: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxSize(),
        elevation = CardDefaults.cardElevation(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.7f else 0.15f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                Icons.Outlined.AccountBalanceWallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            
            Column {
                Text(
                    text = "Guadagno",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = "Ôé¼${amount.formatDecimal()}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
@Composable
private fun UpcomingShiftsSection(
    shifts: List<Shift>,
    notes: Map<kotlinx.datetime.LocalDate, com.matteo.rosterenhancer.domain.model.ShiftNote>,
    onShiftClick: (Shift) -> Unit
) {
    val today = LocalDate.now()
    val endOfMonth = com.matteo.rosterenhancer.util.YearMonth(today.year, today.monthNumber).atEndOfMonth()
    val upcoming = shifts.filter { it.date > today && it.date <= endOfMonth }
    // Dividiamo i turni in gruppi di 4
    val chunks = upcoming.chunked(4)
    
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    // Feedback aptico al cambio di blocco
    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (listState.firstVisibleItemIndex > 0 || listState.isScrollInProgress) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "PROSSIMI TURNI",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        
        if (upcoming.isEmpty()) {
            Text("Nessun turno in programma", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
        } else {
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
            ) {
                items(chunks) { chunk ->
                    Column(
                        modifier = Modifier.width(200.dp) // Leggermente pi├╣ stretto per compattezza
                    ) {
                        chunk.forEachIndexed { index, shift ->
                            UpcomingShiftTimelineItem(
                                shift = shift,
                                note = notes[shift.date],
                                isFirst = index == 0,
                                isLast = index == chunk.size - 1,
                                onClick = { onShiftClick(shift) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthPerformanceWidget(
    roleCounts: Map<String, Int>,
    topColleagues: List<Pair<String, Int>>,
    allShifts: List<Shift>,
    onColleagueClick: (String) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val totalWorkShifts = roleCounts.values.sum()
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "DISTRIBUZIONE TURNI",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.3f else 0.5f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        ) {
            if (totalWorkShifts == 0) {
                Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Text("Nessun dato per questo mese", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Row(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Donut Chart
                    Box(
                        modifier = Modifier.size(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val sortedRoles = roleCounts.filter { it.value > 0 }.toList().sortedByDescending { it.second }
                        
                        // Prepariamo i colori prima del Canvas (contesto non-Composable)
                        val primaryColor = MaterialTheme.colorScheme.primary
                        val roleColors = sortedRoles.map { (role, _) ->
                            when(role) {
                                "PAX" -> BluePrimary
                                "BAG" -> ShiftNight
                                "MER" -> ShiftAfternoon
                                "TAG" -> Color(0xFF673AB7)
                                "SER" -> SuccessGreen
                                else -> primaryColor
                            }
                        }

                        Canvas(modifier = Modifier.size(140.dp)) {
                            var startAngle = -90f
                            sortedRoles.forEachIndexed { index, (_, count) ->
                                val sweepAngle = (count.toFloat() / totalWorkShifts) * 360f
                                val color = roleColors[index]
                                
                                drawArc(
                                    color = color,
                                    startAngle = startAngle,
                                    sweepAngle = sweepAngle,
                                    useCenter = false,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 30f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                )
                                startAngle += sweepAngle
                            }
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = totalWorkShifts.toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "TURNI",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // Legend
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        roleCounts.filter { it.value > 0 }.toList().sortedByDescending { it.second }.take(5).forEach { (role, count) ->
                            val color = when(role) {
                                "PAX" -> BluePrimary
                                "BAG" -> ShiftNight
                                "MER" -> ShiftAfternoon
                                "TAG" -> Color(0xFF673AB7)
                                "SER" -> SuccessGreen
                                else -> MaterialTheme.colorScheme.primary
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                                Text(
                                    text = role,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "$count",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (roleCounts.filter { it.value > 0 }.size > 5) {
                            Text(
                                text = "+ altri ${roleCounts.filter { it.value > 0 }.size - 5}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            }
        }
        
        if (topColleagues.isNotEmpty()) {
            Text(
                text = "TOP COLLEGHI",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                topColleagues.forEach { (name, count) ->
                    Card(
                        onClick = { onColleagueClick(name) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.2f else 0.4f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(getInitials(name), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text("$count turni", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = color)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}
@Composable
private fun BentoNavigationGrid(
    onNavigateToCalendar: () -> Unit,
    onNavigateToColleagues: () -> Unit,
    onNavigateToSalary: () -> Unit,
    nextRestDate: LocalDate?
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BentoNavTile(
                icon = Icons.Outlined.CalendarMonth,
                label = "Calendario",
                color = BluePrimary,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToCalendar
            )
            BentoNavTile(
                icon = Icons.Outlined.Group,
                label = "Colleghi",
                color = ShiftAfternoon,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToColleagues
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().height(100.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BentoNavTile(
                icon = Icons.Outlined.Analytics,
                label = "Statistiche",
                color = ShiftNight,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToSalary // Temporaneo
            )
            
            // Tile per il prossimo riposo (Informativa)
            Card(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1.5f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(SuccessGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.WbSunny, contentDescription = null, tint = SuccessGreen)
                    }
                    Column {
                        Text("Prossimo Riposo", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = nextRestDate?.let { 
                                val formatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)
                                it.format(formatter).replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
                            } ?: "Non previsto",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BentoNavTile(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxHeight(),
        elevation = CardDefaults.cardElevation(if (isDark) 0.dp else 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.4f else 0.85f)).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, contentDescription = null, tint = color)
            Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuickNavCard(icon: ImageVector, label: String,
                         onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = label,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ShiftBadge(shift: Shift?, color: Color) {
    val label = when {
        shift?.shiftType == ShiftType.WORK -> shift.role ?: "Turno"
        else -> shiftTypeShortLabel(shift?.shiftType)
    }
    Box(modifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .padding(horizontal = 4.dp, vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = color, fontWeight = FontWeight.SemiBold)
    }
}

private fun shiftColor(shift: Shift?): Color = when (shift?.shiftType) {
    ShiftType.WORK -> workShiftColor(shift.startTime?.hour)
    ShiftType.REST_1, ShiftType.REST_2 -> ShiftRest1
    ShiftType.DAY_OFF -> ShiftDayOff
    ShiftType.ABSENT -> ShiftAbsent
    ShiftType.PARENTAL_LEAVE -> ShiftParental
    else -> ShiftOther
}

private fun shiftTypeLabel(type: ShiftType?): String = when (type) {
    ShiftType.REST_1         -> "Riposo R1"
    ShiftType.REST_2         -> "Riposo R2"
    ShiftType.DAY_OFF        -> "Giorno libero"
    ShiftType.ABSENT         -> "Assente"
    ShiftType.PARENTAL_LEAVE -> "Congedo parentale"
    ShiftType.HOLIDAY        -> "Festivita"
    ShiftType.WORK           -> "Turno"
    ShiftType.INTERVENTO     -> "Intervento"
    ShiftType.MANCATO_R1     -> "Mancato R1"
    ShiftType.MANCATO_R2     -> "Mancato R2"
    ShiftType.OTHER, null    -> "-"
}

private fun shiftTypeShortLabel(type: ShiftType?): String = when (type) {
    ShiftType.REST_1         -> "R1"
    ShiftType.REST_2         -> "R2"
    ShiftType.DAY_OFF        -> "RO"
    ShiftType.ABSENT         -> "Assente"
    ShiftType.PARENTAL_LEAVE -> "CP"
    ShiftType.HOLIDAY        -> "Fest."
    ShiftType.WORK           -> "Turno"
    ShiftType.INTERVENTO     -> "Int."
    ShiftType.MANCATO_R1     -> "R1 L"
    ShiftType.MANCATO_R2     -> "R2 L"
    ShiftType.OTHER, null    -> "-"
}

@Composable
private fun SmallTodayShiftCard(shift: Shift?) {
    val shiftColor = shiftColor(shift)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    
    GlassCard(
        modifier = Modifier.fillMaxSize(),
        containerColor = shiftColor,
        glassAlpha = if (isDark) 0.15f else 0.1f,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "OGGI",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = shiftColor,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            val displayTime = if (shift?.shiftType == ShiftType.WORK) {
                shift.timeRange.ifBlank { "Orario N/D" }
            } else {
                shiftTypeLabel(shift?.shiftType)
            }

            Text(
                displayTime,
                style = if (displayTime.length > 10) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            // Aggiunto Countdown anche qui per massima visibilit├á
            if (shift?.shiftType == ShiftType.WORK) {
                Spacer(Modifier.height(4.dp))
                // Step 4: Versione compatta del progresso per la card piccola
                ShiftProgressTimeline(shift = shift, isSmall = true)
                Spacer(Modifier.height(4.dp))
                ShiftCountdownBox(shift = shift, isUpcoming = false, note = null, isDark = isDark, isSmall = true)
            }
        }
    }
}

@Composable
private fun SmallTodayEarningsCard(amount: Double) {
    val color = MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    
    GlassCard(
        modifier = Modifier.fillMaxSize(),
        containerColor = color,
        glassAlpha = if (isDark) 0.15f else 0.1f,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "GUADAGNO",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = color,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Ôé¼${amount.formatDecimal()}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun ColleagueShiftsOverlay(
    colleagueName: String,
    sharedShifts: List<Pair<Shift, Shift>>,
    onDismiss: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val surfaceColor = MaterialTheme.colorScheme.surface
    val haptic = LocalHapticFeedback.current

    val dismissWithHaptic = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor.copy(alpha = if (isDark) 0.6f else 0.4f)) // Semi-transparent overlay backdrop
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null, // Disable ripple on backdrop
                onClick = dismissWithHaptic
            )
    ) {
        // Main Container (The Bloomed Card)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp) // Leave space for status bar
                .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
                .background(surfaceColor.copy(alpha = if (isDark) 0.85f else 0.95f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null, 
                    onClick = {} // Consume clicks inside the overlay so they don't dismiss it
                )
        ) {
            // Header with Avatar and Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Avatar e Testi
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Avatar gigante
                    val color = remember { workShiftColor(null) } // Colore default
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = getInitials(colleagueName),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Black
                        )
                    }
                    
                    Column {
                        Text(
                            text = "Turni di",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = colleagueName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Bottone Chiudi circolare
                Surface(
                    onClick = dismissWithHaptic,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Close,
                            contentDescription = "Chiudi",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            Text(
                text = "Questo mese il collega ha ${sharedShifts.size} turni pianificati",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
            )
            
            // Staggered list illusion: List fades in slightly after the overlay
            var listVisible by remember { androidx.compose.runtime.mutableStateOf(false) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(100) // Aspetta che la card "sbocci"
                listVisible = true
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = listVisible,
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { 50 }, animationSpec = androidx.compose.animation.core.tween(300)) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300))
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(
                        items = sharedShifts,
                        key = { _, it -> "${it.first.date}_${it.second.employeeId}" }
                    ) { index, (myShift, colShift) ->
                        // Staggered entrance for each item
                        val itemVisible = remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(100L + (index * 50L))
                            itemVisible.value = true
                        }
                        
                        androidx.compose.animation.AnimatedVisibility(
                            visible = itemVisible.value,
                            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { 30 }) + 
                                    androidx.compose.animation.fadeIn(animationSpec = tween(400)),
                            label = "staggered_item_$index"
                        ) {
                            SharedShiftCard(myShift, colShift)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedShiftCard(myShift: Shift, colShift: Shift) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    
    // Ottimizzazione: ricordiamo la data formattata per evitare ricalcoli durante lo scroll
    val dateStr = remember(myShift.date) {
        myShift.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN))
            .replaceFirstChar { it.uppercase() }
    }
        
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        glassAlpha = if (isDark) 0.2f else 0.15f,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = dateStr,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(Modifier.height(12.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Mio Turno
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        Text("TU", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    
                    val myLabel = remember(myShift.shiftType, myShift.timeRange) {
                        if (myShift.shiftType == ShiftType.WORK) myShift.timeRange.ifBlank { "Lavoro" } else shiftTypeLabel(myShift.shiftType)
                    }
                    val myRole = remember(myShift.role) { myShift.role ?: "Lavoro" }
                    
                    Text(myLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = if (myShift.shiftType == ShiftType.WORK) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    if (myShift.shiftType == ShiftType.WORK) {
                        Text(myRole, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                
                // Divisore
                Box(Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)).align(Alignment.CenterVertically))
                
                // Suo Turno
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.outline, CircleShape))
                        Text("COLLEGA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    val colRole = remember(colShift.role) { colShift.role ?: "Lavoro" }
                    Text(colShift.timeRange, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                    Text(colRole, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ShiftProgressTimeline(shift: Shift, isSmall: Boolean = false) {
    val currentTime = LocalTime.now()
    val startTime = shift.startTime ?: return
    val otMinutes = shift.overtimeMinutes
    val endTime = shift.endTime?.plusMinutes(otMinutes.toLong()) ?: return
    
    // Gestione turni notturni
    val isOngoing = remember(currentTime, startTime, endTime) {
        val now = currentTime
        if (endTime < startTime) { // Turno a cavallo della mezzanotte
            now > startTime || now < endTime
        } else {
            now > startTime && now < endTime
        }
    }
    
    if (!isOngoing) return

    val progress = remember(currentTime, startTime, endTime) {
        val startSec = startTime.toSecondOfDay().toDouble()
        var endSec = endTime.toSecondOfDay().toDouble()
        var currentSec = currentTime.toSecondOfDay().toDouble()
        
        if (endSec < startSec) endSec += 24 * 3600
        if (currentSec < startSec && currentTime < endTime) currentSec += 24 * 3600
        
        ((currentSec - startSec) / (endSec - startSec)).coerceIn(0.0, 1.0).toFloat()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "progress_glow")
    val glowOffset by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "glow_offset"
    )

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (isSmall) 4.dp else 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LiveIndicator()
                Text(
                    "IN CORSO",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontSize = if (isSmall) 8.sp else 10.sp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black
                )
            }
            Text(
                "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = if (isSmall) 8.sp else 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold
            )
        }
        
        // Barra di progresso con effetto vetro e glow animato
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isSmall) 4.dp else 6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                MaterialTheme.colorScheme.primary
                            ),
                            start = androidx.compose.ui.geometry.Offset(glowOffset * 500f, 0f),
                            end = androidx.compose.ui.geometry.Offset((glowOffset + 0.5f) * 500f, 0f)
                        )
                    )
            )
        }
    }
}

@Composable
private fun LiveIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(Color.Red)
    )
}

@Composable
private fun PremiumNoteButton(
    onClick: () -> Unit,
    hasNote: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "note_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    val borderColor = if (hasNote) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(28.dp)
            .graphicsLayer {
                if (!hasNote) {
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
            },
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(
            1.dp, 
            Brush.linearGradient(
                listOf(borderColor, borderColor.copy(alpha = 0.2f))
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (hasNote) Icons.Default.Edit else Icons.Default.Add,
                contentDescription = "Nota",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}


