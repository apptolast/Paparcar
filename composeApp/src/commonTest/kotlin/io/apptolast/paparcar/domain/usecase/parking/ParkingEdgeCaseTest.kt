@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.parking

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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Edge case tests for the parking pipeline [QA-004].
 * Covers degraded conditions: poor GPS, no vehicle, offline geocoding, double release.
 */
class ParkingEdgeCaseTest {

    private val session = FakeAuthRepository.authenticatedSession(userId = "user-42")
    private val config = ParkingDetectionConfig()

    private val goodLocation = GpsPoint(
        latitude = 40.416775,
        longitude = -3.703790,
        accuracy = 8f,
        timestamp = 0L,
        speed = 0f,
    )

    // ── No GPS fix (accuracy = 0f) ────────────────────────────────────────────

    @Test
    fun `should save session with zero accuracy GPS`() = runTest {
        val repo = FakeUserParkingRepository()
        val noGpsLocation = goodLocation.copy(accuracy = 0f)

        buildConfirm(repo = repo)(noGpsLocation, detectionReliability = 0.9f)

        val saved = repo.getActiveSession()
        assertTrue(saved != null)
        assertEquals(0f, saved.location.accuracy)
    }

    @Test
    fun `should use base geofence radius when accuracy is zero`() = runTest {
        val geofence = FakeGeofenceManager()
        val noGpsLocation = goodLocation.copy(accuracy = 0f)

        buildConfirm(geofence = geofence)(noGpsLocation, detectionReliability = 0.9f, sizeCategory = VehicleSize.MEDIUM)

        // base + 0 * padFactor = base radius (no padding when accuracy is 0)
        assertEquals(config.geofenceRadiusMeters, geofence.lastCreatedRadiusMeters)
    }

    // ── Very poor GPS accuracy (offline/indoors) ───────────────────────────────

    @Test
    fun `should cap geofence at max radius when GPS accuracy is very poor`() = runTest {
        val geofence = FakeGeofenceManager()
        // accuracy=200m on MEDIUM: 80 + 200 * 1.5 = 380m >> max 200m
        val poorGpsLocation = goodLocation.copy(accuracy = 200f)

        buildConfirm(geofence = geofence)(poorGpsLocation, detectionReliability = 0.9f, sizeCategory = VehicleSize.MEDIUM)

        assertEquals(config.geofenceMaxRadiusMeters, geofence.lastCreatedRadiusMeters)
    }

    @Test
    fun `should still register geofence even when GPS accuracy is unknown`() = runTest {
        val geofence = FakeGeofenceManager()
        val unknownAccuracy = goodLocation.copy(accuracy = -1f)  // some providers report -1

        buildConfirm(geofence = geofence)(unknownAccuracy, detectionReliability = 0.9f)

        assertEquals(1, geofence.createGeofenceCallCount)
    }

    // ── Manual report type ────────────────────────────────────────────────────

    @Test
    fun `should store MANUAL_REPORT type in saved session`() = runTest {
        val repo = FakeUserParkingRepository()

        buildConfirm(repo = repo)(goodLocation, detectionReliability = 1.0f, spotType = SpotType.MANUAL_REPORT)

        val saved = repo.getActiveSession()
        assertEquals(SpotType.MANUAL_REPORT, saved?.spotType)
    }

    @Test
    fun `should propagate MANUAL_REPORT type through to spot scheduler on release`() = runTest {
        val repo = FakeUserParkingRepository()
        val scheduler = FakeReportSpotScheduler()
        val confirm = buildConfirm(repo = repo)
        val release = buildRelease(repo = repo, scheduler = scheduler)

        confirm(goodLocation, detectionReliability = 1.0f, spotType = SpotType.MANUAL_REPORT)
        release(goodLocation.latitude, goodLocation.longitude, repo.getActiveSession())

        assertEquals(SpotType.MANUAL_REPORT, scheduler.lastSpotType)
    }

    // ── No vehicle registered ─────────────────────────────────────────────────

    @Test
    fun `should save session with null vehicleId when no vehicle is registered`() = runTest {
        val repo = FakeUserParkingRepository()

        buildConfirm(repo = repo, vehicles = FakeVehicleRepository(defaultVehicle = null))(
            goodLocation,
            detectionReliability = 0.9f,
        )

        val saved = repo.getActiveSession()
        assertNull(saved?.vehicleId)
        assertNull(saved?.sizeCategory)
    }

    @Test
    fun `should use null sizeCategory in spot report when no vehicle registered`() = runTest {
        val repo = FakeUserParkingRepository()
        val scheduler = FakeReportSpotScheduler()
        val confirm = buildConfirm(repo = repo, vehicles = FakeVehicleRepository(defaultVehicle = null))
        val release = buildRelease(repo = repo, scheduler = scheduler)

        confirm(goodLocation, detectionReliability = 0.9f)
        release(goodLocation.latitude, goodLocation.longitude, repo.getActiveSession())

        assertNull(scheduler.lastSizeCategory)
    }

    // ── Double-release (idempotency) ──────────────────────────────────────────

    @Test
    fun `double release should schedule two spot reports`() = runTest {
        val repo = FakeUserParkingRepository()
        val scheduler = FakeReportSpotScheduler()
        val confirm = buildConfirm(repo = repo)
        val release = buildRelease(repo = repo, scheduler = scheduler)

        confirm(goodLocation, detectionReliability = 0.9f)
        val firstSession = repo.getActiveSession()

        // First release
        release(goodLocation.latitude, goodLocation.longitude, firstSession)
        assertEquals(1, scheduler.scheduleCallCount)
        assertNull(repo.getActiveSession())

        // Second release with no active session → manual fallback
        release(goodLocation.latitude, goodLocation.longitude, currentSession = null)
        assertEquals(2, scheduler.scheduleCallCount)
        assertTrue(scheduler.lastSpotId!!.startsWith("manual_"))
    }

    @Test
    fun `second release result should succeed even with no session`() = runTest {
        val repo = FakeUserParkingRepository()
        val release = buildRelease(repo = repo)

        release(goodLocation.latitude, goodLocation.longitude, currentSession = null)
        val result = release(goodLocation.latitude, goodLocation.longitude, currentSession = null)

        assertTrue(result.isSuccess)
    }

    // ── Offline geocoding (airplane mode simulation) ───────────────────────────

    @Test
    fun `should schedule spot report even when geocoder is offline`() = runTest {
        val repo = FakeUserParkingRepository()
        val scheduler = FakeReportSpotScheduler()
        val geocoder = FakeGeocoderDataSource().apply {
            addressResult = Result.failure(RuntimeException("Network unavailable"))
        }
        val confirm = buildConfirm(repo = repo)
        val release = buildRelease(repo = repo, scheduler = scheduler, geocoder = geocoder)

        confirm(goodLocation, detectionReliability = 0.9f)
        release(goodLocation.latitude, goodLocation.longitude, repo.getActiveSession())

        assertEquals(1, scheduler.scheduleCallCount)
    }

    @Test
    fun `should preserve spot coordinates even when geocoding fails`() = runTest {
        val repo = FakeUserParkingRepository()
        val scheduler = FakeReportSpotScheduler()
        val geocoder = FakeGeocoderDataSource().apply {
            addressResult = Result.failure(RuntimeException("DNS failure"))
        }
        val confirm = buildConfirm(repo = repo)
        val release = buildRelease(repo = repo, scheduler = scheduler, geocoder = geocoder)

        confirm(goodLocation, detectionReliability = 0.9f)
        release(goodLocation.latitude, goodLocation.longitude, repo.getActiveSession())

        assertEquals(goodLocation.latitude, scheduler.lastLat)
        assertEquals(goodLocation.longitude, scheduler.lastLon)
    }

    // ── VAN vehicle with poor GPS — double-padded radius capped ─────────────

    @Test
    fun `VAN with poor GPS should cap at max geofence radius`() = runTest {
        val geofence = FakeGeofenceManager()
        val vehicle = Vehicle(id = "v-van", userId = "user-42", sizeCategory = VehicleSize.VAN)
        val poorAccuracy = goodLocation.copy(accuracy = 100f)

        // VAN base (120) + 100 * 1.5 = 270 → capped at 200
        buildConfirm(geofence = geofence, vehicles = FakeVehicleRepository(vehicle))(
            poorAccuracy,
            detectionReliability = 0.9f,
        )

        assertEquals(config.geofenceMaxRadiusMeters, geofence.lastCreatedRadiusMeters)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildConfirm(
        repo: FakeUserParkingRepository = FakeUserParkingRepository(),
        vehicles: FakeVehicleRepository = FakeVehicleRepository(
            Vehicle(id = "v-1", userId = "user-42", sizeCategory = VehicleSize.MEDIUM),
        ),
        geofence: FakeGeofenceManager = FakeGeofenceManager(),
        notification: FakeAppNotificationManager = FakeAppNotificationManager(),
        enrichment: FakeParkingEnrichmentScheduler = FakeParkingEnrichmentScheduler(),
    ) = ConfirmParkingUseCase(
        userParkingRepository = repo,
        vehicleRepository = vehicles,
        geofenceService = geofence,
        notificationPort = notification,
        enrichmentScheduler = enrichment,
        authRepository = FakeAuthRepository(initialSession = session),
        config = config,
    )

    private fun buildRelease(
        repo: FakeUserParkingRepository = FakeUserParkingRepository(),
        scheduler: FakeReportSpotScheduler = FakeReportSpotScheduler(),
        geocoder: FakeGeocoderDataSource = FakeGeocoderDataSource(),
    ) = ReleaseActiveParkingSessionUseCase(
        reportSpotReleased = ReportSpotReleasedUseCase(
            reportSpotScheduler = scheduler,
            getLocationInfo = GetLocationInfoUseCase(geocoder, FakePlacesDataSource()),
        ),
        userParkingRepository = repo,
    )
}
