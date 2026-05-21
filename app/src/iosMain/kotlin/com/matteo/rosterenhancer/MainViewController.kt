package com.matteo.rosterenhancer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0C20), // Deep space black-purple
                        Color(0xFF15102A), // Dark purple
                        Color(0xFF06040A)  // Rich near black
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth()
        ) {
            // Elegant Glowing Icon Container
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1E173C),
                modifier = Modifier
                    .size(96.dp)
                    .padding(bottom = 24.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = "📅",
                        fontSize = 40.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // App Name with Premium styling
            Text(
                text = "RosterEnhancer",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = "iOS Beta",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFB388FF),
                letterSpacing = 2.sp,
                modifier = Modifier
                    .background(
                        color = Color(0xFFB388FF).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Beautiful Card showing the status of our journey
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF161224)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Pipeline iOS Convalidata! 🚀",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Il test di sideloading è andato a buon fine ed il motore grafico Compose Multiplatform è attivo sul tuo iPhone.",
                        fontSize = 14.sp,
                        color = Color(0xFF9E9BA9),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Loading / Next Step indicator
            CircularProgressIndicator(
                color = Color(0xFFB388FF),
                strokeWidth = 3.dp,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Pronti per la migrazione dell'interfaccia...",
                fontSize = 12.sp,
                color = Color(0xFF676573),
                textAlign = TextAlign.Center
            )
        }
    }
}

