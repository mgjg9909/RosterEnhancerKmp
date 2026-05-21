package com.matteo.rosterenhancer.domain.payslip

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

import java.io.File
import java.io.FileOutputStream

import javax.inject.Singleton

@Singleton
class PayslipOcrManager constructor(
    private val context: Context
) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Converte un PDF in una lista di stringhe (una per pagina)
     */
    suspend fun analyzePdf(uri: Uri): List<String> = withContext(Dispatchers.IO) {
        val result = mutableListOf<String>()
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext emptyList()
        
        try {
            val renderer = PdfRenderer(pfd)
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                
                // Creazione bitmap della pagina (alta qualità per OCR)
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                
                // Analisi OCR della pagina
                val text = analyzeBitmap(bitmap)
                result.add(text)
                
                page.close()
            }
            renderer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            pfd.close()
        }
        
        result
    }

    suspend fun analyzeImage(uri: Uri): String = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(context, uri)
        val result = recognizer.process(image).await()
        result.text
    }

    /** Restituisce le pagine di un PDF come lista di Bitmap (per Gemini Vision) */
    suspend fun getPdfBitmaps(uri: Uri): List<Bitmap> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Bitmap>()
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext emptyList()
        try {
            val renderer = PdfRenderer(pfd)
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                // Alta risoluzione per Gemini Vision
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                result.add(bitmap)
                page.close()
            }
            renderer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            pfd.close()
        }
        result
    }

    /** Restituisce l'immagine da un Uri come Bitmap (per Gemini Vision) */
    suspend fun getImageBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun analyzeBitmap(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).await()
        return result.text
    }
}




