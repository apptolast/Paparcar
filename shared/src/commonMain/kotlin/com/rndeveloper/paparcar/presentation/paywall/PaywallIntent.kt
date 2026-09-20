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

    /** «Tengo un código» — abre la pantalla de canje de la TIENDA. La app no canjea nada.
     *  [MONEY-A-PROMO-CODE-IS-REDEEMED-WHERE-THE-STORE-LIVES-001] */
    data object RedeemCode : PaywallIntent()
}
