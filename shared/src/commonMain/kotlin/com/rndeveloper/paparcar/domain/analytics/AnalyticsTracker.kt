package com.rndeveloper.paparcar.domain.analytics

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.SpotType

/**
 * Product-analytics sink for the facts that already happen in the app — parks confirmed, sessions
 * closed, spots released and reported. [ANALYTICS-EVENTS-001]
 *
 * Deliberately a SEPARATE system from [com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger]:
 * diagnostics are an opt-in, per-user replay trace; analytics are anonymous population-wide
 * counters. They serve different questions on different populations and must not be merged — but
 * both are fed from the SAME choke points that stamp `detectionPath`, so no third parallel account
 * of the same trip can drift. (roadmap §3.3)
 *
 * **Fire-and-forget contract**: [track] never throws and never blocks. A broken analytics SDK must
 * be invisible to detection and to spot publishing.
 *
 * **Location leaves as a CELL, never a coordinate**: events carry the raw [GpsPoint] so callers
 * stay simple, and the implementation is the single place that reduces it to a coarse geohash cell
 * before anything leaves the device. No implementation may ever emit lat/lon.
 */
interface AnalyticsTracker {
    fun track(event: AnalyticsEvent)
}

/** The facts worth counting. Adding one = adding a case here + one call site — names and wire
 *  params live in the tracker implementation, so use cases never touch magic strings. */
sealed class AnalyticsEvent {

    /** A parking session was confirmed (auto, manual and BT all converge here). */
    data class ParkingSessionStarted(
        val location: GpsPoint,
        val spotType: SpotType,
        /** Confirmation path that placed the pin ("steps+egress", "bt", "manual", …). */
        val detectionPath: String?,
    ) : AnalyticsEvent()

    /** A parking session stopped being active, for [reason]. */
    data class ParkingSessionEnded(
        val location: GpsPoint,
        val reason: SessionEndReason,
    ) : AnalyticsEvent()

    /** A community spot was published from a departure or a manual release. */
    data class SpotReleased(
        val location: GpsPoint,
    ) : AnalyticsEvent()

    /** A community spot was reported manually ("I see a free spot here"). */
    data class SpotReported(
        val location: GpsPoint,
    ) : AnalyticsEvent()
}

/** Why a session ended. A retraction ("I never parked") IS a fact and is emitted — analysis
 *  filters it out of departure funnels by this reason. */
enum class SessionEndReason {
    DEPARTURE_CONFIRMED,
    DEPARTURE_DEDUCED,
    MANUAL_RELEASE,
    REVERTED,
}

/** Silent sink for the mock flavor — the Dev Catalog must never emit real analytics. Same pattern
 *  as [com.rndeveloper.paparcar.domain.diagnostics.NoOpDetectionEventLogger]. */
class NoOpAnalyticsTracker : AnalyticsTracker {
    override fun track(event: AnalyticsEvent) = Unit
}
