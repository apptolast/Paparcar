package com.rndeveloper.paparcar.presentation.util

import com.rndeveloper.paparcar.GallonKind
import com.rndeveloper.paparcar.countryUsesUsGallon
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El precio entre unidades. [I18N-A-PRICE-IS-QUOTED-IN-THE-UNIT-YOU-BUY-IT-001]
 *
 * Lo que se vigila aquí es lo mismo que en [ConsumptionFormatTest] y por la misma razón: **el modelo
 * no se entera**. Un galón llegando a `Vehicle.fuelPricePerLiter` infla el gasto de todos los viajes
 * ×3,785 sin síntoma ninguno — el número sigue pareciendo correcto en todas las pantallas.
 */
class PumpPriceFormatTest {

    @Test
    fun should_quotePerGallon_when_thePumpChargesByTheGallon() {
        // 0,922 €/l son 3,49 por galón US. Es el número que un estadounidense reconoce.
        assertClose(3.4899, pumpPriceToDisplay(0.9219, GallonKind.US))
        // El imperial es mayor, así que el mismo litro sale más caro por galón.
        assertClose(4.1912, pumpPriceToDisplay(0.9219, GallonKind.IMPERIAL))
    }

    @Test
    fun should_leaveTheFigureAlone_when_thePumpChargesByTheLitre() {
        // ⛔ 193 países. `null` es la identidad, no «no sé»: un precio sin conversión es el caso
        // NORMAL, y por eso no puede costar ni un redondeo.
        assertEquals(1.879, pumpPriceToDisplay(1.879, gallon = null))
        assertEquals(1.879, pumpPriceFromDisplay(1.879, gallon = null))
    }

    @Test
    fun should_returnTheSamePrice_when_aValueGoesToTheFieldAndBack() {
        // ⛔ El invariante que protege el formulario: enseña lo guardado y vuelve a guardar lo que
        // queda en el campo. Si ida y vuelta no son inversas, editarle el nombre al coche le
        // multiplica el precio por 3,785 cada vez.
        listOf(0.9219, 1.459, 1.879, 2.049).forEach { stored ->
            listOf(null, GallonKind.US, GallonKind.IMPERIAL).forEach { gallon ->
                val shown = pumpPriceToDisplay(stored, gallon)
                assertClose(stored, pumpPriceFromDisplay(shown, gallon), "ida y vuelta con $gallon")
            }
        }
    }

    @Test
    fun should_settleAtOnce_when_theFieldRoundsToTheThousandth() {
        // El campo escribe milésimas, así que la ida y vuelta no es exacta: es un PUNTO FIJO. El
        // primer guardado desplaza el precio menos de una milésima de céntimo por litro, y a partir
        // de ahí ya no se mueve nunca más — que es lo que hace el desvío aceptable.
        val stored = 1.879
        val firstSave = pumpPriceFromDisplay(roundToThousandth(pumpPriceToDisplay(stored, GallonKind.US)), GallonKind.US)
        assertTrue(abs(firstSave - stored) < 0.0001, "deriva del primer guardado: $firstSave")
        val secondSave = pumpPriceFromDisplay(roundToThousandth(pumpPriceToDisplay(firstSave, GallonKind.US)), GallonKind.US)
        assertEquals(firstSave, secondSave, "el segundo guardado ya no puede mover el precio")
    }

    /**
     * ⛔ **El guard del ticket.** La unidad del SURTIDOR no es la del `mpg`, y el Reino Unido es el
     * caso que las separa: señaliza en millas, habla de galones imperiales y cobra en litros. Si
     * algún día alguien resuelve esto con `localeGallonKind()` —que devuelve `IMPERIAL` para España,
     * Francia y todo el mundo— media Europa empieza a leer sus precios por galón imperial.
     */
    @Test
    fun should_chargeByTheLitre_when_theCountryIsBritishOrEuropean() {
        assertTrue(countryUsesUsGallon("US"))
        assertTrue(countryUsesUsGallon("LR"))
        assertFalse(countryUsesUsGallon("GB"))
        assertFalse(countryUsesUsGallon("ES"))
        assertFalse(countryUsesUsGallon("FR"))
        assertFalse(countryUsesUsGallon(""))
    }

    private fun roundToThousandth(value: Double): Double =
        kotlin.math.round(value * THOUSAND) / THOUSAND

    private fun assertClose(expected: Double, actual: Double, message: String = "") {
        assertTrue(abs(expected - actual) < TOLERANCE, "$message — esperado $expected, obtenido $actual")
    }

    private companion object {
        const val TOLERANCE = 0.001
        const val THOUSAND = 1_000.0
    }
}
