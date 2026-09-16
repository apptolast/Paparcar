package com.rndeveloper.paparcar.presentation.util

import com.rndeveloper.paparcar.GallonKind
import com.rndeveloper.paparcar.countryPrefersImperialUnits
import com.rndeveloper.paparcar.countryUsesUsGallon
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El consumo entre unidades. [I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001]
 *
 * Lo que se vigila aquí no es la aritmética, es que **el modelo no se entere**: el formulario del
 * alta enseña el consumo guardado y vuelve a guardar lo que queda en el campo, así que si ida y
 * vuelta no son exactamente inversas, editar el nombre de un coche le cambia el consumo.
 */
class ConsumptionFormatTest {

    @Test
    fun should_invertTheMagnitude_when_litresBecomeMpg() {
        // 5,5 l/100 km son 42,8 mpg US y 51,4 mpg imperiales — el MISMO coche. Un galón imperial es
        // mayor, así que rinde más millas: por eso el galón no se puede deducir de «usa millas».
        assertClose(42.77f, litresPer100KmToMpg(5.5f, GallonKind.US))
        assertClose(51.36f, litresPer100KmToMpg(5.5f, GallonKind.IMPERIAL))
    }

    @Test
    fun should_scaleNotInvert_when_theConsumptionIsElectric() {
        // El kWh no cambia; la distancia sí. 18 kWh/100 km → 29 kWh/100 mi.
        assertClose(28.97f, kwhPer100KmToPer100Miles(18f))
    }

    @Test
    fun should_returnTheSameFigure_when_aValueGoesToTheFieldAndBack() {
        // ⛔ El invariante que protege el formulario de alta, en las cuatro combinaciones.
        listOf(4.2f, 5.5f, 9.9f, 18f, 21.7f).forEach { stored ->
            listOf(true, false).forEach { usesLiters ->
                DistanceUnit.entries.forEach { unit ->
                    GallonKind.entries.forEach { gallon ->
                        val shown = consumptionToDisplay(stored, usesLiters, unit, gallon)
                        val back = consumptionFromDisplay(shown, usesLiters, unit, gallon)
                        assertClose(stored, back, "ida y vuelta en $unit/$gallon (litros=$usesLiters)")
                    }
                }
            }
        }
    }

    @Test
    fun should_leaveTheDomainUntouched_when_theUserIsMetric() {
        // En métrico no hay conversión NINGUNA: el número del modelo es el que se enseña.
        assertEquals(5.5f, consumptionToDisplay(5.5f, true, DistanceUnit.METRIC, GallonKind.US))
        assertEquals(18f, consumptionToDisplay(18f, false, DistanceUnit.METRIC, GallonKind.IMPERIAL))
    }

    @Test
    fun should_notBlowUp_when_theStoredFigureIsZero() {
        // Un consumo 0 no es válido y el formulario ya lo filtra, pero una división entre cero aquí
        // se llevaría por delante la ficha del coche entera.
        assertEquals(0f, litresPer100KmToMpg(0f, GallonKind.US))
        assertEquals(0f, mpgToLitresPer100Km(0f, GallonKind.US))
    }

    @Test
    fun should_separateMilesFromGallons_when_aCountryUsesOneAndNotTheOther() {
        // El Reino Unido es el caso que obliga a que sean dos preguntas: millas en la carretera,
        // galón imperial en el surtidor.
        assertTrue(countryPrefersImperialUnits("GB"), "GB señaliza en millas")
        assertTrue(!countryUsesUsGallon("GB"), "GB NO usa el galón US")
        assertTrue(countryUsesUsGallon("US"), "US usa el galón US")
        assertTrue(!countryUsesUsGallon("ES"), "ES no usa galones")
    }

    private fun assertClose(expected: Float, actual: Float, message: String = "") {
        assertTrue(
            abs(expected - actual) < TOLERANCE,
            "$message — esperaba ~$expected y llegó $actual",
        )
    }

    private companion object {
        const val TOLERANCE = 0.02f
    }
}
