package io.apptolast.paparcar.presentation.util

import androidx.compose.runtime.Composable

/**
 * Intercepts the platform "back" affordance (Android system back gesture/key).
 * iOS has no global back signal, so the iOS actual is a no-op — callers that
 * need iOS dismissal must wire it through a UI affordance (e.g. swipe-down on
 * the sheet, the in-card back arrow). [SHEET-BACKNAV-001]
 */
@Composable
expect fun PaparcarBackHandler(enabled: Boolean = true, onBack: () -> Unit)
