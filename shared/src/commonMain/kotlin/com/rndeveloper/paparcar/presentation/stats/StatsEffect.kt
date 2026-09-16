package com.rndeveloper.paparcar.presentation.stats

import com.rndeveloper.paparcar.domain.entitlement.PaywallReason
import com.rndeveloper.paparcar.presentation.permissions.PermissionsFocus

sealed class StatsEffect {
    data class NavigateToPaywall(val reason: PaywallReason) : StatsEffect()

    /** The same focused permissions flow Home's banner and Settings' switch open — turning
     *  detection on is one action, so it cannot land on a different screen depending on which
     *  surface asked. [DET-TOGGLE-001] */
    data class NavigateToPermissions(val focus: PermissionsFocus) : StatsEffect()
}
