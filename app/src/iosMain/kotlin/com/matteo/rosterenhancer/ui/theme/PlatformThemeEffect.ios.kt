package com.matteo.rosterenhancer.ui.theme

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformThemeEffect(darkTheme: Boolean) {
    // Su iOS la status bar viene gestita automaticamente da SwiftUI/UIKit.
    // Non serve alcuna personalizzazione Kotlin-side.
}
