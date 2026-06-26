package io.apptolast.paparcar.detection.service

import android.app.Service
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Robolectric coverage for the [CoordinatorDetectionService] FGS lifecycle. [DET-B-03]
 *
 * The null-intent path is the concrete orphan-FGS fix from Fase B: after an OEM process kill,
 * START_STICKY redelivers a null intent. The coordinator's in-memory session is gone, so the
 * service must NOT promote a detection notification with no work behind it — it must stop.
 *
 * This path touches only the lazily-created [ForegroundServiceController] (no Koin-injected
 * dependencies), so the test needs no Koin graph.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoordinatorDetectionServiceTest {

    @Test
    fun `null intent restart stops without promoting the foreground notification`() {
        val service = Robolectric.buildService(CoordinatorDetectionService::class.java).create().get()

        val result = service.onStartCommand(null, /* flags = */ 0, /* startId = */ 1)

        assertEquals(Service.START_STICKY, result, "a null-intent sticky restart must keep START_STICKY")
        val shadow = shadowOf(service)
        assertNull(
            shadow.lastForegroundNotification,
            "a null-intent restart must NOT promote the FGS — there is no recoverable session [DET-B-02]",
        )
        assertTrue(
            shadow.isStoppedBySelf,
            "the service must stop itself instead of leaving an orphan FGS notification [DET-B-02]",
        )
    }
}
