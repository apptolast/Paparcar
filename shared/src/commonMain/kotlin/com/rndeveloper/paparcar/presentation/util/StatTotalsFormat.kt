package com.rndeveloper.paparcar.presentation.util

import com.rndeveloper.paparcar.GallonKind
import com.rndeveloper.paparcar.LITRES_PER_IMPERIAL_GALLON
import com.rndeveloper.paparcar.LITRES_PER_US_GALLON

/**
 * Los TOTALES de un periodo —combustible quemado, CO₂ emitido— en la unidad que este usuario lee.
 * [STATS-A-VOLUME-BURNED-IS-QUOTED-IN-LITRES-EVERYWHERE-001]
 *
 * El tercer borde de unidad de la casa, y el último que faltaba. Los otros dos:
 * [consumptionToDisplay] (magnitud POR distancia, se invierte) y [pumpPriceToDisplay] (dinero POR
 * volumen, factor limpio). Éste son magnitudes a secas, acumuladas sobre el periodo.
 *
 * ## ⛔ Qué galón, y por qué no es el del surtidor
 *
 * Lo decide la aritmética, no el gusto. Los litros quemados **son el consumo integrado sobre la
 * distancia** (`km × consumo/100`), así que las tres cifras tienen que CERRAR en pantalla:
 * *distancia ÷ consumo = volumen*. Un británico con el ajuste en imperial lee «1.234 mi» y «51 mpg»;
 * 1234 ÷ 51 son **24,2 galones imperiales**, y si el volumen usara el galón del SURTIDOR
 * (`localePumpGallon()`, que en el Reino Unido es `null` porque allí se reposta en litros) leería
 * «142 L» al lado y la fila no cerraría.
 *
 * O sea: el eje es el del CONSUMO —el ajuste imperial más [com.rndeveloper.paparcar.localeGallonKind]—,
 * no el del precio. Consecuencia que se acepta a propósito: en el Reino Unido la app cotiza el
 * surtidor en litros y el depósito gastado en galones imperiales. Suena raro y es correcto, porque
 * contestan dos preguntas distintas, que es exactamente lo que `LocaleUnits.kt` separa.
 *
 * ## Sin vuelta
 *
 * ⛔ **No hay inversa, y la ausencia es la nota.** El consumo y el precio la necesitan porque el
 * usuario los TECLEA y el formulario se los devuelve; estos dos sólo se leen — se derivan de los
 * kilómetros medidos y de lo que ya declaró. Escribir una `fromDisplay` aquí sería abrir una puerta
 * por la que sólo puede entrar una unidad equivocada.
 */

/** Litros quemados → lo que se enseña. En métrico no toca nada. */
fun burnedVolumeToDisplay(litres: Double, unit: DistanceUnit, gallon: GallonKind): Double =
    if (unit == DistanceUnit.METRIC) litres else litres / litresPerGallon(gallon)

/**
 * Kilos de CO₂ → lo que se enseña. La masa **no tiene galón**: una libra es una libra en Detroit y
 * en Kent, así que aquí sólo manda el ajuste y no hace falta preguntar por el gas.
 */
fun emittedMassToDisplay(kg: Double, unit: DistanceUnit): Double =
    if (unit == DistanceUnit.METRIC) kg else kg * POUNDS_PER_KG

private fun litresPerGallon(gallon: GallonKind): Double = when (gallon) {
    GallonKind.US -> LITRES_PER_US_GALLON
    GallonKind.IMPERIAL -> LITRES_PER_IMPERIAL_GALLON
}

/** Libras en un kilo, exactas por definición del avoirdupois (1 lb = 0,45359237 kg). */
private const val POUNDS_PER_KG = 2.204622621848776
