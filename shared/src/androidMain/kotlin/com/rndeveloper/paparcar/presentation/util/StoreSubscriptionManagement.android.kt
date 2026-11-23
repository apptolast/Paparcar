package com.rndeveloper.paparcar.presentation.util

/**
 * La lista de suscripciones de Google Play. [MONEY-PLUS-CAN-SEE-ITS-OWN-BENEFITS-001]
 *
 * ⛔ **Sin `package=`, a propósito** — mismo razonamiento que [storeRedeemCodeUrl]: la URL desnuda
 * abre la lista de suscripciones de la cuenta y Play ya sabe resolver cuál es Paparcar sin que
 * nosotros le pasemos el `applicationId`. Play la abre en su app si está instalada y en el
 * navegador si no.
 */
actual fun storeManageSubscriptionUrl(): String? = "https://play.google.com/store/account/subscriptions"
