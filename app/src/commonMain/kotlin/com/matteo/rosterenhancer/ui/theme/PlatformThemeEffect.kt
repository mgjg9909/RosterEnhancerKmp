package com.matteo.rosterenhancer.ui.theme

import androidx.compose.runtime.Composable

/**
 * Effetto platform-specific da applicare durante la composizione del tema.
 * Permette di impostare i colori della status bar e navigation bar
 * in modo diverso per piattaforma senza inquinare il codice comune.
 */
@Composable
expect fun PlatformThemeEffect(darkTheme: Boolean)
