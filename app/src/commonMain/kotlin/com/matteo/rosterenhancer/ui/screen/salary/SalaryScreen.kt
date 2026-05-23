package com.matteo.rosterenhancer.ui.screen.salary

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.matteo.rosterenhancer.ui.components.GlassCard
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import com.matteo.rosterenhancer.util.italianDisplayName
import com.matteo.rosterenhancer.ui.theme.SuccessGreen
import com.matteo.rosterenhancer.util.YearMonth
import com.matteo.rosterenhancer.util.formatDecimal
import com.matteo.rosterenhancer.util.formatCurrency
import com.matteo.rosterenhancer.util.formatCurrencyInt
import com.matteo.rosterenhancer.util.TextStyle
import com.matteo.rosterenhancer.util.Locale
import com.matteo.rosterenhancer.domain.model.DailySummary
import com.matteo.rosterenhancer.domain.model.MonthlySummary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.matteo.rosterenhancer.domain.model.ShiftType
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalaryScreen(
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    viewModel: SalaryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentMonth by viewModel.currentMonth.collectAsState()
    var showManualShiftDialog by remember { mutableStateOf(false) }
    var selectedShiftForEdit by remember { mutableStateOf<com.matteo.rosterenhancer.domain.model.Shift?>(null) }
    var selectedShiftForDetails by remember { mutableStateOf<DailySummary?>(null) }
    var shiftToDelete by remember { mutableStateOf<com.matteo.rosterenhancer.domain.model.Shift?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var privacyMode by remember { mutableStateOf(false) }
    var showBreakdownSheet by remember { mutableStateOf(false) }
    var breakdownTitle by remember { mutableStateOf("") }
    var filteredDailySummaries by remember { mutableStateOf<List<com.matteo.rosterenhancer.domain.model.DailySummary>>(emptyList()) }
    var breakdownType by remember { mutableStateOf("overtime") } // "overtime" o "night"

    fun formatMoney(value: Double): String = if (privacyMode) "€ ***,**" else "€ ${value.formatCurrency()}"
    fun formatMoneyInt(value: Double): String = if (privacyMode) "€ ***" else "€ ${value.formatCurrencyInt()}"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resoconto Guadagni", fontWeight = FontWeight.Black) },
                actions = {
                    IconButton(onClick = { privacyMode = !privacyMode }) {
                        Icon(
                            if (privacyMode) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = "Privacy Mode"
                        )
                    }
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Default.Settings, contentDescription = "Configura Profilo")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = selectedTab == 1,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                SmallFloatingActionButton(
                    onClick = { showManualShiftDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.padding(bottom = 80.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Aggiungi Turno Manuale")
                }
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        val lazyListState = rememberLazyListState()
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .graphicsLayer { alpha = 0.99f }
                .drawWithContent {
                    drawContent()
                    // Rampa smooth basata su layoutInfo (draw phase only, nessuna recomposizione)
                    val info = lazyListState.layoutInfo
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
                }
                .padding(bottom = 112.dp),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 0.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Selettore Mese
            item {
                MonthSelector(
                    currentMonth = YearMonth.of(currentMonth.year, currentMonth.monthNumber),
                    onPrev = viewModel::prevMonth,
                    onNext = viewModel::nextMonth
                )
            }

            // Tab Selector per differenziare Riepilogo e Turni
            item {
                SalaryTabSelector(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }

            if (uiState.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                val summary = uiState.summary
                if (summary == null || summary.dailySummaries.isEmpty()) {
                    item {
                        if (selectedTab == 0) {
                            EmptySalaryState()
                        } else {
                            EmptyShiftsState(onAddClick = { showManualShiftDialog = true })
                        }
                    }
                } else {
                    if (selectedTab == 0) {
                        // --- TAB RIEPILOGO GENERALE ---
                        item {
                            uiState.projection?.let { proj ->
                                ProjectionWidget(
                                    projection = proj, 
                                    taxRate = uiState.profile.taxRate,
                                    formatMoney = ::formatMoney
                                )
                            }
                        }

                        if (uiState.payslipHistory.isNotEmpty()) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        "I TUOI CEDOLINI",
                                        style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 2.sp),
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                    androidx.compose.foundation.lazy.LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(end = 16.dp)
                                    ) {
                                        items(uiState.payslipHistory) { payslip ->
                                            PayslipCard(payslip = payslip, formatMoney = ::formatMoney)
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                BentoCard(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    title = "Ore Totali",
                                    value = "${summary.totalHours.toInt()}h",
                                    subValue = "Ordinario: ${summary.ordinaryHours.toInt()}h",
                                    icon = Icons.Outlined.Timer,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                BentoCard(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    title = "Netto Attuale",
                                    value = formatMoneyInt(uiState.projection?.earnedSoFar ?: summary.estimatedNetPay),
                                    subValue = "Tassazione: ${(uiState.profile.taxRate * 100).formatDecimal(0)}%",
                                    icon = Icons.Outlined.AccountBalanceWallet,
                                    color = SuccessGreen
                                )
                            }
                        }

                        item {
                            EarningsPieChart(summary = summary, formatMoney = ::formatMoney)
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(140.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                BentoCard(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    title = "Straordinari",
                                    value = formatMoneyInt(summary.overtimePay),
                                    subValue = buildString {
                                        if (summary.manualOvertimeHours > 0) append("${summary.manualOvertimeHours.toInt()}h manuali")
                                        if (summary.autoOvertimeHours > 0) {
                                            if (summary.manualOvertimeHours > 0) append(" + ")
                                            append("${summary.autoOvertimeHours.toInt()}h soglia")
                                        }
                                        if (isEmpty()) append("nessuno")
                                    },
                                    icon = Icons.Outlined.Payments,
                                    color = Color(0xFFFF9800),
                                    onClick = {
                                        breakdownTitle = "Dettaglio Straordinari"
                                        breakdownType = "overtime"
                                        filteredDailySummaries = summary.dailySummaries.filter { it.manualOvertimeHours > 0 || it.shift.overtimeMinutes > 0 }
                                        showBreakdownSheet = true
                                    }
                                )
                                BentoCard(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    title = "Notturni",
                                    value = formatMoneyInt(summary.totalNightBonus),
                                    icon = Icons.Outlined.Nightlight,
                                    color = Color(0xFF9C27B0),
                                    onClick = {
                                        breakdownTitle = "Dettaglio Ore Notturne"
                                        breakdownType = "night"
                                        filteredDailySummaries = summary.dailySummaries.filter { it.nightHours > 0 }
                                        showBreakdownSheet = true
                                    }
                                )
                            }
                        }
                    } else {
                        // --- TAB DETTAGLIO TURNI ---
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "I TUOI TURNI DEL MESE",
                                    style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 2.sp),
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                                Text(
                                    "${summary.dailySummaries.size} turni",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        items(summary.dailySummaries.sortedByDescending { it.shift.date }) { daily ->
                            ShiftEarningItem(
                                daily = daily,
                                formatMoney = ::formatMoney,
                                onDetailsClick = { selectedShiftForDetails = daily },
                                onEditClick = { selectedShiftForEdit = daily.shift },
                                onDeleteClick = { shiftToDelete = daily.shift }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBreakdownSheet) {
        val currentSummary = uiState.summary
        ModalBottomSheet(
            onDismissRequest = { showBreakdownSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = breakdownTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = if (breakdownType == "overtime") "Ore extra rispetto all'orario base mensile (${currentSummary?.monthlyThreshold?.toInt() ?: 173}h)." else "Ore lavorate tra le 20:00 e le 08:00.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Nota informativa per le ore automatiche (soglia mensile)
                if (breakdownType == "overtime" && currentSummary != null && currentSummary.autoOvertimeHours > 0) {
                    Surface(
                        color = Color(0xFFFF9800).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, null, tint = Color(0xFFFF9800), modifier = Modifier.size(20.dp))
                            Column {
                                Text(
                                    "${currentSummary.autoOvertimeHours.toInt()}h da superamento soglia mensile",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF9800)
                                )
                                Text(
                                    "Hai lavorato ${currentSummary.totalHours.toInt()}h su ${currentSummary.monthlyThreshold.toInt()}h ordinarie. Le ${currentSummary.autoOvertimeHours.toInt()}h eccedenti vengono pagate al 125%.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                ) {
                    if (filteredDailySummaries.isEmpty() && breakdownType == "overtime") {
                        item {
                            Text(
                                "Nessuno straordinario manuale registrato questo mese.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                    items(filteredDailySummaries.sortedByDescending { it.shift.date }) { daily ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    val dayName = daily.shift.date.dayOfWeek.italianDisplayName().replaceFirstChar { it.uppercase() }
                                    Text(
                                        "$dayName ${daily.shift.date.dayOfMonth}",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (breakdownType == "overtime") "${daily.manualOvertimeHours + (daily.shift.overtimeMinutes / 60.0)} ore extra"
                                        else "${daily.nightHours} ore notturne",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text(
                                    if (breakdownType == "overtime") formatMoney(daily.manualOvertimePay)
                                    else formatMoney(daily.nightBonusPay),
                                    fontWeight = FontWeight.Black,
                                    color = if (breakdownType == "overtime") Color(0xFFFF9800) else Color(0xFF9C27B0)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showManualShiftDialog) {
        com.matteo.rosterenhancer.ui.components.ShiftEditDialog(
            currentMonth = com.matteo.rosterenhancer.util.YearMonth(currentMonth.year, currentMonth.monthNumber),
            onDismiss = { showManualShiftDialog = false },
            onConfirm = { newShift ->
                viewModel.addManualShift(
                    date = newShift.date,
                    startTime = newShift.startTime?.toString() ?: "07:00",
                    duration = newShift.durationHours ?: 0,
                    role = newShift.role ?: "PAX",
                    shiftType = newShift.shiftType,
                    isMensa = newShift.isMensaLavorata,
                    overtimeMinutes = newShift.overtimeMinutes,
                    otStart = newShift.overtimeStartTime,
                    otEnd = newShift.overtimeEndTime
                )
                showManualShiftDialog = false
            }
        )
    }

    if (selectedShiftForEdit != null) {
        com.matteo.rosterenhancer.ui.components.ShiftEditDialog(
            shift = selectedShiftForEdit!!,
            onDismiss = { selectedShiftForEdit = null },
            onConfirm = { updatedShift ->
                viewModel.updateShift(updatedShift)
                selectedShiftForEdit = null
            }
        )
    }

    if (selectedShiftForDetails != null) {
        ShiftDetailsDialog(
            daily = selectedShiftForDetails!!,
            formatMoney = ::formatMoney,
            onDismiss = { selectedShiftForDetails = null }
        )
    }

    if (shiftToDelete != null) {
        AlertDialog(
            onDismissRequest = { shiftToDelete = null },
            title = { Text("Elimina Turno") },
            text = { Text("Sei sicuro di voler eliminare questo turno? L'operazione non è reversibile.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteShift(shiftToDelete!!)
                        shiftToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Elimina")
                }
            },
            dismissButton = {
                TextButton(onClick = { shiftToDelete = null }) {
                    Text("Annulla")
                }
            }
        )
    }
}

/*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShiftEditDialog(
    shift: com.matteo.rosterenhancer.domain.model.Shift,
    onDismiss: () -> Unit,
    onConfirm: (com.matteo.rosterenhancer.domain.model.Shift) -> Unit
) {
    // Stato per Orario Inizio
    var startHour by remember { mutableStateOf(shift.startTime?.hour?.toString()?.padStart(2, '0') ?: "08") }
    var startMin by remember { mutableStateOf(shift.startTime?.minute?.toString()?.padStart(2, '0') ?: "00") }
    
    // Stato per Straordinari
    val initialOvertimeHours = shift.overtimeMinutes / 60
    val initialOvertimeMins = shift.overtimeMinutes % 60
    var overtimeHours by remember { mutableStateOf(initialOvertimeHours.toString()) }
    var overtimeMins by remember { 
        val snapped = when {
            initialOvertimeMins < 8 -> "00"
            initialOvertimeMins < 23 -> "15"
            initialOvertimeMins < 38 -> "30"
            else -> "45"
        }
        mutableStateOf(snapped)
    }

    // Stato per Mansione e Durata
    var role by remember { mutableStateOf(shift.role ?: "PAX") }
    var duration by remember { mutableStateOf(shift.durationHours?.toString() ?: "8") }
    var type by remember { mutableStateOf(shift.shiftType) }
    var isMensa by remember { mutableStateOf(shift.isMensaLavorata) }
    
    val hoursList = (0..23).map { it.toString().padStart(2, '0') }
    val minsList = listOf("00", "15", "30", "45")
    val overtimeHoursList = (0..12).map { it.toString() }
    val rolesList = remember { listOf("PAX", "PAM", "BAG", "BAG_S", "SEF", "SEM", "PEM", "TAM", "TAF", "SPV") }
    val durationOptions = listOf("3", "5", "8")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 700.dp),
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
                        Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Modifica Turno", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${shift.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ITALIAN).capitalize()} ${shift.date.dayOfMonth} ${shift.date.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN).capitalize()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ShiftType.values().forEach { t ->
                                FilterChip(
                                    selected = type == t,
                                    onClick = { type = t },
                                    label = { Text(t.name) }
                                )
                            }
                        }
                    }

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

                    // Durata e Mansione
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Durata:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Mansione:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rolesList.forEach { r ->
                                FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r) })
                            }
                        }
                    }

                    // Straordinari
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Straordinari:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                WheelPicker(items = overtimeHoursList, selectedItem = overtimeHours, onItemSelected = { overtimeHours = it }, modifier = Modifier.width(60.dp))
                                Text("h", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 4.dp))
                                Text(":", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 4.dp))
                                WheelPicker(items = minsList, selectedItem = overtimeMins, onItemSelected = { overtimeMins = it }, modifier = Modifier.width(60.dp))
                                Text("m", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 4.dp))
                            }
                        }
                    }

                    // Mensa
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Pausa Mensa Retribuita", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Switch(checked = isMensa, onCheckedChange = { isMensa = it })
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
                            val startTime = LocalTime.of(h, m)
                            val dur = duration.toIntOrNull() ?: 0
                            val otH = overtimeHours.toIntOrNull() ?: 0
                            val otM = overtimeMins.toIntOrNull() ?: 0
                            
                            val updated = shift.copy(
                                startTime = startTime,
                                durationHours = dur,
                                endTime = startTime.plusHours(dur.toLong()),
                                role = role,
                                shiftType = type,
                                isMensaLavorata = isMensa,
                                overtimeMinutes = otH * 60 + otM
                            )
                            onConfirm(updated)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualShiftDialog(
    currentMonth: YearMonth,
    onDismiss: () -> Unit,
    onConfirm: (Int, String, Int, String, ShiftType, Boolean) -> Unit
) {
    var dayOfMonth by remember { mutableStateOf(LocalDate.now().dayOfMonth.coerceIn(1, currentMonth.lengthOfMonth).toString()) }
    val daysList = remember(currentMonth) { (1..currentMonth.lengthOfMonth).map { it.toString() } }

    var startHour by remember { mutableStateOf("07") }
    var startMin by remember { mutableStateOf("00") }
    val hoursList = (0..23).map { it.toString().padStart(2, '0') }
    val minsList = listOf("00", "15", "30", "45")

    var role by remember { mutableStateOf("PAX") }

    var duration by remember { mutableStateOf("8") } // Default to an available option
    val durationOptions = listOf("3", "5", "8")
    
    var type by remember { mutableStateOf(ShiftType.WORK) }
    var isMensa by remember { mutableStateOf(false) }
    val rolesList = remember { listOf("PAX", "PAM", "BAG", "BAG_S", "SEF", "SEM", "PEM", "TAM", "TAF", "SPV") }
    var isWorkShift by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 700.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Header Icon
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Add, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.onPrimaryContainer, 
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                // Title
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Aggiungi Turno", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN).capitalize()} ${currentMonth.year}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(24.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Type Selection
                    Text("Tipologia:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = type == ShiftType.WORK,
                            onClick = { type = ShiftType.WORK; isWorkShift = true },
                            label = { Text("Ordinario") }
                        )
                        FilterChip(
                            selected = type == ShiftType.INTERVENTO,
                            onClick = { type = ShiftType.INTERVENTO; isWorkShift = true },
                            label = { Text("Intervento (x2)") }
                        )
                        FilterChip(
                            selected = type == ShiftType.MANCATO_R1,
                            onClick = { type = ShiftType.MANCATO_R1; isWorkShift = true },
                            label = { Text("Mancato R1") }
                        )
                        FilterChip(
                            selected = type == ShiftType.MANCATO_R2,
                            onClick = { type = ShiftType.MANCATO_R2; isWorkShift = true },
                            label = { Text("Mancato R2") }
                        )
                        FilterChip(
                            selected = !isWorkShift,
                            onClick = { 
                                isWorkShift = false
                                if (type != ShiftType.REST_1 && type != ShiftType.REST_2) type = ShiftType.REST_1
                            },
                            label = { Text("Riposo") }
                        )
                    }

                    if (isWorkShift) {
                        // Mensa Switch
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Pausa Mensa Retribuita", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("Aggiunge 45 min alla paga base", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = isMensa, onCheckedChange = { isMensa = it })
                        }
                    }

                    // Day Selection (Always visible)
                    Text("Giorno del Mese:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        WheelPicker(
                            items = daysList,
                            selectedItem = dayOfMonth,
                            onItemSelected = { dayOfMonth = it },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )
                    }
                    
                    if (isWorkShift) {
                        Text("Inizio Turno:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Ore", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(8.dp))
                                    WheelPicker(
                                        items = hoursList,
                                        selectedItem = startHour,
                                        onItemSelected = { startHour = it },
                                        modifier = Modifier.width(60.dp)
                                    )
                                }
                                Text(":", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Minuti", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(8.dp))
                                    WheelPicker(
                                        items = minsList,
                                        selectedItem = startMin,
                                        onItemSelected = { startMin = it },
                                        modifier = Modifier.width(60.dp)
                                    )
                                }
                            }
                        }

                        // Role Selection
                        Text("Mansione (clicca per scegliere):", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            rolesList.forEach { r ->
                                FilterChip(
                                    selected = role == r,
                                    onClick = { role = r },
                                    label = { Text(r) }
                                )
                            }
                        }

                        // Duration Chips
                        Text("Ore lavorate (clicca per scegliere):", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            durationOptions.forEach { ore ->
                                FilterChip(
                                    selected = duration == ore,
                                    onClick = { duration = ore },
                                    label = { Text("${ore}h") }
                                )
                            }
                        }
                    } else {
                        // Type of Rest Selection
                        Column {
                            Text("Tipo di riposo:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FilterChip(
                                    selected = type == ShiftType.REST_1,
                                    onClick = { type = ShiftType.REST_1 },
                                    label = { Text("R1 (Riposo 1)") }
                                )
                                FilterChip(
                                    selected = type == ShiftType.REST_2,
                                    onClick = { type = ShiftType.REST_2 },
                                    label = { Text("R2 (Riposo 2)") }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Annulla")
                    }
                    Button(
                        onClick = { 
                            val day = dayOfMonth.toIntOrNull() ?: 1
                            val safeDay = day.coerceIn(1, currentMonth.lengthOfMonth)
                            val finalStartTime = "$startHour:$startMin"
                            onConfirm(safeDay, finalStartTime, duration.toIntOrNull() ?: 0, role, type, isMensa) 
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Crea Turno", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
*/@Composable
private fun SalaryTabSelector(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val containerColor = if (isDark) Color.Black.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(4.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val tabs = listOf("Riepilogo", "I tuoi Turni")
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                val backgroundAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0f,
                    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                    label = "tabBgAlpha"
                )
                val textColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                }
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = backgroundAlpha)
                            else Color.Transparent
                        )
                        .clickable { onTabSelected(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyShiftsState(onAddClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Text(
                "Nessun turno in questo mese",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                "Aggiungi i tuoi turni manualmente per visualizzare il resoconto dettagliato.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Aggiungi Turno", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptySalaryState() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Outlined.AccountBalanceWallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Text(
                "Nessun dato per questo mese",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                "Importa un roster o aggiungi turni manualmente per vedere il calcolo dello stipendio.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MonthSelector(
    currentMonth: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > 100) onPrev()
                        else if (offsetX < -100) onNext()
                        offsetX = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount
                    }
                )
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Mese Precedente")
            }
            
            Text(
                text = "${com.matteo.rosterenhancer.util.getMonthName(currentMonth.month)} ${currentMonth.year}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            
            IconButton(onClick = onNext) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Mese Successivo")
            }
        }
    }
}

@Composable
private fun EarningsHeroCard(summary: MonthlySummary, formatMoney: (Double) -> String, formatMoneyInt: (Double) -> String) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primary = MaterialTheme.colorScheme.primary
    val contentColor = if (isDark) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(0.dp),
        border = if (!isDark) androidx.compose.foundation.BorderStroke(1.dp, primary.copy(alpha = 0.2f)) else null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isDark) {
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.primary
                            ),
                            radius = 2000f
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(
                                primary.copy(alpha = 0.12f),
                                primary.copy(alpha = 0.05f)
                            )
                        )
                    }
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.AccountBalanceWallet,
                            contentDescription = null,
                            tint = if (isDark) contentColor else primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "NETTO STIMATO",
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor.copy(alpha = if (isDark) 0.9f else 0.7f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Surface(
                        color = if (isDark) contentColor.copy(alpha = 0.15f) else primary.copy(alpha = 0.1f),
                        shape = CircleShape
                    ) {
                        Text(
                            "CCNL GPG",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) contentColor else primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Text(
                    text = formatMoney(summary.estimatedNetPay).replace("€ ", "€"),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
                    color = contentColor,
                    fontWeight = FontWeight.Black
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f), thickness = 1.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HeroStatItem(label = "Ore Totali", value = "${summary.totalHours.formatDecimal(1)}h", icon = Icons.Outlined.Timer)
                    HeroStatItem(label = "Lordo Totale", value = formatMoneyInt(summary.totalGrossPay), icon = Icons.Outlined.Payments)
                }
            }
        }
    }
}

@Composable
private fun AccrualsSection(accruals: com.matteo.rosterenhancer.domain.model.AccrualsSummary?, formatMoneyInt: (Double) -> String) {
    if (accruals == null) return
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Ratei Maturati (Cumulativo)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccrualItem(label = "13ª", value = accruals.cumulative13th, formatMoneyInt = formatMoneyInt, modifier = Modifier.weight(1f))
                AccrualItem(label = "14ª", value = accruals.cumulative14th, formatMoneyInt = formatMoneyInt, modifier = Modifier.weight(1f))
                AccrualItem(label = "TFR", value = accruals.cumulativeTfr, formatMoneyInt = formatMoneyInt, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AccrualItem(label: String, value: Double, formatMoneyInt: (Double) -> String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatMoneyInt(value), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun EarningsMetricsGrid(summary: MonthlySummary, formatMoney: (Double) -> String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Metriche del Lordo",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 0.dp)
            )
            Text(
                formatMoney(summary.totalGrossPay),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            EarningMetricTile(
                label = "Paga Base",
                value = formatMoney(summary.basePayOrdinario),
                icon = Icons.Outlined.Payments,
                modifier = Modifier.weight(1f)
            )
            EarningMetricTile(
                label = "Straordinari",
                value = formatMoney(summary.overtimePay),
                icon = Icons.Outlined.Timer,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            EarningMetricTile(
                label = "Notturno",
                value = formatMoney(summary.totalNightBonus),
                icon = Icons.Outlined.Nightlight,
                modifier = Modifier.weight(1f)
            )
            EarningMetricTile(
                label = "Domenicale",
                value = formatMoney(summary.totalSundayBonus),
                icon = Icons.Outlined.AccountBalanceWallet,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            EarningMetricTile(
                label = "Festività",
                value = formatMoney(summary.totalHolidayBonus),
                icon = Icons.Filled.Star,
                modifier = Modifier.weight(1f)
            )
            EarningMetricTile(
                label = "Ind. Presenza",
                value = formatMoney(summary.totalPresenceIndemnity),
                icon = Icons.Outlined.AccountBalanceWallet,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EarningMetricTile(label: String, value: String, icon: ImageVector, modifier: Modifier) {
    Card(
        modifier = modifier.heightIn(min = 105.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                shape = CircleShape
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(8.dp).size(20.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun HeroStatItem(label: String, value: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShiftEarningItem(
    daily: DailySummary,
    formatMoney: (Double) -> String,
    onDetailsClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        glassAlpha = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) 0.15f else 0.12f
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Data
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.width(50.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text(
                            daily.shift.date.dayOfWeek.italianDisplayName(short = true).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.5.sp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            daily.shift.date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                // Info Turno
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            daily.shift.role ?: "Turno",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (daily.shift.isHoliday) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFBC02D), modifier = Modifier.size(14.dp))
                        }
                    }
                    Text(
                        daily.shift.timeRange.ifEmpty { "Orario N/D" },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                }

                // Guadagno
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatMoney(daily.totalGrossPay),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = SuccessGreen
                    )
                    if (daily.manualOvertimeHours > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "+${daily.manualOvertimeHours.formatDecimal(1)}h str.",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Pulsanti Dettagli e Modifica
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onDetailsClick,
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("Dettagli", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
                
                FilledTonalButton(
                    onClick = onEditClick,
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Modifica", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                
                Surface(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(36.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.error
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Delete, contentDescription = "Elimina", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DashedDivider(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.outlineVariant, thickness: androidx.compose.ui.unit.Dp = 1.dp) {
    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxWidth().height(thickness)) {
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(0f, size.height / 2),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2),
            strokeWidth = thickness.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShiftDetailsDialog(
    daily: DailySummary,
    formatMoney: (Double) -> String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 48.dp, top = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text("Dettagli Turno", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val dateStr = "${daily.shift.date.dayOfWeek.italianDisplayName().replaceFirstChar { it.uppercase() }} ${daily.shift.date.dayOfMonth} ${daily.shift.date.month.italianDisplayName().replaceFirstChar { it.uppercase() }}"
            Text(dateStr, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            
            DashedDivider(color = MaterialTheme.colorScheme.outlineVariant)
        
            // Breakdowns Orari
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Orario")
                Text(daily.shift.timeRange.ifEmpty { "N/A" }, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ore Ordinarie")
                Text("${(daily.totalHours - daily.manualOvertimeHours).formatDecimal(1)}h", fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
            if (daily.manualOvertimeHours > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Straordinari")
                    Text("+${daily.manualOvertimeHours.formatDecimal(1)}h", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
        
            DashedDivider(color = MaterialTheme.colorScheme.outlineVariant)
            
            // Compensi
            Text("Compensi (Lordo)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val ordinaryHours = daily.totalHours - daily.manualOvertimeHours
                Text("Paga Base (${ordinaryHours.formatDecimal(1)}h)")
                Text(formatMoney(daily.basePay), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
            if (daily.manualOvertimePay > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Straordinari (${daily.manualOvertimeHours.formatDecimal(1)}h)")
                    Text(formatMoney(daily.manualOvertimePay), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
            if (daily.nightBonusPay > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Notturno (${daily.nightHours.formatDecimal(1)}h)")
                    Text(formatMoney(daily.nightBonusPay), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
            if (daily.sundayBonusPay > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Domenicale (${daily.sundayHours.formatDecimal(1)}h)")
                    Text(formatMoney(daily.sundayBonusPay), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
            if (daily.holidayBonusPay > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Festivo (${daily.holidayHours.formatDecimal(1)}h)")
                    Text(formatMoney(daily.holidayBonusPay), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
            if (daily.presenceIndemnity > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ind. Presenza")
                    Text(formatMoney(daily.presenceIndemnity), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
            if (daily.mensaPay > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Mensa Retribuita")
                    Text(formatMoney(daily.mensaPay), color = SuccessGreen, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
            if (daily.airportIndemnityPay > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Indennità Aeroportuale")
                    Text(formatMoney(daily.airportIndemnityPay), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
            val restBonus = daily.totalGrossPay - (daily.basePay + daily.nightBonusPay + daily.sundayBonusPay + daily.holidayBonusPay + daily.presenceIndemnity + daily.manualOvertimePay + daily.airportIndemnityPay)
            if (restBonus > 0.1) {
                 Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Magg. Mancato Riposo")
                    Text(formatMoney(restBonus), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
            
            DashedDivider(color = MaterialTheme.colorScheme.outlineVariant)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Totale", fontWeight = FontWeight.Bold)
                Text(formatMoney(daily.totalGrossPay), fontWeight = FontWeight.ExtraBold, color = SuccessGreen, style = MaterialTheme.typography.titleMedium, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
        }
    }
}

private fun String.capitalize() = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(
    items: List<String>,
    selectedItem: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: androidx.compose.ui.unit.Dp = 48.dp
) {
    val listState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    
    // Auto-scroll to selectedItem on first composition
    val initialIndex = items.indexOf(selectedItem).takeIf { it >= 0 } ?: 0
    LaunchedEffect(initialIndex) {
        listState.scrollToItem(initialIndex)
    }

    // Update derived state
    val centralIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf -1
            
            val viewportCenter = layoutInfo.viewportStartOffset + layoutInfo.viewportSize.height / 2
            
            var matchedIndex = visibleItems.first().index
            var minDistance = Int.MAX_VALUE
            
            for (itemInfo in visibleItems) {
                val itemCenter = itemInfo.offset + itemInfo.size / 2
                val distance = kotlin.math.abs(itemCenter - viewportCenter)
                if (distance < minDistance) {
                    minDistance = distance
                    matchedIndex = itemInfo.index
                }
            }
            matchedIndex
        }
    }
    
    LaunchedEffect(centralIndex) {
        if (centralIndex in items.indices && items[centralIndex] != selectedItem) {
            onItemSelected(items[centralIndex])
        }
    }

    Box(modifier = modifier.height(itemHeight * 3), contentAlignment = Alignment.Center) {
        // Selection Highlight
        Surface(
            modifier = Modifier.fillMaxWidth().height(itemHeight),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            shape = RoundedCornerShape(8.dp)
        ) {}
        
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items.size) { index ->
                val isSelected = index == centralIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[index],
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

