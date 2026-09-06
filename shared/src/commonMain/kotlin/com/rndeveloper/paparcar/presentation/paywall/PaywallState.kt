package com.rndeveloper.paparcar.presentation.paywall

import androidx.compose.runtime.Immutable

@Immutable
data class PaywallState(
    /**
     * Live entitlement: a purchase completed by the remote paywall flips this through the
     * repository stream, and the screen reacts by dismissing itself — no callback juggling.
     * [MONEY-001]
     */
    val isPlus: Boolean = false,
    /**
     * The store SDK is configured, so the remote paywall can actually sell. False in the mock
     * flavor and in a build without an API key — the screen then shows the static frame with
     * an honest "not available right now" line instead of a broken template.
     */
    val storeAvailable: Boolean = false,
)
