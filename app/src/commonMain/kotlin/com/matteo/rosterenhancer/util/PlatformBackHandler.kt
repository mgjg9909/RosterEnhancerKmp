package com.matteo.rosterenhancer.util

import androidx.compose.runtime.Composable

/**
 * Composable KMP per gestire il tasto "Indietro" hardware.
 * Su Android intercetta il back press.
 * Su iOS è un no-op (non esiste il tasto fisico Indietro su iPhone).
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
