package com.rndeveloper.paparcar.presentation.util

/**
 * La pantalla de canje de Google Play. [MONEY-A-PROMO-CODE-IS-REDEEMED-WHERE-THE-STORE-LIVES-001]
 *
 * ⛔ **Sin `?code=`, a propósito.** La URL admite prefijar el código, pero entonces la app tendría
 * que pedirlo antes en un campo propio: dos sitios donde teclear lo mismo, y el nuestro sin la
 * validación ni los mensajes de error de Play. Con la URL desnuda, Play pide el código, lo valida,
 * lo canjea y explica él mismo qué ha pasado cuando no vale.
 *
 * Play la abre en su app si está instalada (intent filter propio) y en el navegador si no, así que
 * no hace falta comprobar nada antes.
 */
actual fun storeRedeemCodeUrl(): String? = "https://play.google.com/redeem"
