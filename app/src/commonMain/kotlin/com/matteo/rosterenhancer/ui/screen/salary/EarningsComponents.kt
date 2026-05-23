package com.matteo.rosterenhancer.ui.screen.salary

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.matteo.rosterenhancer.ui.components.GlassCard
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matteo.rosterenhancer.domain.model.EarningsProjection
import com.matteo.rosterenhancer.domain.model.MonthlySummary
import com.matteo.rosterenhancer.util.TextStyle
import com.matteo.rosterenhancer.util.Locale
import com.matteo.rosterenhancer.util.getDisplayName

@Composable
fun BentoCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subValue: String? = null,
    icon: ImageVector,
    color: Color,
    isLarge: Boolean = false,
    onClick: () -> Unit = {}
) {
    GlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        containerColor = color,
        glassAlpha = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) 0.15f else 0.1f,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    icon, 
                    contentDescription = null, 
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = color
                )
            }
            
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = value,
                    style = if (isLarge) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subValue != null) {
                    Text(
                        text = subValue,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun ProjectionWidget(
    projection: EarningsProjection, 
    taxRate: Double,
    formatMoney: (Double) -> String
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryColor = MaterialTheme.colorScheme.primary
    val containerColor = if (isDark) MaterialTheme.colorScheme.primaryContainer else primaryColor
    
    // Calcolo proporzioni Lordo/Tasse/Netto per la Stacked Bar
    val net = projection.totalProjected
    val gross = if (taxRate < 1.0) net / (1.0 - taxRate) else net
    val taxes = gross - net
    val taxRatio = if (gross > 0) (taxes / gross).toFloat() else 0.2f

    val contentColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = Color.Transparent,
        border = if (!isDark) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)) else null
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = if (isDark) {
                            listOf(containerColor.copy(alpha = 0.9f), containerColor)
                        } else {
                            listOf(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                            )
                        }
                    )
                )
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Column {
                // Header: Etichetta + Progress Month
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "STIMA NETTO MENSILE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                            color = contentColor.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "CCNL Guardie Giurate",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = contentColor.copy(alpha = 0.5f)
                        )
                    }
                    
                    Surface(
                        color = if (isDark) Color.White.copy(alpha = 0.15f) else primaryColor.copy(alpha = 0.1f),
                        shape = CircleShape
                    ) {
                        Text(
                            "mese al ${(projection.progressPercent * 100).toInt()}%",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else primaryColor
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Main Amount with Glow
                Text(
                    text = formatMoney(projection.totalProjected).replace("€ ", "€"),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = 40.sp,
                        letterSpacing = (-1).sp
                    ),
                    fontWeight = FontWeight.Black,
                    color = contentColor
                )

                Spacer(Modifier.height(20.dp))

                // STACKED BAR: Net vs Taxes
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Netto / Tasse", style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.6f))
                        Text(
                            "Lordo stimato: ${formatMoney(gross)}", 
                            style = MaterialTheme.typography.labelSmall, 
                            color = contentColor.copy(alpha = 0.6f)
                        )
                    }
                    
                    // The Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color.White.copy(alpha = 0.2f) else primaryColor.copy(alpha = 0.1f))
                    ) {
                        // Net Part
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(1f - taxRatio)
                                .fillMaxHeight()
                                .background(if (isDark) Color.White else primaryColor)
                        )
                        // Taxes Part
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .background(if (isDark) Color.White.copy(alpha = 0.4f) else primaryColor.copy(alpha = 0.3f))
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Footer Stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "GIÀ MATURATO",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.6f),
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            formatMoney(projection.earnedSoFar),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(contentColor.copy(alpha = 0.2f))
                            .align(Alignment.CenterVertically)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "DA MATURARE",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.6f),
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            formatMoney(projection.estimatedFuture),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = contentColor.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PayslipCard(payslip: com.matteo.rosterenhancer.domain.model.PayslipMetadata, formatMoney: (Double) -> String) {
    GlassCard(
        modifier = Modifier.width(280.dp),
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        glassAlpha = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) 0.2f else 0.15f
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(androidx.compose.material.icons.Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
            }
            Column {
                val monthName = payslip.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN).replaceFirstChar { it.uppercase() }
                Text(
                    "$monthName ${payslip.year}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    formatMoney(payslip.netPay),
                    style = MaterialTheme.typography.bodyLarge,
                    color = com.matteo.rosterenhancer.ui.theme.SuccessGreen,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
fun EarningsPieChart(summary: MonthlySummary, formatMoney: (Double) -> String) {
    val data = remember(summary) {
        listOf(
            PieChartData("Base", summary.basePayOrdinario, Color(0xFF2196F3)),
            PieChartData("Straord.", summary.overtimePay, Color(0xFFFF9800)),
            PieChartData("Notturni", summary.totalNightBonus, Color(0xFF9C27B0)),
            PieChartData("Bonus", summary.totalSundayBonus + summary.totalHolidayBonus + summary.totalRestBonusPay, Color(0xFF4CAF50)),
            PieChartData("Indennità", summary.totalPresenceIndemnity + summary.totalMensaPay, Color(0xFF607D8B))
        ).filter { it.value > 0 }
    }

    if (data.isEmpty()) return

    val total = data.sumOf { it.value }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        glassAlpha = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) 0.15f else 0.12f
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "COMPOSIZIONE STIPENDIO",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Il Grafico
                Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        var startAngle = -90f
                        data.forEach { slice ->
                            val sweepAngle = (slice.value / total).toFloat() * 360f
                            drawArc(
                                color = slice.color,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 25f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                            )
                            startAngle += sweepAngle
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("LORDO", style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatMoney(total).replace("€", "").trim(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                    }
                }
                
                // La Legenda
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    data.forEach { slice ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(modifier = Modifier.size(8.dp).background(slice.color, CircleShape))
                            Text(slice.label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            Text(formatMoney(slice.value), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

data class PieChartData(val label: String, val value: Double, val color: Color)





