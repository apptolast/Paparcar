package com.rndeveloper.paparcar.domain.sharedcar

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.SharedCar
import com.rndeveloper.paparcar.domain.model.SharedCarState
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.VehicleSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Una sola respuesta a «dónde está el coche».
 * [SHARED-A-GROUP-YOU-CANNOT-JOIN-IS-NOT-A-GROUP-001]
 */
class SharedCarWhereaboutsTest {

    private companion object {
        const val ME = "uid_me"
        const val MARTA = "uid_marta"
        const val CAR = "car-1"
    }

    private fun gps(atMs: Long) = GpsPoint(40.4, -3.7, 8f, atMs, 0f)

    private fun myParking(atMs: Long) = UserParking(
        id = "own_$atMs",
        userId = ME,
        vehicleId = CAR,
        location = gps(atMs),
        routePolyline = "polyline_local",
    )

    private fun car(state: SharedCarState?) = SharedCar(
        carId = CAR,
        ownerUid = ME,
        displayName = "Ford Focus",
        sizeCategory = VehicleSize.MEDIUM_SUV,
        state = state,
    )

    private fun mirrored(atMs: Long, author: String, name: String? = "Marta") = SharedCarState(
        sessionId = "mirror_$atMs",
        location = gps(atMs),
        parkedAtMs = atMs,
        authorUid = author,
        authorName = name,
    )

    @Test
    fun should_answerFromMyOwnSession_when_theCarIsNotShared() {
        val out = carWhereabouts(myParking(1_000), sharedCar = null, currentUid = ME, vehicleId = CAR)

        assertEquals("own_1000", out?.session?.id)
        assertNull(out?.parkedByName)
    }

    @Test
    fun should_answerNothing_when_thereIsNeitherASessionNorAMirror() {
        assertNull(carWhereabouts(null, car(state = null), ME, CAR))
    }

    @Test
    fun should_nameWhoLeftIt_when_somebodyElseParkedItLast() {
        // El dato que justificaba la card duplicada, ahora dentro de la única respuesta.
        val out = carWhereabouts(myParking(1_000), car(mirrored(2_000, MARTA)), ME, CAR)

        assertEquals("mirror_2000", out?.session?.id)
        assertEquals("Marta", out?.parkedByName)
    }

    @Test
    fun should_keepMyOwnRichCopy_when_iParkedItLast() {
        // Empate o posterior → la local, que trae ruta y provenance.
        val out = carWhereabouts(myParking(3_000), car(mirrored(2_000, MARTA)), ME, CAR)

        assertEquals("own_3000", out?.session?.id)
        assertEquals("polyline_local", out?.session?.routePolyline)
        assertNull(out?.parkedByName)
    }

    @Test
    fun should_preferTheLocalCopy_when_bothSidesHaveTheSameInstant() {
        val out = carWhereabouts(myParking(2_000), car(mirrored(2_000, MARTA)), ME, CAR)

        assertEquals("own_2000", out?.session?.id)
    }

    @Test
    fun should_notSignIt_when_theMirroredParkingIsMyOwn() {
        // Firmar tu propio aparcamiento es ruido, aunque venga de vuelta por el espejo.
        val out = carWhereabouts(null, car(mirrored(2_000, ME, name = "Yo")), ME, CAR)

        assertEquals("mirror_2000", out?.session?.id)
        assertNull(out?.parkedByName)
    }

    @Test
    fun should_speakForANewMember_when_theyHaveNoSessionsOfTheirOwn() {
        // Quien acaba de unirse no tiene nada local: sin el espejo, la card saldría muda.
        val out = carWhereabouts(null, car(mirrored(2_000, MARTA)), currentUid = "uid_new", vehicleId = CAR)

        assertEquals("mirror_2000", out?.session?.id)
        assertEquals("Marta", out?.parkedByName)
    }

    @Test
    fun should_notInventAName_when_theAuthorHasNone() {
        val out = carWhereabouts(null, car(mirrored(2_000, MARTA, name = null)), ME, CAR)

        assertNull(out?.parkedByName)
    }
}
