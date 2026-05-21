package com.matteo.rosterenhancer.util

import com.matteo.rosterenhancer.util.format

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sistema di crash logging che:
 * - Cattura eccezioni non gestite (inclusi crash Hilt pre-Activity)
 * - Legge le ultime righe di logcat al momento del crash (cattura anche ANR)
 * - Salva il report su file in filesDir/crashes/last_crash.txt
 * - Permette di leggere il crash al prossimo avvio e mostrarlo in UI
 */
object CrashLogger {

    private const val CRASH_FILE = "last_crash.txt"
    private const val CRASH_DIR  = "crashes"

    /** Deve essere chiamato il prima possibile in Application.onCreate() */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashDir = File(appContext.filesDir, CRASH_DIR)
                crashDir.mkdirs()
                val crashFile = File(crashDir, CRASH_FILE)

                PrintWriter(crashFile).use { pw ->
                    val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ITALIAN).format(Date())
                    pw.println("========== CRASH REPORT ==========")
                    pw.println("Timestamp : $ts")
                    pw.println("Thread    : ${thread.name}")
                    pw.println("Exception : ${throwable.javaClass.canonicalName}")
                    pw.println("Message   : ${throwable.message}")
                    pw.println()
                    pw.println("--- STACK TRACE ---")
                    throwable.printStackTrace(pw)

                    // Causa radice
                    var cause = throwable.cause
                    var depth = 0
                    while (cause != null && depth < 5) {
                        pw.println()
                        pw.println("--- CAUSED BY (depth $depth) ---")
                        cause.printStackTrace(pw)
                        cause = cause.cause
                        depth++
                    }

                    // Logcat delle ultime 300 righe di errori — cattura anche ANR
                    try {
                        pw.println()
                        pw.println("--- LOGCAT (ultimi errori) ---")
                        val proc = Runtime.getRuntime().exec(
                            arrayOf("logcat", "-d", "-t", "300", "*:E", "AndroidRuntime:V", "FATAL:V")
                        )
                        proc.inputStream.bufferedReader().forEachLine { line -> pw.println(line) }
                        proc.destroy()
                    } catch (e: Exception) {
                        pw.println("(logcat non disponibile: ${e.message})")
                    }

                    pw.println()
                    pw.println("========== END OF REPORT ==========")
                }

            } catch (e: Exception) {
                android.util.Log.e("CrashLogger", "Impossibile salvare crash log", e)
            }

            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /** Restituisce il contenuto dell'ultimo crash, o null se non esiste. */
    fun getLastCrash(context: Context): String? {
        val f = File(context.filesDir, "$CRASH_DIR/$CRASH_FILE")
        return if (f.exists() && f.length() > 0) f.readText() else null
    }

    /** Elimina il file di crash dopo averlo letto. */
    fun clearLastCrash(context: Context) {
        File(context.filesDir, "$CRASH_DIR/$CRASH_FILE").delete()
    }

    /** Restituisce il path assoluto del file di crash (per mostrarlo all'utente). */
    fun getCrashFilePath(context: Context): String =
        File(context.filesDir, "$CRASH_DIR/$CRASH_FILE").absolutePath
}




