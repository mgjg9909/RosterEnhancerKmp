package com.matteo.rosterenhancer.ui.screen.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import com.matteo.rosterenhancer.ui.theme.*
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.matteo.rosterenhancer.ui.navigation.Screen
import com.matteo.rosterenhancer.ui.screen.calendar.CalendarScreen
import com.matteo.rosterenhancer.ui.screen.dashboard.DashboardScreen
import com.matteo.rosterenhancer.ui.screen.stats.StatsScreen
import com.matteo.rosterenhancer.ui.screen.salary.SalaryScreen
import com.matteo.rosterenhancer.ui.screen.salary.SalaryViewModel

sealed class TabScreen(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector, val index: Int, val color: Color) {
    object Home   : TabScreen(Screen.Dashboard.route, "Home",     Icons.Outlined.Home,          Icons.Filled.Home,          0, BluePrimary)
    object Roster : TabScreen(Screen.Calendar.route,  "Roster",   Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth, 1, ShiftAfternoon)
    object Salary : TabScreen(Screen.Salary.route,    "Guadagni", Icons.Outlined.Payments,      Icons.Filled.Payments,      2, ShiftRest1)
    object Swaps  : TabScreen(Screen.Swaps.route,     "Cambi",    Icons.Outlined.SwapHoriz,     Icons.Filled.SwapHoriz,     3, ShiftNight)
}

@Composable
fun MainScreen(
    rootNavController: NavHostController,
    onNavigateToSettings: () -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToRestSwap: () -> Unit,
    onNavigateToStats: () -> Unit
) {
    val navController = rememberNavController()
    val items = listOf(
        TabScreen.Home,
        TabScreen.Roster,
        TabScreen.Salary,
        TabScreen.Swaps
    )

    Box(modifier = Modifier.fillMaxSize()) {
        MeshGradientBackground()
        
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            
            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            val primary = MaterialTheme.colorScheme.primary
            val barBaseColor = if (isDark) Color.White else primary
            val barAlpha = if (isDark) 0.08f else 0.05f
            val borderAlpha = if (isDark) 0.15f else 0.25f
            val borderWidth = if (isDark) 0.5.dp else 1.dp

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = barBaseColor.copy(alpha = barAlpha),
                    tonalElevation = 0.dp,
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(
                        width = borderWidth, 
                        color = barBaseColor.copy(alpha = borderAlpha)
                    ),
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .height(64.dp)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(
                            listOf(barBaseColor.copy(alpha = barAlpha * 1.5f), Color.Transparent)
                        ))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val selectedIndex = items.indexOfFirst { currentDestination?.hierarchy?.any { d -> d.route == it.route } == true }.coerceAtLeast(0)
                        val selectedTab = items[selectedIndex]
                        
                        // Animazione della Bolla "Slime/Striscio"
                        var currentTarget by remember { mutableStateOf(selectedIndex) }
                        var movingDirection by remember { mutableStateOf(0) }
                        
                        // Aggiorniamo la direzione solo quando cambia l'indice target
                        if (selectedIndex != currentTarget) {
                            movingDirection = if (selectedIndex > currentTarget) 1 else -1
                            currentTarget = selectedIndex
                        }

                        // Molle configurate per un effetto "lento, delicato e soddisfacente"
                        val stiffnessFast = 140f  
                        val stiffnessSlow = 40f   
                        val damping = 0.65f       

                        val leftEdge by animateFloatAsState(
                            targetValue = selectedIndex.toFloat(),
                            animationSpec = spring(dampingRatio = damping, stiffness = if (movingDirection == -1) stiffnessFast else stiffnessSlow),
                            label = "left_edge"
                        )

                        val rightEdge by animateFloatAsState(
                            targetValue = selectedIndex.toFloat() + 1f,
                            animationSpec = spring(dampingRatio = damping, stiffness = if (movingDirection == 1) stiffnessFast else stiffnessSlow),
                            label = "right_edge"
                        )

                        // --- IDEA: Color Morphing basato sulla POSIZIONE REALE ---
                        // Invece di un'animazione a tempo, il colore della bolla dipende da DOVE si trova.
                        // Questo rende il cambio colore incredibilmente fluido e "fisico".
                        val currentCenter = (leftEdge + rightEdge - 1f) / 2f
                        val colorFraction = currentCenter.coerceIn(0f, (items.size - 1).toFloat())
                        val lowIndex = colorFraction.toInt()
                        val highIndex = (lowIndex + 1).coerceAtMost(items.size - 1)
                        val fraction = colorFraction - lowIndex

                        val animatedBubbleColor = lerp(
                            start = items[lowIndex].color,
                            stop = items[highIndex].color,
                            fraction = fraction
                        )
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .drawBehind {
                                    val tabWidth = size.width / items.size
                                    val left = leftEdge * tabWidth
                                    val right = rightEdge * tabWidth
                                    
                                    // Calcolo dell'allungamento per l'effetto "schiacciamento"
                                    val currentStretchX = (rightEdge - leftEdge).coerceAtLeast(0.1f)
                                    val stretchAmount = (currentStretchX - 1f).coerceAtLeast(0f)
                                    
                                    // Più si allunga sull'asse X, più si schiaccia sull'asse Y (preserva il volume)
                                    val scaleY = 1f - (stretchAmount * 0.35f).coerceAtMost(0.5f)
                                    
                                    val verticalPadding = 8.dp.toPx()
                                    val horizontalPadding = 4.dp.toPx()
                                    
                                    // Calcolo dimensioni con schiacciamento applicato al centro
                                    val baseHeight = size.height - (verticalPadding * 2)
                                    val actualHeight = baseHeight * scaleY
                                    val heightDiff = baseHeight - actualHeight
                                    
                                    val top = verticalPadding + (heightDiff / 2)
                                    val bottom = size.height - verticalPadding - (heightDiff / 2)
                                    
                                    val rectLeft = left + horizontalPadding
                                    val rectRight = right - horizontalPadding
                                    
                                    val rectSize = androidx.compose.ui.geometry.Size(
                                        width = (rectRight - rectLeft).coerceAtLeast(1f), 
                                        height = (bottom - top).coerceAtLeast(1f)
                                    )
                                    val rectTopLeft = androidx.compose.ui.geometry.Offset(rectLeft, top)
                                    val cornerRadius = androidx.compose.ui.geometry.CornerRadius(rectSize.height / 2)

                                    // Gradiente principale della bolla (più intenso in alto)
                                    drawRoundRect(
                                        brush = Brush.verticalGradient(
                                            listOf(
                                                animatedBubbleColor.copy(alpha = 0.45f),
                                                animatedBubbleColor.copy(alpha = 0.15f)
                                            ),
                                            startY = top,
                                            endY = bottom
                                        ),
                                        topLeft = rectTopLeft,
                                        size = rectSize,
                                        cornerRadius = cornerRadius
                                    )
                                    
                                    // Bordo sottile e luminescente per definire la bolla
                                    drawRoundRect(
                                        color = animatedBubbleColor.copy(alpha = 0.5f),
                                        topLeft = rectTopLeft,
                                        size = rectSize,
                                        cornerRadius = cornerRadius,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                                    )
                                }
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items.forEach { item ->
                                val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                                
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                
                                val scale by animateFloatAsState(
                                    targetValue = if (isPressed) 0.92f else 1f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                    label = "tab_scale"
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null
                                        ) {
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val contentColor by animateColorAsState(
                                        targetValue = if (selected) 
                                            item.color 
                                        else 
                                            if (isDark) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        animationSpec = tween(durationMillis = 400),
                                        label = "tab_content_color"
                                    )
                                    
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.icon,
                                            contentDescription = item.label,
                                            tint = contentColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Text(
                                            text = item.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = contentColor,
                                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        val fadeSpec  = tween<Float>(220, easing = LinearEasing)

        val navigateToTab = { route: String ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        NavHost(
            navController = navController,
            startDestination = TabScreen.Home.route,
            modifier = Modifier.padding(
                top = 0.dp,
                bottom = 0.dp
            ),
            enterTransition = { fadeIn(fadeSpec) },
            exitTransition = { fadeOut(fadeSpec) },
            popEnterTransition = { fadeIn(fadeSpec) },
            popExitTransition = { fadeOut(fadeSpec) }
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToCalendar = { navigateToTab(Screen.Calendar.route) },
                    onNavigateToColleagues = { navigateToTab(Screen.Calendar.route) },
                    onNavigateToSalary = { navigateToTab(Screen.Salary.route) },
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToImport = onNavigateToImport,
                    onNavigateToProfile = { rootNavController.navigate(Screen.Profile.route) }
                )
            }
            composable(Screen.Calendar.route) {
                CalendarScreen()
            }
            composable(Screen.Swaps.route) {
                com.matteo.rosterenhancer.ui.screen.swaps.SwapsScreen(onBack = {
                    navController.navigate(TabScreen.Home.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                })
            }
            composable(Screen.Salary.route) {
                SalaryScreen(
                    onBack = {
                        navController.navigate(TabScreen.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToProfile = { rootNavController.navigate(Screen.Profile.route) }
                )
            }
            }
        }
    }
}

@Composable
private fun MeshGradientBackground() {
    val color1 = MaterialTheme.colorScheme.background
    val color2 = MaterialTheme.colorScheme.surface
    val color3 = MaterialTheme.colorScheme.surfaceVariant
    
    // Possiamo dedurre se è dark mode controllando la luminosità del background
    val isDark = color1.luminance() < 0.5f

    Box(modifier = Modifier.fillMaxSize().background(
        brush = androidx.compose.ui.graphics.Brush.linearGradient(
            colors = listOf(color1, color2, color3)
        )
    )) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = BluePrimary.copy(alpha = if (isDark) 0.15f else 0.1f),
                radius = size.width * 0.8f,
                center = Offset(size.width * 0.2f, size.height * 0.1f)
            )
            drawCircle(
                color = ShiftNight.copy(alpha = if (isDark) 0.12f else 0.08f),
                radius = size.width * 0.6f,
                center = Offset(size.width * 0.8f, size.height * 0.4f)
            )
            drawCircle(
                color = SuccessGreen.copy(alpha = if (isDark) 0.1f else 0.05f),
                radius = size.width * 0.7f,
                center = Offset(size.width * 0.4f, size.height * 0.8f)
            )
        }
    }
}




