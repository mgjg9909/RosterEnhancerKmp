package com.matteo.rosterenhancer.util

import androidx.compose.ui.hapticfeedback.HapticFeedback

expect object HapticFeedbackManager {
    fun playLightHaptic(haptic: HapticFeedback, context: Any? = null)
    fun playMediumHaptic(haptic: HapticFeedback, context: Any? = null)
    fun playHeavyHaptic(haptic: HapticFeedback, context: Any? = null)
}

