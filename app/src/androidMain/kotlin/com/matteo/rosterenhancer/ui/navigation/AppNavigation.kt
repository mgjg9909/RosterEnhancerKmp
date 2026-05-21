package com.matteo.rosterenhancer.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.matteo.rosterenhancer.ui.screen.calendar.CalendarScreen
import com.matteo.rosterenhancer.ui.screen.dashboard.DashboardScreen
import com.matteo.rosterenhancer.ui.screen.importscreen.ImportScreen
import com.matteo.rosterenhancer.ui.screen.onboarding.OnboardingScreen
import com.matteo.rosterenhancer.ui.screen.settings.SettingsScreen
import com.matteo.rosterenhancer.ui.screen.stats.StatsScreen
import com.matteo.rosterenhancer.ui.screen.salary.SalaryScreen

sealed class Screen(val route: String) {
    object Onboarding  : Screen("onboarding")
    object Main        : Screen("main")
    object Dashboard   : Screen("dashboard")
    object Calendar    : Screen("calendar")
    object Stats       : Screen("stats")
    object Settings    : Screen("settings")
    object Import      : Screen("import")
    object Swaps       : Screen("swaps")
    object Salary      : Screen("salary")
    object Profile     : Screen("profile")
    object RestSwap    : Screen("restswap") 
    object Payslips    : Screen("payslips")
    object PayslipViewer : Screen("payslip_viewer/{payslipId}") {
        fun createRoute(id: Long) = "payslip_viewer/$id"
    }
}

// Spec condivise per le transizioni
private val navEnterSpec = tween<IntOffset>(350, easing = FastOutSlowInEasing)
private val navFadeIn    = tween<Float>(300, easing = FastOutSlowInEasing)
private val navFadeOut   = tween<Float>(220, easing = FastOutSlowInEasing)

@Composable
fun AppNavigation(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it / 5 },
                animationSpec = navEnterSpec
            ) + fadeIn(navFadeIn)
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it / 5 },
                animationSpec = navEnterSpec
            ) + fadeOut(navFadeOut)
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it / 5 },
                animationSpec = navEnterSpec
            ) + fadeIn(navFadeIn)
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = navEnterSpec
            ) + fadeOut(navFadeOut)
        }
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onDone = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Main.route) {
            com.matteo.rosterenhancer.ui.screen.main.MainScreen(
                rootNavController = navController,
                onNavigateToSettings = { 
                    try { navController.navigate(Screen.Settings.route) } 
                    catch (e: Exception) { println("Navigation Error Settings: $e") } 
                },
                onNavigateToImport = { 
                    try { navController.navigate(Screen.Import.route) } 
                    catch (e: Exception) { println("Navigation Error Import: $e") } 
                },
                onNavigateToRestSwap = { 
                    try { navController.navigate(Screen.Swaps.route) } 
                    catch (e: Exception) { println("Navigation Error Swaps: $e") } 
                },
                onNavigateToStats = { 
                    try { navController.navigate(Screen.Stats.route) } 
                    catch (e: Exception) { println("Navigation Error Stats: $e") } 
                }
            )
        }

        // Le schermate secondarie rimangono fuori dalla MainScreen perché non devono avere la BottomBar
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Stats.route) {
            StatsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Import.route) {
            ImportScreen(
                onImportSuccess = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Swaps.route) {
            com.matteo.rosterenhancer.ui.screen.swaps.SwapsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Salary.route) {
            SalaryScreen(
                onBack = { navController.popBackStack() },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) }
            )
        }
        composable(Screen.Profile.route) {
            com.matteo.rosterenhancer.ui.screen.profile.ProfileScreen(
                onBack = { navController.popBackStack() },
                onNavigateToPayslips = { navController.navigate(Screen.Payslips.route) }
            )
        }
        composable(Screen.Payslips.route) {
            com.matteo.rosterenhancer.ui.screen.salary.PayslipsHistoryScreen(
                onBack = { navController.popBackStack() },
                onViewPayslip = { id -> navController.navigate(Screen.PayslipViewer.createRoute(id)) }
            )
        }
        composable(
            route = Screen.PayslipViewer.route,
            arguments = listOf(
                androidx.navigation.navArgument("payslipId") { type = androidx.navigation.NavType.LongType }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("payslipId") ?: 0L
            com.matteo.rosterenhancer.ui.screen.salary.PayslipViewerScreen(
                payslipId = id,
                onBack = { navController.popBackStack() }
            )
        }
    }
}




