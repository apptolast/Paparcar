package com.rndeveloper.paparcar.domain.analytics

import com.rndeveloper.paparcar.domain.detection.sentry.TriggerDisposition
import com.rndeveloper.paparcar.domain.entitlement.PaywallReason
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.notification.AlertChannel
import com.rndeveloper.paparcar.domain.notification.FuelMoveLane
import com.rndeveloper.paparcar.domain.usecase.fuel.CheaperReference
import com.rndeveloper.paparcar.domain.usecase.fuel.PriceMoveDirection

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

    /**
     * Sets a durable dimension on this install — a state the user LIVES IN, not something that
     * happened. [DET-A-SWITCH-THAT-IS-OFF-LEAVES-NO-TRACE-001]
     *
     * A property rather than an event because the question is "what share of the install base has
     * detection broken", and because it segments the events that already exist
     * ([AnalyticsEvent.TriggerFired], [AnalyticsEvent.ParkingSessionStarted]) backwards — an event
     * could only ever describe the moment it fired.
     *
     * Same fire-and-forget contract as [track]: never throws, never blocks. Unlike [track] it costs
     * no network call of its own — the SDK attaches the value to subsequent events — so callers may
     * set it on every emission without dedupe.
     */
    fun setUserProperty(property: AnalyticsUserProperty)
}

/**
 * The durable dimensions. Closed vocabulary, low cardinality: Firebase caps an install at 25 user
 * properties, and a property with many values does not aggregate into anything readable.
 */
sealed class AnalyticsUserProperty {

    /** Whether automatic detection can actually run for this user, and if not, why not.
     *  [DET-A-SWITCH-THAT-IS-OFF-LEAVES-NO-TRACE-001] */
    data class DetectionState(
        val label: com.rndeveloper.paparcar.domain.detection.DetectionStateLabel,
    ) : AnalyticsUserProperty()

    /** The promise the app can make to this user, given their setup.
     *
     *  Reuses the existing [com.rndeveloper.paparcar.domain.model.DetectionTier] rather than a
     *  boolean of its own: the vocabulary already exists, is already closed at three values, and a
     *  second spelling of "how automatic is this install" would be free to drift from the one the
     *  UI promises. [DET-TIERS-001] */
    data class DetectionTier(
        val tier: com.rndeveloper.paparcar.domain.model.DetectionTier,
    ) : AnalyticsUserProperty()
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
        /** [DET-EVERY-TRIGGER-REPORTS-WHAT-IT-PRODUCED-001] What ARMED the attempt that ended in
         *  this pin ("GEOFENCE_EXIT", "AR_VEHICLE_ENTER", "BT", "MANUAL", …). The path says how it
         *  was confirmed; without this, "which trigger is productive" cannot be answered — two
         *  pins on the same path can come from completely different systems. */
        val armEvidence: String?,
    ) : AnalyticsEvent()

    /**
     * [DET-EVERY-TRIGGER-REPORTS-WHAT-IT-PRODUCED-001] A trigger reached detection and this is what
     * became of it at the door — **the denominator**.
     *
     * The successes were already countable ([ParkingSessionStarted], [SpotReleased]); the attempts
     * were not, so every rate had a numerator and no denominator. The per-device version of this
     * fact has existed since [DET-EVERY-TRIGGER-LEAVES-A-TRACE-001]
     * (`DetectionEvent.Trigger`) but only reaches the handful of opted-in diagnostics users, which
     * cannot say which system is productive across the install base.
     *
     * ⛔ **No location, by construction.** A trigger firing is not a place, and keeping it that way
     * is what makes this event add no new data TYPE to the Play Data Safety declaration.
     */
    data class TriggerFired(
        /** The lane: `GEOFENCE_EXIT`, `AR_VEHICLE_ENTER`, `BT_DISCONNECT`, `SIGMOTION`, … */
        val trigger: String,
        /** What became of it — the same vocabulary the on-device ledger uses, so the two agree. */
        val disposition: TriggerDisposition,
    ) : AnalyticsEvent()

    /** A parking session stopped being active, for [reason]. */
    data class ParkingSessionEnded(
        val location: GpsPoint,
        val reason: SessionEndReason,
    ) : AnalyticsEvent()

    /**
     * A community spot was published from a departure or a manual release.
     *
     * ⛔ [DET-EVERY-TRIGGER-REPORTS-WHAT-IT-PRODUCED-001] **Carries no productivity dimension, and
     * that is a finding rather than an omission.** A departure is the other thing this app
     * "achieves", so it belongs in the same measurement — but there is nothing honest to stamp on
     * it yet: `detectionPath` and `armEvidence` describe how the PIN was placed, never how the
     * departure was detected, and passing them here would label the plaza with the provenance of
     * the arrival that preceded it. The departure's own provenance does not exist as a persisted
     * label anywhere (the raw material is `DepartureProof`, `preconfirmed` and the dispatching
     * trigger). Giving it one is `DET-A-DEPARTURE-HAS-NO-PROVENANCE-001`.
     */
    data class SpotReleased(
        val location: GpsPoint,
    ) : AnalyticsEvent()

    /** A community spot was reported manually ("I see a free spot here"). */
    data class SpotReported(
        val location: GpsPoint,
    ) : AnalyticsEvent()

    /** The paywall opened, and WHICH limit (or entrance) asked — the conversion funnel's first
     *  column. No location: a paywall is not a place. [FREE-LIMITS-001] */
    data class PaywallShown(
        val reason: com.rndeveloper.paparcar.domain.entitlement.PaywallReason,
    ) : AnalyticsEvent()

    /** A daily price-move notification was shown — which lane and which direction.
     *  [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001] */
    data class FuelPriceMoveShown(
        val lane: FuelMoveLane,
        val direction: PriceMoveDirection,
    ) : AnalyticsEvent()

    /** The "cheaper station nearby" notification was shown, against whichever reference it used. */
    data class CheaperFuelNearbyShown(
        val reference: CheaperReference,
    ) : AnalyticsEvent()

    /** The user followed or unfollowed a fuel station — a free gesture.
     *  [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001] */
    data class FuelStationFollowToggled(
        val followed: Boolean,
    ) : AnalyticsEvent()

    /** A zone spot alert reached the user, on whichever channel the moment picked.
     *  [ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001] */
    data class ZoneSpotAlertShown(
        val location: GpsPoint,
        val channel: AlertChannel,
    ) : AnalyticsEvent()

    /** The month recap notification was shown. No location — a recap is not a place, same as
     *  [PaywallShown]. [STATS-PLUS-THE-RECAP-KNOCKS-001] */
    data object MonthRecapShown : AnalyticsEvent()

    /** The user answered "did you refuel here?", from whichever of the three surfaces asked.
     *  [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001] */
    data class RefuelStopAnswered(
        val refuelled: Boolean,
    ) : AnalyticsEvent()

    /** One of the three notification-channel switches in Settings was actually applied — never
     *  the free attempt that opens the paywall, [PaywallShown] already counts that. Reuses
     *  [PaywallReason]'s closed vocabulary rather than a second enum naming the same three
     *  channels, which would be free to drift. */
    data class NotificationChannelToggled(
        val channel: PaywallReason,
        val enabled: Boolean,
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
    override fun setUserProperty(property: AnalyticsUserProperty) = Unit
}
