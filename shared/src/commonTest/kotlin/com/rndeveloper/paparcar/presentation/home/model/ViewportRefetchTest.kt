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
        // (0,0) es lo que un mapa reporta antes de tener cámara — medido en device como una
        // petición real («0 stations around 0.0,0.0»). Misma trampa que cerró
        // SPOT-A-REPORT-WITHOUT-A-PLACE. Es la ÚNICA razón por la que un carril se niega a preguntar.
        assertEquals(false, ViewportRefetch.isAPlace(0.0, 0.0))
        assertEquals(true, ViewportRefetch.isAPlace(40.4168, -3.7038))
        // Un sitio con una coordenada a cero NO es el golfo: el meridiano y el ecuador son sitios.
        assertEquals(true, ViewportRefetch.isAPlace(0.0, -3.7038))
        assertEquals(true, ViewportRefetch.isAPlace(51.4778, 0.0))
    }

    /**
     * ⛔ EL TESTIGO DE LA ISSUE #44, en su forma final. Ya no hay tope de distancia al usuario:
     * arrastrar el mapa hasta otra ciudad **pregunta allí**, igual que abrir el carril.
     * [NEARBY-A-DELIBERATE-LOOK-IS-NOT-A-PAN-001]
     *
     * Lo que impide la tormenta que el tope decía impedir no es este predicado, son tres guardas
     * que ya estaban: el debounce del asentamiento (un arrastre entero = UNA petición), el umbral
     * de media radio / zona entera (moverse poco no pregunta nada) y la caché por celda en Room.
     */
    @Test
    fun should_followTheCameraAnywhere_when_theUserDragsToAnotherCity() {
        // Getafe (~13 km), Toledo (~65 km) y Valencia (~300 km) desde Madrid: los tres son SITIOS,
        // así que a los tres se les pregunta. Valencia caía fuera con el tope de 150 km, y con el
        // de 20 km caía fuera hasta Toledo — la queja original de la issue.
        assertEquals(true, ViewportRefetch.isAPlace(40.3057, -3.7329))
        assertEquals(true, ViewportRefetch.isAPlace(39.8628, -4.0273))
        assertEquals(true, ViewportRefetch.isAPlace(39.4699, -0.3763))
        // París: otro país, mismo trato. Quien arrastra hasta allí quiere ver lo que hay allí.
        assertEquals(true, ViewportRefetch.isAPlace(48.8566, 2.3522))
    }
}
