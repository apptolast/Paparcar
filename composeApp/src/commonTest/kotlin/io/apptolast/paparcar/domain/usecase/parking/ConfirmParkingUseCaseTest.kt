@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.fakes.FakeAppNotificationManager
import io.apptolast.paparcar.fakes.FakeAuthRepository
import io.apptolast.paparcar.fakes.FakeGeofenceManager
import io.apptolast.paparcar.fakes.FakeParkingEnrichmentScheduler
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ConfirmParkingUseCaseTest {

    private val location = GpsPoint(
        latitude = 40.416775,
        longitude = -3.703790,
        accuracy = 8f,
        timestamp = 0L,
        speed = 0f,
    )

    private val session = FakeAuthRepository.authenticatedSession(userId = "user-42")

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun `should save session when called with valid location`() = runTest {
        val repo = FakeUserParkingRepository()
        val useCase = buildUseCase(repo = repo)

        useCase(location)

        assertEquals(1, repo.saveSessionCallCount)
    }

    @Test
    fun `should schedule enrichment after successful save`() = runTest {
        val enrichment = FakeParkingEnrichmentScheduler()
        val useCase = buildUseCase(enrichment = enrichment)

        useCase(location)

        assertEquals(1, enrichment.scheduleCallCount)
    }

    @Test
    fun `should create geofence after successful save`() = runTest {
        val geofence = FakeGeofenceManager()
        val useCase = buildUseCase(geofence = geofence)

        useCase(location)

        assertEquals(1, geofence.createGeofenceCallCount)
    }

    @Test
    fun `should show notification after successful save`() = runTest {
        val notification = FakeAppNotificationManager()
        val useCase = buildUseCase(notification = notification)

        useCase(location)

        assertEquals(1, notification.parkingSpotSavedCallCount)
    }

    @Test
    fun `should use same ID for session and geofence`() = runTest {
        val repo = FakeUserParkingRepository()
        val geofence = FakeGeofenceManager()
        val useCase = buildUseCase(repo = repo, geofence = geofence)

        useCase(location)

        val savedSession = repo.getActiveSession()
        assertNotNull(savedSession)
        assertEquals(savedSession.id, savedSession.geofenceId)
        assertEquals(savedSession.geofenceId, geofence.lastCreatedGeofenceId)
    }

    @Test
    fun `should use authenticated user ID in session`() = runTest {
        val repo = FakeUserParkingRepository()
        val useCase = buildUseCase(repo = repo)

        useCase(location)

        val savedSession = repo.getActiveSession()
        assertNotNull(savedSession)
        assertEquals(session.userId, savedSession.userId)
    }

    // ── Save failure — abort early ────────────────────────────────────────────

    @Test
    fun `should not schedule enrichment when save fails`() = runTest {
        val repo = FakeUserParkingRepository().apply {
            saveSessionResult = Result.failure(RuntimeException("DB error"))
        }
        val enrichment = FakeParkingEnrichmentScheduler()
        val useCase = buildUseCase(repo = repo, enrichment = enrichment)

        useCase(location)

        assertEquals(0, enrichment.scheduleCallCount)
    }

    @Test
    fun `should not create geofence when save fails`() = runTest {
        val repo = FakeUserParkingRepository().apply {
            saveSessionResult = Result.failure(RuntimeException("DB error"))
        }
        val geofence = FakeGeofenceManager()
        val useCase = buildUseCase(repo = repo, geofence = geofence)

        useCase(location)

        assertEquals(0, geofence.createGeofenceCallCount)
    }

    @Test
    fun `should not show notification when save fails`() = runTest {
        val repo = FakeUserParkingRepository().apply {
            saveSessionResult = Result.failure(RuntimeException("DB error"))
        }
        val notification = FakeAppNotificationManager()
        val useCase = buildUseCase(repo = repo, notification = notification)

        useCase(location)

        assertEquals(0, notification.parkingSpotSavedCallCount)
    }

    // ── No authenticated user ─────────────────────────────────────────────────

    @Test
    fun `should save session with empty userId when no authenticated session`() = runTest {
        val repo = FakeUserParkingRepository()
        val noAuthCase = buildUseCase(
            repo = repo,
            auth = FakeAuthRepository(initialSession = null),
        )

        noAuthCase(location)

        val savedSession = repo.getActiveSession()
        assertNotNull(savedSession)
        assertEquals("", savedSession.userId)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildUseCase(
        repo: FakeUserParkingRepository = FakeUserParkingRepository(),
        geofence: FakeGeofenceManager = FakeGeofenceManager(),
        notification: FakeAppNotificationManager = FakeAppNotificationManager(),
        enrichment: FakeParkingEnrichmentScheduler = FakeParkingEnrichmentScheduler(),
        auth: FakeAuthRepository = FakeAuthRepository(initialSession = session),
    ) = ConfirmParkingUseCase(
        userParkingRepository = repo,
        geofenceService = geofence,
        notificationPort = notification,
        enrichmentScheduler = enrichment,
        authRepository = auth,
        config = ParkingDetectionConfig(),
    )
}
