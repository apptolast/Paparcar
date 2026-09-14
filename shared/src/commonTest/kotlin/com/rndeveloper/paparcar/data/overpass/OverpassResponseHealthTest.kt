package com.rndeveloper.paparcar.data.overpass

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two ways a 200 lies. [OVERPASS-A-MIRROR-WALK-MUST-OUTLIVE-THE-SERVER-IT-ASKS-001]
 *
 * ⛔ This check had **no test of its own** until the second lie was found: it was only ever
 * exercised through the client, which is how a shape-only check kept waving through a mirror
 * describing last quarter. A prohibition with no witness is not a check.
 */
class OverpassResponseHealthTest {

    /** 2026-09-14T09:00:00Z, the morning the live mirrors were measured. */
    private val nowMs = 1_789_376_400_000L

    private fun bodyWithBase(stamp: String) =
        """{"version":0.6,"osm3s":{"timestamp_osm_base":"$stamp"},"elements":[]}"""

    @Test
    fun should_trustTheAnswer_when_theBaseTimestampIsFromToday() {
        assertTrue(OverpassResponseHealth.isTrustworthy(bodyWithBase("2026-09-14T08:54:36Z"), nowMs))
    }

    @Test
    fun should_trustAnEmptyAnswer_when_theServerItselfIsHealthy() {
        // ⛔ An empty list is a legitimate answer in the middle of nowhere. Judging health by the
        // number of elements would make deserts retry forever.
        val empty = """{"version":0.6,"osm3s":{"timestamp_osm_base":"2026-09-14T08:54:36Z"},"elements":[]}"""

        assertTrue(OverpassResponseHealth.isTrustworthy(empty, nowMs))
    }

    @Test
    fun should_refuseTheAnswer_when_theBaseTimestampIsACounterRatherThanADate() {
        // The measured shape of `overpass.osm.ch`: 200 in 0,3 s, empty list, and this in the one
        // field nobody was reading.
        assertFalse(OverpassResponseHealth.isTrustworthy(bodyWithBase("116992"), nowMs))
    }

    @Test
    fun should_refuseTheAnswer_when_theDatabaseIsMonthsBehind() {
        // `overpass.private.coffee` on 14-09-2026 — the FIRST fallback, serving 2026-06-01 and
        // passing the old shape-only check.
        assertFalse(
            OverpassResponseHealth.isTrustworthy(bodyWithBase("2026-06-01T08:52:28Z"), nowMs),
            "105 days behind is not a slow mirror, it is an unmaintained one",
        )
    }

    @Test
    fun should_stillTrustTheAnswer_when_theMirrorIsLaggingButWithinTheWeek() {
        // A mirror reloading its database must not be condemned for it.
        val sixDaysBack = nowMs - 6L * 24 * 60 * 60 * 1_000
        assertTrue(OverpassResponseHealth.isTrustworthy(bodyWithBase("2026-09-08T09:00:00Z"), nowMs))
        assertTrue(sixDaysBack < nowMs)
    }

    @Test
    fun should_notCondemnTheMirror_when_thePhoneClockRunsAhead() {
        // ⛔ Only staleness counts. A device whose clock is skewed forward would otherwise reject
        // every healthy server at once, turning a phone setting into an outage.
        val base = "2026-09-14T08:54:36Z"
        assertTrue(OverpassResponseHealth.isTrustworthy(bodyWithBase(base), nowMs = nowMs - 60L * 60 * 1_000))
    }

    @Test
    fun should_refuseTheAnswer_when_thereIsNoBaseTimestampAtAll() {
        assertFalse(OverpassResponseHealth.isTrustworthy("""{"elements":[]}""", nowMs))
        assertFalse(OverpassResponseHealth.isTrustworthy("<html>502 Bad Gateway</html>", nowMs))
    }
}
