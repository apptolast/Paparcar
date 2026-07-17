package io.apptolast.paparcar.presentation.util

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleResumeEffect

@Composable
actual fun MapForegroundEffect(onChanged: (Boolean) -> Unit) {
    LifecycleResumeEffect(Unit) {
        onChanged(true)
        onPauseOrDispose { onChanged(false) }
    }
}
