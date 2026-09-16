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

/** Which gallon a country means when it says «mpg». [I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001] */
enum class GallonKind { US, IMPERIAL }

/**
 * Qué galón usa un país. **No se deduce de «usa millas»**: US y UK dicen los dos «mpg» y no es la
 * misma cifra — el galón US son 3,785 l y el imperial 4,546, así que el mismo coche marca 30 mpg en
 * Michigan y 36 mpg en Kent. Elegir mal no descoloca la unidad, deja el número mal por un 20 %, que
 * es peor: parece correcto.
 *
 * Reino Unido es el caso que obliga a separar las dos preguntas — señaliza en millas y reposta en
 * litros, pero un británico habla de su coche en mpg imperiales. Por eso `GB` está en
 * [IMPERIAL_COUNTRIES] y NO aquí. [SETTINGS-UNITS-DEFAULT-FOLLOWS-COUNTRY-001]
 */
fun countryUsesUsGallon(countryCode: String): Boolean = countryCode.uppercase() in US_GALLON_COUNTRIES

/** El galón del dispositivo, por el mismo camino que [localePrefersImperialUnits]. */
fun localeUsesUsGallon(): Boolean = countryUsesUsGallon(Locale.current.region)

/** El galón de este dispositivo, ya resuelto. */
fun localeGallonKind(): GallonKind = if (localeUsesUsGallon()) GallonKind.US else GallonKind.IMPERIAL

/** El galón US es de los EE. UU. y de quienes siguen su mercado del automóvil (Liberia lo hace). */
private val US_GALLON_COUNTRIES = setOf("US", "LR")
