package com.rndeveloper.paparcar.presentation.util

import java.util.Currency
import java.util.Locale

/**
 * The JVM knows the currency of the default locale — but only when that locale has a REGION:
 * `Locale("es")` has no country, so `Currency.getInstance` throws rather than guessing. Catching
 * that is the whole implementation. [FUEL-PRICE-RESOLVER-001]
 */
actual fun platformCurrencyCode(): String? =
    runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull()
