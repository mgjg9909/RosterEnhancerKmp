package com.matteo.rosterenhancer

import androidx.compose.ui.window.ComposeUIViewController
import androidx.navigation.compose.rememberNavController
import com.matteo.rosterenhancer.di.initKoin
import com.matteo.rosterenhancer.ui.screen.main.MainScreen
import com.matteo.rosterenhancer.ui.theme.RosterEnhancerTheme

fun initKoinIOS() {
    initKoin()
}

fun MainViewController() = ComposeUIViewController(
    configure = {
        enforceStrictPlistSanityCheck = false
    }
) {
    RosterEnhancerTheme {
        val rootNavController = rememberNavController()
        
        MainScreen(
            rootNavController = rootNavController,
            onNavigateToSettings = { },
            onNavigateToImport = { },
            onNavigateToRestSwap = { },
            onNavigateToStats = { }
        )
    }
}
