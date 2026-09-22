package com.rndeveloper.paparcar.presentation.util

/**
 * Dónde canjea un código de promoción el usuario de ESTA tienda.
 * [MONEY-A-PROMO-CODE-IS-REDEEMED-WHERE-THE-STORE-LIVES-001]
 *
 * Un código de promoción no lo canjea la app: lo canjea la TIENDA. Nosotros sólo abrimos su puerta
 * y esperamos a que la suscripción aparezca por el mismo sitio por el que aparece una compra — el
 * `CustomerInfo` que RevenueCat empuja. Por eso esto no es una llamada de compra ni pasa por el
 * repositorio de entitlement: es una dirección.
 *
 * `null` = esta plataforma no tiene puerta que abrir, y entonces la fila NO se enseña. Un botón que
 * lleva a ninguna parte es peor que no tener el botón, que es justo el problema que tiene hoy el
 * paywall de iOS con su «Subscriptions aren't available right now».
 */
expect fun storeRedeemCodeUrl(): String?
