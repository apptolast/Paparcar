package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.model.GpsPoint
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [DET-SUPERSEDE-001] Pure supersede-vs-suppress decision for a trigger that arrives while a
 *  detection job is already running. */
class SessionSupersedeTest {

    private fun p(lat: Double, lon: Double = -3.7, acc: Float = 10f) =
        GpsPoint(latitude = lat, longitude = lon, accuracy = acc, timestamp = 0L, speed = 0f)

    @Test
    fun should_supersede_when_new_park_is_beyond_the_fence_from_the_running_anchor() {
        // ~222 m apart (0.002° lat); fence 80 m + acc 10 m = 90 m boundary → a different place →
        // the running session is a zombie relative to it, supersede (caso WA YUKI ~100 m del FP).
        assertTrue(
            shouldSupersedeRunningSession(
                newParkLocation = p(40.002),
                runningAnchor = p(40.0),
                newFenceRadiusMeters = 80f,
            ),
        )
    }

    @Test
    fun should_suppress_when_new_trigger_is_within_the_fence() {
        // ~33 m apart (0.0003° lat), below the 90 m boundary → same place → keep suppressing so a
        // running session's own stream can't reset its abort timer [DET-AR-REARM-001].
        assertFalse(
            shouldSupersedeRunningSession(
                newParkLocation = p(40.0003),
                runningAnchor = p(40.0),
                newFenceRadiusMeters = 80f,
            ),
        )
    }

    @Test
    fun should_never_supersede_when_the_running_anchor_is_unknown() {
        assertFalse(
            shouldSupersedeRunningSession(
                newParkLocation = p(40.002),
                runningAnchor = null,
                newFenceRadiusMeters = 80f,
            ),
        )
    }
}
