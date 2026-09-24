package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.analytics.AnalyticsEvent
import com.rndeveloper.paparcar.domain.analytics.SessionEndReason
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.fakes.FakeAnalyticsTracker
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.fakes.FakeAppNotificationManager
import com.rndeveloper.paparcar.fakes.FakeDetectionEventLogger
import com.rndeveloper.paparcar.fakes.FakeGeofenceManager
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

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
        location = GpsPoint(40.4, -3.7, 8f, Clock.System.now().toEpochMilliseconds() - 90_000L, 0f),
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
        assertTrue(event.sessionAgeMs >= 90_000L)
    }

    @Test
    fun should_close_the_row_without_claiming_a_spot() = runTest {
        // A revert is a user-labelled false positive: the session ends now and nothing was ever
        // given to the community. [VEH-STATS-SAY-SOMETHING-USEFUL-001]
        val repo = FakeUserParkingRepository(initialSession = activeSession())
        buildUseCase(repo = repo)("parking-1")

        val closed = repo.getSessionById("parking-1")!!
        assertNotNull(closed.endedAtMs)
        assertEquals(false, closed.publishedSpot)
    }

    /**
     * [PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001] This use case's own comment already said "the pin
     * was wrong" — and the wrong pin then sat in the user's history as an ordinary parking, because
     * closing a session was the only thing the app could do to a row. A revert is the strongest
     * refutation there is: the user's own word.
     */
    @Test
    fun should_takeTheParkingOutOfTheHistory_when_theUserRevertsIt() = runTest {
        val repo = FakeUserParkingRepository(initialSession = activeSession())
        buildUseCase(repo = repo)("parking-1")

        val reverted = repo.getSessionById("parking-1")!!
        assertTrue(reverted.isRetracted, "the row the user called wrong must leave their history")
        // Withdrawn, never deleted: the field report that follows a wrong pin has to read it.
        assertNotNull(reverted.retractedAtMs)
    }

    /**
     * ⛔ No policy is consulted on this door. A revert is an INSTRUCTION, not a verdict — the pin
     * may have been placed by any path, minutes or hours ago, and the user still outranks every
     * measurement. [DET-ASSERTION-OUTRANKS-INFERENCE-001]
     */
    @Test
    fun should_takeItOutWhateverPlacedIt_when_theUserRevertsAnOldMeasuredPin() = runTest {
        val old = activeSession().copy(
            location = GpsPoint(40.4, -3.7, 8f, Clock.System.now().toEpochMilliseconds() - 6 * 60 * 60_000L, 0f),
            detectionPath = "steps+egress",
        )
        val repo = FakeUserParkingRepository(initialSession = old)
        buildUseCase(repo = repo)("parking-1")

        assertTrue(repo.getSessionById("parking-1")!!.isRetracted)
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

    // ── The card can outlive its trip [DET-A-SAVED-CARD-MUST-NOT-OUTLIVE-ITS-TRIP-001] ───────
    //
    // The «No, cancelar» PendingIntent carries the id of the parking it was posted for, and the card
    // survives its session: the Coordinator protects the undo window for 15 minutes and the BT lane
    // never took the card down at all. A tap arriving after the trip ended must resolve its subject.

    @Test
    fun should_withdrawNothing_when_theIdIsNotAnActiveSession() = runTest {
        // The session this card talks about ended with the departure; the user is already on another
        // trip. Acting would take a REAL parking out of their history.
        val closed = activeSession().copy(isActive = false)
        val repo = FakeUserParkingRepository(initialSession = closed)
        val geofence = FakeGeofenceManager()

        val result = useCaseOf(repo = repo, geofence = geofence)("parking-1")

        assertTrue(result.isSuccess)
        val untouched = repo.getSessionById("parking-1")!!
        assertFalse(untouched.isRetracted, "a real trip must not leave the history on a stale tap")
        assertNull(untouched.retractedAtMs)
        assertEquals(emptyList(), geofence.removedIds, "nothing of the live state may be undone")
    }

    @Test
    fun should_fileAStaleTapAndNotAFalsePositive_when_theIdIsNotAnActiveSession() = runTest {
        // `Reverted` means "the user refuted this pin". Filing a stale tap under it poisons the most
        // valuable signal detection telemetry produces. [DET-SOLID-001]
        val logger = FakeDetectionEventLogger()
        val repo = FakeUserParkingRepository(initialSession = activeSession().copy(isActive = false))

        useCaseOf(repo = repo, logger = logger)("parking-1")

        assertTrue(logger.events.filterIsInstance<DetectionEvent.Reverted>().isEmpty())
        val decision = logger.events.filterIsInstance<DetectionEvent.Decision>()
            .single { it.outcome == "REVERT_STALE" }
        assertEquals("not_an_active_session", decision.reason)
    }

    @Test
    fun should_stillDismissTheCard_when_theIdIsNotAnActiveSession() = runTest {
        // The tap has to end the conversation either way — leaving the card up would invite the
        // same tap again. [NOTIF-A-FLOW-SPEAKS-THROUGH-ONE-SLOT-001]
        val notification = FakeAppNotificationManager()
        val repo = FakeUserParkingRepository(initialSession = activeSession().copy(isActive = false))

        useCaseOf(repo = repo, notification = notification)("parking-1")

        assertTrue(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID in notification.dismissedIds)
    }

    @Test
    fun should_emitNoSessionEnded_when_theTapIsStale() = runTest {
        val tracker = FakeAnalyticsTracker()
        val repo = FakeUserParkingRepository(initialSession = activeSession().copy(isActive = false))

        useCaseOf(repo = repo, tracker = tracker)("parking-1")

        assertTrue(
            tracker.events.filterIsInstance<AnalyticsEvent.ParkingSessionEnded>().isEmpty(),
            "a session that ended on its own must not be counted as ended a second time, as REVERTED",
        )
    }

    @Test
    fun should_declineAndSayItCouldNotLook_when_theSessionsAreUnreadable() = runTest {
        // "I looked and it is not there" and "I could not look" are different facts, and only one of
        // them is evidence that a card outlived its trip. Restraint is the same on both.
        val logger = FakeDetectionEventLogger()
        val repo = FakeUserParkingRepository(initialSession = activeSession()).apply {
            observeActiveSessionsThrows = RuntimeException("room hiccup")
        }

        val result = useCaseOf(repo = repo, logger = logger)("parking-1")

        assertTrue(result.isSuccess)
        assertFalse(repo.getSessionById("parking-1")!!.isRetracted)
        val decision = logger.events.filterIsInstance<DetectionEvent.Decision>()
            .single { it.outcome == "REVERT_STALE" }
        assertEquals("sessions_unreadable", decision.reason)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Same as [buildUseCase]; named so the stale-tap block above reads in call order. */
    private fun useCaseOf(
        repo: FakeUserParkingRepository = FakeUserParkingRepository(),
        geofence: FakeGeofenceManager = FakeGeofenceManager(),
        notification: FakeAppNotificationManager = FakeAppNotificationManager(),
        logger: FakeDetectionEventLogger = FakeDetectionEventLogger(),
        tracker: FakeAnalyticsTracker? = null,
    ) = buildUseCase(repo, geofence, notification, logger, tracker)

    private fun buildUseCase(
        repo: FakeUserParkingRepository = FakeUserParkingRepository(),
        geofence: FakeGeofenceManager = FakeGeofenceManager(),
        notification: FakeAppNotificationManager = FakeAppNotificationManager(),
        logger: FakeDetectionEventLogger = FakeDetectionEventLogger(),
        tracker: FakeAnalyticsTracker? = null,
    ) = RevertParkingUseCase(
        userParkingRepository = repo,
        geofenceService = geofence,
        notificationPort = notification,
        detectionEventLogger = logger,
        analyticsTracker = tracker,
    )

    // ── Product analytics [ANALYTICS-EVENTS-001] ─────────────────────────────

    @Test
    fun should_emit_session_ended_as_reverted() = runTest {
        val tracker = FakeAnalyticsTracker()
        val repo = FakeUserParkingRepository(initialSession = activeSession())

        buildUseCase(repo = repo, tracker = tracker)("parking-1")

        val ended = tracker.events.filterIsInstance<AnalyticsEvent.ParkingSessionEnded>().single()
        assertEquals(SessionEndReason.REVERTED, ended.reason)
    }
}
