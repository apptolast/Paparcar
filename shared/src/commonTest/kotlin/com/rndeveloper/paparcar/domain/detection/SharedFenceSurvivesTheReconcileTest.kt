package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.detection.fence.SharedCarFence
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El reconcile de regiones (el de iOS) no puede barrer la valla del grupo.
 * [DET-A-SHARED-FENCE-FOLLOWS-THE-CAR-001]
 *
 * Es el cuarto sitio que decide por «¿qué sesión respalda esta valla?», y el más destructivo de los
 * cuatro: no espera a que la valla dispare, la borra en CADA pasada. Sin este caso, en iOS la valla
 * compartida nacería y moriría entre dos reconciles, y el miembro no sabría nunca dónde está el
 * coche.
 */
class SharedFenceSurvivesTheReconcileTest {

    private val sharedId = SharedCarFence.idFor("car-1")

    @Test
    fun should_leaveTheSharedFenceAlone_when_reconcilingAgainstSessions() {
        val actions = reconcileFences(
            activeSessions = listOf(session("s-1")),
            monitoredIds = setOf("s-1", sharedId),
        )

        assertEquals(emptySet(), actions.toRemove)
        assertTrue(actions.isInSync)
    }

    @Test
    fun should_stillRemoveARealOrphan_when_aSharedFenceIsAlsoMonitored() {
        val actions = reconcileFences(
            activeSessions = listOf(session("s-1")),
            monitoredIds = setOf("s-1", sharedId, "s-vieja"),
        )

        assertEquals(setOf("s-vieja"), actions.toRemove)
    }

    @Test
    fun should_notCountTheSharedFenceAsRegistered_when_theSessionFenceIsMissing() {
        // La valla del grupo no sustituye a la de la sesión: son dos cosas, y la propia falta.
        val actions = reconcileFences(
            activeSessions = listOf(session("s-1")),
            monitoredIds = setOf(sharedId),
        )

        assertEquals(listOf("s-1"), actions.toRegister.map { it.geofenceId })
    }

    private fun session(id: String) = UserParking(
        id = id,
        userId = "antonio",
        vehicleId = "car-1",
        location = GpsPoint(40.4, -3.7, 8f, 1_000L, 0f),
        isActive = true,
        geofenceId = id,
    )
}
