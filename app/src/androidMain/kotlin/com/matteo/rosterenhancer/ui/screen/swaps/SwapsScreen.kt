package com.matteo.rosterenhancer.ui.screen.swaps

import com.matteo.rosterenhancer.util.format

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import com.matteo.rosterenhancer.domain.model.Shift
import com.matteo.rosterenhancer.domain.usecase.RestSwapCandidate
import com.matteo.rosterenhancer.domain.usecase.SwapCandidate
import com.matteo.rosterenhancer.ui.component.HapticStyle
import com.matteo.rosterenhancer.ui.component.bouncyClick
import com.matteo.rosterenhancer.ui.screen.calendar.CandidateSwapCard
import com.matteo.rosterenhancer.ui.screen.calendar.RestSwapCandidateCard
import com.matteo.rosterenhancer.ui.theme.*
import com.matteo.rosterenhancer.util.HapticFeedbackManager
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class SwapsViewType {
    SHIFT_SWAP, REST_SWAP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapsScreen(
    onBack: () -> Unit,
    shiftViewModel: ShiftSwapViewModel = koinViewModel(),
    restViewModel: RestSwapViewModel = koinViewModel()
) {
    var viewType by remember { mutableStateOf(SwapsViewType.SHIFT_SWAP) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("Hub Cambi", fontWeight = FontWeight.Bold) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .graphicsLayer { alpha = 0.99f }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.85f to Color.Black,
                            1.0f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
        ) {
            // Segmented Selection
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    onClick = { viewType = SwapsViewType.SHIFT_SWAP },
                    selected = viewType == SwapsViewType.SHIFT_SWAP,
                    icon = { SegmentedButtonDefaults.Icon(viewType == SwapsViewType.SHIFT_SWAP) }
                ) {
                    Text("Turni")
                }
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    onClick = { viewType = SwapsViewType.REST_SWAP },
                    selected = viewType == SwapsViewType.REST_SWAP,
                    icon = { SegmentedButtonDefaults.Icon(viewType == SwapsViewType.REST_SWAP) }
                ) {
                    Text("Riposi")
                }
            }

            AnimatedContent(
                targetState = viewType,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "swaps_content",
                modifier = Modifier
                    .weight(1f)
                    .animateContentSize()
            ) { targetView ->
                when (targetView) {
                    SwapsViewType.SHIFT_SWAP -> ShiftSwapView(shiftViewModel)
                    SwapsViewType.REST_SWAP -> RestSwapView(restViewModel)
                }
            }
        }
    }
}

@Composable
private fun ShiftSwapView(viewModel: ShiftSwapViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            text = "1. Scegli il tuo turno",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 4.dp)
        )
        Spacer(Modifier.height(12.dp))
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(uiState.upcomingShifts, key = { it.id }) { shift ->
                val isSelected = uiState.selectedShift?.id == shift.id
                ShiftSelectableCard(
                    shift = shift,
                    isSelected = isSelected,
                    onClick = { viewModel.selectShift(shift) }
                )
            }
        }
        
        Spacer(Modifier.height(32.dp))
        Text(
            text = "2. Colleghi idonei",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 4.dp)
        )
        Spacer(Modifier.height(12.dp))
        
        AnimatedVisibility(
            visible = uiState.selectedShift != null,
            modifier = Modifier.weight(1f),
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (uiState.isSearching) {
                    LoadingPlaceholder()
                } else {
                    val candidates = uiState.candidates ?: emptyList()
                    if (candidates.isEmpty()) {
                        EmptyResultsPlaceholder("Mmmh, sembra che nessuno sia libero per scambiare questo turno. Prova con un'altra data!")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(bottom = 32.dp, top = 4.dp, start = 4.dp, end = 4.dp)
                        ) {
                            items(
                                items = (uiState.candidates ?: emptyList()).map { candidate ->
                                    candidate.copy(isFavorite = uiState.favorites.contains(candidate.employeeId))
                                }.sortedWith(
                                    compareBy<SwapCandidate> { it.validationError != null }
                                        .thenBy { !it.isFavorite }
                                        .thenBy { it.employeeName }
                                ),
                                key = { "${it.employeeId}_${it.proposedShift.id}" }
                            ) { candidate ->
                                CandidateSwapCard(candidate = candidate, onClick = { /* Azione */ })
                            }
                        }
                    }
                }
            }
        }
        
        if (uiState.selectedShift == null) {
            EmptyResultsPlaceholder("Seleziona una data sopra per trovare il tuo match perfetto")
        }
    }
}

@Composable
private fun RestSwapView(viewModel: RestSwapViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            text = "1. Il giorno che vuoi RIPOSARE",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 4.dp)
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(uiState.myWorkShiftsForMonth, key = { it.id }) { shift ->
                val isSelected = uiState.selectedWorkShiftToDrop?.id == shift.id
                ShiftSelectableCard(shift = shift, isSelected = isSelected, onClick = { viewModel.selectWorkShiftToDrop(shift) })
            }
        }
        
        Spacer(Modifier.height(24.dp))
        Text(
            text = "2. Il riposo che vuoi CEDERE",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 4.dp)
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(uiState.myRestDaysForMonth, key = { it.id }) { shift ->
                val isSelected = uiState.selectedRestDayToWork?.id == shift.id
                ShiftSelectableCard(shift = shift, isSelected = isSelected, onClick = { viewModel.selectRestDayToWork(shift) }, isRest = true)
            }
        }
        
        Spacer(Modifier.height(32.dp))
        Text(
            text = "3. Risultati Ricerca",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 4.dp)
        )
        Spacer(Modifier.height(12.dp))
        
        AnimatedVisibility(
            visible = uiState.selectedWorkShiftToDrop != null && uiState.selectedRestDayToWork != null,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut()
        ) {
            Column {
                if (uiState.isSearching) {
                    LoadingPlaceholder()
                } else {
                    val candidates = uiState.candidates ?: emptyList()
                    if (candidates.isEmpty()) {
                        EmptyResultsPlaceholder("Nessun collega può scambiare questa combinazione di riposi.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(bottom = 32.dp, top = 4.dp, start = 4.dp, end = 4.dp)
                        ) {
                            items(candidates, key = { "${it.employeeId}_${it.theirShiftOnMyRestDate.id}" }) { candidate: RestSwapCandidate ->
                                RestSwapCandidateCard(candidate = candidate)
                            }
                        }
                    }
                }
            }
        }
        
        if (uiState.selectedWorkShiftToDrop == null || uiState.selectedRestDayToWork == null) {
            EmptyResultsPlaceholder("Configura entrambe le date per attivare la ricerca")
        }
    }
}

@Composable
private fun EmptyResultsPlaceholder(text: String) {
    Box(modifier = Modifier.fillMaxWidth().height(150.dp), Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun LoadingPlaceholder() {
    Box(modifier = Modifier.fillMaxWidth().height(150.dp), Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun ShiftSelectableCard(
    shift: Shift,
    isSelected: Boolean,
    onClick: () -> Unit,
    isRest: Boolean = false
) {
    val dateStr = try {
        shift.date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN))
    } catch (e: Exception) {
        shift.date.toString()
    }
    
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        label = "card_color"
    )
    
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        label = "content_color"
    )

    Card(
        modifier = Modifier
            .width(115.dp)
            .height(100.dp)
            .clickable { onClick() }
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp).fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = dateStr.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelMedium,
                color = (if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface).copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )
            
            val timeText = if (isRest) "Riposo" else if (shift.startTime != null && shift.endTime != null) "${shift.startTime} - ${shift.endTime}" else ""
            Text(
                text = timeText,
                style = MaterialTheme.typography.titleMedium,
                color = if (isRest && !isSelected) MaterialTheme.colorScheme.primary else if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                lineHeight = 18.sp
            )
            
            val role = shift.role?.takeIf { it.isNotBlank() }
            if (role != null) {
                Text(
                    text = role,
                    style = MaterialTheme.typography.labelSmall,
                    color = (if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}






