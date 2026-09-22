package com.rndeveloper.paparcar.presentation.util

/**
 * `null`: en iOS un código de oferta NO se canjea con una URL.
 * [MONEY-A-PROMO-CODE-IS-REDEEMED-WHERE-THE-STORE-LIVES-001]
 *
 * StoreKit lo hace con una hoja del sistema (`presentCodeRedemptionSheet`), que es una llamada
 * nativa y no una dirección — abrir aquí la URL de Play mandaría a un usuario de iPhone a la tienda
 * de Android, que es peor que no ofrecerlo.
 *
 * Y hoy tampoco habría qué canjear: el SDK de compras no está configurado en iOS
 * (`Purchases.configure()` sólo corre en `PaparcarApp`), así que la app no vende nada ahí. Cuando
 * iOS tenga tienda, esto deja de ser una URL y pasa a ser un puerto con su `actual` nativo.
 */
actual fun storeRedeemCodeUrl(): String? = null
