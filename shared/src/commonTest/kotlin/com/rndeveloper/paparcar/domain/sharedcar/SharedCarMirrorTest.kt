package com.rndeveloper.paparcar.domain.sharedcar

import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.PlaceCategory
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.model.UserParking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The privacy promise of the shared car, as a test. [VEH-SHARED-001]
 *
 * ⛔ The real guard is the TYPE — `SharedCarSession` has no field for a route — so what is checked
 * here is the other half: that the whitelist copies the core FAITHFULLY, and that the fix it
 * publishes carries no detection provenance. A payload that silently lost the address would be as
 * broken as one that leaked the trace, and only one of the two is impossible by construction.
 */
class SharedCarMirrorTest {

    private val session = UserParking(
        id = "s-1",
        userId = "marta",
        vehicleId = "car-1",
        location = GpsPoint(
            latitude = 40.4,
            longitude = -3.7,
            accuracy = 8f,
            timestamp = 1_000L,
            speed = 13.5f,
            provider = "gps",
            satelliteCount = 11,
        ),
        isActive = true,
        address = AddressInfo(street = "Ponzano 14", city = "Madrid", region = null, country = "España"),
        placeInfo = PlaceInfo(name = "Mercadona", category = PlaceCategory.SUPERMARKET),
        // Everything below is the personal record, and none of it may travel.
        routePolyline = "abc_encoded_polyline",
        routeSnapped = true,
        routeDistanceMeters = 4_200f,
        searchTimeMs = 360_000L,
        armEvidence = "vehicle_enter",
        detectionPath = "steps+egress",
        detectionReliability = 0.9f,
    )

    private fun payload(of: UserParking = session, shareRoutes: Boolean = true) =
        sharedCarMirrorPayload(
            of,
            authorUid = "marta",
            authorName = "Marta",
            nowMs = 5_000L,
            shareRoutes = shareRoutes,
        )

    @Test
    fun should_leaveTheRouteOnThePhone_when_theAuthorDoesNotShareRoutes() {
        val payload = payload(shareRoutes = false)

        // The geometry is the only thing the switch withholds, and it withholds it BEFORE the
        // upload — not on reading, which is where it used to be caught after travelling anyway.
        // [PRIV-A-ROUTE-LEAVES-THE-PHONE-BY-ONE-DOOR-001]
        assertNull(payload.routePolyline)
        assertFalse(payload.routeSnapped)
        assertNull(payload.routeInferredSpans)
        // ⛔ The figures still travel: they say how much, not where, and they are what the group's
        // statistics are made of.
        assertEquals(4_200f, payload.routeDistanceMeters)
        assertEquals(360_000L, payload.searchTimeMs)
        assertEquals(40.4, payload.location.latitude)
    }

    @Test
    fun should_carryWhereAndWhenAndWho_when_mirroringAParking() {
        val payload = payload()

        assertEquals("s-1", payload.sessionId)
        assertEquals("marta", payload.authorUid)
        assertEquals("Marta", payload.authorName)
        assertEquals(40.4, payload.location.latitude)
        assertEquals(-3.7, payload.location.longitude)
        assertEquals(8f, payload.location.accuracy)
        assertEquals(1_000L, payload.parkedAtMs)
        assertEquals("Ponzano 14", payload.address?.street)
        assertEquals("Mercadona", payload.placeInfo?.name)
        assertTrue(payload.isActive)
        assertEquals(5_000L, payload.updatedAtMs)
    }

    @Test
    fun should_stripTheFixProvenance_when_mirroringAParking() {
        val payload = payload()

        // Speed, provider and satellites answer "how did this phone decide", which is detection
        // business and would also say how the author was driving.
        assertEquals(0f, payload.location.speed)
        assertEquals(null, payload.location.provider)
        assertEquals(null, payload.location.satelliteCount)
    }

    @Test
    fun should_notEvenMentionTheTrace_when_serialisedAsText() {
        // A blunt instrument on purpose: whatever anybody adds to the payload later, the polyline,
        // the path and the arm evidence must not be findable anywhere in it.
        val printed = payload().toString()

        assertFalse(printed.contains("steps+egress"))
        assertFalse(printed.contains("vehicle_enter"))
    }

    @Test
    fun should_storeTheRouteAndTheFigures_whateverTheAuthorShows() {
        // ⛔ La geometría SIEMPRE se guarda (user, 15-09): es lo que permite volver a enseñar los
        // viejos viajes cuando alguien vuelve a encender el interruptor, y lo que deja que las
        // cifras del grupo se puedan recalcular. Lo que el interruptor decide es qué se VE, y eso
        // se resuelve al leer — ver `asHistoryEntry`.
        val mirrored = payload()

        assertEquals("abc_encoded_polyline", mirrored.routePolyline)
        assertTrue(mirrored.routeSnapped)
        assertEquals(4_200f, mirrored.routeDistanceMeters)
        assertEquals(360_000L, mirrored.searchTimeMs)
    }

    @Test
    fun should_hideTheLineButKeepTheDistance_when_theAuthorHasRoutesHidden() {
        val hidden = payload().asHistoryEntry(vehicleId = "car-1", showRoute = false)

        assertNull(hidden.routePolyline)
        assertNull(hidden.routeInferredSpans)
        assertFalse(hidden.routeSnapped)
        // La cifra es del coche, no del conductor.
        assertEquals(4_200f, hidden.routeDistanceMeters)
        assertEquals(360_000L, hidden.searchTimeMs)
    }

    @Test
    fun should_drawTheRouteAgain_when_theAuthorTurnsItBackOn() {
        // El sello NO vive en la sesión: la misma sesión del espejo, leída con el interruptor
        // encendido, vuelve a traer su línea. Un flag congelado por sesión no podría deshacerse.
        val shown = payload().asHistoryEntry(vehicleId = "car-1", showRoute = true)

        assertEquals("abc_encoded_polyline", shown.routePolyline)
        assertTrue(shown.routeSnapped)
    }

    @Test
    fun should_saySilence_when_thereWasNoRouteAtAll() {
        // Un aparcamiento BT despierta en el destino y nunca tuvo ruta: no hay nada oculto que
        // comentar, y decir «ruta oculta» inventaría una decisión que nadie tomó.
        val btPark = payload(of = session.copy(routePolyline = null, routeInferredSpans = null))

        assertFalse(btPark.routeHidden(showRoute = false))
        assertTrue(payload().routeHidden(showRoute = false))
    }

    @Test
    fun should_keepTheLastKnownPosition_when_theParkingHasEnded() {
        val ended = session.copy(isActive = false, endedAtMs = 9_000L)

        val state = sharedCarStateOf(payload(of = ended))

        // ⛔ The car does not vanish off the map when it leaves: the group keeps the last place it
        // was seen, with its hour, and reads "not parked any more".
        assertFalse(state.isParked)
        assertEquals(40.4, state.location.latitude)
        assertEquals(1_000L, state.parkedAtMs)
        assertEquals("Ponzano 14", state.address?.street)
    }
}
