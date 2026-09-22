package com.rndeveloper.paparcar.presentation.paywall

sealed class PaywallEffect {
    data object Dismiss : PaywallEffect()

    /** Abre una dirección fuera de la app — hoy sólo la pantalla de canje de la tienda.
     *  Mismo patrón que `SettingsEffect.OpenUrl`: el `UriHandler` vive en la pantalla, no en el
     *  ViewModel. [MONEY-A-PROMO-CODE-IS-REDEEMED-WHERE-THE-STORE-LIVES-001] */
    data class OpenUrl(val url: String) : PaywallEffect()
}
