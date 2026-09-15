package com.rndeveloper.paparcar.domain.usecase.sharedcar

import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.SharedCar
import com.rndeveloper.paparcar.domain.model.SharedCarState
import com.rndeveloper.paparcar.domain.model.UserParking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Qué vigila cada móvil de un coche que conducen varios.
 * [DET-A-SHARED-FENCE-FOLLOWS-THE-CAR-001]
 *
 * El caso que da nombre al ticket es `should_moveTheFence_when_somebodyElseParksItElsewhere`: la
 * valla sigue al COCHE, no a quien lo aparcó.
 */
class EvaluateSharedCarWatchUseCaseTest {

    private val evaluate = EvaluateSharedCarWatchUseCase()
    private val me = "antonio"
    private val her = "marta"

    @Test
    fun should_moveTheFence_when_somebodyElseParksItElsewhere() {
        // Antonio aparcó el lunes; Marta lo deja el martes tres calles más allá.
        val mine = session(id = "s-lunes", atMs = MONDAY, lat = 40.40, lon = -3.70)
        val car = car(state = parkedBy(her, "s-martes", TUESDAY, lat = 40.42, lon = -3.71))

        val watch = evaluate(car, myUid = me, myActiveSessionForCar = mine)

        assertEquals("shared:car-1", watch.fenceId)
        assertEquals(40.42, watch.register?.latitude)
        assertEquals(-3.71, watch.register?.longitude)
        assertEquals("s-martes", watch.register?.sessionId)
        // Y la sesión del lunes deja de ser verdad: el coche ya no está donde la dejó.
        assertEquals("s-lunes", watch.supersededSessionId)
    }

    @Test
    fun should_notRegisterASecondFence_when_iAmTheOneWhoParkedIt() {
        // Mi propia sesión ya tiene su valla. Una compartida encima sería el mismo coche con dos
        // ids y dos EXIT por salida, el segundo sin sesión detrás.
        val mine = session(id = "s-mia", atMs = TUESDAY, lat = 40.42, lon = -3.71)
        val car = car(state = parkedBy(me, "s-mia", TUESDAY, lat = 40.42, lon = -3.71))

        val watch = evaluate(car, myUid = me, myActiveSessionForCar = mine)

        assertNull(watch.register)
        assertNull(watch.supersededSessionId)
    }

    @Test
    fun should_dropTheFence_when_theGroupSaysTheCarIsNotParked() {
        val car = car(state = parkedBy(her, "s-martes", TUESDAY, lat = 40.42, lon = -3.71).copy(isParked = false))

        val watch = evaluate(car, myUid = me, myActiveSessionForCar = null)

        assertNull(watch.register)
    }

    @Test
    fun should_keepMyParking_when_theMirrorOnlyKnowsAnOlderOne() {
        // ⛔ El caso que el freno existe para evitar: aparco sin cobertura y el espejo aún cuenta
        // lo de ayer. Superseder aquí BORRARÍA un aparcamiento real de su dueño.
        val mine = session(id = "s-nueva", atMs = TUESDAY, lat = 40.45, lon = -3.75)
        val car = car(state = parkedBy(her, "s-vieja", MONDAY, lat = 40.40, lon = -3.70))

        val watch = evaluate(car, myUid = me, myActiveSessionForCar = mine)

        assertNull(watch.supersededSessionId)
    }

    @Test
    fun should_keepMyParking_when_theGroupJustDoesNotKnowAboutIt() {
        // «No aparcado» no es prueba de que no exista: es el mismo móvil sin cobertura.
        val mine = session(id = "s-mia", atMs = TUESDAY, lat = 40.45, lon = -3.75)
        val car = car(state = null)

        val watch = evaluate(car, myUid = me, myActiveSessionForCar = mine)

        assertNull(watch.supersededSessionId)
        assertNull(watch.register)
    }

    @Test
    fun should_keepMyParking_when_theOtherSessionIsMineFromAnotherPhone() {
        // Mismo autor: sigue siendo mi aparcamiento, aunque el id no coincida todavía.
        val mine = session(id = "s-local", atMs = MONDAY, lat = 40.40, lon = -3.70)
        val car = car(state = parkedBy(me, "s-remota", TUESDAY, lat = 40.42, lon = -3.71))

        val watch = evaluate(car, myUid = me, myActiveSessionForCar = mine)

        assertNull(watch.supersededSessionId)
        // La valla sí se planta: el espejo apunta a un aparcamiento que este móvil no tiene.
        assertEquals("s-remota", watch.register?.sessionId)
    }

    private fun car(state: SharedCarState?) = SharedCar(
        carId = "car-1",
        ownerUid = her,
        displayName = "Seat León",
        state = state,
    )

    private fun parkedBy(uid: String, sessionId: String, atMs: Long, lat: Double, lon: Double) =
        SharedCarState(
            sessionId = sessionId,
            location = GpsPoint(lat, lon, accuracy = 8f, timestamp = atMs, speed = 0f),
            parkedAtMs = atMs,
            authorUid = uid,
            authorName = uid,
            address = AddressInfo(street = "Ponzano 14", city = "Madrid", region = null, country = "España"),
            isParked = true,
            updatedAtMs = atMs,
        )

    private fun session(id: String, atMs: Long, lat: Double, lon: Double) = UserParking(
        id = id,
        userId = me,
        vehicleId = "car-1",
        location = GpsPoint(lat, lon, accuracy = 8f, timestamp = atMs, speed = 0f),
        isActive = true,
    )

    private companion object {
        const val MONDAY = 1_757_000_000_000L
        const val TUESDAY = 1_757_100_000_000L
    }
}
