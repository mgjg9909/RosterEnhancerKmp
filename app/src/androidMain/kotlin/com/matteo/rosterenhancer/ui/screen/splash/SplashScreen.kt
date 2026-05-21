package com.matteo.rosterenhancer.ui.screen.splash

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matteo.rosterenhancer.R
import kotlinx.coroutines.delay

@Composable
fun AnimatedSplashScreen(onAnimationFinished: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    
    // 1. Animazione per lo scivolamento del logo dal basso (da 180.dp a 0.dp)
    val logoOffsetY by animateDpAsState(
        targetValue = if (startAnimation) 0.dp else 180.dp,
        animationSpec = tween(
            durationMillis = 1000,
            easing = ExpoOut
        ),
        label = "logo_offset"
    )

    // 2. Animazione per la posizione verticale del testo (sale leggermente di 20dp)
    val textOffsetY by animateDpAsState(
        targetValue = if (startAnimation) 0.dp else 20.dp,
        animationSpec = tween(
            durationMillis = 1000,
            easing = ExpoOut
        ),
        label = "text_offset"
    )

    // 3. Dissolvenza del testo
    val textAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(
            durationMillis = 800,
            delayMillis = 400 // Inizia quando il logo è quasi a metà salita
        ),
        label = "text_alpha"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2000) // Durata totale dell'animazione accorciata per una maggiore reattività
        onAnimationFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo che sale con scivolamento morbido
            Image(
                painter = painterResource(id = R.drawable.logo_ascent),
                contentDescription = null,
                modifier = Modifier
                    .size(140.dp)
                    .offset(y = logoOffsetY)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Scritta che appare in dissolvenza salendo leggermente
            Text(
                text = "Roster Enhancer",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .offset(y = textOffsetY)
                    .alpha(textAlpha)
            )
        }
    }
}

// Helper per easing personalizzato
val ExpoOut = Easing { fraction ->
    if (fraction == 1f) 1f else 1f - Math.pow(2.0, -10.0 * fraction).toFloat()
}




