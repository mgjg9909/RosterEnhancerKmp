package com.matteo.rosterenhancer.ui.screen.profile

import com.matteo.rosterenhancer.util.format

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onNavigateToPayslips: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel()
) {
    val profile by viewModel.profile.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profilo Contrattuale", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Sezione Informazione
            Text(
                text = "Configura i tuoi dati per avere calcoli precisi dello stipendio (CCNL GPG 2024).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Smart Calibration & Cedolini
            ProfileSection(title = "Smart Calibration", icon = Icons.Default.History) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Migliora la precisione caricando i tuoi cedolini PDF reali.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onNavigateToPayslips,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Gestisci Cedolini & Insight")
                    }
                }
            }

            // Livello CCNL
            ProfileSection(title = "Livello CCNL", icon = Icons.Default.Badge) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (1..7).forEach { level ->
                        FilterChip(
                            selected = profile.level == level,
                            onClick = { viewModel.updateLevel(level) },
                            label = { Text("Liv. $level") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Scatti di Anzianità
            ProfileSection(title = "Scatti di Anzianità", icon = Icons.Default.History) {
                Column {
                    Text(
                        text = "${profile.gpgSenioritySteps} scatti maturati",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = profile.gpgSenioritySteps.toFloat(),
                        onValueChange = { viewModel.updateSenioritySteps(it.toInt()) },
                        valueRange = 0f..6f,
                        steps = 5,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // Part-time
            ProfileSection(title = "Percentuale Part-Time", icon = Icons.Default.Work) {
                Column {
                    Text(
                        text = "${profile.partTimePercentage.toInt()}%",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = profile.partTimePercentage.toFloat(),
                        onValueChange = { viewModel.updatePartTime(it.toDouble()) },
                        valueRange = 50f..100f,
                        steps = 9, // Incrementi di 5%
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // Tassazione
            ProfileSection(title = "Aliquota Fiscale Stimata", icon = Icons.Default.Percent) {
                Column {
                    Text(
                        text = "${(profile.taxRate * 100).toInt()}% (IRPEF + Addizionali)",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = profile.taxRate.toFloat() * 100f,
                        onValueChange = { viewModel.updateTaxRate(it.toDouble() / 100.0) },
                        valueRange = 10f..40f,
                        steps = 29,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // Indennità Aeroportuale
            ProfileSection(title = "Indennità Aeroportuale (Bologna)", icon = Icons.Default.AirplanemodeActive) {
                Column {
                    Text(
                        text = "€ ${"%.2f".format(profile.airportIndemnity)} / turno",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Slider(
                        value = profile.airportIndemnity.toFloat(),
                        onValueChange = { viewModel.updateAirportIndemnity(it.toDouble()) },
                        valueRange = 0f..10f,
                        steps = 100, // Incrementi di 0.10€
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = "Valore combinato (Giornaliera + Turno + Sosta) dedotto dal tuo cedolino.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProfileSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}





