package io.apptolast.paparcar.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
actual fun MapForegroundEffect(onChanged: (Boolean) -> Unit) {
    // iOS has no lifecycle-resume equivalent wired yet; treat the screen as always foreground so the
    // location request behaves as before on iOS. [UI-LOC-FOREGROUND-001]
    LaunchedEffect(Unit) { onChanged(true) }
}
