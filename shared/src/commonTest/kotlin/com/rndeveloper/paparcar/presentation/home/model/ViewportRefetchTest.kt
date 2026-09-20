package com.rndeveloper.paparcar.presentation.home.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** The reusable half of "a layer shows what you look at". [FUEL-LAYER-FOLLOWS-THE-CAMERA-001] */
class ViewportRefetchTest {

    @Test
    fun should_ask_when_thereIsNoAnchorYet() {
        assertEquals(true, ViewportRefetch.shouldRefetch(null, null, 40.4168, -3.7038, 5_000))
    }

    @Test
    fun should_stayQuiet_when_theCameraSettledUnderHalfTheRadiusAway() {
        // ~1.1 km due north of the anchor — well under 2.5 km.
        assertEquals(
            false,
            ViewportRefetch.shouldRefetch(40.4168, -3.7038, 40.4268, -3.7038, 5_000),
        )
    }

    @Test
    fun should_refuseToAsk_at_theGulfOfGuinea() {
        // (0,0) is what a map reports before it has a camera — measured on device as an actual
        // request ("0 stations around 0.0,0.0"). Same trap SPOT-A-REPORT-WITHOUT-A-PLACE closed.
        assertEquals(false, ViewportRefetch.isWorthAsking(0.0, 0.0, 40.4168, -3.7038))
        assertEquals(false, ViewportRefetch.isWorthAsking(0.0, 0.0, null, null))
    }

    @Test
    fun should_followTheCamera_asFarAsTodaysTrip() {
        val madridLat = 40.4168
        val madridLon = -3.7038
        // Getafe (~13 km) y Alcalá (~29 km): el pueblo de al lado, dentro de toda la vida.
        assertEquals(true, ViewportRefetch.isWorthAsking(40.3057, -3.7329, madridLat, madridLon))
        assertEquals(true, ViewportRefetch.isWorthAsking(40.4820, -3.3635, madridLat, madridLon))
        // Toledo (~65 km): el destino de hoy. Con el tope en 20 km caía fuera, que es la queja de
        // la issue #44 — «salgo de casa y quiero ver los parkings de la otra ciudad».
        assertEquals(true, ViewportRefetch.isWorthAsking(39.8628, -4.0273, madridLat, madridLon))
        // Valencia (~300 km): eso ya no es seguir la cámara, es otro viaje. Arrastrar hasta allí
        // son cientos de asentamientos, y para eso está `deliberate`.
        assertEquals(false, ViewportRefetch.isWorthAsking(39.4699, -0.3763, madridLat, madridLon))
        // No fix yet: nothing to measure against, so the camera is trusted.
        assertEquals(true, ViewportRefetch.isWorthAsking(48.8566, 2.3522, null, null))
    }

    /**
     * ⛔ EL TESTIGO DE LA ISSUE #44. Pedir un sitio no es arrastrar hasta él: quien busca otra
     * ciudad y abre el carril ha hecho UNA pregunta, y contestarle «demasiado lejos» es negarle lo
     * único que pidió. [NEARBY-A-DELIBERATE-LOOK-IS-NOT-A-PAN-001]
     */
    @Test
    fun should_answer_when_theUserDeliberatelyAsksAboutAFarPlace() {
        val madridLat = 40.4168
        val madridLon = -3.7038
        // Valencia, a 300 km: bloqueada al arrastrar, contestada al pedirla.
        assertEquals(false, ViewportRefetch.isWorthAsking(39.4699, -0.3763, madridLat, madridLon))
        assertEquals(
            true,
            ViewportRefetch.isWorthAsking(39.4699, -0.3763, madridLat, madridLon, deliberate = true),
        )
    }

    /** Lo único que ni pedirlo salva: (0,0) no es un sitio, es lo que dice un mapa sin cámara. */
    @Test
    fun should_stillRefuse_theGulfOfGuinea_evenWhenAsked() {
        assertEquals(false, ViewportRefetch.isWorthAsking(0.0, 0.0, 40.4168, -3.7038, deliberate = true))
    }

    @Test
    fun should_askAgain_when_theCameraSettledPastHalfTheRadius() {
        // ~3.3 km due north — past the 2.5 km line, and the visible edge would be running dry.
        assertEquals(
            true,
            ViewportRefetch.shouldRefetch(40.4168, -3.7038, 40.4468, -3.7038, 5_000),
        )
    }
}
