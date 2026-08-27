package io.apptolast.paparcar.domain.detection.sentry

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking

/**
 * [DET-CHEAP-WAKE-INSTEAD-OF-SILENCE-001] A wake that costs too much is made CHEAPER, not silenced.
 *
 * The storm damper ([SentryWakeCooldown]) reacted to the 2026-08-13 storm — ≈130 armed-and-refuted
 * sessions in an hour during one walk beside the parked car — by SUPPRESSING the significant-motion
 * re-arm for a quiet period. That was the wrong axis, and the framing error is worth stating plainly:
 *
 * **What is expensive is not the sensor.** `TYPE_SIGNIFICANT_MOTION` runs on the sensor hub and
 * costs ~zero armed. What is expensive is what the trigger UNLEASHES — a full FGS session with a
 * GPS stream, a session document and an arm document in Firestore, and a fresh chance for a
 * cold-start GPS mirage to fake measured driving. We were switching off the cheap thing to avoid
 * paying for the expensive one.
 *
 * So during the quiet period the sensor stays ARMED and the trigger buys a TRIAGE instead of a
 * session: one fix, and an escalation only if that fix is incompatible with "wandering around near
 * the car". Otherwise it goes back to sleep — no FGS, no stream, no documents.
 *
 * **Why this stopped being optional (field 2026-08-22, Redmi).** Three sentry-wake aborts in 114
 * seconds — 18:38:26, 18:39:39, 18:40:20 — all while walking INSIDE the fence, and a 75 km/h drive
 * starting three minutes later. Every escape hatch [DET-COOLDOWN-MUST-NOT-BLIND-A-DRIVE-001] added
 * failed to apply: the aborts were far tighter than the 10-minute decay, and the phone was inside
 * its own fence the whole time, so the damper silenced the sensor exactly as designed. The trip was
 * caught anyway — but by `GEOFENCE_EXIT`, not by the sentry. The system was hanging by one thread,
 * and the night of 2026-08-21 is what it looks like when that thread is not there.
 *
 * Both functions are PREDICATES, not verdicts: they produce no `detectionPath`, no `outcome` and
 * nothing the user reads. They live here with the rest of detection's pure policy rather than as
 * injected `Evaluate…UseCase`s. [DET-VERDICT-NOT-PREDICATE-001]
 */

/** What a cheap wake decided to do with the fix it bought. */
enum class CheapWakeVerdict {
    /** The fix cannot be explained by someone walking near their parked car — start the real session. */
    ESCALATE,

    /** Consistent with wandering beside the car. Back to sleep without paying for a session. */
    STAY_QUIET,
}

/**
 * Cadence floor between triages. Significant motion re-fires roughly every ~18 s during a walk;
 * a fix every 18 s for an hour would trade one bill for another, so a triage that arrives too soon
 * after the previous one is dropped without even reading a fix.
 *
 * Not a risk to a real departure: the floor is [ParkingDetectionConfig.cheapWakeMinTriageIntervalMs]
 * and a car cannot hide inside it — at 30 km/h that window is ~500 m, and crossing the fence is what
 * the NEXT triage sees. A drive does not stop re-triggering the sensor after one shake.
 *
 * @param msSinceLastTriage Elapsed since the previous triage, or null when there is none (fresh
 *   process, or the first wake of a quiet period). Null always allows — nothing to be too soon
 *   after. A negative elapsed (clock skew) is caller-mapped to null.
 */
fun mayTriageSentryWake(msSinceLastTriage: Long?, config: ParkingDetectionConfig): Boolean =
    msSinceLastTriage == null || msSinceLastTriage >= config.cheapWakeMinTriageIntervalMs

/**
 * The whole decision a cheap wake makes, from the single fix it bought.
 *
 * Escalates when the fix is incompatible with a pedestrian near their car, by either of the two
 * questions the project ALREADY knows how to ask — no third notion of "is this driving" is
 * introduced here:
 *
 *  1. **Credible driving speed** — [ParkingDetectionConfig.isCredibleDrivingSpeed], the same bar the
 *     rest of detection uses. Deliberately NOT raw speed: the damper's own doc warns that every wake
 *     is a lottery ticket for a cold-start Doppler mirage, and reading a fix buys no ticket only as
 *     long as the escalation bar demands credible ACCURACY too.
 *  2. **Outside every owned fence** — [isInsideAnyOwnedFence] inverted, the same resolver
 *     `ConfirmParkingUseCase` registers fences with. If the body has left the fence, the quiet
 *     period has already outlived its own justification.
 *
 * @param fix The single reading, or null when the one-shot timed out. **Null ESCALATES.** The
 *   asymmetry is the same one the damper's fence gate uses: failing towards noise costs one session,
 *   failing towards silence costs a parking spot. A triage that cannot see must not conclude
 *   "nothing happening".
 */
fun cheapWakeVerdict(
    fix: GpsPoint?,
    parkedSessions: List<UserParking>,
    config: ParkingDetectionConfig,
): CheapWakeVerdict {
    val here = fix ?: return CheapWakeVerdict.ESCALATE
    val speedKmh = here.speed * MPS_TO_KMH
    if (config.isCredibleDrivingSpeed(speedKmh, here.accuracy)) return CheapWakeVerdict.ESCALATE
    if (!isInsideAnyOwnedFence(here, parkedSessions, config)) return CheapWakeVerdict.ESCALATE
    return CheapWakeVerdict.STAY_QUIET
}

private const val MPS_TO_KMH = 3.6f
