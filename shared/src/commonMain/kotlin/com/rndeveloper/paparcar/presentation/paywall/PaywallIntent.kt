package com.rndeveloper.paparcar.presentation.paywall

import com.rndeveloper.paparcar.domain.model.PlusPlan

sealed class PaywallIntent {
    data object Close : PaywallIntent()

    /** The user picked a billing period. Selection is free and reversible — nothing is charged
     *  until [Purchase]. */
    data class SelectPlan(val plan: PlusPlan) : PaywallIntent()

    /** Buy the selected plan. */
    data object Purchase : PaywallIntent()

    /** "I already paid" — re-grant whatever this store account owns. Charges nothing. */
    data object Restore : PaywallIntent()
}
