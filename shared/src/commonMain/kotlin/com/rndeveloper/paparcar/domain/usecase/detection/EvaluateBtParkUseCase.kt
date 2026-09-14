package com.rndeveloper.paparcar.domain.usecase.detection

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.util.haversineMeters

/**
 * Verdicts of the Bluetooth park decision. [DET-AUDIT-002 T2/T3]
 */
sealed interface BtParkVerdict {
    /** Nothing decidable from this fix — keep sampling. */
    data object KeepWaiting : BtParkVerdict

    /** Credible driving movement observed: the BT drop happened MID-DRIVE (head-unit battery cut,
     *  interference) — the car is still moving, there is no parking. Abort, save nothing. */
    data object DrivingAbort : BtParkVerdict

    /** Stationary, pin-grade fix — accept as the parked-car candidate position. */
    data object CandidateAccepted : BtParkVerdict

    /** The user provably WALKED away from the candidate — the park is real, confirm at the
     *  candidate fix. */
    data object WalkAwayConfirmed : BtParkVerdict
}

/**
 * A parked-car candidate: the fix that will BE the pin, and the instant it was sampled.
 * [DET-BT-PIN-IS-SAMPLED-AFTER-THE-WALK-001]
 *
 * The two travel together on purpose. The walk-away check measures a displacement FROM this fix, so
 * the pedestrian rate that judges it has to be measured from THIS instant — not from whenever the
 * watch happened to start. While the candidate was sampled immediately before the watch the two
 * coincided by accident; now that the hunt runs from the disconnect, a candidate can be half a
 * minute older than the watch, and pairing it with the wrong clock turns a normal walk into a
 * teleport (`averageSpeed > maxPedestrianSpeedMps` ⇒ [BtParkVerdict.DrivingAbort]) — aborting a real
 * park. One seal, two halves.
 */
data class BtCandidate(val fix: GpsPoint, val atMs: Long)

/**
 * Running state of the candidate hunt. [DET-BT-PIN-IS-SAMPLED-AFTER-THE-WALK-001]
 *
 * Not a use case of its own: "who won the hunt" appears in no diagnostic vocabulary and only ever
 * feeds the verdict this file already owns, so by [DET-VERDICT-NOT-PREDICATE-001] it lives INSIDE
 * that verdict. Kept as a fold rather than as a loop in the Android detector so the rule is
 * testable with a list of fixes instead of with a foreground service.
 */
data class BtCandidateHunt(
    /** The disconnect. Nothing stamped before it may pin or abort — see [EvaluateBtParkUseCase.foldCandidateFix]. */
    val sinceMs: Long,
    val candidate: BtCandidate? = null,
    val aborted: Boolean = false,
)

/**
 * What the connect→disconnect engagement with the paired car was SHAPED like.
 * [DET-BT-DISCONNECT-WITHOUT-RIDE-001]
 *
 * A Bluetooth engagement proves the car came within radio range of the phone. That is PRESENCE,
 * not driving — and the two are only the same thing when the phone was inside the car.
 */
sealed interface BtEngagement {
    /** Long enough to be the end of a trip: the detector may go on to place a pin. */
    data class Ride(val durationMs: Long) : BtEngagement

    /** Too short to be a trip — the car came into range and left again (parked beside the phone by
     *  someone else, or simply driven past). Nominates: ask, never place. */
    data class ProximityOnly(val durationMs: Long) : BtEngagement

    /** No connect on record, so the engagement cannot be sized at all. Treated as doubt, not as
     *  permission. */
    data object Unknown : BtEngagement
}

/**
 * Why a ride could not be SIZED IN SPACE. [DET-BT-A-RIDE-IS-MEASURED-NOT-TIMED-001]
 *
 * Each value is a diagnostic label: it names which half of the measurement was missing, so the
 * field can tell "we never had an origin" from "the origin we had was too old to mean anything".
 */
enum class BtRideOriginGap(val label: String) {
    /** No fix was cached when the car connected — nothing to measure the displacement FROM. */
    NO_FIX("no_origin_fix"),
    /** The origin fix predates the connect by more than [ParkingDetectionConfig.btConnectFixMaxAgeMs].
     *  Its walk allowance would swamp any real trip, so the arithmetic is skipped and named. */
    STALE_FIX("origin_fix_stale"),
    /** The origin is stamped AFTER the connect — a clock change between the two. Not a distance. */
    INCOHERENT_CLOCK("origin_clock_incoherent"),
}

/**
 * Which measurement a [BtRideProof] verdict stands on. [DET-BT-A-PARK-IS-CONFIRMED-NOT-ASSUMED-001]
 * Travels to the logs so the field can calibrate how often each witness decides.
 */
enum class BtRideEvidence(val label: String) {
    /** The trip recorder's own fixes, sampled DURING the engagement by our stream — the primary
     *  witness since the tracker exists. [TRIP-THE-TRACKER-RIDES-THE-SENTINEL-001] */
    RECORDED_ROUTE("recorded_route"),

    /** The cached last-known fix stamped at the connect — the original, pre-tracker witness. */
    CONNECT_FIX("connect_fix"),
}

/**
 * Did the car actually GO anywhere between connect and park?
 * [DET-BT-A-RIDE-IS-MEASURED-NOT-TIMED-001]
 *
 * Two witnesses can answer, in strict order of quality [DET-BT-A-PARK-IS-CONFIRMED-NOT-ASSUMED-001]:
 * the trip RECORDER's route (fixes measured during the engagement by our own stream — since
 * [TRIP-THE-TRACKER-RIDES-THE-SENTINEL-001] the lane DOES hold a stream while you drive), and the
 * cached connect fix (the original witness, now the fallback). A trip is, before anything else, a
 * change of place: [displacementMeters] against [requiredMeters] is that measurement; both travel
 * on every verdict so the field can see how close a decision was, not just which way it went.
 */
sealed interface BtRideProof {
    /** The car is measurably somewhere else than where the engagement began. */
    data class Moved(
        val displacementMeters: Double,
        val requiredMeters: Double,
        val originAgeMs: Long,
        val evidence: BtRideEvidence = BtRideEvidence.CONNECT_FIX,
    ) : BtRideProof

    /** Measured, and it did NOT move: the engagement happened with the car parked where it already
     *  was (sitting in it with the radio on, waiting for someone, charging the phone). */
    data class DidNotMove(
        val displacementMeters: Double,
        val requiredMeters: Double,
        val originAgeMs: Long,
        val evidence: BtRideEvidence = BtRideEvidence.CONNECT_FIX,
    ) : BtRideProof

    /** The displacement could not be measured at all. Doubt, never permission. */
    data class Unmeasurable(val gap: BtRideOriginGap) : BtRideProof
}

/**
 * Pure decision core of the Bluetooth detection path. [DET-AUDIT-002 T2/T3]
 *
 * Extracted from [the Android `BluetoothParkingDetector`] so the deterministic-path rules are
 * unit-testable in commonTest and reusable on iOS — the platform detector keeps only the
 * plumbing (debounce, sampling loop, timeouts, telemetry).
 *
 * The two audit holes this closes (2026-07-04 findings A2, both PHANTOM-SPOT class):
 *  - **Candidate gate**: the old detector accepted the first fix with good ACCURACY, never
 *    checking speed. A BT drop while driving pinned a "park" on the road, and the car's own
 *    displacement satisfied the walk-away check → phantom park → phantom community spot.
 *    Now a candidate must be pin-grade AND stationary; a credible driving fix aborts outright.
 *  - **Walk-away gate**: distance alone cannot tell the walker from the car (the coordinator
 *    learned this as BUG-WALK-DEPART-001's mirror image). The displacement must be at pedestrian
 *    rate; covering it faster than [ParkingDetectionConfig.maxPedestrianSpeedMps] means wheels,
 *    not feet — abort.
 *
 * And the hole neither of them could see, because both judge the END of a ride and never ask
 * whether there WAS one [DET-BT-A-RIDE-IS-MEASURED-NOT-TIMED-001]:
 *  - **Ride gate**: `evaluateEngagement` sizes the engagement in TIME (all that exists at the
 *    disconnect), and time cannot tell a trip from sitting in a parked car. `evaluateRideDisplacement`
 *    adds the measurement the lane never had — the car has to be somewhere ELSE — so the app's
 *    ruling doctrine (*the event nominates, only MEASURED movement confirms*) finally holds here too.
 *
 * Asymmetric-error rule as everywhere: every ambiguous reading is a false NEGATIVE (KeepWaiting
 * or abort) — the BT tier's authority ("deterministic, 0.95") must be earned, never assumed.
 * Degraded-accuracy fixes can neither confirm nor abort: they are noise either way.
 */
class EvaluateBtParkUseCase(private val config: ParkingDetectionConfig) {

    /**
     * Size the engagement that just ended, BEFORE any fix is sampled.
     * [DET-BT-DISCONNECT-WITHOUT-RIDE-001]
     *
     * This is the gate the BT lane never had. Every other confirmation path in the app demands
     * measured driving; this one demanded only that a paired MAC dropped, and then accepted the
     * candidate fix *because the user was standing still* — the exact state of someone who has not
     * driven anywhere. Field 2026-08-21 (Oppo + Kamiq): an 11.5 s engagement, the car brought home
     * by a family member and switched off next to the phone, produced a 0.85 session with a
     * geofence whose EXIT then armed the coordinator.
     *
     * Deliberately NOT a distance or speed test: at this instant nothing has been measured yet.
     * Duration is the one thing already on disk (the manifest ACL receiver stamps it across OEM
     * process kills), and it separates the two shapes cleanly — no key-turn handshake or drive-by
     * lasts [ParkingDetectionConfig.btMinRideDurationMs], and no real trip lasts less.
     *
     * @param connectedAtMs epoch-ms of the matching `ACL_CONNECTED`, or null when none is on record.
     * @param disconnectedAtMs epoch-ms of the `ACL_DISCONNECTED` being handled.
     */
    fun evaluateEngagement(connectedAtMs: Long?, disconnectedAtMs: Long): BtEngagement {
        if (connectedAtMs == null) return BtEngagement.Unknown
        val durationMs = disconnectedAtMs - connectedAtMs
        // A connect stamped in the FUTURE (clock change between the two edges) sizes nothing, and
        // neither does one so old it can only be a stamp the app missed the replacement for
        // (OEM force-stop swallowing the CONNECT broadcast).
        if (durationMs < 0 || durationMs > config.btMaxRideDurationMs) return BtEngagement.Unknown
        return if (durationMs >= config.btMinRideDurationMs) {
            BtEngagement.Ride(durationMs)
        } else {
            BtEngagement.ProximityOnly(durationMs)
        }
    }

    /**
     * Did the car MOVE during the engagement? Asked once the parked-car candidate is in hand.
     * [DET-BT-A-RIDE-IS-MEASURED-NOT-TIMED-001]
     *
     * [evaluateEngagement] sizes the engagement in TIME because at the disconnect nothing has been
     * measured yet — and time alone cannot tell a trip from sitting in a parked car with the radio
     * on. That gap made this the one lane in the app that confirmed a park without a single metre
     * of measured movement: 90 s of radio plus a stationary fix (the exact state of someone who has
     * driven nowhere) plus a walk to the front door, and a 0.95 session was born, with a geofence
     * whose EXIT armed the coordinator and a community spot released on the next departure.
     *
     * Measured HERE and not earlier on purpose: the origin is stamped at the connect, but the
     * destination only exists once the hunt settles. Asking before the candidate would mean holding
     * the foreground service to find out — exactly what the duration gate is there to avoid.
     *
     * **The required distance grows with the origin's age**, in the shape
     * [ParkingDetectionConfig.isBeyondPedestrianReach] already uses: the origin is a CACHED fix, so
     * between it and the connect the user may have walked toward the car — or toward wherever the
     * car later parked. Charging that walk to the threshold is what keeps a stale origin from
     * manufacturing a displacement nobody drove. Both accuracies are added for the same reason: a
     * distance smaller than the error bars of its own endpoints is not a distance.
     *
     * **The recorded route outranks the cached fix** [DET-BT-A-PARK-IS-CONFIRMED-NOT-ASSUMED-001]:
     * since the trip recorder exists, the engagement's own fixes — sampled by our stream, already
     * accuracy-gated and decimated at ingest — are on disk. When ≥2 of them postdate the connect,
     * the ride is sized from THEM (first recorded point → the candidate, or → the last recorded
     * point when the post-disconnect hunt never produced one), and that verdict is TERMINAL: a
     * pre-connect cached fix never re-litigates what the ride's own measurements said. No
     * pedestrian-age allowance applies to it — both endpoints were measured during the engagement,
     * so there is no cached gap for a walk to hide in. Every origin-fix failure mode
     * ([BtRideOriginGap]) is thereby survivable when a route was recorded.
     *
     * @param origin last known fix at the moment the paired car connected, or null if none was
     *   cached. Its [GpsPoint.timestamp] is the fix's own instant, not the connect's.
     * @param connectedAtMs the connect this engagement began with.
     * @param candidate the parked-car candidate — the destination of the alleged ride. Null when
     *   the hunt timed out without one: the recorded route alone may still size the ride (so the
     *   timeout can ASK instead of losing the park in silence), but a cached pre-connect fix may
     *   not — it cannot say where the ride ENDED.
     * @param recordedRide the trip recorder's fixes for this engagement (empty when none/unwired).
     */
    fun evaluateRideDisplacement(
        origin: GpsPoint?,
        connectedAtMs: Long,
        candidate: BtCandidate?,
        recordedRide: List<GpsPoint> = emptyList(),
    ): BtRideProof {
        // Witness 1 — the recorded ride. Only fixes stamped after the connect testify: the store
        // self-segments and clears, but a leftover of the previous trip must never size this one.
        val ridePoints = recordedRide.filter { it.timestamp >= connectedAtMs }
        if (ridePoints.size >= MIN_RECORDED_RIDE_POINTS) {
            val first = ridePoints.first()
            val end = candidate?.fix ?: ridePoints.last()
            val displacementMeters = haversineMeters(
                first.latitude, first.longitude, end.latitude, end.longitude,
            )
            val requiredMeters = config.btMinRideDisplacementMeters.toDouble() +
                first.accuracy + end.accuracy
            val originAgeMs = first.timestamp - connectedAtMs // how late the recorder's first fix came
            return if (displacementMeters >= requiredMeters) {
                BtRideProof.Moved(displacementMeters, requiredMeters, originAgeMs, BtRideEvidence.RECORDED_ROUTE)
            } else {
                BtRideProof.DidNotMove(displacementMeters, requiredMeters, originAgeMs, BtRideEvidence.RECORDED_ROUTE)
            }
        }

        // Witness 2 — the cached connect fix (the original arithmetic, unchanged). It measures a
        // displacement TO the candidate, so without one it has nothing to say.
        if (candidate == null) return BtRideProof.Unmeasurable(BtRideOriginGap.NO_FIX)
        if (origin == null) return BtRideProof.Unmeasurable(BtRideOriginGap.NO_FIX)
        val originAgeMs = connectedAtMs - origin.timestamp
        if (originAgeMs < 0) return BtRideProof.Unmeasurable(BtRideOriginGap.INCOHERENT_CLOCK)
        if (originAgeMs > config.btConnectFixMaxAgeMs) {
            return BtRideProof.Unmeasurable(BtRideOriginGap.STALE_FIX)
        }
        val displacementMeters = haversineMeters(
            origin.latitude, origin.longitude,
            candidate.fix.latitude, candidate.fix.longitude,
        )
        val requiredMeters = config.btMinRideDisplacementMeters +
            origin.accuracy + candidate.fix.accuracy +
            config.maxPedestrianSpeedMps * (originAgeMs / 1000.0)
        return if (displacementMeters >= requiredMeters) {
            BtRideProof.Moved(displacementMeters, requiredMeters, originAgeMs, BtRideEvidence.CONNECT_FIX)
        } else {
            BtRideProof.DidNotMove(displacementMeters, requiredMeters, originAgeMs, BtRideEvidence.CONNECT_FIX)
        }
    }

    /** Classify one sampled fix while hunting for the parked-car candidate position. */
    fun evaluateCandidateFix(fix: GpsPoint): BtParkVerdict = when {
        config.isCredibleDrivingSpeed(fix.speed * KMH_PER_MPS, fix.accuracy) ->
            BtParkVerdict.DrivingAbort
        fix.accuracy <= config.minGpsAccuracyForDriving &&
            fix.speed < config.stoppedSpeedThresholdMps ->
            BtParkVerdict.CandidateAccepted
        else -> BtParkVerdict.KeepWaiting
    }

    /**
     * Fold one sampled fix into the hunt for the parked-car position.
     * [DET-BT-PIN-IS-SAMPLED-AFTER-THE-WALK-001]
     *
     * Two rules, and the first one is the whole ticket:
     *  - **The EARLIEST accepted fix wins.** The car's position can only be lost with time, never
     *    recovered: every second between the disconnect and the sample is metres of the user's walk
     *    baked into the pin. A later fix never replaces an accepted candidate.
     *  - **Any credible driving fix aborts**, candidate in hand or not. This is what lets the hunt
     *    start at the disconnect instead of after the debounce: the mid-drive BT drop (head-unit
     *    battery cut, interference) is still caught — now across the whole window rather than only
     *    its tail, which used to leave the first 30 s unwatched.
     *
     * Abort is terminal: a stationary fix afterwards does not resurrect the hunt.
     *
     * **A fix stamped before [BtCandidateHunt.sinceMs] decides nothing** — neither pins nor aborts.
     * Subscribing at the disconnect instead of 30 s later means the fused provider may hand us a
     * CACHED fix first (its default max age is twice the request interval, so ~10 s at high
     * accuracy), and 10 s before the ignition went off the car was still rolling. Such a fix
     * witnessed the arrival, not the park: pinning it would place the car where it was a block
     * back, and — since `minimumDepartureSpeedKmh` is only 10 km/h — aborting on it would silently
     * kill ordinary BT parks. The hazard is created by looking earlier, so it is closed here.
     * An unstamped fix (`timestamp <= 0`, which Android never produces) is judged normally: a lane
     * that refused to park without a stamp would be a worse failure than the one being prevented.
     */
    fun foldCandidateFix(state: BtCandidateHunt, fix: GpsPoint, atMs: Long): BtCandidateHunt {
        if (state.aborted) return state
        if (fix.timestamp > 0L && fix.timestamp < state.sinceMs) return state
        return when (evaluateCandidateFix(fix)) {
            BtParkVerdict.DrivingAbort -> state.copy(aborted = true)
            BtParkVerdict.CandidateAccepted ->
                if (state.candidate == null) state.copy(candidate = BtCandidate(fix, atMs)) else state
            else -> state
        }
    }

    /**
     * Classify one fix of the walk-away phase.
     *
     * @param candidate the parked-car candidate: its fix is the origin of the displacement AND its
     *   instant is the origin of the clock. Both halves come from one [BtCandidate] on purpose — a
     *   distance measured from one moment against a duration measured from another is not a speed.
     * @param nowMs wall-clock of [current]. A non-positive span since the candidate (first fix raced
     *   the clock, or a position teleport) is treated as non-pedestrian: physically a jump, so it
     *   must not confirm.
     */
    fun evaluateWalkAway(candidate: BtCandidate, current: GpsPoint, nowMs: Long): BtParkVerdict {
        if (config.isCredibleDrivingSpeed(current.speed * KMH_PER_MPS, current.accuracy)) {
            return BtParkVerdict.DrivingAbort
        }
        // A degraded fix can fake a 30 m displacement by noise alone — never decide on it.
        if (current.accuracy > config.minGpsAccuracyForDriving) return BtParkVerdict.KeepWaiting

        val elapsedMs = nowMs - candidate.atMs
        val distanceMeters = haversineMeters(
            candidate.fix.latitude, candidate.fix.longitude,
            current.latitude, current.longitude,
        )
        if (distanceMeters < config.btWalkAwayDistanceMeters) return BtParkVerdict.KeepWaiting

        val averageSpeedMps =
            if (elapsedMs > 0) distanceMeters / (elapsedMs / 1000.0) else Double.MAX_VALUE
        if (averageSpeedMps > config.maxPedestrianSpeedMps) return BtParkVerdict.DrivingAbort

        return BtParkVerdict.WalkAwayConfirmed
    }

    private companion object {
        const val KMH_PER_MPS = 3.6f

        /** One recorded fix is a point, not a ride: sizing needs two endpoints of OUR OWN stream.
         *  [DET-BT-A-PARK-IS-CONFIRMED-NOT-ASSUMED-001] */
        const val MIN_RECORDED_RIDE_POINTS = 2
    }
}
