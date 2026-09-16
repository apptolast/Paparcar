package com.rndeveloper.paparcar.domain.sharedcar

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Dos móviles en el mismo coche. [SHARED-A-CAR-THAT-LEAVES-IS-NEWS-TOO-001]
 *
 * Lo que este predicado impide es una estadística falsa, no un aviso de más: si el mismo frenazo se
 * espeja dos veces, el grupo cuenta el doble de kilómetros y el doble de tiempo buscando plaza. Por
 * eso los casos que importan son los dos bordes — el que tiene que juntar (el carril lento, que
 * confirma minutos después) y el que NO puede juntar (dos aparcamientos de verdad).
 */
class SharedCarSameParkingTest {

    // Madrid. 0,001° de latitud ≈ 111 m.
    private val lat = 40.4168
    private val lon = -3.7038
    private val anchor = 1_700_000_000_000L

    private fun mark(
        sessionId: String = "s-mine",
        atMs: Long = anchor,
        latitude: Double = lat,
        longitude: Double = lon,
    ) = ParkingMark(sessionId, atMs, latitude, longitude)

    @Test
    fun should_beTheSameParking_when_bothPhonesAnchoredTheSameStop() {
        assertTrue(isSameParking(mine = mark(), theirs = mark(sessionId = "s-theirs")))
    }

    @Test
    fun should_beTheSameParking_when_theSlowLaneConfirmedMinutesLater() {
        // El caso que más duele y el que justifica comparar ANCLAS: el móvil con Bluetooth confirma
        // en segundos y el del Coordinator tarda minutos en tener pasos y egress. Las dos anclas
        // siguen siendo el mismo frenazo, con unos segundos de diferencia por el congelado.
        val theirs = mark(sessionId = "s-theirs", atMs = anchor + 40_000L)
        assertTrue(isSameParking(mine = mark(), theirs = theirs))
    }

    @Test
    fun should_beTheSameParking_when_theTwoFixesDisagreeByAStreet() {
        // ~55 m: dos GPS urbanos del mismo hueco, no dos huecos.
        val theirs = mark(sessionId = "s-theirs", latitude = lat + 0.0005)
        assertTrue(isSameParking(mine = mark(), theirs = theirs))
    }

    @Test
    fun should_notBeTheSameParking_when_itIsMyOwnSessionMirroredBack() {
        // El espejo me devuelve lo que yo escribí. Tomarlo por «otro móvil» haría que un segundo
        // guardado de MI sesión —la dirección que llega tarde— dejara de espejarse.
        assertFalse(isSameParking(mine = mark(), theirs = mark()))
    }

    @Test
    fun should_notBeTheSameParking_when_theCarWasParkedAgainLater() {
        val theirs = mark(sessionId = "s-theirs", atMs = anchor + SAME_PARKING_WINDOW_MS + 1)
        assertFalse(isSameParking(mine = mark(), theirs = theirs))
    }

    @Test
    fun should_notBeTheSameParking_when_theyAreTwoDifferentSpots() {
        // ~330 m de separación dentro de la ventana: sigue siendo raro, pero no lo juntamos. El
        // radio existe para perdonar un fix malo, no para fusionar dos sitios distintos.
        val theirs = mark(sessionId = "s-theirs", latitude = lat + 0.003)
        assertFalse(isSameParking(mine = mark(), theirs = theirs))
    }

    @Test
    fun should_beSymmetric_when_askedFromEitherPhone() {
        // Los dos móviles corren el mismo código con los papeles cambiados; si la respuesta no
        // fuera la misma, el aparcamiento se espejaría dos veces o ninguna según quién llegue antes.
        val a = mark(sessionId = "s-a", atMs = anchor)
        val b = mark(sessionId = "s-b", atMs = anchor + 30_000L, latitude = lat + 0.0004)
        assertTrue(isSameParking(mine = a, theirs = b))
        assertTrue(isSameParking(mine = b, theirs = a))
    }
}
