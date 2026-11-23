package com.rndeveloper.paparcar.presentation.util

/**
 * `null`: en iOS no hay tienda configurada todavía. [MONEY-PLUS-CAN-SEE-ITS-OWN-BENEFITS-001]
 *
 * Mismo razonamiento que [storeRedeemCodeUrl]: el SDK de compras no está configurado en iOS
 * (`Purchases.configure()` sólo corre en `PaparcarApp`), así que no hay suscripción que gestionar
 * ahí todavía. Cuando iOS venda, esto deja de ser `null` y abre la gestión de suscripciones nativa
 * (`https://apps.apple.com/account/subscriptions` o el `manageSubscriptionsSheet` de StoreKit).
 */
actual fun storeManageSubscriptionUrl(): String? = null
