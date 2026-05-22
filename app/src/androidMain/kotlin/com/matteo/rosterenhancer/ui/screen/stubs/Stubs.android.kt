package com.matteo.rosterenhancer.ui.screen.stubs

import androidx.compose.runtime.Composable

@Composable
actual fun CalendarScreenStub() {
    com.matteo.rosterenhancer.ui.screen.calendar.CalendarScreen()
}

@Composable
actual fun SalaryScreenStub(
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    com.matteo.rosterenhancer.ui.screen.salary.SalaryScreen(onBack = onBack, onNavigateToProfile = onNavigateToProfile)
}

@Composable
actual fun SwapsScreenStub(
    onBack: () -> Unit
) {
    com.matteo.rosterenhancer.ui.screen.swaps.SwapsScreen(onBack = onBack)
}
