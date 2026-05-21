package com.matteo.rosterenhancer.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

actual object HapticFeedbackManager {
    actual fun playLightHaptic(haptic: HapticFeedback, context: Any?) {
        val androidContext = context as? Context
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && androidContext != null) {
            val vibrator = getVibrator(androidContext)
            if (vibrator?.hasVibrator() == true) {
                try {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                    return
                } catch (e: SecurityException) {
                    // Fallback to Compose
                }
            }
        }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    actual fun playMediumHaptic(haptic: HapticFeedback, context: Any?) {
        val androidContext = context as? Context
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && androidContext != null) {
            val vibrator = getVibrator(androidContext)
            if (vibrator?.hasVibrator() == true) {
                try {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                    return
                } catch (e: SecurityException) {
                    // Fallback to Compose
                }
            }
        }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    actual fun playHeavyHaptic(haptic: HapticFeedback, context: Any?) {
        val androidContext = context as? Context
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && androidContext != null) {
            val vibrator = getVibrator(androidContext)
            if (vibrator?.hasVibrator() == true) {
                try {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                    return
                } catch (e: SecurityException) {
                    // Fallback to Compose
                }
            }
        }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
}
