package com.rndeveloper.paparcar.presentation.util

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * El formateador que los radios del copy no tenían.
 * [I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001]
 *
 * Los dos radios que abrieron el ticket —el de gasolineras (5 km) y el de parkings (3 km)— tienen su
 * caso aquí en los dos sistemas, porque el fallo era justo ése: la frase decía «km» a todo el mundo.
 */
class FormatRadiusTest {

    @Test
    fun should_dropTheTenthOfARoundFigure_when_theRadiusIsMetric() {
        // El radio de parkings: 3 km, no «3.0 km». La cifra la elegimos nosotros, no se midió.
        assertEquals("3 km", formatRadius(3_000f, DistanceUnit.METRIC))
        assertEquals("5 km", formatRadius(5_000f, DistanceUnit.METRIC))
        assertEquals("20 km", formatRadius(20_000f, DistanceUnit.METRIC))
    }

    @Test
    fun should_keepTheTenthItEarned_when_theFigureIsNotRound() {
        // Aquí el decimal SÍ dice algo, así que se queda — en los dos sistemas.
        assertEquals("1.5 km", formatRadius(1_500f, DistanceUnit.METRIC))
        assertEquals("1.9 mi", formatRadius(3_000f, DistanceUnit.IMPERIAL))
        assertEquals("3.1 mi", formatRadius(5_000f, DistanceUnit.IMPERIAL))
    }

    @Test
    fun should_speakFeet_when_theRadiusIsShortAndTheUserChoseImperial() {
        // El radio de una zona (250 m por defecto) y el de una sesión aproximada viven en esta banda.
        assertEquals("250 m", formatRadius(250f, DistanceUnit.METRIC))
        assertEquals("820 ft", formatRadius(250f, DistanceUnit.IMPERIAL))
    }

    @Test
    fun should_readAHeightInFeetAndInches_when_theUserChoseImperial() {
        // El gálibo de un garaje. ⛔ Nunca «6.9 ft»: una altura se dice en pies y PULGADAS.
        // [I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001]
        assertEquals("2.10 m", formatHeight(2.10f, DistanceUnit.METRIC))
        assertEquals("6 ft 11 in", formatHeight(2.10f, DistanceUnit.IMPERIAL))
        // Los dos decimales se mantienen aunque el segundo sea cero: así está pintado en la barra.
        assertEquals("2.00 m", formatHeight(2f, DistanceUnit.METRIC))
        // Y las pulgadas desaparecen sólo cuando de verdad no hay.
        assertEquals("5 ft", formatHeight(1.524f, DistanceUnit.IMPERIAL))
    }

    @Test
    fun should_matchFormatDistanceExceptForTheTenth_when_bothAreAskedTheSameRadius() {
        // ⛔ Un solo convertidor. Si esto deja de cumplirse es que `formatRadius` se ha convertido en
        // el segundo formateador que el ticket existe para no tener.
        listOf(250f, 1_500f, 3_000f, 5_000f, 20_000f).forEach { meters ->
            DistanceUnit.entries.forEach { unit ->
                assertEquals(
                    formatDistance(meters, unit).replace(".0 ", " "),
                    formatRadius(meters, unit),
                    "$meters m en $unit divergió de formatDistance",
                )
            }
        }
    }
}
