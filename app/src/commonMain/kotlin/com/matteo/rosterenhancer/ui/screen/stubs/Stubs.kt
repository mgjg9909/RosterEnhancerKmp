package com.matteo.rosterenhancer.ui.screen.stubs

import androidx.compose.runtime.Composable

@Composable
expect fun CalendarScreenStub()

@Composable
expect fun SalaryScreenStub(
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit
)

@Composable
expect fun SwapsScreenStub(
    onBack: () -> Unit
)
