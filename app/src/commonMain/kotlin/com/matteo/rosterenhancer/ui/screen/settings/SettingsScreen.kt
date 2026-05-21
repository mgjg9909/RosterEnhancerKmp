package com.matteo.rosterenhancer.ui.screen.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import com.matteo.rosterenhancer.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import org.koin.compose.viewmodel.koinViewModel
import com.matteo.rosterenhancer.data.local.entity.MonthRosterEntity
import com.matteo.rosterenhancer.util.DataStoreManager
import com.matteo.rosterenhancer.util.getMonthName
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.matteo.rosterenhancer.ui.component.bouncyClick
import com.matteo.rosterenhancer.ui.component.HapticStyle
import com.matteo.rosterenhancer.ui.components.GlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<MonthRosterEntity?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // Overlay eliminazione premium
    if (showDeleteDialog != null) {
        val roster = showDeleteDialog!!
        val haptic = LocalHapticFeedback.current
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(onClick = { showDeleteDialog = null }),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clickable(enabled = false) {},
                cornerRadius = 32.dp,
                glassAlpha = 0.9f
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    
                    val monthName = getMonthName(roster.month)

                    Text(
                        "Eliminare il roster?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        "Vuoi eliminare il roster di $monthName ${roster.year}?",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.deleteRoster(roster)
                            showDeleteDialog = null
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Elimina definitivamente", fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = { showDeleteDialog = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Annulla", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Impostazioni", 
                        style = MaterialTheme.typography.headlineMedium, 
                        fontWeight = FontWeight.Black
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            com.matteo.rosterenhancer.ui.components.MeshGradientBackground()
            
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SectionTitle("Profilo Utente")
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .graphicsLayer(clip = true, shape = RoundedCornerShape(28.dp)),
                        cornerRadius = 28.dp
                    ) {
                        Column(modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Identificativo Aziendale", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = state.newMatricola,
                                    onValueChange = viewModel::onMatricolaChanged,
                                    label = { Text("Matricola") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                    )
                                )
                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.saveMatricola()
                                    },
                                    enabled = state.newMatricola != state.currentMatricola,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.height(56.dp).bouncyClick { viewModel.saveMatricola() }
                                ) {
                                    Text("Salva", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                item {
                    SectionTitle("Sincronizzazione Portale")
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .graphicsLayer(clip = true, shape = RoundedCornerShape(28.dp)),
                        cornerRadius = 28.dp
                    ) {
                        Column(modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.Cloud, contentDescription = null, tint = Color(0xFF03A9F4))
                                Text("Airport Portal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            
                            OutlinedTextField(
                                value = state.cloudUser,
                                onValueChange = viewModel::onCloudUserChanged,
                                label = { Text("Username") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            var passwordVisible by remember { mutableStateOf(false) }
                            OutlinedTextField(
                                value = state.cloudPass,
                                onValueChange = viewModel::onCloudPassChanged,
                                label = { Text("Password") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.saveCloudCredentials()
                                    },
                                    modifier = Modifier.weight(1f).height(56.dp).bouncyClick { viewModel.saveCloudCredentials() },
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Salva")
                                }

                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.syncNow()
                                    },
                                    modifier = Modifier.weight(1f).height(56.dp).bouncyClick { viewModel.syncNow() },
                                    enabled = !state.isSyncing,
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    if (state.isSyncing) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                    } else {
                                        Icon(Icons.Default.Sync, contentDescription = null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Sincronizza")
                                    }
                                }
                            }

                            state.syncStatus?.let { status ->
                                Surface(
                                    color = if (status.startsWith("Errore")) MaterialTheme.colorScheme.error.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = status,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (status.startsWith("Errore")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    SectionTitle("Intelligenza Artificiale")
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .graphicsLayer(clip = true, shape = RoundedCornerShape(28.dp)),
                        cornerRadius = 28.dp
                    ) {
                        Column(modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF9C27B0))
                                Text("Gemini Vision", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                                
                            var isKeyVisible by remember { mutableStateOf(false) }
                            OutlinedTextField(
                                value = state.geminiApiKey,
                                onValueChange = viewModel::onGeminiApiKeyChanged,
                                label = { Text("Gemini API Key") },
                                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                visualTransformation = if (isKeyVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                        Icon(if (isKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.saveGeminiApiKey()
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp).bouncyClick { viewModel.saveGeminiApiKey() },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Verifica e Salva")
                            }
                        }
                    }
                }

                item {
                    SectionTitle("CCNL Guardie Giurate")
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .graphicsLayer(clip = true, shape = RoundedCornerShape(28.dp)),
                        cornerRadius = 28.dp
                    ) {
                        Column(modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Outlined.Gavel, contentDescription = null, tint = Color(0xFFFF9800))
                                Text("Parametri Retributivi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }

                            // Selezione Livello
                            Column {
                                Text("Livello Contrattuale", 
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(7, 6, 5, 4, 3, 2, 1).forEach { level ->
                                        val isSelected = state.gpgLevel == level
                                        Surface(
                                            onClick = { 
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewModel.setGpgLevel(level) 
                                            },
                                            modifier = Modifier.weight(1f).height(44.dp).bouncyClick { viewModel.setGpgLevel(level) },
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                            border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(level.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                            }
                                        }
                                    }
                                }
                            }

                            // Scatti di Anzianità
                            Column {
                                Text("Scatti di Anzianità", 
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f))
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    (0..6).forEach { steps ->
                                        val isSelected = state.gpgSenioritySteps == steps
                                        Surface(
                                            onClick = { 
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewModel.setGpgSenioritySteps(steps) 
                                            },
                                            modifier = Modifier.weight(1f).height(44.dp).bouncyClick { viewModel.setGpgSenioritySteps(steps) },
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                            border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(steps.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface)
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = state.gpgPartTime,
                                    onValueChange = viewModel::onGpgPartTimeChanged,
                                    label = { Text("Part-Time %") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = state.gpgTaxRate,
                                    onValueChange = viewModel::onGpgTaxRateChanged,
                                    label = { Text("Tassazione %") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                item {
                    SectionTitle("Preferenze App")
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .graphicsLayer(clip = true, shape = RoundedCornerShape(28.dp)),
                        cornerRadius = 28.dp
                    ) {
                        Column(modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            
                            // Tema Scuro
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (state.useDarkTheme) Icons.Outlined.DarkMode else Icons.Outlined.LightMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text("Modalità Scura", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                                Switch(
                                    checked = state.useDarkTheme, 
                                    onCheckedChange = { 
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.toggleTheme(it) 
                                    }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                            // Ore Settimanali
                            Column {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text("Ore Contrattuali", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    com.matteo.rosterenhancer.util.DataStoreManager.MONTHLY_HOURS_OPTIONS.forEach { hours ->
                                        val isSelected = state.monthlyHoursTarget == hours
                                        Surface(
                                            onClick = { 
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewModel.setMonthlyHours(hours) 
                                            },
                                            modifier = Modifier.weight(1f).height(44.dp).bouncyClick { viewModel.setMonthlyHours(hours) },
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                            border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("${hours}h", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    SectionTitle("Identità Lavorativa")
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .graphicsLayer(clip = true, shape = RoundedCornerShape(28.dp)),
                        cornerRadius = 28.dp
                    ) {
                        Column(modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            
                            // Selezione Genere
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Face, contentDescription = null, tint = Color(0xFFE91E63))
                                    Text("Genere", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("M" to "Uomo", "F" to "Donna").forEach { (code, label) ->
                                        val isSelected = state.userGender == code
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { 
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewModel.setGender(code) 
                                            },
                                            label = { Text(label) },
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                            // Switch Supervisore & FAS
                            SettingsToggle(
                                label = "Sono un Supervisore",
                                subLabel = "Abilita funzioni extra",
                                icon = Icons.Outlined.Shield,
                                checked = state.isSupervisor,
                                onCheckedChange = { 
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.setSupervisor(it) 
                                }
                            )

                            SettingsToggle(
                                label = "Sono un FAS",
                                subLabel = "Flight Airport Security",
                                icon = Icons.Outlined.FlightTakeoff,
                                checked = state.isFas,
                                onCheckedChange = { 
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.setFas(it) 
                                }
                            )
                        }
                    }
                }

                // Sezione Archivio Roster
                item { SectionTitle("Archivio Roster Importati") }

                if (state.rosters.isEmpty()) {
                    item {
                        com.matteo.rosterenhancer.ui.components.GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            cornerRadius = 20.dp
                        ) {
                            Text("Nessun roster in memoria.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp),
                                textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    items(
                        items = state.rosters,
                        key = { it.year * 100 + it.month }
                    ) { roster ->
                        Box(modifier = Modifier.graphicsLayer(clip = true, shape = RoundedCornerShape(20.dp))) {
                            RosterArchiveItem(
                                roster = roster,
                                onDelete = { showDeleteDialog = roster }
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(112.dp)) } // Spazio per la Bottom Nav
            }
        }
    }
}

@Composable
private fun SettingsToggle(
    label: String,
    subLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Column {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(subLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 20.dp, bottom = 8.dp, top = 12.dp)
    )
}


@Composable
private fun RosterArchiveItem(
    roster: MonthRosterEntity,
    onDelete: () -> Unit
) {
    val monthName = getMonthName(roster.month)

    com.matteo.rosterenhancer.ui.components.GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        cornerRadius = 20.dp,
        glassAlpha = 0.3f
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column {
                    Text("$monthName ${roster.year}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold)
                    Text("${roster.employeeCount} dipendenti", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Elimina", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            }
        }
    }
}






