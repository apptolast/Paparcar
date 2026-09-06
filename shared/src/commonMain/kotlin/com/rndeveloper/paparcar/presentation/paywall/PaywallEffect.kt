package com.rndeveloper.paparcar.presentation.paywall

sealed class PaywallEffect {
    data object Dismiss : PaywallEffect()
}
