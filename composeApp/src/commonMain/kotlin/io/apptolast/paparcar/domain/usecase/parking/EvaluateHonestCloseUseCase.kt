package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.util.haversineMeters
import kotlin.math.ceil

/**
 * What the honest-close ladder decides for a detection session that is about to ABORT
 * (`aborted_false_enter` / `aborted_no_movement`) while a stale pin from a previous parking is
 * still active. [DET-HONEST-CLOSE-001]
 *
 * The doctrine: *no session armed by a real trigger may end in SILENCE when the car provably
 * drove away from its last pin* — it either leaves an artifact (approximate pin / zone) or asks.
 * The two dominant field aborts (Camelias hop 2026-07-14, D2 return 2026-07-15) both ended silent,
 * discarding the departure evidence the session already held: the stale pin went rancid, the car
 * ended with no pin, and nothing was asked.
 *
 * This is the SAME ride-proof question the parked-session safety net answers on its wake-ups
 * ([EvaluateSafetyNetCheckUseCase]) — "did the car drive away, or did the person walk?" — run
 * IMMEDIATELY at the abort from the live foreground service instead of waiting for the 15-min
 * worker Doze holds for hours ("the prompts only appear when you open the app"). It reuses the
 * same step-budget language: a displacement the counted steps cannot explain on foot was ridden.
 */
sealed interface HonestCloseDecision {

    /** Rung 1 — trip proven AND a pin-grade fix is in hand: release the stale pin and drop an
     *  APPROXIMATE pin here (LOW reliability, never auto-published). A point, but a soft one. */
    data class ApproximatePin(val location: GpsPoint) : HonestCloseDecision

    /** Rung 2 — trip proven but NO pin-grade anchor (urban multipath at the new spot): release the
     *  stale pin and open an APPROXIMATE ZONE — an AREA of [radiusMeters], never a deceptively
     *  precise dot. The chain never breaks (the zone carries a fence); the prompt asks to refine. */
    data class ApproximateZone(val center: GpsPoint, val radiusMeters: Float) : HonestCloseDecision

    /** Rung 3 — no ride proof: the walk explains the distance (the car never moved), the
     *  displacement is GPS wobble, the counter is mute or provably FROZEN, the body has not
     *  displaced far enough since the seal for the budget to mean anything, the stale pin is the
     *  USER's own assertion, or there is no stale pin to reason about. Stay silent, keep the old
     *  pin intact — nagging here would assert the car is where the PEDESTRIAN is
     *  (BUG-WALK-DEPART-001). */
    data object KeepSilent : HonestCloseDecision
}

/**
 * The ladder's decision plus every number that produced it — the telemetry payload that makes the
 * abort auditable from Firestore without a logcat attached ("no mute zones", field 2026-07-25:
 * a restaurant pin whose reasoning was invisible). [DET-FROZEN-COUNTER-001]
 */
data class HonestCloseVerdict(
    val decision: HonestCloseDecision,
    /** Why: "trip_proven" for pin/zone; for silence one of [REASON_NO_STALE_PIN], [REASON_TOO_CLOSE],
     *  [REASON_USER_ASSERTED_PIN], [REASON_UNWITNESSED_DISPLACEMENT], [REASON_STALE_SEAL],
     *  [REASON_MUTE_COUNTER], [REASON_FROZEN_COUNTER], [REASON_NO_SEAL_ORIGIN],
     *  [REASON_WALK_EXPLAINS], [REASON_WALK_TOO_SHORT]. */
    val reason: String,
    /** Stale pin → abort fix, meters. Null when there is no stale pin. */
    val pinDistanceMeters: Double? = null,
    /** Step-seal origin → abort fix, meters (what the body actually displaced). Null without a seal origin. */
    val walkDistanceMeters: Double? = null,
    /** Cumulative-counter delta since the stale pin was sealed, or null when mute. */
    val stepsDelta: Long? = null,
    /** Steps the walk budget demanded to call it "walked" (walkDistance/stride × fraction). */
    val requiredSteps: Int? = null,
    /** Last witnessed position → abort fix, meters. Null when no witness was available.
     *  [DET-UNWITNESSED-DISPLACEMENT-001] */
    val witnessDistanceMeters: Double? = null,
    /** Age (ms) of that witness at the abort moment. Null when no witness was available. */
    val witnessAgeMs: Long? = null,
) {
    companion object {
        const val REASON_TRIP_PROVEN = "trip_proven"
        const val REASON_UNWITNESSED_DISPLACEMENT = "unwitnessed_displacement"
        const val REASON_NO_STALE_PIN = "no_stale_pin"
        const val REASON_TOO_CLOSE = "too_close"
        const val REASON_STALE_SEAL = "stale_seal"
        const val REASON_MUTE_COUNTER = "mute_counter"
        const val REASON_FROZEN_COUNTER = "frozen_counter"
        const val REASON_NO_SEAL_ORIGIN = "no_seal_origin"
        const val REASON_WALK_EXPLAINS = "walk_explains"
        const val REASON_WALK_TOO_SHORT = "walk_too_short"
        const val REASON_USER_ASSERTED_PIN = "user_asserted_pin"
        const val REASON_SESSION_MEASURED_DRIVING = "session_measured_driving"
    }
}

/**
 * Pure decision core of the honest-close ladder. [DET-HONEST-CLOSE-001]
 *
 * Deliberately primitives in, sealed decision out — no coroutines, no repositories — so the
 * ladder is replayable from a recorded abort and unit-tested in isolation. The coordinator owns
 * the side effects (releasing the stale pin, creating the zone/pin session, firing the nudge,
 * stamping the outcome).
 *
 * **The trip-proof gate.** Distance alone cannot tell "I drove here" from "I walked here" — the
 * D2 return (2026-07-15) delivered its fresh exit fix where the PEDESTRIAN stood 1.1 km from the
 * still-parked car. So the gate is the hardware step counter's delta since the stale pin was
 * sealed (mirror of [EvaluateSafetyNetCheckUseCase]): walking the displacement MUST have produced
 * ~distance/stride steps. A delta far below that proves a ride; a delta that matches is the normal
 * parked-and-walked-away state and stays silent.
 *
 * **Mute counter (null steps).** Conservatively → [HonestCloseDecision.KeepSilent]. Without steps
 * this evaluator cannot separate a ride from a long walk, and the doctrine is asymmetric (a false
 * negative here costs a late prompt from the safety-net, which still runs; a false zone plants a
 * wrong area). The AR-boarding / pedestrian-physics proofs for mute counters are a documented
 * follow-up — the safety net remains the backstop.
 *
 * **Frozen counter (the negative inference's blind spot). [DET-FROZEN-COUNTER-001]** The trip
 * proof is an inference from ABSENT steps — it is only as good as the counter's liveness. A
 * cumulative counter that keeps returning the same stale non-zero value (MIUI batching for a
 * backgrounded app) passes the mute check yet makes any walk look ridden. The aborting session
 * itself carries the ground truth: its wakeup step DETECTOR counted the walk live. If the
 * detector witnessed N steps in a window the cumulative delta CONTAINS, the delta can never
 * honestly be below N — when it is, the cumulative counter is frozen → treat as mute, refuse
 * the verdict. Field 2026-07-25 22:29, Redmi/Jerez: session aborted false-ENTER on 8 pedestrian
 * detector steps + 0 driving fixes, cumulative delta ≈ 0 over a 150 m walk from the fresh pin —
 * the "ride" inference planted an approximate pin inside the restaurant and released the correct
 * pin planted 6 minutes earlier. The Oppo beside it, counter alive, stayed correctly silent.
 *
 * **Stale seal (the budget expires). [DET-TRIP-WITNESS-001]** The step budget compares TWO
 * positions through ONE cumulative counter — it is only interpretable while that counter is
 * trustworthy across the whole span. A seal stamped hours ago has lived through sleep, process
 * deaths and MIUI batching; its delta can arrive frozen at ≈0 with no session witness to expose
 * it (the [sessionStepEvents] cross-check needs the aborting session itself to have SEEN steps).
 * Field 2026-07-30 17:53, Redmi/Glorieta: a geofence-EXIT echo hit a phone sitting at HOME, the
 * 16-hour-old seal's delta read 0 over last night's 200 m walk, and "198 m without steps" became
 * a "proven trip" — the correct pin was released and re-planted on the user's sofa. The legit
 * closes this ladder exists for (Camelias hop, D2 return) all abort MINUTES after their real
 * trip; past [ParkingDetectionConfig.honestCloseMaxSealAgeMs] the budget is noise, so the ladder
 * refuses the verdict (measured driving above is untouched — it needs no counter). A null age
 * (legacy seal without a timestamp) is indistinguishable from an old one → same refusal.
 *
 * **User-asserted pin (inference never overrules assertion). [DET-WALK-FLOOR-001]** A pin whose
 * reliability is [ParkingDetectionConfig.reliabilityUserConfirmed] was placed or confirmed BY THE
 * USER — the strongest statement of where the car is that this system can hold. The step budget
 * is an inference; an inference must never depose an assertion. Only measured driving (the
 * session kinematics branch below) may release such a pin. Field 2026-07-26 20:28, Oppo/Glorieta:
 * a hand-placed pin 12 minutes old, sitting exactly on the car, was released by a one-step budget
 * margin and re-planted 100 m away at the walker's position.
 *
 * **Walk floor (the budget needs distance to mean anything). [DET-WALK-FLOOR-001]** The trip
 * proof compares counted steps against the steps a displacement demands — a statistical
 * inference whose signal grows with distance. Below the same accuracy-envelopes-plus-floor bar
 * the too-close guard applies to the PIN, applied here to the SEAL-origin displacement, the
 * required count sits within the counter's quantization noise and the verdict flips on a single
 * step (Glorieta: 31.8 m → 17 required vs 16 counted). The two distances diverge legitimately —
 * a pin placed on the map from afar puts the pin far while the body barely moved — so the floor
 * must be checked on the budget's OWN origin, not only the pin's.
 *
 * **Unwitnessed displacement (the abort fix itself can lie). [DET-UNWITNESSED-DISPLACEMENT-001]**
 * Every inference below trusts the abort fix as "where the body is now" — but that fix is one
 * wake's GPS cluster, and indoor multipath can teleport it kilometers with optimistic accuracy.
 * The refutation is spatio-temporal: when the LAST independently witnessed position (previous
 * wake's end fix, safety-net check, seal) sits so close in time that reaching the abort fix would
 * have required an average speed above [ParkingDetectionConfig.honestCloseMaxImpliedTravelSpeedMps]
 * door-to-door, the two witnesses contradict each other physically — and when witnesses disagree,
 * NO fix is pin-grade: refuse the verdict. Field 2026-08-19 03:26, Oppo asleep at home: the phone
 * was witnessed stationary at home 32 s before an abort fix 950 m away (implied ~107 km/h between
 * two stationary observations, zero measured movement) — "trip_proven" planted a pin there, whose
 * fence then saw the phone outside and cascaded a second approximate pin onto the user's home.
 * The formula self-limits: with an old witness the time term dwarfs any real displacement, so the
 * legit late closes (Camelias hop, D2 return — witness gaps of hours) pass untouched, and no
 * separate freshness cap is needed. Measured driving above is untouched: a session that SAW the
 * ride witnessed the displacement by definition.
 *
 * **Session kinematics.** [sessionMaxSpeedMps] is measured movement — and measured movement
 * outranks every inference in this file: a session that reached driving speed PROVES the ride
 * directly, no step budget needed. For today's two triggering outcomes that branch is defensively
 * unreachable (they abort precisely because driving speed was never reached), but it makes speed
 * a first-class evidence input: any future abort routed here that did see driving skips the
 * counter entirely, and the value is stamped into telemetry either way.
 */
class EvaluateHonestCloseUseCase(
    private val config: ParkingDetectionConfig,
) {
    /**
     * @param stalePin        The vehicle's still-active parked session (the pin the ladder may
     *                        release), or null when there is none to reason about.
     * @param abortFix        The position at the abort moment (the candidate new spot) + accuracy.
     * @param stepsSinceStalePin Hardware cumulative-counter delta since [stalePin] was sealed, or
     *                        null when the counter is mute / unknown (caller maps a negative delta
     *                        to null). NOT the session's own step-detector count — that resets per
     *                        session and never saw the drive that preceded the arm.
     * @param stepSealPoint   WHERE the body was when the step baseline was sealed, or null when
     *                        the seal recorded no position (legacy seal). The walked-vs-rode
     *                        budget is only comparable against a displacement measured FROM THIS
     *                        POINT: the counter zeroed at the seal position, so steps can only
     *                        explain distance walked from there. [DET-STEP-BUDGET-ORIGIN-001]
     * @param sealAgeMs       HOW LONG AGO the step baseline was sealed, or null when the seal
     *                        recorded no timestamp (legacy seal). Past
     *                        [ParkingDetectionConfig.honestCloseMaxSealAgeMs] (or unknown) the
     *                        cumulative delta is no longer interpretable and the ladder refuses
     *                        the step-budget verdict. [DET-TRIP-WITNESS-001]
     * @param lastWitnessedFix The last position an INDEPENDENT earlier observation vouched for
     *                        (previous wake's end fix / safety-net check / seal), or null when
     *                        none is known. The abort fix must be spatio-temporally compatible
     *                        with it before any inference may trust it.
     *                        [DET-UNWITNESSED-DISPLACEMENT-001]
     * @param witnessAgeMs    HOW LONG before the abort that witness was observed, or null when
     *                        unknown (caller maps a negative elapsed — clock skew — to null).
     * @param sessionStepEvents Steps the aborting session's own wakeup step DETECTOR counted —
     *                        the liveness witness for the cumulative counter. 0 = no witness
     *                        (the cross-check stays out of the way). [DET-FROZEN-COUNTER-001]
     * @param sessionMaxSpeedMps Max GPS speed (m/s) the aborting session measured. Telemetry +
     *                        future-proofing; see class KDoc. [DET-FROZEN-COUNTER-001]
     */
    operator fun invoke(
        stalePin: UserParking?,
        abortFix: GpsPoint,
        stepsSinceStalePin: Long?,
        stepSealPoint: GpsPoint?,
        sealAgeMs: Long?,
        lastWitnessedFix: GpsPoint?,
        witnessAgeMs: Long?,
        sessionStepEvents: Int = 0,
        sessionMaxSpeedMps: Float = 0f,
    ): HonestCloseVerdict {
        val pin = stalePin
            ?: return HonestCloseVerdict(HonestCloseDecision.KeepSilent, HonestCloseVerdict.REASON_NO_STALE_PIN)

        val distanceMeters = haversineMeters(
            pin.location.latitude, pin.location.longitude,
            abortFix.latitude, abortFix.longitude,
        )

        // Too close to be a trip: within both accuracy envelopes plus the floor, this is a re-arm
        // jitter beside the parked car, not a drive-away. Keep the pin, stay silent. (Pin-based on
        // purpose — this guard asks about the CAR's position, not the walker's.)
        if (distanceMeters <= pin.location.accuracy + abortFix.accuracy + config.honestCloseMinTripMeters) {
            return HonestCloseVerdict(
                HonestCloseDecision.KeepSilent, HonestCloseVerdict.REASON_TOO_CLOSE,
                pinDistanceMeters = distanceMeters,
            )
        }

        // [DET-FROZEN-COUNTER-001] Measured driving in the aborting session IS the ride, no
        // inference needed — the strongest evidence this evaluator can receive. Unreachable from
        // today's two abort outcomes (see class KDoc); decisive for any future caller.
        if (sessionMaxSpeedMps >= config.minimumTripSpeedMps) {
            val decision = if (abortFix.accuracy <= config.minGpsAccuracyForDriving) {
                HonestCloseDecision.ApproximatePin(abortFix)
            } else {
                HonestCloseDecision.ApproximateZone(
                    abortFix,
                    maxOf(abortFix.accuracy, config.honestCloseMinZoneRadiusMeters),
                )
            }
            return HonestCloseVerdict(
                decision, HonestCloseVerdict.REASON_SESSION_MEASURED_DRIVING,
                pinDistanceMeters = distanceMeters, stepsDelta = stepsSinceStalePin,
            )
        }

        // [DET-WALK-FLOOR-001] User-asserted pin: hand-placed or "yes, parked" — reliability is
        // stamped reliabilityUserConfirmed by user confirmation only (BT and vehicle-exit sit
        // strictly below it, enforced by config invariants). Everything past this line is
        // INFERENCE, and inference never deposes assertion; only the measured driving above may
        // release this pin. The safety net remains the backstop if the car truly left.
        if ((pin.detectionReliability ?: 0f) >= config.reliabilityUserConfirmed) {
            return HonestCloseVerdict(
                HonestCloseDecision.KeepSilent, HonestCloseVerdict.REASON_USER_ASSERTED_PIN,
                pinDistanceMeters = distanceMeters, stepsDelta = stepsSinceStalePin,
            )
        }

        // [DET-UNWITNESSED-DISPLACEMENT-001] Everything below trusts the abort fix as "where the
        // body is now" — but the fix is one wake's GPS cluster and indoor multipath teleports.
        // If reaching it from the last independently witnessed position would have needed an
        // average speed no door-to-door drive achieves, the two witnesses contradict each other
        // physically: no fix is pin-grade when witnesses disagree. Refuse the verdict; silence is
        // right whichever endpoint was the mirage, and the safety net remains the backstop.
        val witnessDistanceMeters = lastWitnessedFix?.let {
            haversineMeters(it.latitude, it.longitude, abortFix.latitude, abortFix.longitude)
        }
        if (lastWitnessedFix != null && witnessDistanceMeters != null && witnessAgeMs != null) {
            val plausibleMeters = lastWitnessedFix.accuracy + abortFix.accuracy +
                (witnessAgeMs / 1000.0) * config.honestCloseMaxImpliedTravelSpeedMps
            if (witnessDistanceMeters > plausibleMeters) {
                return HonestCloseVerdict(
                    HonestCloseDecision.KeepSilent, HonestCloseVerdict.REASON_UNWITNESSED_DISPLACEMENT,
                    pinDistanceMeters = distanceMeters, stepsDelta = stepsSinceStalePin,
                    witnessDistanceMeters = witnessDistanceMeters, witnessAgeMs = witnessAgeMs,
                )
            }
        }

        // [DET-TRIP-WITNESS-001] Everything below is the STEP-BUDGET inference, and the budget
        // expires: a delta spanning hours of sleep / process deaths / MIUI batching can read ≈0
        // over a real walk with no session witness to expose it — exactly the false "ride proof"
        // that re-planted the Glorieta pin on the user's home (field 2026-07-30, 16 h seal, EXIT
        // echo). Unknown age (legacy seal) is indistinguishable from old. Refuse the verdict;
        // the safety net remains the backstop for a genuinely late real departure.
        if (sealAgeMs == null || sealAgeMs > config.honestCloseMaxSealAgeMs) {
            return HonestCloseVerdict(
                HonestCloseDecision.KeepSilent, HonestCloseVerdict.REASON_STALE_SEAL,
                pinDistanceMeters = distanceMeters, stepsDelta = stepsSinceStalePin,
            )
        }

        // Mute counter → cannot prove a ride (nor rule out a long walk). Conservative silence;
        // the safety net's mute-counter proofs (AR boarding, pedestrian physics) are the backstop.
        val steps = stepsSinceStalePin
            ?: return HonestCloseVerdict(
                HonestCloseDecision.KeepSilent, HonestCloseVerdict.REASON_MUTE_COUNTER,
                pinDistanceMeters = distanceMeters,
            )

        // [DET-FROZEN-COUNTER-001] Liveness cross-check: the session's own step detector counted
        // [sessionStepEvents] inside a window the cumulative delta spans, so a live cumulative
        // counter can never report fewer. Reporting fewer proves it FROZEN (stale cached sample) —
        // and a frozen counter's low delta is exactly the false "ride proof" this gate exists to
        // reject. Treat as mute: silence, safety net backstops.
        if (sessionStepEvents > 0 && steps < sessionStepEvents) {
            return HonestCloseVerdict(
                HonestCloseDecision.KeepSilent, HonestCloseVerdict.REASON_FROZEN_COUNTER,
                pinDistanceMeters = distanceMeters, stepsDelta = steps,
            )
        }

        // [DET-STEP-BUDGET-ORIGIN-001] No seal origin → the budget is not comparable: the counter
        // was zeroed at an unknown position, so "steps vs distance-from-pin" mixes two origins.
        // That mix is exactly what read a walk home as a ride and planted a pin inside the user's
        // home (field 2026-07-22 01:47, Redmi/Glorieta: seal happened mid-egress ~159 m from the
        // pin, the remaining ~83 m walk cost ~110 steps < the 129 the PIN distance demanded).
        // Conservative silence; the safety net remains the backstop.
        val origin = stepSealPoint
            ?: return HonestCloseVerdict(
                HonestCloseDecision.KeepSilent, HonestCloseVerdict.REASON_NO_SEAL_ORIGIN,
                pinDistanceMeters = distanceMeters, stepsDelta = steps,
            )

        // Trip-proof gate, SAME ORIGIN on both sides: walking from the seal point to here costs
        // ~walkDistance/stride steps. A delta at or above walkedStepFraction of that is a WALK
        // (the car never moved) → rung 3. Below it, the steps cannot account for the displacement
        // the body actually made since the seal → the car was driven → rung 1/2.
        val walkDistanceMeters = haversineMeters(
            origin.latitude, origin.longitude,
            abortFix.latitude, abortFix.longitude,
        )
        val stepsToWalkHere = walkDistanceMeters / config.strideMeters
        val requiredSteps = ceil(stepsToWalkHere * config.walkedStepFraction).toInt()
        val walkExplainsIt = steps >= requiredSteps
        if (walkExplainsIt) {
            return HonestCloseVerdict(
                HonestCloseDecision.KeepSilent, HonestCloseVerdict.REASON_WALK_EXPLAINS,
                pinDistanceMeters = distanceMeters, walkDistanceMeters = walkDistanceMeters,
                stepsDelta = steps, requiredSteps = requiredSteps,
            )
        }

        // [DET-WALK-FLOOR-001] The steps fell short of the budget — but a shortfall only proves a
        // ride when the budget itself is meaningful. Same bar as the too-close guard, applied to
        // the BODY's displacement: within the accuracy envelopes plus the minimum trip, the
        // required count is quantization noise (Glorieta 2026-07-26: 16 counted vs 17 required
        // over 31.8 m — one step from planting a pin on the pedestrian). No ride is provable
        // from a walk this short; silence, the safety net backstops.
        if (walkDistanceMeters <= origin.accuracy + abortFix.accuracy + config.honestCloseMinTripMeters) {
            return HonestCloseVerdict(
                HonestCloseDecision.KeepSilent, HonestCloseVerdict.REASON_WALK_TOO_SHORT,
                pinDistanceMeters = distanceMeters, walkDistanceMeters = walkDistanceMeters,
                stepsDelta = steps, requiredSteps = requiredSteps,
            )
        }

        // Trip proven. A pin-grade fix (same bar the reconcile backfill trusts) earns a point;
        // anything vaguer becomes an honest AREA rather than a precise lie.
        val decision = if (abortFix.accuracy <= config.minGpsAccuracyForDriving) {
            HonestCloseDecision.ApproximatePin(abortFix)
        } else {
            val radiusMeters = maxOf(abortFix.accuracy, config.honestCloseMinZoneRadiusMeters)
            HonestCloseDecision.ApproximateZone(abortFix, radiusMeters)
        }
        return HonestCloseVerdict(
            decision, HonestCloseVerdict.REASON_TRIP_PROVEN,
            pinDistanceMeters = distanceMeters, walkDistanceMeters = walkDistanceMeters,
            stepsDelta = steps, requiredSteps = requiredSteps,
            // How close this legit trip came to the coherence ceiling — the field data that
            // audits honestCloseMaxImpliedTravelSpeedMps. [DET-UNWITNESSED-DISPLACEMENT-001]
            witnessDistanceMeters = witnessDistanceMeters, witnessAgeMs = witnessAgeMs,
        )
    }
}
