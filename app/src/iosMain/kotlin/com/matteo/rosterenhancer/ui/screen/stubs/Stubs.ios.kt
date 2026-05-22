package com.matteo.rosterenhancer.ui.screen.stubs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun ComingSoonPlaceholder(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "${title} in arrivo su iOS! ??")
    }
}

@Composable
actual fun CalendarScreenStub() {
    ComingSoonPlaceholder("Calendario")
}

@Composable
actual fun SalaryScreenStub(
    onBack: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    ComingSoonPlaceholder("Guadagni")
}

@Composable
actual fun SwapsScreenStub(
    onBack: () -> Unit
) {
    ComingSoonPlaceholder("Cambi Turno")
}
