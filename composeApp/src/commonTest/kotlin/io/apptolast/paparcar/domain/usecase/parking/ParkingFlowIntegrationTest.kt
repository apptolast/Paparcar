@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.usecase.location.GetLocationInfoUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import io.apptolast.paparcar.fakes.FakeAppNotificationManager
import io.apptolast.paparcar.fakes.FakeAuthRepository
import io.apptolast.paparcar.fakes.FakeGeocoderDataSource
import io.apptolast.paparcar.fakes.FakeGeofenceManager
import io.apptolast.paparcar.fakes.FakeParkingEnrichmentScheduler
import io.apptolast.paparcar.fakes.FakePlacesDataSource
import io.apptolast.paparcar.fakes.FakeReportSpotScheduler
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import io.apptolast.paparcar.fakes.FakeVehicleRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for the full parking lifecycle:
 *   ConfirmParkingUseCase → ReleaseActiveParkingSessionUseCase → ReportSpotReleasedUseCase
 *
 * Unlike isolated unit tests, these wire shared repository instances so we can
 * verify that IDs, reliability scores, and size categories flow correctly across
 * the entire park → release pipeline.
 */
class ParkingFlowIntegrationTest {

    private val location = GpsPoint(
        latitude = 40.416775,
        longitude = -3.703790,
        accuracy = 8f,
        timestamp = 0L,
        speed = 0f,
    )

    private val session = FakeAuthRepository.authenticatedSession(userId = "user-42")

    // ── Shared fakes — state is intentionally shared across both use cases ────

    private val parkingRepo = FakeUserParkingRepository()
    private val vehicleRepo = FakeVehicleRepository(
        defaultVehicle = Vehicle(id = "v-1", userId = "user-42", sizeCategory = VehicleSize.MEDIUM),
    )
    private val geocoder = FakeGeocoderDataSource()
    private val places = FakePlacesDataSource()
    private val spotScheduler = FakeReportSpotScheduler()
    private val geofence = FakeGeofenceManager()
    private val notification = FakeAppNotificationManager()
    private val enrichment = FakeParkingEnrichmentScheduler()
    private val auth = FakeAuthRepository(initialSession = session)

    private val confirmParking = ConfirmParkingUseCase(
        userParkingRepository = parkingRepo,
        vehicleRepository = vehicleRepo,
        geofenceService = geofence,
        notificationPort = notification,
        enrichmentScheduler = enrichment,
        authRepository = auth,
        config = ParkingDetectionConfig(),
    )

    private val releaseParking = ReleaseActiveParkingSessionUseCase(
        reportSpotReleased = ReportSpotReleasedUseCase(
            reportSpotScheduler = spotScheduler,
            getLocationInfo = GetLocationInfoUseCase(geocoder, places),
        ),
        userParkingRepository = parkingRepo,
    )

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun `should clear active session after full park-then-release cycle`() = runTest {
        confirmParking(location, detectionReliability = 0.9f)

        assertNotNull(parkingRepo.getActiveSession())

        releaseParking(location.latitude, location.longitude, parkingRepo.getActiveSession())

        assertNull(parkingRepo.getActiveSession())
    }

    @Test
    fun `should schedule spot report exactly once on release`() = runTest {
        confirmParking(location, detectionReliability = 0.9f)

        releaseParking(location.latitude, location.longitude, parkingRepo.getActiveSession())

        assertEquals(1, spotScheduler.scheduleCallCount)
    }

    @Test
    fun `should propagate session ID as spot ID through the full pipeline`() = runTest {
        val parkResult = confirmParking(location, detectionReliability = 0.9f)
        assertTrue(parkResult.isSuccess)
        val savedSessionId = parkResult.getOrNull()!!.id

        releaseParking(location.latitude, location.longitude, parkResult.getOrNull())

        assertEquals(savedSessionId, spotScheduler.lastSpotId)
        assertEquals(savedSessionId, geofence.lastCreatedGeofenceId)
    }

    @Test
    fun `should propagate detection reliability from confirm to spot report`() = runTest {
        confirmParking(location, detectionReliability = 0.85f)
        val confirmedSession = parkingRepo.getActiveSession()

        releaseParking(location.latitude, location.longitude, confirmedSession)

        assertEquals(0.85f, spotScheduler.lastConfidence)
    }

    @Test
    fun `should propagate sizeCategory resolved at confirm time to spot report`() = runTest {
        confirmParking(location, detectionReliability = 0.9f)
        val confirmedSession = parkingRepo.getActiveSession()

        releaseParking(location.latitude, location.longitude, confirmedSession)

        assertEquals(VehicleSize.MEDIUM, spotScheduler.lastSizeCategory)
    }

    @Test
    fun `should propagate spot coordinates to report scheduler`() = runTest {
        confirmParking(location, detectionReliability = 0.9f)
        val confirmedSession = parkingRepo.getActiveSession()

        releaseParking(location.latitude, location.longitude, confirmedSession)

        assertEquals(location.latitude, spotScheduler.lastLat)
        assertEquals(location.longitude, spotScheduler.lastLon)
    }

    @Test
    fun `should mark spot as AUTO_DETECTED when confirmed with default type`() = runTest {
        confirmParking(location, detectionReliability = 0.9f)
        val confirmedSession = parkingRepo.getActiveSession()

        releaseParking(location.latitude, location.longitude, confirmedSession)

        assertEquals(SpotType.AUTO_DETECTED, spotScheduler.lastSpotType)
    }

    // ── Geocoding during release ───────────────────────────────────────────────

    @Test
    fun `should include geocoded address in spot report when geocoder succeeds`() = runTest {
        val address = AddressInfo(street = "Calle Mayor", city = "Madrid", region = null, country = "ES")
        geocoder.addressResult = Result.success(address)

        confirmParking(location, detectionReliability = 0.9f)
        val confirmedSession = parkingRepo.getActiveSession()
        releaseParking(location.latitude, location.longitude, confirmedSession)

        assertEquals(address, spotScheduler.lastAddress)
    }

    @Test
    fun `should still schedule spot report when geocoding fails during release`() = runTest {
        geocoder.addressResult = Result.failure(RuntimeException("Geocoder unavailable"))

        confirmParking(location, detectionReliability = 0.9f)
        val confirmedSession = parkingRepo.getActiveSession()
        releaseParking(location.latitude, location.longitude, confirmedSession)

        assertEquals(1, spotScheduler.scheduleCallCount)
    }

    // ── No active session at release time ─────────────────────────────────────

    @Test
    fun `should schedule spot report with manual ID when no session exists at release time`() = runTest {
        releaseParking(location.latitude, location.longitude, currentSession = null)

        assertEquals(1, spotScheduler.scheduleCallCount)
        assertTrue(spotScheduler.lastSpotId!!.startsWith("manual_"))
    }

    @Test
    fun `should succeed even when release is called without a prior confirm`() = runTest {
        val result = releaseParking(location.latitude, location.longitude, currentSession = null)

        assertTrue(result.isSuccess)
    }

    // ── Save failure at confirm time — release has nothing to clear ───────────

    @Test
    fun `should have no active session to release when confirm fails`() = runTest {
        parkingRepo.saveSessionResult = Result.failure(RuntimeException("DB error"))

        val confirmResult = confirmParking(location, detectionReliability = 0.9f)
        assertTrue(confirmResult.isFailure)

        assertNull(parkingRepo.getActiveSession())
        // release called explicitly with null because confirm returned nothing
        releaseParking(location.latitude, location.longitude, currentSession = null)

        // spot was still scheduled (manual fallback), but not from session data
        assertEquals(1, spotScheduler.scheduleCallCount)
        assertTrue(spotScheduler.lastSpotId!!.startsWith("manual_"))
    }

    // ── Confirm side effects ──────────────────────────────────────────────────

    @Test
    fun `confirm should register geofence and enrichment before release is called`() = runTest {
        confirmParking(location, detectionReliability = 0.9f)

        assertEquals(1, geofence.createGeofenceCallCount)
        assertEquals(1, enrichment.scheduleCallCount)
        assertEquals(1, notification.parkingSpotSavedCallCount)
    }

    @Test
    fun `second confirm replaces the active session`() = runTest {
        val secondLocation = location.copy(latitude = 41.0, longitude = -4.0)

        confirmParking(location, detectionReliability = 0.9f)
        val firstSession = parkingRepo.getActiveSession()
        assertNotNull(firstSession)

        confirmParking(secondLocation, detectionReliability = 0.75f)
        val secondSession = parkingRepo.getActiveSession()
        assertNotNull(secondSession)

        assertFalse(firstSession.id == secondSession.id)
        assertEquals(41.0, secondSession.location.latitude)
    }
}
