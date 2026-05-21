package com.matteo.rosterenhancer.ui.component
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

// Importa il modificatore clickable per gestire i click senza ripple
import androidx.compose.foundation.clickable
// Importa MutableInteractionSource per controllare gli effetti visivi di interazione
import androidx.compose.foundation.interaction.MutableInteractionSource
// Importa collectIsPressedAsState per sapere in tempo reale se il componente è premuto
import androidx.compose.foundation.interaction.collectIsPressedAsState
// Importa le API fondamentali di Compose per il layout e il composable pattern
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
// Importa animateFloatAsState per animare la scala in modo fluido
import androidx.compose.animation.core.animateFloatAsState
// Importa spring per un'animazione con rimbalzo naturale
// Importa Modifier per costruire catene di modificatori
import androidx.compose.ui.Modifier
// Importa graphicsLayer per applicare la scala via GPU senza ricomporre il layout
import androidx.compose.ui.graphics.graphicsLayer
// Importa LocalHapticFeedback per accedere al controller di feedback aptico del dispositivo
import androidx.compose.ui.platform.LocalHapticFeedback
// Importa i tipi di feedback aptico disponibili su Android
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

// Enum che definisce l'intensità del feedback aptico da usare nella bouncyClick
enum class HapticStyle { LIGHT, MEDIUM, HEAVY, NONE }

/**
 * Modificatore premium che aggiunge:
 * - Animazione di scala "bouncy" quando il componente viene premuto
 * - Feedback aptico configurabile (LIGHT, MEDIUM, HEAVY, NONE)
 * - Nessun effetto ripple (look glassmorphic pulito)
 */
@Composable
fun Modifier.bouncyClick(
    // Se false, il componente non risponde ai click
    enabled: Boolean = true,
    // Stile del feedback aptico da dare all'utente alla pressione
    hapticStyle: HapticStyle = HapticStyle.MEDIUM,
    // Fattore di riduzione scala durante la pressione (0.92 = rimpicciolisce all'88%)
    scaleDownFactor: Float = 0.92f,
    // La funzione da eseguire al click
    onClick: () -> Unit
): Modifier {
    // Recupera il controller del feedback aptico dal sistema Android
    val haptic = LocalHapticFeedback.current
    // Crea una sorgente di interazione per osservare lo stato di pressione
    val interactionSource = remember { MutableInteractionSource() }
    // Osserva in tempo reale se il componente è attualmente premuto
    val isPressed by interactionSource.collectIsPressedAsState()

    // Calcola la scala target: ridotta durante la pressione, normale altrimenti
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDownFactor else 1f,
        // Molla morbida con rimbalzo: restituisce un'animazione fisica e soddisfacente
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bouncyClick_scale"
    )

    // Concatena i modificatori: prima la scala GPU, poi il clickable senza ripple
    return this
        // Applica la scala tramite GPU (non causa recomposizione del layout, solo di rendering)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        // Gestisce il click senza effetto ripple (indication = null)
        .clickable(
            interactionSource = interactionSource,
            indication = null, // Nessun ripple: il feedback visivo è solo la scala
            enabled = enabled,
            onClick = {
                // Esegue il feedback aptico configurato prima di invocare l'azione
                when (hapticStyle) {
                    // Feedback leggero: tocco delicato (es. selezione elemento)
                    HapticStyle.LIGHT  -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    // Feedback medio: pressione standard (es. pulsante generico)
                    HapticStyle.MEDIUM -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    // Feedback pesante: azione importante (es. conferma o eliminazione)
                    HapticStyle.HEAVY  -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    // Nessun feedback aptico
                    HapticStyle.NONE   -> Unit
                }
                // Esegue l'azione del click
                onClick()
            }
        )
}







