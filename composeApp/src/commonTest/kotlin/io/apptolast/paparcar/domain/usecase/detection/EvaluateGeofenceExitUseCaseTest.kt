package io.apptolast.paparcar.domain.usecase.detection

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [AUDIT-A9-KMP-001] The geofence-exit orchestration decision, extracted from the Android service
 * so it is testable and shared with iOS.
 */
class EvaluateGeofenceExitUseCaseTest {

    private val config = ParkingDetectionConfig()
    private val useCase = EvaluateGeofenceExitUseCase(config)

    // Parked car at the base coords.
    private val carLat = 36.6024
    private val carLon = -6.2766

    private fun session(geofenceId: String, vehicleId: String = "v-1") = UserParking(
        id = "session-$geofenceId",
        location = GpsPoint(carLat, carLon, accuracy = 10f, timestamp = 1_000L, speed = 0f),
        geofenceId = geofenceId,
        vehicleId = vehicleId,
    )

    /** A trigger location [meters] north of the car. */
    private fun triggerNorth(meters: Double): Pair<Double, Double> =
        (carLat + meters / 111_320.0) to carLon

    @Test
    fun should_cleanOrphan_when_lookupFoundNoSession() {
        val decision = useCase(
            lookups = listOf(GeofenceExitLookup.NoSession("geof-orphan")),
            activeVehicleId = "v-1",
            triggerLatitude = carLat,
            triggerLongitude = carLon,
        )
        assertEquals(listOf("geof-orphan"), decision.orphanGeofenceIds)
        assertTrue(!decision.hasRealExit)
        assertNull(decision.armTarget)
    }

    @Test
    fun should_skipFailedLookup_neitherOrphanNorExit() {
        // A failed read is indeterminate — it must NOT be cleaned as an orphan (the field
        // 2026-07-11 00:38 regression) nor dispatched as an exit.
        val decision = useCase(
            lookups = listOf(GeofenceExitLookup.LookupFailed("geof-x")),
            activeVehicleId = "v-1",
            triggerLatitude = carLat,
            triggerLongitude = carLon,
        )
        assertTrue(decision.orphanGeofenceIds.isEmpty())
        assertTrue(!decision.hasRealExit)
    }

    @Test
    fun should_dispatchBoundary_when_deliveredAtTheFence() {
        val decision = useCase(
            lookups = listOf(GeofenceExitLookup.Found("geof-1", session("geof-1"))),
            activeVehicleId = "v-1",
            triggerLatitude = carLat, // delivered right at the car
            triggerLongitude = carLon,
        )
        assertEquals(listOf("geof-1"), decision.boundaryDepartures.map { it.geofenceId })
        assertTrue(decision.staleDepartures.isEmpty())
        assertEquals("geof-1", decision.armTarget?.geofenceId)
    }

    @Test
    fun should_dispatchStale_when_deliveredFarFromTheFence() {
        // A real drive-away is delivered far by construction (moving car + OEM lag).
        val (lat, lon) = triggerNorth(config.watchdogFarThresholdMeters.toDouble() + 500.0)
        val decision = useCase(
            lookups = listOf(GeofenceExitLookup.Found("geof-1", session("geof-1"))),
            activeVehicleId = "v-1",
            triggerLatitude = lat,
            triggerLongitude = lon,
        )
        assertTrue(decision.boundaryDepartures.isEmpty())
        assertEquals(listOf("geof-1"), decision.staleDepartures.map { it.geofenceId })
        assertEquals("geof-1", decision.armTarget?.geofenceId, "a stale exit still arms")
    }

    @Test
    fun should_preferActiveVehicle_when_overlappingFencesBothFire() {
        // Active vehicle's fence + an inactive still-parked car's fence both fire (overlap).
        // Only the active one departs; the inactive car stays parked.
        val decision = useCase(
            lookups = listOf(
                GeofenceExitLookup.Found("geof-active", session("geof-active", vehicleId = "v-active")),
                GeofenceExitLookup.Found("geof-inactive", session("geof-inactive", vehicleId = "v-inactive")),
            ),
            activeVehicleId = "v-active",
            triggerLatitude = carLat,
            triggerLongitude = carLon,
        )
        val departingIds = (decision.boundaryDepartures + decision.staleDepartures).map { it.geofenceId }
        assertEquals(listOf("geof-active"), departingIds, "only the active vehicle departs")
    }

    @Test
    fun should_fallBackToWhateverFired_when_activeVehicleFenceDidNotFire() {
        // The user left with an INACTIVE vehicle: the active car's fence is not among those that
        // fired, so release the one that did.
        val decision = useCase(
            lookups = listOf(GeofenceExitLookup.Found("geof-inactive", session("geof-inactive", vehicleId = "v-inactive"))),
            activeVehicleId = "v-active",
            triggerLatitude = carLat,
            triggerLongitude = carLon,
        )
        assertEquals("geof-inactive", decision.armTarget?.geofenceId)
    }

    @Test
    fun should_treatNullTriggerLocation_asBoundary() {
        // No delivery location → cannot claim "far"; default to the boundary path (dispatch + Exited).
        val decision = useCase(
            lookups = listOf(GeofenceExitLookup.Found("geof-1", session("geof-1"))),
            activeVehicleId = "v-1",
            triggerLatitude = null,
            triggerLongitude = null,
        )
        assertEquals(listOf("geof-1"), decision.boundaryDepartures.map { it.geofenceId })
    }
}
