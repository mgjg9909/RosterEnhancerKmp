package com.matteo.rosterenhancer.util

import androidx.compose.ui.hapticfeedback.HapticFeedback
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import kotlinx.cinterop.ExperimentalForeignApi

actual object HapticFeedbackManager {
    @OptIn(ExperimentalForeignApi::class)
    actual fun playLightHaptic(haptic: HapticFeedback, context: Any?) {
        val generator = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
        generator.prepare()
        generator.impactOccurred()
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun playMediumHaptic(haptic: HapticFeedback, context: Any?) {
        val generator = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
        generator.prepare()
        generator.impactOccurred()
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun playHeavyHaptic(haptic: HapticFeedback, context: Any?) {
        val generator = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
        generator.prepare()
        generator.impactOccurred()
    }
}
