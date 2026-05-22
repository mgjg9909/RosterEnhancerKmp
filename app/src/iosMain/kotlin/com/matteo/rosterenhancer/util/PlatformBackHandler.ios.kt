package com.matteo.rosterenhancer.util

import androidx.compose.runtime.Composable

/**
 * Su iOS non esiste il tasto hardware "Indietro".
 * Il back è gestito tramite i gesti di swipe nativi di iOS (già integrati nella navigazione).
 * Questa implementazione è quindi un no-op.
 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op su iOS: la navigazione "indietro" è gestita dai gesti nativi di iOS.
}
