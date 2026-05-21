package com.matteo.rosterenhancer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Una card premium con effetto glassmorphism avanzato, 
 * riflessi 3D e profondità migliorata, senza artefatti geometrici.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderAlpha: Float = 0.1f,
    glassAlpha: Float = 0.7f,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val finalShape = cornerRadius?.let { RoundedCornerShape(it) } ?: shape
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    
    val highlightColor = androidx.compose.runtime.remember(isDark, borderAlpha) {
        if (isDark) Color.White.copy(alpha = 0.15f + borderAlpha) else Color.White.copy(alpha = 0.3f + borderAlpha)
    }
    val shadowColor = androidx.compose.runtime.remember(isDark, borderAlpha) {
        if (isDark) Color.Black.copy(alpha = 0.15f + borderAlpha) else Color.Black.copy(alpha = 0.05f + borderAlpha)
    }

    val glassBrush = androidx.compose.runtime.remember(isDark) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = if (isDark) 0.08f else 0.15f),
                Color.Transparent,
                Color.Transparent
            ),
            start = Offset(0f, 0f),
            end = Offset(1000f, 1000f)
        )
    }

    val borderBrush = androidx.compose.runtime.remember(highlightColor, shadowColor) {
        Brush.linearGradient(
            colors = listOf(highlightColor, highlightColor.copy(alpha = 0.2f), shadowColor.copy(alpha = 0.1f), shadowColor),
            start = Offset(0f, 0f),
            end = Offset(1000f, 1000f)
        )
    }

    var cardModifier = modifier
        .clip(finalShape)
        .background(containerColor.copy(alpha = glassAlpha))
        .background(glassBrush)
        .border(width = 1.dp, brush = borderBrush, shape = finalShape)

    // Aggiungi clickabilità se necessario
    if (onClick != null) {
        cardModifier = cardModifier.clickable(onClick = onClick)
    }

    // Contenitore principale
    Column(
        modifier = cardModifier,
        content = content
    )
}
@Composable
fun MeshGradientBackground(modifier: Modifier = Modifier, customColor: Color? = null) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val color1 = customColor?.copy(alpha = if (isDark) 0.15f else 0.1f) ?: MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.12f else 0.08f)
    val color2 = MaterialTheme.colorScheme.secondary.copy(alpha = if (isDark) 0.1f else 0.06f)
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(color1, color2, MaterialTheme.colorScheme.background),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
    )
}




