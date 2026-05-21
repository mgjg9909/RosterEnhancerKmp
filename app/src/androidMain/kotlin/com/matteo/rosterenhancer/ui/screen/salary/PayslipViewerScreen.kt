package com.matteo.rosterenhancer.ui.screen.salary

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.matteo.rosterenhancer.util.getMonthName
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayslipViewerScreen(
    payslipId: Long,
    onBack: () -> Unit,
    viewModel: PayslipViewModel = koinViewModel()
) {
    val payslips by viewModel.payslips.collectAsState()
    val payslip = payslips.find { it.id == payslipId }
    
    // Lista delle bitmap (pagine PDF o singola immagine)
    val pages = remember(payslip) {
        val list = mutableListOf<Bitmap>()
        payslip?.filePath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                if (path.lowercase().endsWith(".pdf")) {
                    try {
                        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                        val renderer = PdfRenderer(pfd)
                        for (i in 0 until renderer.pageCount) {
                            val page = renderer.openPage(i)
                            // Aumentiamo la scala per una migliore leggibilità
                            val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            list.add(bitmap)
                            page.close()
                        }
                        renderer.close()
                        pfd.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    // È un'immagine
                    try {
                        BitmapFactory.decodeFile(path)?.let { list.add(it) }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        list
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            text = payslip?.let { "${getMonthName(it.month)} ${it.year}" } ?: "Visualizzatore", 
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        payslip?.fileName?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = Color(0xFF1A1A1A) // Grigio molto scuro quasi nero
    ) { padding ->
        if (payslip == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Cedolino non trovato", color = Color.White)
            }
        } else if (payslip.filePath == null || !File(payslip.filePath).exists()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("File non disponibile", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Il file originale non è stato trovato nella memoria interna.", 
                        color = Color.White.copy(alpha = 0.6f), 
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp))
                }
            }
        } else if (pages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(pages) { bitmap ->
                    Card(
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Pagina Documento",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.FillWidth
                        )
                    }
                }
                
                item {
                    Spacer(Modifier.height(32.dp))
                    Text(
                        "Fine del documento", 
                        style = MaterialTheme.typography.labelMedium, 
                        color = Color.White.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}




