package io.apptolast.paparcar.presentation.util

import androidx.compose.runtime.Composable

@Composable
actual fun PaparcarBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS has no global back signal — back-style dismissal must come from a
    // visible UI affordance. Intentional no-op; do not add a TODO that would
    // suggest a missing implementation.
}
