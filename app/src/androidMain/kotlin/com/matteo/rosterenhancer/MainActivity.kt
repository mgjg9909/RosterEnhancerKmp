package com.matteo.rosterenhancer

// Dichiara il package di appartenenza di questa classe all'interno del progetto Android

// Importa ClipData, usato per creare un oggetto "testo" da copiare negli appunti
import android.content.ClipData
// Importa ClipboardManager, il servizio Android per gestire gli appunti di sistema
import android.content.ClipboardManager
// Importa Context, la classe base che fornisce accesso alle risorse e ai servizi Android
import android.content.Context
// Importa Bundle, il contenitore chiave-valore usato per passare dati tra Activity (es. stato salvato)
import android.os.Bundle
// Importa Toast, usato per mostrare brevi messaggi temporanei a schermo
import android.widget.Toast
// Importa ComponentActivity, la classe base moderna per le Activity che usano Jetpack Compose
import androidx.activity.ComponentActivity
// Importa setContent, la funzione che permette di definire l'interfaccia grafica con Compose
import androidx.activity.compose.setContent
// Importa enableEdgeToEdge, che fa estendere l'app fino ai bordi dello schermo (sotto status/nav bar)
import androidx.activity.enableEdgeToEdge
// Importa l'estensione per installare la Splash Screen nativa di Android 12+
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
// Importa lifecycleScope, uno scope di coroutine legato al ciclo di vita dell'Activity
import androidx.lifecycle.lifecycleScope
// Importa delay, una funzione sospendibile che mette in pausa l'esecuzione per un certo tempo
import kotlinx.coroutines.delay
// Importa launch, che avvia una nuova coroutine (esecuzione asincrona) nello scope indicato
import kotlinx.coroutines.launch
// Importa il modificatore background per impostare il colore di sfondo di un componente Compose
import androidx.compose.foundation.background
// Importa tutti i modificatori e i contenitori di layout di Compose (Box, Column, Row, ecc.)
import androidx.compose.foundation.layout.*
// Importa rememberScrollState, che memorizza lo stato di scroll di un contenitore
import androidx.compose.foundation.rememberScrollState
// Importa verticalScroll, modificatore che rende un componente scorribile verticalmente
import androidx.compose.foundation.verticalScroll
// Importa tutti i componenti Material 3 (Button, AlertDialog, Surface, ecc.)
import androidx.compose.material3.*
// Importa le API fondamentali di Compose per la gestione dello stato reattivo
import androidx.compose.runtime.*
// Importa Modifier, la classe base per applicare modificatori a qualsiasi componente Compose
import androidx.compose.ui.Modifier
// Importa FontFamily per scegliere il tipo di carattere (es. Monospace per i log)
import androidx.compose.ui.text.font.FontFamily
// Importa dp, l'unità di misura density-independent pixels usata per le dimensioni in Compose
import androidx.compose.ui.unit.dp
// Importa sp, l'unità di misura scale-independent pixels usata per le dimensioni del testo
import androidx.compose.ui.unit.sp
// Importa hiltViewModel, la funzione che recupera un ViewModel iniettato da Hilt nel contesto Compose
import org.koin.compose.viewmodel.koinViewModel
// Importa rememberNavController, che crea e memorizza il controller di navigazione tra schermate
import androidx.navigation.compose.rememberNavController
// Importa AppNavigation, il grafo di navigazione personalizzato dell'app
import com.matteo.rosterenhancer.ui.navigation.AppNavigation
// Importa Screen, la classe sealed che contiene tutte le route (percorsi) dell'app
import com.matteo.rosterenhancer.ui.navigation.Screen
// Importa DashboardViewModel, usato qui per leggere le preferenze globali (tema, onboarding)
import com.matteo.rosterenhancer.ui.screen.dashboard.DashboardViewModel
// Importa il tema Material 3 personalizzato dell'app (colori, tipografia, ecc.)
import com.matteo.rosterenhancer.ui.theme.RosterEnhancerTheme
// Importa CrashLogger, il nostro sistema per leggere e cancellare i crash salvati
import com.matteo.rosterenhancer.util.CrashLogger
// Importa l'annotazione di Hilt che abilita l'iniezione delle dipendenze in questa Activity


// Importa la nostra schermata di splash animata personalizzata (mostrata dopo quella nativa)
import com.matteo.rosterenhancer.ui.screen.splash.AnimatedSplashScreen

// Annotazione che dice a Hilt di generare il codice necessario per iniettare dipendenze in questa Activity

// Definisce MainActivity come l'unica Activity dell'app (single-activity pattern con Compose Navigation)
class MainActivity : ComponentActivity() {
    // Metodo chiamato da Android alla creazione dell'Activity; savedInstanceState contiene lo stato precedente (se esiste)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Installa la Splash Screen nativa di Android (gestisce la transizione dal launcher all'app)
        val splashScreen = installSplashScreen()
        // Chiama il metodo padre per completare l'inizializzazione standard dell'Activity
        super.onCreate(savedInstanceState)
        
        // Flag che, finché è true, mantiene visibile la splash screen nativa di Android
        var keepNativeSplash = true
        // Dice alla splash screen di restare visibile finché keepNativeSplash è true
        splashScreen.setKeepOnScreenCondition { keepNativeSplash }
        
        // Avvia una coroutine legata al ciclo di vita dell'Activity
        lifecycleScope.launch {
            // Nasconde la splash nativa immediatamente appena l'app è pronta
            keepNativeSplash = false
        }
        
        // Abilita la modalità edge-to-edge: il contenuto dell'app si estende dietro la status bar e la nav bar
        enableEdgeToEdge()

        // Legge l'eventuale crash precedente dal file salvato da CrashLogger (null se non ce ne sono)
        val lastCrash = CrashLogger.getLastCrash(this)

        // Imposta il contenuto dell'Activity come interfaccia Compose (sostituisce il tradizionale XML)
        setContent {
            // Ottiene il DashboardViewModel iniettato da Hilt, che contiene le preferenze globali dell'utente
            val viewModel: DashboardViewModel = koinViewModel()
            // Osserva la preferenza tema scuro/chiaro come stato reattivo; default: tema scuro
            val isDark by viewModel.useDarkTheme.collectAsState(initial = true)
            // Osserva se l'utente ha già completato l'onboarding; null finché il dato non è caricato
            val isOnboardingDone by viewModel.isOnboardingDone.collectAsState(initial = null)
            
            // Stato che controlla se mostrare la nostra splash screen animata personalizzata
            var showAnimatedSplash by remember { mutableStateOf(true) }

            // Stato che controlla la visibilità del dialog di crash; true se c'è un crash da mostrare
            var showCrashDialog by remember { mutableStateOf(lastCrash != null) }

            // Applica il tema Material 3 dell'app a tutto il contenuto; darkTheme segue la preferenza utente
            RosterEnhancerTheme(darkTheme = isDark) {
                // Se la splash animata è ancora visibile, la mostra a schermo intero
                if (showAnimatedSplash) {
                    // Mostra la schermata di splash animata; quando finisce, aggiorna lo stato
                    AnimatedSplashScreen(onAnimationFinished = {
                        // Nasconde la splash animata non appena l'animazione è completata
                        showAnimatedSplash = false
                    })
                } else {
                    // Box è un contenitore che sovrappone i suoi figli (utile per overlay come il dialog)
                    Box(
                        // Il Box occupa tutto lo spazio disponibile sullo schermo
                        Modifier.fillMaxSize()
                    ) {
                        // Aspetta che isOnboardingDone sia stato caricato (non null) prima di navigare
                        if (isOnboardingDone != null) {
                            // Decide la prima schermata: Main se l'onboarding è stato completato, altrimenti Onboarding
                            val startDestination = if (isOnboardingDone == true)
                                Screen.Main.route else Screen.Onboarding.route
                            // Crea e ricorda il controller di navigazione per gestire il backstack e le route
                            val navController = rememberNavController()
                            // Avvia il sistema di navigazione dell'app con la schermata di partenza calcolata
                            AppNavigation(
                                navController = navController,        // Il controller che gestisce la navigazione
                                startDestination = startDestination   // La schermata da cui partire
                            )
                        }
                        
                    }
                    // Mostra il dialog di crash solo se c'è un crash e showCrashDialog è true
                    if (showCrashDialog && lastCrash != null) {
                        // AlertDialog è un popup modale di Material 3 con titolo, testo e pulsanti
                        AlertDialog(
                            // Quando l'utente tocca fuori dal dialog, lo chiude e cancella il file di crash
                            onDismissRequest = {
                                showCrashDialog = false                             // Nasconde il dialog
                                CrashLogger.clearLastCrash(this@MainActivity)      // Cancella il file di crash
                            },
                            // Titolo del dialog: indica che è stato rilevato un crash
                            title = {
                                Text(
                                    "💥 Crash Rilevato",                        // Testo del titolo
                                    style = MaterialTheme.typography.titleLarge  // Stile tipografico grande
                                )
                            },
                            // Corpo del dialog: mostra il log del crash in un box scorrevole
                            text = {
                                // Column impila verticalmente i suoi figli
                                Column {
                                    // Breve introduzione che spiega cosa si sta vedendo
                                    Text(
                                        "L'app è crashata al run precedente. Ecco il log:",
                                        style = MaterialTheme.typography.bodyMedium  // Testo normale di dimensione media
                                    )
                                    // Spazio verticale di 8dp tra l'introduzione e il box del log
                                    Spacer(Modifier.height(8.dp))
                                    // Surface crea un contenitore con sfondo colorato e forma arrotondata
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,  // Sfondo grigio/neutro del tema
                                        shape = MaterialTheme.shapes.small,                // Angoli leggermente arrotondati
                                        modifier = Modifier
                                            .fillMaxWidth()          // Occupa tutta la larghezza disponibile
                                            .heightIn(max = 350.dp)  // Altezza massima 350dp (poi si scorre)
                                    ) {
                                        // Mostra il testo del crash in formato monospaciato (stile terminale)
                                        Text(
                                            text = lastCrash.take(3000),        // Limita a 3000 caratteri per non appesantire il layout
                                            fontFamily = FontFamily.Monospace,  // Carattere a spaziatura fissa, ideale per i log
                                            fontSize = 10.sp,                   // Testo piccolo per stare in poco spazio
                                            modifier = Modifier
                                                .padding(8.dp)                          // Margine interno di 8dp
                                                .verticalScroll(rememberScrollState()) // Rende il testo scorribile verticalmente
                                        )
                                    }
                                    // Spazio verticale di 8dp tra il log e il percorso del file
                                    Spacer(Modifier.height(8.dp))
                                    // Mostra il percorso del file di crash per permettere di aprirlo manualmente
                                    Text(
                                        "📁 File: ${CrashLogger.getCrashFilePath(this@MainActivity)}", // Percorso completo del file
                                        style = MaterialTheme.typography.labelSmall,                   // Testo piccolo come etichetta
                                        color = MaterialTheme.colorScheme.primary                      // Colorato col colore primario del tema
                                    )
                                }
                            },
                            // Sezione dei pulsanti di azione in fondo al dialog
                            confirmButton = {
                                // Row affianca i pulsanti orizzontalmente con uno spazio di 8dp tra loro
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Pulsante con bordo (non riempito) per copiare il log negli appunti
                                    OutlinedButton(onClick = {
                                        // Recupera il servizio clipboard di sistema tramite il Context
                                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        // Crea un oggetto ClipData con il testo del log, etichettato "Crash Log"
                                        val clip = ClipData.newPlainText("Crash Log", lastCrash)
                                        // Imposta il ClipData come contenuto attuale degli appunti
                                        clipboard.setPrimaryClip(clip)
                                        // Mostra un messaggio breve di conferma all'utente
                                        Toast.makeText(
                                            this@MainActivity,          // Contesto necessario per il Toast
                                            "📋 Log copiato negli appunti!", // Messaggio di conferma
                                            Toast.LENGTH_SHORT          // Durata breve (~1.5 secondi)
                                        ).show()
                                    }) {
                                        // Etichetta del pulsante di copia
                                        Text("📋 Copia Log")
                                    }

                                    // Pulsante principale (riempito) per chiudere il dialog e cancellare il crash
                                    Button(onClick = {
                                        // Nasconde il dialog
                                        showCrashDialog = false
                                        // Cancella il file di crash così non viene mostrato al prossimo avvio
                                        CrashLogger.clearLastCrash(this@MainActivity)
                                    }) {
                                        // Etichetta del pulsante di chiusura
                                        Text("Chiudi")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}



