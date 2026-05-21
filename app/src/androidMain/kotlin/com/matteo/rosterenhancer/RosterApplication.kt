package com.matteo.rosterenhancer

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.work.*
import com.matteo.rosterenhancer.util.CrashLogger
import java.util.concurrent.TimeUnit

import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import com.matteo.rosterenhancer.di.appModule
import com.matteo.rosterenhancer.di.viewModelModule
import com.matteo.rosterenhancer.di.workerModule

class RosterApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .build()

    override fun onCreate() {
        // Inizializza Koin per la Dependency Injection
        startKoin {
            androidLogger()
            androidContext(this@RosterApplication)
            workManagerFactory()
            modules(appModule, viewModelModule, workerModule)
        }
        // ⚠️ Prima di qualsiasi altra cosa, installa il crash logger per catturare i crash futuri
        CrashLogger.install(this)

        // Legge l'eventuale crash precedente salvato su file dal CrashLogger
        val lastCrash = CrashLogger.getLastCrash(this)
        // Se esiste un crash salvato, lo mostra all'utente tramite un Toast
        if (lastCrash != null) {
            // Usa Handler sul main thread per mostrare il Toast dopo 500ms (Hilt potrebbe non essere ancora pronto)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                // Divide il testo del crash in righe per estrarre solo la riga più significativa
                val summary = lastCrash.lines()
                    // Cerca la prima riga che descrive l'eccezione, il messaggio o il punto del codice
                    .firstOrNull { it.startsWith("Exception") || it.startsWith("Message") || it.contains("at ") }
                    // Se non trova nulla di specifico, usa i primi 120 caratteri del crash grezzo
                    ?: lastCrash.take(120)
                // Mostra un Toast (messaggio temporaneo a schermo) con un riassunto del crash
                android.widget.Toast.makeText(
                    this,                                      // Contesto dell'applicazione
                    "💥 CRASH PRECEDENTE:\n$summary",         // Testo del messaggio con emoji e riassunto
                    android.widget.Toast.LENGTH_LONG           // Durata lunga del Toast (~3.5 secondi)
                ).show()
            }, 500) // Ritardo di 500 millisecondi prima di mostrare il Toast
            // Scrive anche il crash completo nel log di Android Studio per analisi più approfondita
            android.util.Log.e("CRASH_LOG", "======= ULTIMO CRASH =======\n$lastCrash")
        }

        // Avvia il thread "watchdog" che monitora se il main thread si blocca (ANR)
        installAnrWatchdog()

        // Chiama il metodo onCreate() della classe padre (Application), necessario per inizializzare Android
        super.onCreate()

        // Tenta di schedulare la sincronizzazione periodica con il cloud
        try {
            scheduleCloudSync()
        } catch (e: Exception) {
            // Se la schedulazione fallisce, logga l'errore senza far crashare l'app
            android.util.Log.e("RosterApplication", "Errore schedulazione sync cloud", e)
        }
    }

    /**
     * Watchdog thread che monitora il main thread.
     * Se il main thread non risponde entro 5 secondi, scrive lo stack trace su file.
     * Gli ANR non triggerano UncaughtExceptionHandler, quindi questo è l'unico modo
     * per catturarli senza root.
     */
    private fun installAnrWatchdog() {
        // Crea un Handler collegato al main thread per inviargli messaggi di "ping"
        val mainHandler = Handler(Looper.getMainLooper())
        // Crea un nuovo thread in background dedicato al monitoraggio del main thread
        Thread({
            // Il watchdog gira in loop finché non viene interrotto dall'esterno
            while (!Thread.currentThread().isInterrupted) {
                try {
                    // Flag che diventerà true solo se il main thread risponde al nostro ping
                    var responded = false
                    // Invia un piccolo task al main thread: se viene eseguito, il main thread è vivo
                    mainHandler.post { responded = true }
                    // Aspetta 5 secondi per dare tempo al main thread di eseguire il ping
                    Thread.sleep(5000)

                    // Se dopo 5 secondi il main thread non ha risposto, è bloccato (ANR)
                    if (!responded) {
                        // Recupera il riferimento al main thread di Android
                        val mainThread = Looper.getMainLooper().thread
                        // Cattura l'elenco delle istruzioni attualmente in esecuzione sul main thread
                        val stackTrace = mainThread.stackTrace
                        // Costruisce il testo del report usando uno StringBuilder per efficienza
                        val sb = StringBuilder()
                        // Aggiunge l'intestazione del report ANR
                        sb.appendLine("========== ANR DETECTED ==========")
                        // Descrive il problema rilevato
                        sb.appendLine("Il main thread è bloccato da >5 secondi!")
                        // Indica lo stato attuale del thread (BLOCKED, WAITING, ecc.)
                        sb.appendLine("Main thread state: ${mainThread.state}")
                        // Aggiunge una riga vuota per leggibilità
                        sb.appendLine()
                        // Inizia la sezione con lo stack trace del main thread
                        sb.appendLine("--- MAIN THREAD STACK TRACE ---")
                        // Itera ogni elemento dello stack trace e lo aggiunge al report
                        for (element in stackTrace) {
                            sb.appendLine("  at $element")
                        }
                        // Aggiunge una riga vuota per separare le sezioni
                        sb.appendLine()
                        // Inizia la sezione con tutti i thread attivi nell'app per avere più contesto
                        sb.appendLine("--- ALL THREADS ---")
                        // Itera tutti i thread attivi e i loro stack trace
                        for ((t, stack) in Thread.getAllStackTraces()) {
                            // Aggiunge il nome e lo stato di ogni thread
                            sb.appendLine("Thread '${t.name}' [${t.state}]:")
                            // Aggiunge ogni istruzione nello stack trace di quel thread
                            for (el in stack) sb.appendLine("  at $el")
                        }
                        // Chiude il report con una riga separatrice
                        sb.appendLine("========== END ANR REPORT ==========")

                        // Converte lo StringBuilder in una stringa finale
                        val report = sb.toString()
                        // Scrive il report nel log di Android Studio con livello ERROR
                        android.util.Log.e("ANR_WATCHDOG", report)

                        // Salva il report su file così è leggibile al prossimo avvio dell'app
                        try {
                            // Crea (o accede) alla cartella "crashes" nella memoria interna dell'app
                            val dir = java.io.File(filesDir, "crashes")
                            // Crea la cartella e tutte le cartelle intermedie se non esistono
                            dir.mkdirs()
                            // Scrive il report nel file "last_crash.txt", sovrascrivendo quello precedente
                            java.io.File(dir, "last_crash.txt").writeText(report)
                        } catch (e: Exception) {
                            // Se la scrittura su file fallisce, lo logga senza far crashare il watchdog
                            android.util.Log.e("ANR_WATCHDOG", "Cannot write ANR file", e)
                        }
                        // Non esce dal loop: continua a monitorare anche dopo un ANR rilevato
                    }
                } catch (e: InterruptedException) {
                    // Se il thread viene interrotto (es. all'arresto dell'app), esce dal loop pulitamente
                    break
                }
            }
        // Assegna un nome riconoscibile al thread watchdog (visibile nei log e nei debugger)
        }, "ANR-Watchdog").apply {
            // Imposta il thread come "daemon": non blocca la chiusura dell'app quando tutte le Activity terminano
            isDaemon = true
            // Assegna la priorità minima: il watchdog non deve competere con il lavoro principale
            priority = Thread.MIN_PRIORITY
            // Avvia il thread watchdog
            start()
        }
    }

    // Funzione privata che configura e schedula la sincronizzazione periodica con il cloud
    private fun scheduleCloudSync() {
        // Crea i vincoli da rispettare prima di eseguire il task (es. rete disponibile)
        val constraints = Constraints.Builder()
            // Richiede che ci sia una connessione di rete attiva prima di eseguire il sync
            .setRequiredNetworkType(NetworkType.CONNECTED)
            // Costruisce l'oggetto Constraints finale
            .build()

        // Crea una richiesta di lavoro periodico: esegue il CloudSyncWorker ogni 12 ore
        val syncRequest = PeriodicWorkRequestBuilder<com.matteo.rosterenhancer.worker.CloudSyncWorker>(12, TimeUnit.HOURS)
            // Associa i vincoli alla richiesta (la rete deve essere disponibile)
            .setConstraints(constraints)
            // Costruisce l'oggetto PeriodicWorkRequest finale
            .build()

        // Aggiunge il task alla coda di WorkManager in modo che sia unico (non duplicato)
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "CloudSyncWork",                      // Nome univoco del task: evita duplicati
            ExistingPeriodicWorkPolicy.KEEP,       // Se esiste già un task con questo nome, lo mantiene senza rimpiazzarlo
            syncRequest                            // La richiesta di lavoro periodico da schedulare
        )
    }
}



