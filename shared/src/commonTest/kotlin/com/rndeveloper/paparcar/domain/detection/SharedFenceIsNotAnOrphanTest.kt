package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.detection.fence.SharedCarFence
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.service.GeofenceManager
import com.rndeveloper.paparcar.domain.usecase.detection.EvaluateGeofenceExitUseCase
import com.rndeveloper.paparcar.domain.usecase.detection.GeofenceExitLookup
import com.rndeveloper.paparcar.fakes.FakeGeofenceManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Una valla del grupo no tiene sesión local, y aun así está viva.
 * [DET-A-SHARED-FENCE-FOLLOWS-THE-CAR-001]
 *
 * Los dos barridos de huérfanas deciden por «¿qué sesión respalda esta valla?», así que sin este
 * caso se la llevarían por delante en su primer disparo — dejando al miembro sin saber dónde está
 * el coche, que es justo lo que la valla compartida existe para contar. Y el intake la leería como
 * una salida: pasar andando por donde Marta aparcó publicaría SU plaza.
 */
class SharedFenceIsNotAnOrphanTest {

    private val evaluate = EvaluateGeofenceExitUseCase(ParkingDetectionConfig())
    private val sharedId = SharedCarFence.idFor("car-1")

    @Test
    fun should_notSweepIt_when_aSharedFenceExitsWithNoLocalSession() {
        val decision = evaluate(
            lookups = listOf(GeofenceExitLookup.NoSession(sharedId)),
            activeVehicleId = "car-1",
            triggerLatitude = null,
            triggerLongitude = null,
        )

        assertEquals(emptyList(), decision.orphanGeofenceIds)
    }

    @Test
    fun should_notArmAnything_when_theExitCameFromASharedFence() {
        // Que el coche esté vallado en mi móvil no prueba que lo conduzca yo: el que se ha movido
        // puede ser el peatón. Nominar aquí publicaría la plaza de otra persona.
        val decision = evaluate(
            lookups = listOf(GeofenceExitLookup.Found(sharedId, session())),
            activeVehicleId = "car-1",
            triggerLatitude = null,
            triggerLongitude = null,
        )

        assertFalse(decision.hasRealExit)
        assertEquals(null, decision.armTarget)
    }

    @Test
    fun should_stillSweepARealOrphan_when_itTravelsWithASharedFence() {
        // El veto no puede tapar el barrido de verdad: en el mismo lote, la huérfana se limpia.
        val decision = evaluate(
            lookups = listOf(
                GeofenceExitLookup.NoSession(sharedId),
                GeofenceExitLookup.NoSession("s-huerfana"),
            ),
            activeVehicleId = "car-1",
            triggerLatitude = null,
            triggerLongitude = null,
        )

        assertEquals(listOf("s-huerfana"), decision.orphanGeofenceIds)
    }

    @Test
    fun should_notSweepIt_when_theEnterLaneFires() = runTest {
        val manager: GeofenceManager = FakeGeofenceManager()

        val swept = cleanOrphanEnterFences(
            deliveredGeofenceIds = listOf(sharedId, "s-huerfana"),
            activeSessions = emptyList(),
            geofenceManager = manager,
            detectionEventLogger = null,
            nowMs = 1_000L,
        )

        assertEquals(listOf("s-huerfana"), swept)
    }

    @Test
    fun should_tellASharedFenceFromASessionOne() {
        assertTrue(SharedCarFence.isSharedFenceId(sharedId))
        assertEquals("car-1", SharedCarFence.carIdOf(sharedId))
        assertFalse(SharedCarFence.isSharedFenceId("s-de-sesion"))
        assertEquals(null, SharedCarFence.carIdOf("s-de-sesion"))
    }

    private fun session() = UserParking(
        id = "s-1",
        userId = "antonio",
        vehicleId = "car-1",
        location = GpsPoint(40.4, -3.7, 8f, 1_000L, 0f),
        isActive = true,
        geofenceId = sharedId,
    )
}
