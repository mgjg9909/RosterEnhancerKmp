package com.matteo.rosterenhancer.ui.screen.salary

import com.matteo.rosterenhancer.util.format

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.matteo.rosterenhancer.data.local.entity.LearningLogEntity
import com.matteo.rosterenhancer.data.local.entity.PayslipEntity
import java.time.format.DateTimeFormatter
import com.matteo.rosterenhancer.util.getMonthName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayslipsHistoryScreen(
    onBack: () -> Unit,
    onViewPayslip: (Long) -> Unit,
    viewModel: PayslipViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val payslips by viewModel.payslips.collectAsState()
    val logs by viewModel.learningLogs.collectAsState()
    
    var selectedTab by remember { mutableIntStateOf(0) }
    
    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { viewModel.processPayslip(it, isPdf = true) } }
    )
    
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> uri?.let { viewModel.processPayslip(it, isPdf = false) } }
    )

    var showUploadDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cedolini & Insight", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showUploadDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Aggiungi Cedolino")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("Archivio", modifier = Modifier.padding(16.dp))
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("Insight", modifier = Modifier.padding(16.dp))
                }
            }

            if (selectedTab == 0) {
                PayslipList(payslips, onViewPayslip, onDeleteClick = { viewModel.deletePayslip(it) })
            } else {
                LearningLogsList(logs, onDeleteLog = { viewModel.deleteLearningLog(it) })
            }
        }
    }

    // Risultato calibrazione (Dialog)
    uiState.lastResult?.let { result ->
        val usedGemini = result.data.readMethod == com.matteo.rosterenhancer.domain.payslip.ReadMethod.GEMINI
        AlertDialog(
            onDismissRequest = { viewModel.dismissResult() },
            title = { Text("Analisi Completata") },
            text = {
                Column {
                    // Indicatore metodo di lettura
                    val badgeColor = if (usedGemini) Color(0xFF1B5E20) else Color(0xFFE65100)
                    val badgeBackground = if (usedGemini) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                    val badgeLabel = if (usedGemini) "✨ Letto da Gemini AI" else "📷 Letto da OCR Locale"
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = badgeBackground,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = badgeLabel,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = badgeColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // Se Gemini ha fallito, mostra il motivo
                    if (!usedGemini && result.geminiFailReason != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "⚠️ Motivo fallimento AI: ${result.geminiFailReason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Ho letto il cedolino di ${result.data.month}/${result.data.year}.")
                    Text("Netto rilevato: €${"%.2f".format(result.data.netPay)}")
                    Spacer(Modifier.height(8.dp))
                    if (result.delta != 0.0) {
                        val color = if (result.delta > 0) Color(0xFF4CAF50) else Color(0xFFE91E63)
                        Text(
                            text = "Differenza rispetto alla stima: €${"%.2f".format(result.delta)}",
                            color = color,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (result.delta > 0) "Vuoi che impari da questo dato per le stime future?" 
                                   else "La stima era superiore al reale. Vuoi ricalibrare i parametri?",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(result.message ?: "Tutto pronto per l'archiviazione.")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.applyCalibration(result) }) {
                    Text("Calibra & Salva")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissResult() }) {
                    Text("Salva senza calibrare")
                }
            }
        )
    }

    uiState.error?.let { errorMessage ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("Errore di importazione", color = MaterialTheme.colorScheme.error) },
            text = { Text(errorMessage) },
            confirmButton = {
                Button(onClick = { viewModel.dismissError() }) {
                    Text("Ho capito")
                }
            }
        )
    }

    if (showUploadDialog) {
        AlertDialog(
            onDismissRequest = { showUploadDialog = false },
            title = { Text("Carica Cedolino") },
            text = { Text("Seleziona il formato del file:") },
            confirmButton = {
                Button(onClick = { 
                    showUploadDialog = false
                    pdfLauncher.launch(arrayOf("application/pdf")) 
                }) {
                    Icon(Icons.Default.PictureAsPdf, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Documento PDF")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { 
                    showUploadDialog = false
                    imageLauncher.launch("image/*") 
                }) {
                    Icon(Icons.Default.Image, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Immagine/Foto")
                }
            }
        )
    }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun PayslipList(
    payslips: List<PayslipEntity>, 
    onItemClick: (Long) -> Unit,
    onDeleteClick: (PayslipEntity) -> Unit
) {
    if (payslips.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nessun cedolino archiviato", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(payslips) { payslip ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    onClick = { onItemClick(payslip.id) }
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${getMonthName(payslip.month)} ${payslip.year}", fontWeight = FontWeight.Bold)
                            Text("Caricato il ${payslip.uploadDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("€ ${"%.2f".format(payslip.netPay)}", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            IconButton(
                                onClick = { onDeleteClick(payslip) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete, 
                                    contentDescription = "Elimina",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
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
fun LearningLogsList(logs: List<LearningLogEntity>, onDeleteLog: (LearningLogEntity) -> Unit) {
    if (logs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("L'app non ha ancora imparato nulla", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(logs) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoGraph, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Insight ${getMonthName(log.month)} ${log.year}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.weight(1f))
                            Text(log.timestamp.format(DateTimeFormatter.ofPattern("dd/MM HH:mm")), style = MaterialTheme.typography.labelSmall)
                            
                            IconButton(
                                onClick = { onDeleteLog(log) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete, 
                                    null, 
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(log.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}







