package com.rndeveloper.paparcar.presentation.util

import com.rndeveloper.paparcar.GallonKind
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Los totales del periodo entre unidades. [STATS-A-VOLUME-BURNED-IS-QUOTED-IN-LITRES-EVERYWHERE-001]
 *
 * Lo que se vigila aquí no es la aritmética de cada conversión por separado —eso es un factor y no
 * se equivoca nadie—, es que **las tres cifras de la pantalla cierren entre ellas**. Es la razón por
 * la que este borde usa el galón del CONSUMO y no el del surtidor, y el único sitio donde esa
 * decisión se puede comprobar.
 */
class StatTotalsFormatTest {

    /**
     * ⛔ **EL TESTIGO.** Distancia ÷ consumo = volumen, y tiene que seguir siendo verdad DESPUÉS de
     * convertir las tres a la unidad del lector. Con el galón equivocado la igualdad se rompe por un
     * 20 % y nada más: ninguna cifra parece absurda, simplemente la fila deja de cuadrar.
     *
     * El caso británico es el que separa las dos preguntas — señaliza en millas y habla de galones
     * imperiales, pero reposta en LITROS, así que `localePumpGallon()` diría `null` aquí y dejaría
     * los litros crudos al lado de unos mpg imperiales.
     */
    @Test
    fun `should_keepTheRowConsistent_when_distanceConsumptionAndVolumeAreAllImperial`() {
        GallonKind.entries.forEach { gallon ->
            val km = 500.0
            val litresPer100 = 5.5f
            val litres = km * litresPer100 / 100.0

            val miles = km * KM_TO_MILES
            val mpg = litresPer100KmToMpg(litresPer100, gallon).toDouble()
            val gallons = burnedVolumeToDisplay(litres, DistanceUnit.IMPERIAL, gallon)

            assertClose(gallons, miles / mpg, "millas ÷ mpg tiene que dar los galones ($gallon)")
        }
    }

    /** Y en métrico la misma fila cierra sin que nada se convierta: km ÷ (l/100) × 100 = litros. */
    @Test
    fun `should_keepTheRowConsistent_when_everythingStaysMetric`() {
        val litres = 500.0 * 5.5 / 100.0
        assertEquals(litres, burnedVolumeToDisplay(litres, DistanceUnit.METRIC, GallonKind.IMPERIAL))
        assertEquals(27.5, litres)
    }

    /** 27,5 l son 7,26 galones US y 6,05 imperiales — el imperial es mayor, así que caben menos. */
    @Test
    fun `should_useTheBiggerGallon_when_theReaderIsBritish`() {
        assertClose(7.264, burnedVolumeToDisplay(27.5, DistanceUnit.IMPERIAL, GallonKind.US))
        assertClose(6.049, burnedVolumeToDisplay(27.5, DistanceUnit.IMPERIAL, GallonKind.IMPERIAL))
    }

    /** La masa no tiene galón: sólo el ajuste. 64 kg de CO₂ son 141,1 libras en los dos sitios. */
    @Test
    fun `should_convertMassWithoutAskingForAGallon`() {
        assertClose(141.1, emittedMassToDisplay(64.0, DistanceUnit.IMPERIAL))
        assertEquals(64.0, emittedMassToDisplay(64.0, DistanceUnit.METRIC))
    }

    private fun assertClose(expected: Double, actual: Double, message: String = "") {
        assertTrue(abs(expected - actual) < TOLERANCE, "$message — esperado $expected, obtenido $actual")
    }

    private companion object {
        const val TOLERANCE = 0.01
        const val KM_TO_MILES = 1000.0 / 1609.344
    }
}
