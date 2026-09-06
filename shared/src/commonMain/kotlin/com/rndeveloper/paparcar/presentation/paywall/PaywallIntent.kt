package com.rndeveloper.paparcar.presentation.paywall

sealed class PaywallIntent {
    data object Close : PaywallIntent()
}
