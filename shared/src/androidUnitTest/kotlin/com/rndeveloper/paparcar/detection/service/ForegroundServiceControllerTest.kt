package com.rndeveloper.paparcar.detection.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * [DET-A-LOCATION-FGS-MUST-NOT-KILL-THE-APP-001]
 *
 * The one invariant this class exists to hold: **promoting to foreground never throws.** It was a
 * fatal crash in production (`ForegroundServiceController.kt:43`, twice in five seconds on
 * ACTION_RESUME_SENTRY) because the refusal arrived as an exception on the main thread and the only
 * catch at the call site was for [IllegalStateException].
 *
 * No Robolectric shadow raises these refusals — the system does, from `ActiveServices
 * .validateForegroundServiceType` across an IPC — so the platform call is faked here. That is also
 * why this is a test of the CONTROLLER and not of a shadow: what needs proving is that whatever the
 * platform throws comes back as a value.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForegroundServiceControllerTest {

    private class FakeService(private val failure: RuntimeException?) : Service() {
        var promotions = 0
        override fun onBind(intent: Intent?): IBinder? = null
        fun promote() {
            promotions++
            failure?.let { throw it }
        }
    }

    private fun controllerFor(failure: RuntimeException?): Pair<ForegroundServiceController, FakeService> {
        val service = FakeService(failure)
        return ForegroundServiceController(service) { _, _, _ -> service.promote() } to service
    }

    @Test
    fun `should report PROMOTED when the platform accepts the promotion`() {
        val (controller, service) = controllerFor(failure = null)

        val outcome = controller.promote(ID, Notification(), hasLocationPermission = true)

        assertEquals(ForegroundPromotion.PROMOTED, outcome)
        assertEquals(1, service.promotions, "the platform call must actually be made")
    }

    @Test
    fun `should report REFUSED_MISSING_LOCATION_PERMISSION when the platform refuses the location type`() {
        // Verbatim shape of the production refusal, including the clause that no checkSelfPermission
        // can predict: eligibility is process state at the instant of the IPC.
        val refusal = SecurityException(
            "Starting FGS with type location callerApp=ProcessRecord{...} targetSDK=37 requires " +
                "permissions: all of the permissions [android.permission.FOREGROUND_SERVICE_LOCATION] " +
                "and any of the permissions [android.permission.ACCESS_COARSE_LOCATION, " +
                "android.permission.ACCESS_FINE_LOCATION] and the app must be in the eligible state",
        )
        val (controller, _) = controllerFor(refusal)

        val outcome = controller.promote(ID, Notification(), hasLocationPermission = false)

        assertEquals(
            ForegroundPromotion.REFUSED_MISSING_LOCATION_PERMISSION,
            outcome,
            "a refused location type must come back as a value — throwing here killed the app",
        )
    }

    @Test
    fun `should report REFUSED_MISSING_LOCATION_PERMISSION even when we believed we held the permission`() {
        // The pre-check is necessary, never sufficient: the OS can refuse a process that is not in an
        // eligible state despite the permission being granted. Same call, same outcome, no crash.
        val (controller, _) = controllerFor(SecurityException("not in the eligible state"))

        val outcome = controller.promote(ID, Notification(), hasLocationPermission = true)

        assertEquals(ForegroundPromotion.REFUSED_MISSING_LOCATION_PERMISSION, outcome)
    }

    @Test
    fun `should report REFUSED_NOT_FOREGROUND_ELIGIBLE when the start itself was not allowed`() {
        // ForegroundServiceStartNotAllowedException is an IllegalStateException; the Android 12+
        // background-start restriction, unrelated to the type. [DET-G-01]
        val (controller, _) = controllerFor(IllegalStateException("startForegroundService() not allowed"))

        val outcome = controller.promote(ID, Notification(), hasLocationPermission = true)

        assertEquals(ForegroundPromotion.REFUSED_NOT_FOREGROUND_ELIGIBLE, outcome)
    }

    private companion object {
        const val ID = 1001
    }
}
