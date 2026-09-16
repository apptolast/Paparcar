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

/**
 * En qué unidad **cobra el surtidor** aquí: `null` = por litro, y sólo `GallonKind.US` lo contradice.
 * [I18N-A-PRICE-IS-QUOTED-IN-THE-UNIT-YOU-BUY-IT-001]
 *
 * ⛔ **No es [localeGallonKind], por mucho que lo parezca.** Aquélla contesta *«cuando este país dice
 * mpg, ¿qué galón quiere decir?»* y devuelve `IMPERIAL` para España, Francia y todo el que no sea
 * US/LR — usarla aquí pondría los precios de media Europa en galones imperiales. Ésta contesta otra
 * pregunta: *¿en qué unidad se paga en el surtidor?*
 *
 * Y por eso **no devuelve `IMPERIAL` nunca**: no hay país que venda el combustible por galón
 * imperial. El Reino Unido señaliza en millas y habla de mpg imperiales, pero reposta en litros —
 * es el mismo caso que ya obligó a separar [IMPERIAL_COUNTRIES] de [US_GALLON_COUNTRIES].
 */
fun localePumpGallon(): GallonKind? = if (localeUsesUsGallon()) GallonKind.US else null

/** El galón US es de los EE. UU. y de quienes siguen su mercado del automóvil (Liberia lo hace). */
private val US_GALLON_COUNTRIES = setOf("US", "LR")

/**
 * Litros en un galón, exactos por definición. Viven aquí —al lado de [GallonKind], que es quien los
 * nombra— porque el mismo número lo necesitan la frontera de SALIDA (precio por galón) y la de
 * ENTRADA (`OpenVanFuelPricesParser`, que normaliza los catorce países que cotizan así). Un número
 * con dos copias es un número que algún día se escribe distinto en una de ellas.
 */
const val LITRES_PER_US_GALLON = 3.785411784

/** El galón imperial (UK, Canadá histórico): 4,54609 l exactos. Nadie cobra en él; se convierte mpg. */
const val LITRES_PER_IMPERIAL_GALLON = 4.54609
