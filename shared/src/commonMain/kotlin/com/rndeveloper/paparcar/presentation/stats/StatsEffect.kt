package com.rndeveloper.paparcar.presentation.stats

import com.rndeveloper.paparcar.domain.entitlement.PaywallReason

sealed class StatsEffect {
    data class NavigateToPaywall(val reason: PaywallReason) : StatsEffect()
}
