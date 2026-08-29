package com.rndeveloper.paparcar

import androidx.compose.ui.text.intl.Locale

/** The non-metric trio (US, Liberia, Myanmar) plus the UK, which is metric on paper but signs
 *  road distances in miles — maps apps default UK devices to miles for the same reason. */
private val IMPERIAL_COUNTRIES = setOf("US", "GB", "LR", "MM")

/**
 * Whether a country measures road distances in imperial units (mi/ft).
 * [countryCode] is an ISO 3166-1 alpha-2 code, case-insensitive; unknown or blank → metric.
 * [SETTINGS-UNITS-DEFAULT-FOLLOWS-COUNTRY-001]
 */
fun countryPrefersImperialUnits(countryCode: String): Boolean =
    countryCode.uppercase() in IMPERIAL_COUNTRIES

/**
 * Whether the device locale's country uses imperial units. This is the DEFAULT for
 * `AppPreferences.useImperialUnits` while the user has never touched the Settings toggle —
 * once set, the stored value wins. [Locale.current] is plain multiplatform API (usable outside
 * composition), so no expect/actual is needed. [SETTINGS-UNITS-DEFAULT-FOLLOWS-COUNTRY-001]
 */
fun localePrefersImperialUnits(): Boolean =
    countryPrefersImperialUnits(Locale.current.region)
