package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.GpsPoint
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [DET-A-FIX-MUST-SAY-WHERE-IT-CAME-FROM-001] The label is diagnostic output, so what these assert
 * is that it never claims more than the fix told it — an invented `gnss` on a network fix would send
 * a future field diagnosis down the wrong road, which is the exact failure this ticket exists to
 * prevent.
 */
class FixProvenanceTest {

    private fun fix(provider: String? = null, satellites: Int? = null) = GpsPoint(
        latitude = 36.6084105,
        longitude = -6.2780907,
        accuracy = 12f,
        timestamp = 1_788_032_825_991L,
        speed = 8.48f,
        provider = provider,
        satelliteCount = satellites,
    )

    @Test
    fun should_reportSatelliteCount_when_theFixIsGnssDerived() {
        assertEquals("gnss(7sat)", fix(provider = "gps", satellites = 7).provenanceLabel())
    }

    /**
     * The case that motivated preferring the count over the string: the fused client labels a
     * satellite fix `fused`, so trusting the provider alone would have hidden the GNSS origin.
     */
    @Test
    fun should_preferTheSatelliteCount_when_theProviderSaysFused() {
        assertEquals("gnss(4sat)", fix(provider = "fused", satellites = 4).provenanceLabel())
    }

    @Test
    fun should_reportNetwork_when_thereIsNoSatelliteCount() {
        assertEquals("network", fix(provider = "network", satellites = null).provenanceLabel())
    }

    @Test
    fun should_reportPassive_when_theFixCameFromAnotherAppsRequest() {
        assertEquals("passive", fix(provider = "passive").provenanceLabel())
    }

    /** A satellite fix that arrived without its count is still a satellite fix, said without one. */
    @Test
    fun should_reportGnssWithoutACount_when_theProviderIsGpsAndNoCountCame() {
        assertEquals("gnss", fix(provider = "gps").provenanceLabel())
    }

    /**
     * A fix rebuilt from storage or synthesised by a test genuinely has no provenance. It must read
     * as unknown, never as a default world it was never observed in.
     */
    @Test
    fun should_reportUnknown_when_theFixCarriesNoProvenanceAtAll() {
        assertEquals("?", fix().provenanceLabel())
        assertEquals("?", fix(provider = "").provenanceLabel())
    }

    /**
     * A GNSS fix that reports zero satellites is NOT a network fix, and must not be laundered into
     * one — `getInt` answering 0 for an absent key is precisely the confusion the mapper guards
     * against, and the label has to stay honest if one ever gets through.
     */
    @Test
    fun should_stillSayGnss_when_theProviderIsGpsButTheCountIsZero() {
        assertEquals("gnss(0sat)", fix(provider = "gps", satellites = 0).provenanceLabel())
    }

    @Test
    fun should_passThroughAnUnrecognisedProvider_when_thePlatformInventsOne() {
        assertEquals("vendor-hybrid", fix(provider = "VENDOR-HYBRID").provenanceLabel())
    }
}
