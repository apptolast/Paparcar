package io.apptolast.paparcar.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** [AUDIT-ARCH-001 M13] The spot-TTL rule, now the single source of truth for Android + iOS. */
class SpotTtlPolicyTest {

    @Test
    fun should_giveShortTtl_when_manualReport() {
        assertEquals(SpotTtlPolicy.MANUAL_SPOT_TTL_MS, SpotTtlPolicy.ttlMsForType(SpotType.MANUAL_REPORT))
        assertEquals(15 * 60 * 1_000L, SpotTtlPolicy.MANUAL_SPOT_TTL_MS)
    }

    @Test
    fun should_giveLongTtl_when_autoDetected() {
        assertEquals(SpotTtlPolicy.AUTO_SPOT_TTL_MS, SpotTtlPolicy.ttlMsForType(SpotType.AUTO_DETECTED))
        assertEquals(2 * 60 * 60 * 1_000L, SpotTtlPolicy.AUTO_SPOT_TTL_MS)
    }

    @Test
    fun should_giveLongTtl_when_homeGeofence() {
        // Home-geofence spots are as durable as auto-detected ones — only an explicit manual tap
        // is the short-lived "right now" signal.
        assertEquals(SpotTtlPolicy.AUTO_SPOT_TTL_MS, SpotTtlPolicy.ttlMsForType(SpotType.HOME_GEOFENCE))
    }
}
