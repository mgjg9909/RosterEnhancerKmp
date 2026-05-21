package com.matteo.rosterenhancer.ui.screen.stats

import kotlinx.datetime.todayIn
import com.matteo.rosterenhancer.util.*
import kotlinx.datetime.periodUntil
import com.matteo.rosterenhancer.util.format

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.Canvas
import com.matteo.rosterenhancer.ui.component.HapticStyle
import com.matteo.rosterenhancer.ui.component.bouncyClick
import org.koin.compose.viewmodel.koinViewModel
import com.matteo.rosterenhancer.ui.theme.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import com.matteo.rosterenhancer.util.TextStyle
import com.matteo.rosterenhancer.util.Locale

import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.compose.style.currentChartStyle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.matteo.rosterenhancer.util.YearMonth
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = koinViewModel()
) {
    BackHandler { onBack() }
    val state by viewModel.uiState.collectAsState()
    var selectedCompanion by remember { mutableStateOf<TopCompanion?>(null) }
    val currentMonth = Clock.System.todayIn(TimeZone.currentSystemDefault()).month.italianDisplayName()
        .replaceFirstChar { it.uppercase() }

    if (selectedCompanion != null) {
        val comp = selectedCompanion!!
        AlertDialog(
            onDismissRequest = { selectedCompanion = null },
            icon = {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(48.dp)
                )
            },
            title = { 
                Text(
                    text = comp.name.split(" ").joinToString(" ") { it.lowercase().replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() } },
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                ) 
            },
            text = { 
                Text(
                    "Voi due avete condiviso ${comp.sharedHours} ore di servizio distribuite su ben ${comp.sharedDays} turni durante questo roster.",
                    textAlign = TextAlign.Center
                ) 
            },
            confirmButton = {
                Button(onClick = { selectedCompanion = null }, modifier = Modifier.fillMaxWidth()) { Text("Chiudi") }
            }
        )
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                tonalElevation = 2.dp
            ) {
                TopAppBar(
                    title = { 
                        Text(
                            "Statistiche — $currentMonth", 
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleLarge
                        ) 
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.clickable { onBack() }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                return@LazyColumn
            }

            // Card progressi mensili (migrata dalla Dashboard)
            item {
                MonthProgressCard(
                    worked = state.totalHours,
                    max = state.monthMaxHours
                )
            }

            // Sezione Grafico Trend Stipendio
            if (state.salaryTrend.isNotEmpty()) {
                item { SalaryTrendCard(trend = state.salaryTrend) }
            }

            // Griglia statistiche principali
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Sostituito "Ore totali" con "Giorni lavorativi" e "Riposi" in prima riga per varietà
                    StatCard("Giorni lavorativi", state.workDays.toString(), "turni",
                        SuccessGreen, Modifier.weight(1f))
                    StatCard("Riposi", state.restDays.toString(), "R1/R2/RO",
                        ShiftRest1, Modifier.weight(1f))
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Assenze", state.absentDays.toString(), "giorni",
                        ShiftAbsent, Modifier.weight(1f))
                    // Placeholder o altra statistica utile
                    StatCard("Anno", Clock.System.todayIn(TimeZone.currentSystemDefault()).year.toString(), "roster",
                        MaterialTheme.colorScheme.outline, Modifier.weight(1f))
                }
            }

            // Podio Compagni
            if (state.topCompanions.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Colleghi più presenti",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // Secondo classificato
                            if (state.topCompanions.size > 1) {
                                PodiumSpot(
                                    companion = state.topCompanions[1],
                                    rank = 2,
                                    color = Color(0xFFC0C0C0), // Argento
                                    height = 80.dp,
                                    onClick = { selectedCompanion = state.topCompanions[1] }
                                )
                            }
                            
                            // Primo classificato (al centro)
                            PodiumSpot(
                                companion = state.topCompanions[0],
                                rank = 1,
                                color = Color(0xFFFFD700), // Oro
                                height = 110.dp,
                                onClick = { selectedCompanion = state.topCompanions[0] }
                            )
                            
                            // Terzo classificato
                            if (state.topCompanions.size > 2) {
                                PodiumSpot(
                                    companion = state.topCompanions[2],
                                    rank = 3,
                                    color = Color(0xFFCD7F32), // Bronzo
                                    height = 60.dp,
                                    onClick = { selectedCompanion = state.topCompanions[2] }
                                )
                            }
                        }
                    }
                }
            }
            }

            // Distribuzione turni — Donut Chart
            item {
                ShiftDonutCard(
                    morningShifts   = state.morningShifts,
                    centraleShifts  = state.centraleShifts,
                    afternoonShifts = state.afternoonShifts,
                    nightShifts     = state.nightShifts
                )
            }

            // Barchart ore settimanali
            if (state.weeklyHours.isNotEmpty()) {
                item { WeeklyBarChartCard(weeklyHours = state.weeklyHours) }
            }

            // Heatmap mensile
            if (state.monthShiftMap.isNotEmpty()) {
                item { MonthHeatmapCard(shiftMap = state.monthShiftMap) }
            }

            // Distribuzione mansioni
            if (state.roleDistribution.isNotEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Mansioni del mese",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface)
                        val totalRoles = state.roleDistribution.values.sum().coerceAtLeast(1)
                        val roleColors = listOf(BluePrimary, ShiftAfternoon, ShiftNight,
                            SuccessGreen, ShiftParental, InfoCyan, WarningYellow, ShiftAbsent)
                        state.roleDistribution.entries.forEachIndexed { idx, (role, count) ->
                            val color = roleColors.getOrElse(idx) { ShiftOther }
                            ShiftTypeBar(role, count, totalRoles, color, idx)
                        }
                    }
                }
            }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun MonthProgressCard(worked: Int, max: Int) {
    val progress = if (max > 0) (worked.toFloat() / max).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "progress"
    )
    val overTarget = worked > max

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Obiettivo mensile", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom) {
                    Text("$worked",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (overTarget) SuccessGreen else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black)
                    Text("/ $max",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 2.dp))
                }
            }
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                color = if (overTarget) SuccessGreen else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = if (overTarget)
                    "✨ Obiettivo superato! (+${worked - max} ore)"
                else
                    "Target mensile: $max ore · mancano ${max - worked} ore",
                style = MaterialTheme.typography.labelMedium,
                color = if (overTarget) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            Text(value, style = MaterialTheme.typography.headlineMedium,
                color = color, fontWeight = FontWeight.Black)
            Text(subtitle, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun ShiftDonutCard(
    morningShifts: Int,
    centraleShifts: Int,
    afternoonShifts: Int,
    nightShifts: Int
) {
    val segments = listOf(
        Triple("🌅 Mattina",    morningShifts,   ShiftMorning),
        Triple("☀️ Centrale",   centraleShifts,  ShiftCentrale),
        Triple("🌆 Pomeriggio", afternoonShifts, ShiftAfternoon),
        Triple("🌙 Notte",      nightShifts,     ShiftNight)
    ).filter { it.second > 0 }

    val total = segments.sumOf { it.second }.coerceAtLeast(1).toFloat()

    // Animazione dell'arco
    var startAnim by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { startAnim = true }
    val animatedSweep by animateFloatAsState(
        targetValue = if (startAnim) 360f else 0f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "donut_sweep"
    )

    // Segmento selezionato
    var selectedIndex by remember { mutableIntStateOf(-1) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                "Distribuzione turni",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Il Donut
                Box(
                    modifier = Modifier.size(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val strokeWidth = 22.dp
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokePx = strokeWidth.toPx()
                        val inset = strokePx / 2f
                        val diameter = size.minDimension - strokePx
                        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                        val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)

                        // Sfondo grigio
                        drawArc(
                            color = Color.White.copy(alpha = 0.07f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                        )

                        var currentAngle = -90f
                        segments.forEachIndexed { idx, (_, count, color) ->
                            val sweep = (count / total) * animatedSweep
                            val isSelected = selectedIndex == idx
                            val actualStroke = if (isSelected) strokePx * 1.3f else strokePx
                            drawArc(
                                color = color.copy(alpha = if (isSelected) 1f else 0.85f),
                                startAngle = currentAngle + 1f, // gap tra segmenti
                                sweepAngle = sweep - 1f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = actualStroke, cap = StrokeCap.Butt)
                            )
                            currentAngle += sweep
                        }
                    }

                    // Numero centrale
                    val displaySeg = if (selectedIndex >= 0 && selectedIndex < segments.size)
                        segments[selectedIndex] else null
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = displaySeg?.second?.toString() ?: total.toInt().toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = displaySeg?.third ?: MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (displaySeg != null) "turni" else "totale",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }

                // Legenda
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    segments.forEachIndexed { idx, (label, count, color) ->
                        val pct = (count / total * 100).toInt()
                        val isSelected = selectedIndex == idx
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedIndex = if (isSelected) -1 else idx
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) color.copy(alpha = 0.15f) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$pct%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = color
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShiftTypeBar(label: String, count: Int, total: Int, color: Color, index: Int = 0) {
    val progress = if (total > 0) count.toFloat() / total else 0f
    
    var startAnim by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { startAnim = true }

    val animatedProgress by animateFloatAsState(
        targetValue = if (startAnim) progress else 0f,
        animationSpec = tween(800, delayMillis = index * 120, easing = FastOutSlowInEasing),
        label = "bar_$label"
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$count", style = MaterialTheme.typography.labelMedium,
                color = color, fontWeight = FontWeight.Black)
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(animatedProgress).fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp)).background(color)
            )
        }
    }
}

@Composable
private fun PodiumSpot(
    companion: TopCompanion, 
    rank: Int, 
    color: Color, 
    height: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Surface(
        color = Color.Transparent,
        modifier = Modifier.bouncyClick {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            onClick()
        },
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            val names = companion.name.split(" ")
            val firstName = names.lastOrNull() ?: ""
            
            Text(
                text = firstName.lowercase().replaceFirstChar { it.titlecase() },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = companion.id,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${companion.sharedHours}h",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        Spacer(Modifier.height(10.dp))
        
        Box(
            modifier = Modifier
                .width(75.dp)
                .height(height)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            color.copy(alpha = 0.8f),
                            color.copy(alpha = 0.1f)
                        )
                    )
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                modifier = Modifier.padding(top = 10.dp),
                color = Color.White.copy(alpha = 0.2f),
                shape = CircleShape
            ) {
                Text(
                    text = "$rank°",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        }
    }
    }
}
@Composable
private fun WeeklyBarChartCard(weeklyHours: List<Pair<String, Int>>) {
    val maxH = weeklyHours.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    var startAnim by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { startAnim = true }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Ore per settimana",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                weeklyHours.forEachIndexed { idx, (label, hours) ->
                    val targetFrac = hours.toFloat() / maxH
                    val animFrac by animateFloatAsState(
                        targetValue = if (startAnim) targetFrac else 0f,
                        animationSpec = tween(900, delayMillis = idx * 120, easing = FastOutSlowInEasing),
                        label = "bar_$idx"
                    )
                    val color = MaterialTheme.colorScheme.primary
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            "${hours}h",
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(animFrac)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(color, color.copy(alpha = 0.4f))
                                    )
                                )
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeatmapCard(shiftMap: Map<LocalDate, String>) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val ym = YearMonth.of(today.year, today.monthNumber)
    val firstDay = ym.atDay(1)
    val startOffset = (firstDay.dayOfWeek.value - 1) % 7 // lun=0
    val daysOfWeek = listOf("L", "M", "M", "G", "V", "S", "D")

    fun colorForType(type: String?): Color = when (type) {
        "WORK"     -> ShiftMorning.copy(alpha = 0.85f)
        "REST"     -> ShiftRest1.copy(alpha = 0.7f)
        "OFF"      -> ShiftDayOff.copy(alpha = 0.7f)
        "ABSENT"   -> ShiftAbsent.copy(alpha = 0.85f)
        "HOLIDAY"  -> SuccessGreen.copy(alpha = 0.7f)
        "PARENTAL" -> ShiftParental.copy(alpha = 0.7f)
        else       -> Color.Transparent
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Mese in un colpo d'occhio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Intestazione giorni settimana
            Row(modifier = Modifier.fillMaxWidth()) {
                daysOfWeek.forEach { d ->
                    Text(
                        d,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // Griglia
            val rows = ((startOffset + ym.lengthOfMonth) + 6) / 7
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in 0 until rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (col in 0 until 7) {
                            val dayNum = row * 7 + col - startOffset + 1
                            if (dayNum < 1 || dayNum > ym.lengthOfMonth) {
                                Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                            } else {
                                val date = ym.atDay(dayNum)
                                val type = shiftMap[date]
                                val isToday = date == today
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (type != null) colorForType(type)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "$dayNum",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (type != null)
                                            MaterialTheme.colorScheme.onSurface
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        fontWeight = if (isToday) FontWeight.Black else FontWeight.Normal
                                    )
                                    if (isToday) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Legenda
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    "Lavoro" to ShiftMorning,
                    "Riposo" to ShiftRest1,
                    "OFF" to ShiftDayOff,
                    "Assenza" to ShiftAbsent
                ).forEach { (label, color) ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(color.copy(alpha = 0.85f))
                        )
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SalaryTrendCard(trend: List<SalaryTrendPoint>) {
    var touchX by remember { mutableStateOf<Float?>(null) }
    var canvasWidth by remember { mutableStateOf(0f) }

    val minVal = trend.minOf { it.netSalary }.toFloat()
    val maxVal = trend.maxOf { it.netSalary }.toFloat().coerceAtLeast(minVal + 1f)
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    // Indice punto toccato
    val touchedIndex = touchX?.let { tx ->
        if (canvasWidth <= 0f || trend.isEmpty()) null
        else {
            val step = canvasWidth / (trend.size - 1).coerceAtLeast(1)
            val idx = ((tx / step).toInt()).coerceIn(0, trend.size - 1)
            idx
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Andamento Stipendio (Netto)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (touchedIndex != null) {
                    Text(
                        "€${String.format("%.0f", trend[touchedIndex].netSalary)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = primaryColor
                    )
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = { offset ->
                                touchX = offset.x
                                tryAwaitRelease()
                                touchX = null
                            }
                        )
                    }
            ) {
                canvasWidth = size.width
                val w = size.width
                val h = size.height
                val range = (maxVal - minVal).coerceAtLeast(1f)
                val step = if (trend.size > 1) w / (trend.size - 1) else w

                val points = trend.mapIndexed { i, p ->
                    Offset(
                        x = i * step,
                        y = h - ((p.netSalary.toFloat() - minVal) / range) * h * 0.85f - h * 0.05f
                    )
                }

                // Linea gradiente
                if (points.size > 1) {
                    for (i in 0 until points.size - 1) {
                        drawLine(
                            color = primaryColor.copy(alpha = 0.8f),
                            start = points[i],
                            end = points[i + 1],
                            strokeWidth = 3.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }
                }

                // Punti
                points.forEachIndexed { i, pt ->
                    val isActive = touchedIndex == i
                    drawCircle(
                        color = if (isActive) primaryColor else primaryColor.copy(alpha = 0.6f),
                        radius = if (isActive) 7.dp.toPx() else 4.dp.toPx(),
                        center = pt
                    )
                    if (isActive) {
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.2f),
                            radius = 14.dp.toPx(),
                            center = pt
                        )
                        // Linea verticale marker
                        drawLine(
                            color = primaryColor.copy(alpha = 0.3f),
                            start = Offset(pt.x, 0f),
                            end = Offset(pt.x, h),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
            }

            // Etichette mesi
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                trend.forEach { p ->
                    Text(
                        p.monthName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Text(
                "Media ultimi 6 mesi: €${String.format("%.2f", trend.map { it.netSalary }.average())}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}








