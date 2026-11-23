package com.rndeveloper.paparcar.presentation.paywall

sealed class PlusStatusIntent {
    /** "Gestionar suscripción" — abre la puerta de la TIENDA, igual que [PaywallIntent.RedeemCode].
     *  [MONEY-PLUS-CAN-SEE-ITS-OWN-BENEFITS-001] */
    data object ManageSubscription : PlusStatusIntent()
}
