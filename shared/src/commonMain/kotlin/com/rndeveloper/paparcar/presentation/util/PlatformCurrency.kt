package com.rndeveloper.paparcar.presentation.util

/**
 * The ISO-4217 code of the money this phone is set up in — "EUR", "PLN", "RON"…
 * [FUEL-PRICE-RESOLVER-001]
 *
 * It answers one question the vehicle form cannot answer any other way: when the user types what
 * they pay for a litre, **in what currency did they type it?** The form has no country — a car has
 * no country, and asking the user to pick a currency to enter one number is a tax on the honest
 * path. The device's own region is the closest thing to the truth that costs nothing, and the user
 * can always retype the number if they move.
 *
 * It is NOT used to price anything: the averages carry their own currency, per country. This exists
 * only to label what the user typed, and a mislabelled override is visible and fixable on the same
 * screen where it was entered.
 *
 * - **androidMain**: the JVM's `Currency` for the default locale.
 * - **iosMain**: `NSLocale.currencyCode`.
 *
 * Null where the platform cannot say (a locale with no region). Then the field is not offered —
 * storing an amount whose currency nobody knows is how you get "1.30" meaning three different
 * things on three phones.
 */
expect fun platformCurrencyCode(): String?
