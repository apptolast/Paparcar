package com.rndeveloper.paparcar.ui.components

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La separación entre un marcador y su punto es un **rasgo de la familia**, y lo que la familia
 * comparte es el aire que se VE.
 * [MAP-EVERY-MARKER-SITS-THE-SAME-HEIGHT-OVER-ITS-DOT-001]
 * [MAP-THE-FAMILY-LINE-IS-THE-ONE-YOU-CAN-SEE-001]
 *
 * Existe porque el rasgo estaba escrito sobre el borde del RELLENO (63) y el puck de plaza
 * sobresale 1,6 u por debajo con su contorno de papel: la plaza enseñaba 3,4 u de aire y el disco
 * de parking los 5 enteros. Dos marcadores de la misma familia con un 47 % de diferencia, y la
 * constante decía que eran iguales.
 */
class MarkerGeometryTest {

    private companion object { const val EPS = 0.01f }

    /** El borde inferior VISIBLE de un cuerpo: la línea de la familia más lo que su silueta
     *  sobresale por debajo. */
    private fun visibleBottom(overshoot: Float) = MARKER_BODY_BOTTOM_Y + overshoot

    /** El aire que se VE entre ese borde y la tapa del punto. */
    private fun visibleAir(overshoot: Float, dotCy: Float) =
        (dotCy - SPOT_DOT_R) - visibleBottom(overshoot)

    @Test
    fun should_showTheSameAir_when_theBodiesHaveDifferentSilhouettes() {
        val spot = visibleAir(SPOT_BODY_OVERSHOOT_U, SPOT_DOT_CY)
        val nearby = visibleAir(NEARBY_BODY_OVERSHOOT_U, NEARBY_DOT_CY)

        // Tolerancia de 0,01 u: a 46 dp de ancho eso es 0,006 dp, o sea ruido de coma flotante de
        // sumar 63 + 1,6 + 3,4. Lo que se comprueba es la geometría, no el binario del Float.
        assertEquals(MARKER_DOT_GAP, spot, EPS, "la plaza enseña el aire de la familia")
        assertEquals(MARKER_DOT_GAP, nearby, EPS, "y el disco/cápsula de al lado, el mismo")
        assertTrue(abs(spot - nearby) < 0.01f, "ninguno de los dos lleva más aire que el otro")
    }

    @Test
    fun should_leaveTheSpotPuckExactlyWhereItWas_when_theFamilyLineMoves() {
        // ⛔ El marcador que el user aprobó NO se mueve: 3,4 de aire visible es precisamente lo que
        // la plaza ya enseñaba con el `MARKER_DOT_GAP = 5` medido sobre el relleno. Si este testigo
        // cae, el arreglo del parking se ha llevado por delante la plaza, que es el marcador que
        // más se mira.
        assertEquals(72.5f, SPOT_DOT_CY)
    }

    @Test
    fun should_sitTheNearbyDotCloser_than_theSpotDot() {
        // La consecuencia medible del arreglo, y la dirección correcta: el punto del parking SUBE
        // hacia su cuerpo (menos y), porque era él quien llevaba aire de más.
        assertTrue(
            NEARBY_DOT_CY < SPOT_DOT_CY,
            "el punto de un cuerpo sin contorno se acerca, no se aleja",
        )
        assertEquals(SPOT_BODY_OVERSHOOT_U, SPOT_DOT_CY - NEARBY_DOT_CY, EPS)
    }

    @Test
    fun should_deriveTheOvershoot_from_theStrokeThatCausesIt() {
        // Media pluma del contorno de papel cae fuera del relleno: es de donde sale el 1,6, y por
        // eso no se escribe como literal. Cambiar el grosor del contorno tiene que mover el punto.
        assertEquals(PUCK_PAPER_STROKE_U / 2f, SPOT_BODY_OVERSHOOT_U)
        assertEquals(0f, NEARBY_BODY_OVERSHOOT_U, "el disco no lleva contorno en reposo")
    }
}
