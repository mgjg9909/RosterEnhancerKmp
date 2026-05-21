package com.matteo.rosterenhancer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary          = BluePrimary,
    onPrimary        = White,
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = BlueLight,
    secondary        = SuccessGreen,
    onSecondary      = Color(0xFF003319),
    secondaryContainer = Color(0xFF004D25),
    onSecondaryContainer = Color(0xFF9DFFC4),
    tertiary         = WarningYellow,
    onTertiary       = Color(0xFF3D2E00),
    background       = BackgroundDark,
    onBackground     = TextPrimary,
    surface          = SurfaceDark,
    onSurface        = TextPrimary,
    surfaceVariant   = SurfaceDark2,
    onSurfaceVariant = TextSecondary,
    outline          = OutlineDark,
    error            = ErrorRed,
    onError          = White
)

private val LightColorScheme = lightColorScheme(
    primary          = BlueDark,
    onPrimary        = White,
    primaryContainer = SurfaceLight2,
    onPrimaryContainer = BlueDark,
    secondary        = Color(0xFF1A7A4A),
    onSecondary      = White,
    background       = BackgroundLight,
    onBackground     = Color(0xFF1A202C),
    surface          = SurfaceLight,
    onSurface        = Color(0xFF1A202C),
    surfaceVariant   = SurfaceLight2,
    onSurfaceVariant = Color(0xFF4A5568),
    outline          = OutlineLight,
    error            = Color(0xFFDC2626),
    onError          = White
)

@Composable
fun RosterEnhancerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            
            val controller = WindowCompat.getInsetsController(window, view)
            // Se siamo in DarkMode, NON vogliamo icone "Light" (cioè nere), vogliamo icone bianche
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RosterTypography,
        content = content
    )
}

/** Restituisce il colore associato al tipo di turno */
fun shiftTypeColor(shiftType: String): Color = when (shiftType) {
    "REST_1"         -> ShiftRest1
    "REST_2"         -> ShiftRest2
    "DAY_OFF"        -> ShiftDayOff
    "ABSENT"         -> ShiftAbsent
    "PARENTAL_LEAVE" -> ShiftParental
    "WORK"           -> ShiftMorning   // override in base all'orario fatto nel composable
    else             -> ShiftOther
}

/**
 * Restituisce il colore del turno lavorativo in base all'ora di inizio.
 *
 * Fasce aziendali:
 *   🌅 Mattina   → dalle 03:00 (< 07:00)
 *   ☀️ Centrale  → dalle 07:00 (< 12:00)
 *   🌆 Pomeriggio → dalle 12:00 (< 19:00)
 *   🌙 Notte     → dalle 19:00 (o < 03:00)
 */
fun workShiftColor(startHour: Int?): Color = when {
    startHour == null                        -> ShiftOther
    startHour >= 19 || startHour < 3        -> ShiftNight       // 19:00 – 02:59
    startHour in 3 until 7                  -> ShiftMorning     // 03:00 – 06:59
    startHour in 7 until 12                 -> ShiftCentrale    // 07:00 – 11:59
    else                                    -> ShiftAfternoon   // 12:00 – 18:59
}

/** Etichetta testuale per la fascia oraria */
fun workShiftLabel(startHour: Int?): String = when {
    startHour == null                        -> "—"
    startHour >= 19 || startHour < 3        -> "Notte"
    startHour in 3 until 7                  -> "Mattina"
    startHour in 7 until 12                 -> "Centrale"
    else                                    -> "Pomeriggio"
}




