package com.matteo.rosterenhancer.ui.screen.calendar
import androidx.compose.ui.focus.*
import kotlinx.datetime.LocalTime

import kotlinx.datetime.todayIn
import com.matteo.rosterenhancer.util.*
import kotlinx.datetime.periodUntil
import com.matteo.rosterenhancer.util.minusDays
import com.matteo.rosterenhancer.util.plusDays
import com.matteo.rosterenhancer.util.format

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.pager.*
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.blur
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.People
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import com.matteo.rosterenhancer.ui.components.GlassCard
import com.matteo.rosterenhancer.ui.components.MeshGradientBackground
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import com.matteo.rosterenhancer.domain.model.Shift
import com.matteo.rosterenhancer.domain.model.ShiftType
import com.matteo.rosterenhancer.ui.components.*
import com.matteo.rosterenhancer.ui.screen.colleagues.ColleaguesViewModel
import com.matteo.rosterenhancer.ui.theme.*
import com.matteo.rosterenhancer.util.HapticFeedbackManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import com.matteo.rosterenhancer.util.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val noBounceTween = tween<IntSize>(durationMillis = 300, easing = FastOutSlowInEasing)

enum class RosterViewType { CALENDAR, PEOPLE }

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = koinViewModel(),
    colleaguesViewModel: ColleaguesViewModel = koinViewModel()
) {
    var viewType by remember { mutableStateOf(RosterViewType.CALENDAR) }
    val currentMonth      by viewModel.currentMonth.collectAsState()
    val selectedDate      by viewModel.selectedDate.collectAsState()
    val myShifts          by viewModel.myShiftsThisMonth.collectAsState()
    val dayShifts         by viewModel.shiftsForSelectedDay.collectAsState()
    val notesForSelectedDay by viewModel.notesForSelectedDay.collectAsState()
    val uiState           by viewModel.uiState.collectAsState()
    val availableRoles    by viewModel.availableRoles.collectAsState()
    val availableSubRoles by viewModel.availableSubRoles.collectAsState()
    val favoriteColleagues by colleaguesViewModel.favoriteColleagues.collectAsState()
    val syncStatus        by viewModel.syncStatus.collectAsState()

    var showFilterBar by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var isSearchMode by remember { mutableStateOf(false) }
    var selectedShiftForNote by remember { mutableStateOf<Shift?>(null) }
    var selectedShiftForEdit by remember { mutableStateOf<Shift?>(null) }
    val focusRequester = remember { FocusRequester() }

    val shiftsByDate = remember(myShifts) { myShifts.associateBy { it.date } }
    val listState    = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val hasActiveFilter = uiState.shiftFilter != CalendarShiftFilter.ALL ||
        uiState.activeRoleFilter != null ||
        uiState.sortMode != CalendarSortMode.ALPHABETICAL

    BackHandler(enabled = isSearchMode) {
        viewModel.setSearchQuery("")
        isSearchMode = false
    }

    // Sincronizza la query di ricerca verso il ViewModel dei colleghi
    LaunchedEffect(uiState.searchQuery) {
        colleaguesViewModel.onSearchChanged(uiState.searchQuery)
    }

    // Feedback aptico durante lo scroll della lista principale (tick)
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .collect {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
        }
    }

    // Banner Sync Cloud (posizionato nel contenuto dello Scaffold sotto)

    // Rimosso l'effetto che sincronizzava showCalendar con showFilterBar, ora usiamo il BottomSheet

    // 1. Sfondo Liquido
    MeshGradientBackground()

    Scaffold(
        topBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = {
                        AnimatedContent(
                            targetState = isSearchMode,
                            transitionSpec = {
                                if (targetState) {
                                    (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                                } else {
                                    (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut())
                                }
                            },
                             label = "title_search_anim"
                        ) { searching ->
                            if (searching) {
                                TextField(
                                    value = uiState.searchQuery,
                                    onValueChange = { viewModel.setSearchQuery(it) },
                                    placeholder = { Text("Cerca...", style = MaterialTheme.typography.bodyMedium) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester),
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                    ),
                                    textStyle = MaterialTheme.typography.bodyLarge
                                )
                                LaunchedEffect(Unit) {
                                    focusRequester.requestFocus()
                                }
                            } else {
                                if (viewType == RosterViewType.CALENDAR) {
                                    // TopAppBar minimalista: Nessun titolo, solo barra di ricerca se attiva
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else {
                                    Text("Persone")
                                }

                            }
                        }
                    },
                    actions = {
                        val context = LocalContext.current
                        IconButton(
                            onClick = { shareRoster(context, myShifts, currentMonth) },
                            modifier = Modifier
                        ) {
                            Icon(androidx.compose.material.icons.Icons.Default.Share, contentDescription = "Condividi il mio Roster via App")
                        }

                        IconButton(
                            onClick = { 
                                if (isSearchMode) {
                                    viewModel.setSearchQuery("")
                                    isSearchMode = false
                                } else {
                                    isSearchMode = true
                                }
                            },
                            modifier = Modifier
                        ) {
                            Icon(
                                if (isSearchMode) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Cerca",
                                tint = if (uiState.searchQuery.isNotBlank()) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    LocalContentColor.current
                            )
                        }

                        IconButton(
                            onClick = { showFilterSheet = true },
                            modifier = Modifier
                        ) {
                            BadgedBox(badge = { if (hasActiveFilter) Badge() }) {
                                Icon(
                                    Icons.Default.FilterList,
                                    contentDescription = "Filtri e Ordinamento",
                                    tint = if (hasActiveFilter)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        LocalContentColor.current
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            }
        },
        floatingActionButton = {
            Column(
                modifier = Modifier.padding(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // FAB "Torna a Oggi"
                AnimatedVisibility(
                    visible = selectedDate != Clock.System.todayIn(TimeZone.currentSystemDefault()),
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    SmallFloatingActionButton(
                        onClick = { viewModel.selectDate(Clock.System.todayIn(TimeZone.currentSystemDefault())) },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = CircleShape
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Default.Today, contentDescription = "Torna a Oggi")
                    }
                }

                // FAB "Torna su"
                val showScrollFab by remember {
                    derivedStateOf { listState.firstVisibleItemIndex > 1 }
                }
                AnimatedVisibility(
                    visible = showScrollFab,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                listState.scrollToItem(0)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Torna su")
                    }
                }
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding(), bottom = 112.dp)
                .blur(if (showFilterSheet) 15.dp else 0.dp)
                .graphicsLayer { alpha = 0.99f }
                .drawWithContent {
                    drawContent()
                    val fadeHeight = 80.dp.toPx()
                    drawRect(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            startY = size.height - fadeHeight,
                            endY = size.height
                        ),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                    )
                }
        ) {
            // Banner Sincronizzazione Cloud
            AnimatedVisibility(
                visible = syncStatus != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (syncStatus?.contains("Errore") == true || syncStatus?.contains("fallito") == true) 
                                Icons.Default.SyncProblem else Icons.Default.CloudSync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = syncStatus ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Nastro delle date (Date Ribbon)
            selectedDate?.let { date ->
                DateRibbon(
                    selectedDate = date,
                    onDateSelected = { 
                        viewModel.selectDate(it)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
                
                Spacer(Modifier.height(8.dp))
                
                // Il mio turno oggi
                val myShift = myShifts.find { it.date == date }
                MyShiftBentoCard(
                    shift = myShift,
                    selectedDate = date,
                    onClick = {
                        myShift?.let { shift ->
                            selectedShiftForNote = shift
                        }
                    }
                )
            }
            
            Spacer(Modifier.height(16.dp))

            // Barra filtri attiva (Pillole orizzontali)
            ActiveFiltersPills(
                uiState = uiState,
                onReset = {
                    viewModel.setSortMode(CalendarSortMode.ALPHABETICAL)
                    viewModel.setShiftFilter(CalendarShiftFilter.ALL)
                    viewModel.setRoleFilter(null)
                    viewModel.setSubRoleFilter(null)
                },
                onRemoveRole = { viewModel.setRoleFilter(null) },
                onRemoveSubRole = { viewModel.setSubRoleFilter(null) },
                onRemoveShiftFilter = { viewModel.setShiftFilter(CalendarShiftFilter.ALL) },
                onRemoveSort = { viewModel.setSortMode(CalendarSortMode.ALPHABETICAL) }
            )

            // Colleagues Feed (The Hub)
            val filteredColleagues = remember(dayShifts, uiState.shiftFilter, uiState.activeRoleFilter, uiState.sortMode, myShifts, favoriteColleagues) {
                val list = dayShifts.filter { shift ->
                    // Escludi me stesso
                    val isMe = myShifts.any { it.employeeName.uppercase().trim() == shift.employeeName.uppercase().trim() }
                    if (isMe) return@filter false
                    
                    val matchesShiftType = when (uiState.shiftFilter) {
                        CalendarShiftFilter.ALL -> true
                        CalendarShiftFilter.WORK_ONLY -> shift.shiftType == com.matteo.rosterenhancer.domain.model.ShiftType.WORK
                        CalendarShiftFilter.REST_ONLY -> shift.shiftType.name.startsWith("REST") || shift.shiftType.name == "DAY_OFF"
                    }
                    
                    val matchesRole = uiState.activeRoleFilter == null || com.matteo.rosterenhancer.util.RoleGroups.normalize(shift.role) == uiState.activeRoleFilter
                    
                    matchesShiftType && matchesRole
                }

                // Sorting: Favoriti -> (SortMode scelto dall'utente)
                list.sortedWith { a, b ->
                    val favA = favoriteColleagues.contains(a.employeeId)
                    val favB = favoriteColleagues.contains(b.employeeId)
                    if (favA != favB) return@sortedWith if (favA) -1 else 1

                    when (uiState.sortMode) {
                        CalendarSortMode.ALPHABETICAL -> a.employeeName.trim().compareTo(b.employeeName.trim(), ignoreCase = true)
                        CalendarSortMode.BY_SHIFT_TIME -> {
                            val timeA = a.startTime ?: LocalTime(23, 59, 59, 999999999)
                            val timeB = b.startTime ?: LocalTime(23, 59, 59, 999999999)
                            timeA.compareTo(timeB)
                        }
                        CalendarSortMode.BY_ROLE -> {
                            val roleA = com.matteo.rosterenhancer.util.RoleGroups.normalize(a.role)
                            val roleB = com.matteo.rosterenhancer.util.RoleGroups.normalize(b.role)
                            val roleCmp = roleA.compareTo(roleB)
                            if (roleCmp != 0) roleCmp else {
                                val timeA = a.startTime ?: LocalTime(23, 59, 59, 999999999)
                                val timeB = b.startTime ?: LocalTime(23, 59, 59, 999999999)
                                timeA.compareTo(timeB)
                            }
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.99f }
                    .drawWithContent {
                        drawContent()
                        val fadeHeight = 40.dp.toPx()
                        drawRect(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startY = 0f,
                                endY = fadeHeight
                            ),
                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                        )
                    },
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Groups, 
                                contentDescription = null, 
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "GRUPPO",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.2.sp
                            )
                        }
                    }
                }
                
                if (filteredColleagues.isEmpty()) {
                    item {
                        com.matteo.rosterenhancer.ui.screen.calendar.GhostEmptyCard()
                    }
                } else {
                    items(filteredColleagues, key = { it.id }) { shift ->
                        ColleagueHubCard(
                            shift = shift,
                            isFavorite = favoriteColleagues.contains(shift.employeeId),
                            onToggleFavorite = { colleaguesViewModel.toggleFavorite(shift.employeeId) }
                        )
                    }
                }
            }
        }
    }


    var shiftToEditNote by remember { mutableStateOf<Shift?>(null) }

    // Bottom Sheet Focus Colleghi e Dettagli
    selectedShiftForNote?.let { shift ->
        FocusColleaguesBottomSheet(
            shift = shift,
            note = notesForSelectedDay[shift.employeeName.uppercase().trim()],
            dayShifts = dayShifts,
            onDismiss = { selectedShiftForNote = null },
            onEditNote = { 
                shiftToEditNote = shift
                selectedShiftForNote = null 
            }
        )
    }

    // Bottom Sheet per modifica vera e propria delle Note
    shiftToEditNote?.let { shift ->
        ShiftNoteBottomSheet(
            shift = shift,
            existingNote = notesForSelectedDay[shift.employeeName.uppercase().trim()],
            onDismiss = { shiftToEditNote = null },
            onSave = { text, mins, start, end ->
                viewModel.saveShiftNote(shift, text, mins, start, end)
                shiftToEditNote = null
            }
        )
    }
    
    // Dialog per la Modifica Completa del Turno
    selectedShiftForEdit?.let { shift ->
        com.matteo.rosterenhancer.ui.components.ShiftEditDialog(
            shift = shift,
            onDismiss = { selectedShiftForEdit = null },
            onConfirm = { updatedShift ->
                viewModel.updateShift(updatedShift)
                selectedShiftForEdit = null
            }
        )
    }
    
    // Glass Bento Filter Overlay (Sostituisce il ModalBottomSheet per permettere il blur dello sfondo)
    AnimatedVisibility(
        visible = showFilterSheet,
        enter = fadeIn(tween(400)) + slideInVertically(initialOffsetY = { h -> h / 2 }),
        exit = fadeOut(tween(300)) + slideOutVertically(targetOffsetY = { h -> h / 2 })
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { showFilterSheet = false }
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            FilterBentoSheet(
                uiState = uiState,
                availableRoles = availableRoles,
                availableSubRoles = availableSubRoles,
                onDismiss = { showFilterSheet = false },
                onSortChanged = viewModel::setSortMode,
                onFilterChanged = viewModel::setShiftFilter,
                onRoleFilterChanged = viewModel::setRoleFilter,
                onSubRoleFilterChanged = viewModel::setSubRoleFilter,
                onReset = {
                    viewModel.setSortMode(CalendarSortMode.ALPHABETICAL)
                    viewModel.setShiftFilter(CalendarShiftFilter.ALL)
                    viewModel.setRoleFilter(null)
                    viewModel.setSubRoleFilter(null)
                }
            )
        }
    }
}

@Composable
private fun ActiveFiltersPills(
    uiState: CalendarUiState,
    onReset: () -> Unit,
    onRemoveRole: () -> Unit,
    onRemoveSubRole: () -> Unit,
    onRemoveShiftFilter: () -> Unit,
    onRemoveSort: () -> Unit
) {
    val hasActiveFilter = uiState.shiftFilter != CalendarShiftFilter.ALL ||
        uiState.activeRoleFilter != null ||
        uiState.sortMode != CalendarSortMode.ALPHABETICAL

    AnimatedVisibility(
        visible = hasActiveFilter,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                Surface(
                    onClick = onReset,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.padding(8.dp).size(16.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            if (uiState.sortMode != CalendarSortMode.ALPHABETICAL) {
                item {
                    FilterPill(
                        label = when(uiState.sortMode) {
                            CalendarSortMode.BY_SHIFT_TIME -> "Orario"
                            CalendarSortMode.BY_ROLE -> "Mansione"
                            else -> ""
                        },
                        icon = Icons.Default.Sort,
                        onClick = onRemoveSort
                    )
                }
            }

            if (uiState.shiftFilter != CalendarShiftFilter.ALL) {
                item {
                    FilterPill(
                        label = when(uiState.shiftFilter) {
                            CalendarShiftFilter.WORK_ONLY -> "Turni"
                            CalendarShiftFilter.REST_ONLY -> "Riposi"
                            else -> ""
                        },
                        icon = Icons.Default.Visibility,
                        onClick = onRemoveShiftFilter
                    )
                }
            }

            if (uiState.activeRoleFilter != null) {
                item {
                    FilterPill(
                        label = uiState.activeRoleFilter,
                        icon = Icons.Default.Work,
                        onClick = onRemoveRole
                    )
                }
            }

            if (uiState.activeSubRoleFilter != null) {
                item {
                    FilterPill(
                        label = uiState.activeSubRoleFilter,
                        icon = Icons.Default.SubdirectoryArrowRight,
                        onClick = onRemoveSubRole
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun FilterBentoSheet(
    uiState: CalendarUiState,
    availableRoles: List<String>,
    availableSubRoles: List<String>,
    onDismiss: () -> Unit,
    onSortChanged: (CalendarSortMode) -> Unit,
    onFilterChanged: (CalendarShiftFilter) -> Unit,
    onRoleFilterChanged: (String?) -> Unit,
    onSubRoleFilterChanged: (String?) -> Unit,
    onReset: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = false) {} // Evita che il click passi sotto
    ) {
        com.matteo.rosterenhancer.ui.components.GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 112.dp),
            cornerRadius = 32.dp,
            glassAlpha = 0.8f,
            borderAlpha = 0.3f
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Preferenze Vista", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    IconButton(
                        onClick = { onReset(); onDismiss() },
                        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset", tint = MaterialTheme.colorScheme.error)
                    }
                }

                // Ordinamento
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionHeader(text = "ORDINA PER", icon = Icons.Default.Sort)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BentoFilterTile(
                            label = "Nome",
                            icon = Icons.Default.SortByAlpha,
                            selected = uiState.sortMode == CalendarSortMode.ALPHABETICAL,
                            onClick = { onSortChanged(CalendarSortMode.ALPHABETICAL) },
                            modifier = Modifier.weight(1f)
                        )
                        BentoFilterTile(
                            label = "Orario",
                            icon = Icons.Default.Schedule,
                            selected = uiState.sortMode == CalendarSortMode.BY_SHIFT_TIME,
                            onClick = { onSortChanged(CalendarSortMode.BY_SHIFT_TIME) },
                            modifier = Modifier.weight(1f)
                        )
                        BentoFilterTile(
                            label = "Ruolo",
                            icon = Icons.Default.WorkOutline,
                            selected = uiState.sortMode == CalendarSortMode.BY_ROLE,
                            onClick = { onSortChanged(CalendarSortMode.BY_ROLE) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Visualizzazione
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionHeader(text = "FILTRA", icon = Icons.Default.FilterAlt)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BentoFilterTile(
                            label = "Tutti",
                            icon = Icons.Default.AllInclusive,
                            selected = uiState.shiftFilter == CalendarShiftFilter.ALL,
                            onClick = { onFilterChanged(CalendarShiftFilter.ALL) },
                            modifier = Modifier.weight(1f)
                        )
                        BentoFilterTile(
                            label = "In Turno",
                            icon = Icons.Default.DirectionsRun,
                            selected = uiState.shiftFilter == CalendarShiftFilter.WORK_ONLY,
                            onClick = { onFilterChanged(CalendarShiftFilter.WORK_ONLY) },
                            modifier = Modifier.weight(1f)
                        )
                        BentoFilterTile(
                            label = "Riposi",
                            icon = Icons.Default.Weekend,
                            selected = uiState.shiftFilter == CalendarShiftFilter.REST_ONLY,
                            onClick = { onFilterChanged(CalendarShiftFilter.REST_ONLY) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Mansioni
                if (availableRoles.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SectionHeader(text = "MANSIONE", icon = Icons.Default.Badge)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            item {
                                BentoFilterTile(
                                    label = "Tutte",
                                    icon = Icons.Default.Groups,
                                    selected = uiState.activeRoleFilter == null,
                                    onClick = { onRoleFilterChanged(null) }
                                )
                            }
                            items(availableRoles) { role ->
                                BentoFilterTile(
                                    label = role,
                                    icon = Icons.Default.Badge,
                                    selected = uiState.activeRoleFilter == role,
                                    onClick = { onRoleFilterChanged(role) }
                                )
                            }
                        }
                    }
                }

                // Sotto-Mansioni (Granulari) con Animazione
                AnimatedVisibility(
                    visible = availableSubRoles.isNotEmpty(),
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 12.dp)) {
                        SectionHeader(text = "SOTTO-MANSIONE", icon = Icons.Default.SubdirectoryArrowRight)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            item {
                                BentoFilterTile(
                                    label = "Tutte",
                                    icon = Icons.Default.AllInclusive,
                                    selected = uiState.activeSubRoleFilter == null,
                                    onClick = { onSubRoleFilterChanged(null) }
                                )
                            }
                            items(availableSubRoles) { subRole ->
                                BentoFilterTile(
                                    label = subRole,
                                    icon = Icons.Default.Link,
                                    selected = uiState.activeSubRoleFilter == subRole,
                                    onClick = { onSubRoleFilterChanged(subRole) }
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Conferma", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
    }
}

@Composable
private fun BentoFilterTile(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.3f else 0.5f),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                icon, 
                contentDescription = null, 
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                label, 
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─── Header navigazione mese ──────────────────────────────────────────────────

@Composable
private fun MonthNavigationHeader(
    currentMonth: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onPrevious,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
        ) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Mese precedente",
                tint = MaterialTheme.colorScheme.primary)
        }
        
        AnimatedContent(
            targetState = currentMonth,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { it * dir } + fadeIn(tween(300)))
                    .togetherWith(slideOutHorizontally { -it * dir } + fadeOut(tween(200)))
            },
            label = "month_title"
        ) { month ->
            Text(
                text = "${month.month
                    .getDisplayName(TextStyle.FULL_STANDALONE, Locale.ITALIAN)
                    .replaceFirstChar { it.uppercase() }} ${month.year}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Black
            )
        }
        
        IconButton(
            onClick = onNext,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
        ) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Mese successivo",
                tint = MaterialTheme.colorScheme.primary)
        }
    }
}

// ─── Griglia mese ─────────────────────────────────────────────────────────────

@Composable
private fun MonthGrid(
    yearMonth: YearMonth,
    selectedDate: LocalDate?,
    shiftsByDate: Map<LocalDate, Shift>,
    onDateSelected: (LocalDate) -> Unit
) {
    val daysOfWeek  = listOf("L", "M", "M", "G", "V", "S", "D")
    val firstDay    = yearMonth.atDay(1)
    val startOffset = (firstDay.dayOfWeek.value - 1) % 7
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    GlassCard(
        modifier = Modifier.padding(horizontal = 16.dp),
        cornerRadius = 28.dp,
        borderAlpha = 0.2f,
        glassAlpha = if (isDark) 0.3f else 0.5f
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (day in daysOfWeek) {
                    Text(day, modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(12.dp))

            val rows = ((startOffset + yearMonth.lengthOfMonth) + 6) / 7
            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val dayNumber = row * 7 + col - startOffset + 1
                        if (dayNumber < 1 || dayNumber > yearMonth.lengthOfMonth) {
                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val date       = yearMonth.atDay(dayNumber)
                            val shift      = shiftsByDate[date]
                            val isSelected = date == selectedDate
                            val isToday    = date == Clock.System.todayIn(TimeZone.currentSystemDefault())
                            val dotColor   = getShiftDotColor(shift)

                            val scale by animateFloatAsState(if (isSelected) 1.2f else 1f, label = "scale")
                            val bgColor by animateColorAsState(
                                targetValue = when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    isToday    -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else       -> Color.Transparent
                                },
                                animationSpec = tween(300, easing = FastOutSlowInEasing),
                                label = "cell_bg_$dayNumber"
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f).aspectRatio(1f).padding(4.dp)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                    .clip(CircleShape)
                                    .background(bgColor)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) { 
                                        onDateSelected(date) 
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        dayNumber.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.onPrimary
                                            isToday    -> MaterialTheme.colorScheme.primary
                                            else       -> MaterialTheme.colorScheme.onSurface
                                        },
                                        fontWeight = if (isToday || isSelected) FontWeight.Black else FontWeight.Bold
                                    )
                                    if (dotColor != Color.Transparent && !isSelected) {
                                        Box(Modifier.size(4.dp).clip(CircleShape).background(dotColor))
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

// ─── Card turno ───────────────────────────────────────────────────────────────

@Composable
private fun ShiftListItem(
    shift: Shift,
    note: com.matteo.rosterenhancer.domain.model.ShiftNote? = null,
    isClickable: Boolean = true,
    isOutlined: Boolean = false,
    showEditButton: Boolean = false,
    onClick: () -> Unit = {},
    onEditClick: () -> Unit = {}
) {
    val color = if (shift.shiftType == ShiftType.WORK)
        workShiftColor(shift.startTime?.hour)
    else
        shiftTypeColor(shift.shiftType.name)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isClickable) 
                    Modifier.clickable(
                        onClick = onClick
                    ) 
                else Modifier
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOutlined) Color.Transparent else color.copy(alpha = 0.12f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isOutlined) 1.5.dp else 1.dp, 
            color = if (isOutlined) color.copy(alpha = 0.8f) else color.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Glow Icon Container
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (shift.shiftType == com.matteo.rosterenhancer.domain.model.ShiftType.WORK) Icons.Default.Schedule else Icons.Default.Event,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shift.employeeName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (shift.shiftType == ShiftType.WORK && shift.startTime != null) {
                            shift.timeRange
                        } else {
                            shift.shiftType.name.replace("_", " ").lowercase().capitalize()
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    
                    if (shift.shiftType == ShiftType.WORK) {
                         Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                            modifier = Modifier.size(3.dp)
                        ) {}
                        Text(
                            text = "Matr. ${shift.employeeId}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Mansione / Role Chip
            if (shift.role != null) {
                Surface(
                    color = color.copy(alpha = 1f),
                    shape = RoundedCornerShape(10.dp),
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = shift.role,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Note/Selection Indicator
            if (note != null && (note.note.isNotBlank() || note.extraMinutes > 0)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.EditNote, 
                        contentDescription = "Nota", 
                        modifier = Modifier.size(16.dp), 
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (showEditButton) {
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Modifica",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableShiftItem(
    shift: Shift,
    note: com.matteo.rosterenhancer.domain.model.ShiftNote?,
    onNoteClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onEditClick()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onNoteClick()
                    false
                }
                else -> false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.25f }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.secondaryContainer
                    else -> Color.Transparent
                },
                label = "swipe_color"
            )

            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }

            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Edit
                SwipeToDismissBoxValue.EndToStart -> Icons.Default.EditNote
                else -> Icons.Default.Circle
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) {
                if (direction != SwipeToDismissBoxValue.Settled) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        content = {
            ShiftListItem(
                shift = shift,
                note = note,
                isClickable = false,
                isOutlined = true
            )
        }
    )
}

// ─── Utilities ────────────────────────────────────────────────────────────────

private fun getShiftDotColor(shift: Shift?): Color = when (shift?.shiftType) {
    ShiftType.WORK           -> workShiftColor(shift.startTime?.hour)
    ShiftType.REST_1         -> ShiftRest1
    ShiftType.REST_2         -> ShiftRest2
    ShiftType.DAY_OFF        -> ShiftDayOff
    ShiftType.ABSENT         -> ShiftAbsent
    ShiftType.PARENTAL_LEAVE -> ShiftParental
    else                     -> Color.Transparent
}

// (DayHeader è stato rimosso in quanto le info sono ora nella TopAppBar)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalendarDateView(
    listState: LazyListState,
    selectedDate: LocalDate?,
    uiState: CalendarUiState,
    availableRoles: List<String>,
    availableSubRoles: List<String>,
    dayShifts: List<Shift>,
    myShifts: List<Shift>,
    notesForSelectedDay: Map<String, com.matteo.rosterenhancer.domain.model.ShiftNote>,
    viewModel: CalendarViewModel,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onNoteClick: (Shift) -> Unit,
    onEditClick: (Shift) -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    val context = LocalContext.current

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect {
                if (listState.isScrollInProgress) {
                    HapticFeedbackManager.playLightHaptic(haptic, context)
                }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(selectedDate) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > 150f) {
                            selectedDate?.let { 
                                viewModel.selectDate(it.minusDays(1))
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        } else if (offsetX < -150f) {
                            selectedDate?.let { 
                                viewModel.selectDate(it.plusDays(1))
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                        offsetX = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount
                    }
                )
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 112.dp)
                .graphicsLayer { alpha = 0.99f }
                .drawWithContent {
                    drawContent()
                    // Rampa smooth basata su layoutInfo (draw phase only, nessuna recomposizione)
                    val info = listState.layoutInfo
                    val lastVisible = info.visibleItemsInfo.lastOrNull()
                    val fadeAmount = if (lastVisible != null && lastVisible.index == info.totalItemsCount - 1) {
                        val distanceFromViewportBottom = info.viewportEndOffset - (lastVisible.offset + lastVisible.size)
                        (distanceFromViewportBottom / 400f).coerceIn(0f, 1f)
                    } else 1f
                    
                    // Disegniamo sempre per evitare glitch strutturali
                    drawRect(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            0.85f to Color.Black,
                            1.0f to Color.Black.copy(alpha = 1f - fadeAmount)
                        ),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                    )
                },
            contentPadding = PaddingValues(top = 8.dp)
        ) {
        if (dayShifts.isEmpty()) {
            item("empty") { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { Text("Nessun turno trovato", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        } else {
                val myShift = dayShifts.find { shift ->
                    (myShifts.firstOrNull()?.employeeId == shift.employeeId && shift.employeeId.isNotBlank()) || 
                    myShifts.any { it.employeeName.uppercase().trim() == shift.employeeName.uppercase().trim() }
                }

                if (myShift != null) {
                    item("my_shift") {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Text("Il tuo turno", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
                            SwipeableShiftItem(
                                shift = myShift,
                                note = notesForSelectedDay[myShift.employeeName.uppercase().trim()],
                                onNoteClick = { onNoteClick(myShift) },
                                onEditClick = { onEditClick(myShift) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                items(
                    dayShifts.filter { it != myShift }, 
                    key = { it.employeeName }
                ) { shift ->
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        ShiftListItem(
                            shift = shift,
                            note = notesForSelectedDay[shift.employeeName.uppercase().trim()],
                            isClickable = false
                        )
                    }
                }
            }
        }
    }
}

// ─── Vista Persone ────────────────────────────────────────────────────────────

@Composable
private fun ColleaguesPeopleView(
    viewModel: com.matteo.rosterenhancer.ui.screen.colleagues.ColleaguesViewModel
) {
    val activeFilter by viewModel.filter.collectAsState()
    val shifts by viewModel.filteredShifts.collectAsState()
    val favorites by viewModel.favoriteColleagues.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = activeFilter == com.matteo.rosterenhancer.ui.screen.colleagues.ColleagueFilter.WORKING_NOW,
                    onClick = { viewModel.onFilterChanged(com.matteo.rosterenhancer.ui.screen.colleagues.ColleagueFilter.WORKING_NOW) },
                    label = { Text("In turno ora") }
                )
            }
            item {
                FilterChip(
                    selected = activeFilter == com.matteo.rosterenhancer.ui.screen.colleagues.ColleagueFilter.ALL,
                    onClick = { viewModel.onFilterChanged(com.matteo.rosterenhancer.ui.screen.colleagues.ColleagueFilter.ALL) },
                    label = { Text("Tutti") }
                )
            }
            item {
                FilterChip(
                    selected = activeFilter == com.matteo.rosterenhancer.ui.screen.colleagues.ColleagueFilter.WORKING_TODAY,
                    onClick = { viewModel.onFilterChanged(com.matteo.rosterenhancer.ui.screen.colleagues.ColleagueFilter.WORKING_TODAY) },
                    label = { Text("Oggi") }
                )
            }
        }

        val listState = rememberLazyListState()
        val haptic = LocalHapticFeedback.current
        val context = LocalContext.current

        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .collect {
                    if (listState.isScrollInProgress) {
                        HapticFeedbackManager.playLightHaptic(haptic, context)
                    }
                }
        }

        if (shifts.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Nessun collega trovato", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 112.dp)
                    .graphicsLayer { alpha = 0.99f }
                    .drawWithContent {
                        drawContent()
                        // Rampa smooth basata su layoutInfo (draw phase only, nessuna recomposizione)
                        val info = listState.layoutInfo
                        val lastVisible = info.visibleItemsInfo.lastOrNull()
                        val fadeAmount = if (lastVisible != null && lastVisible.index == info.totalItemsCount - 1) {
                            val distanceFromViewportBottom = info.viewportEndOffset - (lastVisible.offset + lastVisible.size)
                            (distanceFromViewportBottom / 400f).coerceIn(0f, 1f)
                        } else 1f
                        
                        // Disegniamo sempre per evitare glitch strutturali
                        drawRect(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                0.85f to Color.Black,
                                1.0f to Color.Black.copy(alpha = 1f - fadeAmount)
                            ),
                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                        )
                    },
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 0.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(shifts, key = { it.id.toString() + "_" + it.employeeId + "_" + it.date.toString() + "_" + it.startTime?.toString() }) { shift ->
                    ColleagueHubCard(
                        shift = shift,
                        isFavorite = favorites.contains(shift.employeeId),
                        onToggleFavorite = { viewModel.toggleFavorite(shift.employeeId) }
                    )
                }
            }
        }
    }
}



private fun shareRoster(context: android.content.Context, shifts: List<Shift>, month: YearMonth) {
    if (shifts.isEmpty()) return
    
    val monthName = month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ITALIAN).replaceFirstChar { it.uppercase() }
    val title = "✈️ *IL MIO ROSTER* - $monthName ${month.year} ✈️\n\n"
    
    val body = shifts.sortedBy { it.date }.joinToString("\n") { shift ->
        val dateStr = shift.date.format(java.time.format.DateTimeFormatter.ofPattern("dd EEE", java.util.Locale.ITALIAN)).uppercase()
        val shiftVal = when (shift.shiftType) {
            com.matteo.rosterenhancer.domain.model.ShiftType.WORK -> {
                val start = shift.startTime?.toString() ?: ""
                val end = shift.endTime?.toString() ?: ""
                val role = shift.role ?: ""
                "*$start - $end* ($role)"
            }
            com.matteo.rosterenhancer.domain.model.ShiftType.REST_1 -> "_Riposo R1_"
            com.matteo.rosterenhancer.domain.model.ShiftType.REST_2 -> "_Riposo R2_"
            com.matteo.rosterenhancer.domain.model.ShiftType.DAY_OFF -> "_Riposo (RO)_"
            com.matteo.rosterenhancer.domain.model.ShiftType.HOLIDAY -> "Festività"
            com.matteo.rosterenhancer.domain.model.ShiftType.ABSENT -> "Assente"
            com.matteo.rosterenhancer.domain.model.ShiftType.PARENTAL_LEAVE -> "Congedo"
            else -> "—"
        }
        "🗓 $dateStr: $shiftVal"
    }

    val textToShare = "$title$body\n\n_Generato da RosterEnhancer_"
    val sendIntent = android.content.Intent().apply {
        action = android.content.Intent.ACTION_SEND
        putExtra(android.content.Intent.EXTRA_TEXT, textToShare)
        type = "text/plain"
    }
    val shareIntent = android.content.Intent.createChooser(sendIntent, "Condividi i tuoi turni con...")
    context.startActivity(shareIntent)
}









