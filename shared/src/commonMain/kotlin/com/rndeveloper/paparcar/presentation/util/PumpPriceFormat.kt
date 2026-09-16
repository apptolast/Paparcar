package com.rndeveloper.paparcar.presentation.util

import androidx.compose.runtime.compositionLocalOf
import com.rndeveloper.paparcar.GallonKind
import com.rndeveloper.paparcar.LITRES_PER_IMPERIAL_GALLON
import com.rndeveloper.paparcar.LITRES_PER_US_GALLON
import com.rndeveloper.paparcar.localePumpGallon

/**
 * El precio del combustible, tal y como se ENSEÑA y como se TECLEA.
 * [I18N-A-PRICE-IS-QUOTED-IN-THE-UNIT-YOU-BUY-IT-001]
 *
 * ⛔ **El dominio se queda en litros y no se entera de nada de esto.** `Vehicle.fuelPricePerLiter`,
 * `StationPrice.perUnit`, `FuelPrice.perUnit`, `AverageFuelPriceTable` y los umbrales de las dos
 * alertas siguen siendo €/litro. El KDoc de `AverageFuelPriceTable` ya dejó escrita la razón con su
 * medida: *un galón llegando a esa clase infla un viaje en EE. UU. ×3,79 en silencio*. Esto es la
 * frontera de SALIDA, simétrica a la de ENTRADA que `OpenVanFuelPricesParser` lleva desde el
 * principio — el feed cotiza catorce países por galón y los normaliza al leerlos.
 *
 * Hermano de [consumptionToDisplay] y **en su propio fichero a propósito**: el consumo es una
 * magnitud POR DISTANCIA que hay que invertir (l/100 km ⇄ mpg, donde más es peor y menos es mejor);
 * esto es dinero POR VOLUMEN, un factor limpio. Comparten borde, no aritmética.
 */

/**
 * Precio por litro → precio en la unidad que se paga aquí.
 *
 * [gallon] `null` significa «este surtidor cobra por litro», que es el caso de 193 países: la
 * identidad, sin tocar el número. Lo resuelve [com.rndeveloper.paparcar.localePumpGallon].
 */
fun pumpPriceToDisplay(perLitre: Double, gallon: GallonKind?): Double =
    if (gallon == null) perLitre else perLitre * litresPerGallon(gallon)

/**
 * Lo que el usuario tecleó, de vuelta a €/litro.
 *
 * ⛔ Inversa EXACTA de [pumpPriceToDisplay], y las dos van SIEMPRE en pareja. El formulario de
 * vehículo enseña el precio guardado y vuelve a guardar lo que quede en el campo: convertir sólo al
 * guardar multiplicaría el precio por 3,785 cada vez que alguien abre la ficha a cambiarle el nombre
 * al coche. Es el mismo contrato que [consumptionFromDisplay] escribió para el consumo, y por la
 * misma razón.
 */
fun pumpPriceFromDisplay(shown: Double, gallon: GallonKind?): Double =
    if (gallon == null) shown else shown / litresPerGallon(gallon)

/**
 * La unidad del surtidor, disponible para toda la UI — hermano de `LocalDistanceUnit` y por la misma
 * razón: un formateador que se lee el locale por su cuenta no se puede poner a prueba ni enseñar en
 * la galería de estados, y una superficie que no se puede enseñar en galones es la que algún día
 * vuelve a los litros sin que nadie lo vea.
 *
 * Su valor por defecto es [localePumpGallon], así que en producción no hace falta proveerlo para que
 * diga la verdad; `App.kt` lo provee igualmente, donde ya se provee el sistema de unidades.
 */
val LocalPumpGallon = compositionLocalOf { localePumpGallon() }

private fun litresPerGallon(gallon: GallonKind): Double = when (gallon) {
    GallonKind.US -> LITRES_PER_US_GALLON
    GallonKind.IMPERIAL -> LITRES_PER_IMPERIAL_GALLON
}
