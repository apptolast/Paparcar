package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.detection.PendingParkNudge
import io.apptolast.paparcar.domain.detection.shouldShowParkNudgeBanner
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.fakes.FakeAppNotificationManager
import io.apptolast.paparcar.fakes.FakeAppPreferences
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [DET-NUDGE-PERSIST-001] Durable park nudge: store round-trip, clear and banner visibility. */
class ParkNudgeUseCasesTest {

    private val prefs = FakeAppPreferences()
    private val notifications = FakeAppNotificationManager()
    private val clear = ClearParkNudgeUseCase(prefs, notifications)

    private fun session(id: String = "s1", vehicleId: String? = null) = UserParking(
        id = id,
        vehicleId = vehicleId,
        location = GpsPoint(36.6, -6.23, accuracy = 5f, timestamp = 1L, speed = 0f),
        isActive = true,
    )

    private fun nudge(vehicleId: String? = null) =
        PendingParkNudge(createdAtMs = 1L, source = "unattended_timeout", vehicleId = vehicleId)

    // ── store round-trip ──────────────────────────────────────────────────────

    @Test
    fun should_replacePreviousNudge_when_setAgain() {
        prefs.setPendingParkNudge(nudge().copy(source = "first"))
        prefs.setPendingParkNudge(nudge().copy(source = "second", createdAtMs = 2L))

        assertEquals("second", prefs.pendingParkNudge.value?.source)
        assertEquals(2L, prefs.pendingParkNudge.value?.createdAtMs)
    }

    // ── clear ─────────────────────────────────────────────────────────────────

    @Test
    fun should_clearStoreAndDismissNotification_when_cleared() = runTest {
        prefs.setPendingParkNudge(nudge())

        val result = clear()

        assertTrue(result.isSuccess)
        assertNull(prefs.pendingParkNudge.value)
        assertTrue(AppNotificationManager.MARK_PARKING_NUDGE_NOTIFICATION_ID in notifications.dismissedIds)
    }

    // ── banner visibility ─────────────────────────────────────────────────────

    @Test
    fun should_hideBanner_when_noPendingNudge() {
        assertFalse(shouldShowParkNudgeBanner(nudge = null, activeSessions = emptyList()))
    }

    @Test
    fun should_showBanner_when_nudgePendingAndNoActiveSessions() {
        assertTrue(shouldShowParkNudgeBanner(nudge(), activeSessions = emptyList()))
    }

    @Test
    fun should_hideBanner_when_nudgedVehicleParkedAgain() {
        assertFalse(shouldShowParkNudgeBanner(nudge(vehicleId = "v1"), listOf(session(vehicleId = "v1"))))
    }

    @Test
    fun should_showBanner_when_onlyAnotherVehicleParked() {
        assertTrue(shouldShowParkNudgeBanner(nudge(vehicleId = "v1"), listOf(session(vehicleId = "v2"))))
    }

    @Test
    fun should_hideBanner_when_vehiclelessNudgeAndAnySessionActive() {
        assertFalse(shouldShowParkNudgeBanner(nudge(), listOf(session())))
    }
}
