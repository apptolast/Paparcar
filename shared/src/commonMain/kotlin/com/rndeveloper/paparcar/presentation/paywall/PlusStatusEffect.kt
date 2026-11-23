package com.rndeveloper.paparcar.presentation.paywall

sealed class PlusStatusEffect {
    /** Abre la gestión de suscripción de la tienda. Mismo patrón que `PaywallEffect.OpenUrl`: el
     *  `UriHandler` vive en la pantalla, no en el ViewModel.
     *  [MONEY-PLUS-CAN-SEE-ITS-OWN-BENEFITS-001] */
    data class OpenUrl(val url: String) : PlusStatusEffect()
}
