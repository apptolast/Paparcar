package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.fakes.FakeAppNotificationManager
import io.apptolast.paparcar.fakes.FakeDetectionEventLogger
import io.apptolast.paparcar.fakes.FakeGeofenceManager
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [DET-SOLID-001] First coverage of the user's false-positive correction path: the REVERT
 * action on the post-save card must leave a clean state (session inactive, geofence gone,
 * notification dismissed) and record the user-labelled false positive.
 */
class RevertParkingUseCaseTest {

    private fun activeSession(id: String = "parking-1") = UserParking(
        id = id,
        userId = "user-42",
        vehicleId = "v-1",
        location = GpsPoint(40.4, -3.7, 8f, System.currentTimeMillis() - 90_000L, 0f),
        geofenceId = id,
        isActive = true,
    )

    @Test
    fun should_clear_session_remove_geofence_and_dismiss_notification() = runTest {
        val repo = FakeUserParkingRepository(initialSession = activeSession())
        val geofence = FakeGeofenceManager()
        val notification = FakeAppNotificationManager()
        val useCase = buildUseCase(repo = repo, geofence = geofence, notification = notification)

        val result = useCase("parking-1")

        assertTrue(result.isSuccess)
        assertNull(repo.getActiveSession(), "session must be inactive after revert")
        assertEquals(listOf("parking-1"), geofence.removedIds, "geofence must be removed")
        assertTrue(
            AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID in notification.dismissedIds,
            "post-save card must be dismissed",
        )
    }

    @Test
    fun should_log_reverted_event_with_session_age() = runTest {
        val logger = FakeDetectionEventLogger()
        val repo = FakeUserParkingRepository(initialSession = activeSession())
        buildUseCase(repo = repo, logger = logger)("parking-1")

        val event = logger.events.filterIsInstance<DetectionEvent.Reverted>().single()
        assertEquals("parking-1", event.sessionId)
        assertNotNull(event.sessionAgeMs, "the age of the falsely-saved session is the key datum")
        assertTrue(event.sessionAgeMs!! >= 90_000L)
    }

    @Test
    fun should_still_succeed_and_dismiss_when_clear_fails() = runTest {
        // Best-effort contract: each step logs and continues; the user can retry manually.
        val repo = FakeUserParkingRepository(initialSession = activeSession()).apply {
            clearActiveParkingSessionResult = Result.failure(RuntimeException("db error"))
        }
        val notification = FakeAppNotificationManager()
        val useCase = buildUseCase(repo = repo, notification = notification)

        val result = useCase("parking-1")

        assertTrue(result.isSuccess)
        assertTrue(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID in notification.dismissedIds)
    }

    @Test
    fun should_still_dismiss_when_geofence_removal_fails() = runTest {
        val geofence = FakeGeofenceManager().apply { removeResult = Result.failure(RuntimeException("gms")) }
        val notification = FakeAppNotificationManager()
        val useCase = buildUseCase(geofence = geofence, notification = notification)

        val result = useCase("parking-1")

        assertTrue(result.isSuccess)
        assertTrue(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID in notification.dismissedIds)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildUseCase(
        repo: FakeUserParkingRepository = FakeUserParkingRepository(),
        geofence: FakeGeofenceManager = FakeGeofenceManager(),
        notification: FakeAppNotificationManager = FakeAppNotificationManager(),
        logger: FakeDetectionEventLogger = FakeDetectionEventLogger(),
    ) = RevertParkingUseCase(
        userParkingRepository = repo,
        geofenceService = geofence,
        notificationPort = notification,
        detectionEventLogger = logger,
    )
}
