package com.rndeveloper.paparcar.presentation.util

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.currencyCode

/**
 * `NSLocale.currentLocale.currencyCode` — nullable on the Foundation side too, for the same reason
 * as the JVM: a locale without a region has no money. [FUEL-PRICE-RESOLVER-001]
 */
actual fun platformCurrencyCode(): String? = NSLocale.currentLocale.currencyCode
