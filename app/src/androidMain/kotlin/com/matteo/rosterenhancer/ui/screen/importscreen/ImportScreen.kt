package com.matteo.rosterenhancer.ui.screen.importscreen

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.blur
import org.koin.compose.viewmodel.koinViewModel
import com.matteo.rosterenhancer.domain.parser.XlsxParser
import com.matteo.rosterenhancer.ui.theme.SuccessGreen
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onImportSuccess: () -> Unit,
    onBack: () -> Unit,
    viewModel: ImportViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state) {
        if (state is ImportState.Success) {
            kotlinx.coroutines.delay(2000)
            onImportSuccess()
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val fileName = getFileName(context, it)
            val stream = context.contentResolver.openInputStream(it)
            if (stream != null) {
                viewModel.parseFile(stream, fileName)
            }
        }
    }

    Scaffold(
        topBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                ) {}
                TopAppBar(
                    title = { Text("Importa Roster") },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Indietro")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val s = state) {
                is ImportState.Idle -> {
                    ImportPickerContent(onPickFile = { fileLauncher.launch("*/*") })
                }
                is ImportState.Loading -> {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text("Analisi del file in corso...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                is ImportState.Preview -> {
                    ImportPreviewContent(
                        result = s.result,
                        fileName = s.fileName,
                        onConfirm = viewModel::confirmImport,
                        onCancel = viewModel::reset
                    )
                }
                is ImportState.Success -> {
                    ImportSuccessContent(s)
                }
                is ImportState.Error -> {
                    ImportErrorContent(
                        message = s.message,
                        onRetry = viewModel::reset
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportPickerContent(onPickFile: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.TableChart,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Seleziona il file Excel",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground)
            Text(
                "Scarica il roster dall'intranet aziendale (.xlsx)\ne importalo qui",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Button(
            onClick = onPickFile,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(Icons.Default.FileUpload, contentDescription = null,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Scegli file .xlsx", style = MaterialTheme.typography.titleMedium)
        }

        // Istruzioni
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Come fare", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface)
                listOf(
                    "1. Apri l'intranet aziendale",
                    "2. Scarica il roster del mese in formato Excel",
                    "3. Torna qui e seleziona il file"
                ).forEach { step ->
                    Text(step, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ImportPreviewContent(
    result: XlsxParser.ParseResult,
    fileName: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val monthName = try {
        Month.of(result.month).getDisplayName(TextStyle.FULL_STANDALONE, Locale.ITALIAN)
            .replaceFirstChar { it.uppercase() }
    } catch (_: Exception) { "Mese ${result.month}" }

    // Sommario
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null,
                    tint = SuccessGreen, modifier = Modifier.size(20.dp))
                Text("File analizzato con successo",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            InfoRow("File", fileName)
            InfoRow("Mese", "$monthName ${result.year}")
            InfoRow("Dipendenti trovati", "${result.employees.size}")
            InfoRow("Turni trovati", "${result.shifts.size}")
        }
    }

    // Anteprima dipendenti (primi 5)
    Text("Anteprima dipendenti",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)

    Card(shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column {
            result.employees.take(5).forEachIndexed { idx, emp ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(emp.fullName, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f))
                    Text("#${emp.id}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (idx < minOf(4, result.employees.size - 1)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
            }
            if (result.employees.size > 5) {
                Text(
                    "... e altri ${result.employees.size - 5} dipendenti",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }

    // Bottoni
    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
        ) {
            Text("Annulla")
        }
        Button(
            onClick = onConfirm,
            modifier = Modifier
                .weight(2f)
                .height(52.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null,
                modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Importa")
        }
    }
}

@Composable
private fun ImportSuccessContent(state: ImportState.Success) {
    val monthName = try {
        Month.of(state.month).getDisplayName(TextStyle.FULL_STANDALONE, Locale.ITALIAN)
            .replaceFirstChar { it.uppercase() }
    } catch (_: Exception) { "Mese ${state.month}" }

    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null,
            tint = SuccessGreen, modifier = Modifier.size(72.dp))
        Text("Importazione completata!",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground)
        Text("$monthName ${state.year} — ${state.shiftCount} turni salvati",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp
        )
        Text("Ritorno alla dashboard...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ImportErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Icon(Icons.Default.ErrorOutline, contentDescription = null,
            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
        Text("Errore nell'importazione",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground)
        Card(shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f))) {
            Text(message, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp))
        }
        // Debug info: aiuta a capire la struttura del file
        Text("Info diagnostica (utile per il debug):",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Text(
                text = message.substringAfter("ERROR:").ifBlank { message },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        }
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Riprova con un altro file")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium)
    }
}

private fun getFileName(context: Context, uri: android.net.Uri): String {
    var name = "roster.xlsx"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) {
            name = cursor.getString(nameIndex)
        }
    }
    return name
}





