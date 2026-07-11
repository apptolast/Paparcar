package io.apptolast.paparcar.domain.model

/**
 * Configuration thresholds for the parking-detection algorithm.
 *
 * All values are provided with sensible defaults so the use case works
 * out of the box, while allowing overrides in tests or via remote config
 * without touching business logic.
 *
 * Injected into [CalculateParkingConfidenceUseCase] via Koin.
 */
data class ParkingDetectionConfig(

    // ── FAST PATH ─────────────────────────────────────────────────────────────
    /** Minimum stopped duration (ms) required to enter the fast path when an activity-exit event is present. */
    val fastPathMinStoppedMs: Long = 30_000L,
    /** Base confidence score granted by the activity-exit signal alone.
     *  0.50 lets the fast path reach High (0.75) when both the speed bonus AND the
     *  STILL + GPS-accuracy bonus are present, auto-confirming without requiring user
     *  action. The maximum fast-path score is 0.65 (Medium) — the fast path opens the
     *  user prompt, never an auto-confirm. [BUG-DETECT-310503] */
    val fastPathBaseScore: Float = 0.50f,
    /** Bonus added when speed is below [maxSpeedMps] in the fast path. */
    val fastPathSpeedBonus: Float = 0.15f,
    // [DET-SOLID-001 C1] fastPathAccuracyBonus removed: it was gated on the STILL signal, which
    // was dropped long ago — the branch was unreachable and the fast path correctly tops out at
    // Medium (prompt, never auto-confirm). [BUG-DETECT-310503]

    // ── SLOW PATH ─────────────────────────────────────────────────────────────
    /** Minimum stopped duration (ms) before the slow path starts scoring (filters traffic lights). */
    val slowPathGateMs: Long = 90_000L,
    /** Stopped duration threshold (ms) for the highest slow-path base score (~5 min). */
    val slowPath5MinMs: Long = 300_000L,
    /** Stopped duration threshold (ms) for the medium slow-path base score (~3 min). */
    val slowPath3MinMs: Long = 180_000L,
    /** Base score when stopped >= [slowPath5MinMs]. */
    val slowPath5MinScore: Float = 0.70f,
    /** Base score when stopped >= [slowPath3MinMs].
     *  Deliberately set to 0.45 so that a 3-minute stop with all bonuses (still + speed +
     *  accuracy) reaches 0.45 + 0.10 + 0.05 + 0.05 = 0.65 — Medium only, never High.
     *  Auto-confirmation via the slow path therefore requires ≥ 5 minutes. Shorter stops
     *  (package pickups, errand waits) can only be confirmed via user action or the fast path
     *  (which requires an explicit IN_VEHICLE→EXIT activity transition). */
    val slowPath3MinScore: Float = 0.45f,
    /** Base score when stopped >= [slowPathGateMs] but below [slowPath3MinMs]. */
    val slowPathBaseScore: Float = 0.40f,
    // [DET-SOLID-001 C1] stillBonus removed with the STILL signal — unreachable branch.
    /** Bonus added when speed is below [maxSpeedMps] in the slow path. */
    val speedBonus: Float = 0.05f,
    /** Bonus added when GPS accuracy is better than [minGpsAccuracyMeters] in the slow path. */
    val accuracyBonus: Float = 0.05f,

    // ── SHARED THRESHOLDS ─────────────────────────────────────────────────────
    /** Score threshold (inclusive) to classify as [ParkingConfidence.High]. */
    val highConfidenceThreshold: Float = 0.75f,
    /** Score threshold (inclusive) to classify as [ParkingConfidence.Medium]. */
    val mediumConfidenceThreshold: Float = 0.55f,
    /** Speed (m/s) below which the vehicle is considered stationary for bonus calculation. */
    val maxSpeedMps: Float = 0.3f,
    /** GPS horizontal accuracy (meters) below which the fix is considered high-quality. */
    val minGpsAccuracyMeters: Float = 15f,

    // ── EGRESS DISPLACEMENT GATE (DET-A) ──────────────────────────────────────
    /** Minimum displacement (meters) from [egressAnchor] — the position captured at the
     *  moment the vehicle first stopped — that the current fix must reach before a
     *  steps-based auto-confirm (Path 8 and the candidate `hasStepsProof`) is allowed.
     *
     *  **Why.** Steps alone are not proof of egress: a phone bouncing in a pocket during
     *  stop-and-go traffic accumulates step events while the car never moved and the user
     *  never left it (the Prague false positive). Requiring the user to have physically
     *  walked away from the parked-car anchor turns "steps" into "steps AND displacement"
     *  — the conjunction of two independent signals, which is the only thing physically
     *  impossible to fake at a traffic stop.
     *
     *  Set strictly above [minGpsAccuracyMeters] (enforced in `init`) so a single noisy fix
     *  inside the accuracy envelope cannot satisfy the gate on its own. Default 18 m. */
    val minEgressDisplacementMeters: Float = 18f,

    // ── GEOFENCE ──────────────────────────────────────────────────────────────
    /** Base geofence radius (meters) for MOTO vehicles. Smaller because motorcycles park in tighter spots. */
    val geofenceRadiusMotoMeters: Float = 60f,
    /** Base geofence radius (meters) for SMALL, MEDIUM, and unknown-size vehicles. */
    val geofenceRadiusMeters: Float = 80f,
    /** Base geofence radius (meters) for LARGE vehicles. */
    val geofenceRadiusLargeMeters: Float = 100f,
    /** Base geofence radius (meters) for VAN vehicles. */
    val geofenceRadiusVanMeters: Float = 120f,
    /**
     * Multiplier applied to the GPS accuracy reading (meters) as additional padding on top of the
     * base radius. Guards against false departures when the fix was imprecise at park time.
     * e.g. accuracy=15m → 15 × 1.5 = 22.5m extra padding.
     */
    val geofenceAccuracyPadFactor: Float = 1.5f,
    /** Maximum allowed geofence radius (meters) — caps the accuracy-padded result. */
    val geofenceMaxRadiusMeters: Float = 200f,

    // ── AR PROXIMITY RE-ARM / WATCHDOG [DET-AR-REARM-001] ──────────────────────
    // Note: the AR proximity-arm threshold is NOT a flat constant — it is derived per-session from
    // the parked car's own geofence radius via [geofenceRadiusFor]. The proximity gate is the
    // primary defence against the bus/taxi false positive (the egress gate does NOT reject a bus
    // ride: it looks like drive+walk-away), so it must sit exactly on the geofence anchor — large
    // enough to catch a short trip that stays within the radius, tight enough to exclude vehicles
    // boarded outside your parked-car bubble. A flat value would leave a dead ring (too small for
    // vans) or widen the bus surface (too large for motorcycles).
    /**
     * Distance (meters) from the parked car beyond which the safety net
     * ([io.apptolast.paparcar.domain.usecase.parking.EvaluateSafetyNetCheckUseCase]) treats the
     * user as "clearly away from the car". Far alone never releases: with a fresh position anchor
     * + vehicle evidence the normal departure pipeline is dispatched; anything weaker surfaces the
     * low-confidence "still parked?" prompt — only the user can disambiguate
     * drove/walked/got-picked-up. Set well beyond [geofenceMaxRadiusMeters] so a fix that merely
     * sits at the edge of a large geofence does not trip it. Default 300 m. [DET-SAFETY-NET-001]
     */
    val watchdogFarThresholdMeters: Float = 300f,

    // ── STEP BUDGET (parked-state reconcile) [DET-RECONCILE-001] ──────────────
    // Field fact 2026-07-06: geofence EXIT delivery latency reaches minutes on ColorOS — a
    // 2-minute hop fits entirely inside it, arrives post-trip and reads as a walking exit.
    // The cumulative hardware step counter survives process/CPU sleep, so at any later
    // wake-up "displacement without the steps to walk it" PROVES the user was driven — and a
    // fresh position anchor proves the drive started at their car. Same bus/taxi risk
    // envelope as the geofence EXIT itself.
    /** Average pedestrian stride (meters/step) used to convert a displacement into the step
     *  count that walking it would have produced. 0.75 m is a deliberately LONG stride —
     *  it inflates the expected count, making the "did not walk here" verdict conservative. */
    val strideMeters: Float = 0.75f,
    /** Fraction of the expected walking-step count BELOW which the reconcile concludes the
     *  displacement was ridden, not walked. 0.4 tolerates counter under-reporting and pockets
     *  of walking (parking lot to door) while still cleanly separating a drive (steps ≈ 0–10%
     *  of expected) from a walk (≈ 100%). */
    val walkedStepFraction: Float = 0.4f,
    /** Sustained average speed (m/s) from the anchor to the current fix above which the
     *  displacement is physically impossible on foot — the no-step-counter fallback verdict.
     *  2.5 m/s = 9 km/h sustained; brisk walking is ~1.7 m/s, running commuters don't carry
     *  a parked-car session. */
    val maxPedestrianSpeedMps: Float = 2.5f,
    /** ABSOLUTE cap on the step delta for the "was driven from their car" verdict. The relative
     *  [walkedStepFraction] check alone leaks on long displacements: walking 400 m to a bus stop
     *  (~530 steps) then riding 5 km still satisfies "steps ≪ distance/stride". If the ride truly
     *  began at YOUR car, the steps between the anchor (seen inside the fence) and boarding are
     *  at most a fence-diameter's worth (~2×135 m van radius ≈ 360 m worst case, typically far
     *  less). 300 steps ≈ 225 m — beyond it the user demonstrably walked somewhere first and
     *  boarded a vehicle AWAY from the car → never auto-release. */
    val maxBoardingSteps: Long = 300L,
    /** Step-delta ceiling under which the reconcile may BACKFILL the new parking at the wake-up
     *  fix after a step-budget departure verdict. The user's distance to the just-parked car is
     *  bounded by stepsSinceAnchor × stride: at 150 steps that is ≤ ~110 m — comparable to a
     *  geofence radius — so marking the car at the current fix (LOW reliability, revert card) is
     *  more useful than marking nothing. Above it the position is too vague: better no mark than
     *  a wrong one. [DET-RECONCILE-001] */
    val backfillMaxSteps: Long = 150L,
    /** Step delta AT or BELOW which a cumulative-counter reading loses authority whenever the
     *  observed displacement was reachable on foot in the elapsed time. A frozen counter (field
     *  2026-07-09, Redmi: stuck at 307 all day while its own session step DETECTOR counted 157
     *  steps) reads as delta=0 — indistinguishable from "sat in the car the whole trip" by the
     *  delta alone. Physics disambiguates: a real never-left-the-car delta of 0 comes with a
     *  displacement no walker could cover ([isBeyondPedestrianReach]); delta≈0 over a walkable
     *  displacement is exactly what a dead counter produces → treat the counter as MUTE and fall
     *  back to the mute-counter proofs. An ALIVE counter ticks getting out of the car alone, so
     *  real short hops (Oppo 2026-07-07: 113 steps / 4.3 km) sit far above this. [DET-RIDE-PROOF-001] */
    val frozenCounterSuspectSteps: Long = 5L,

    // ── LOCATION CAPTURE WINDOW ───────────────────────────────────────────────
    /** Time window (ms) after the vehicle first stops during which GPS fixes are
     *  collected into [stoppedFixes]. Fixes outside this window are ignored so that
     *  locations recorded AFTER the user has walked away from the car are not used
     *  as the saved parking spot. At HIGH_ACCURACY (~2–5 s cadence: 5 s requested
     *  interval, 2 s fastest — see AndroidLocationDataSourceImpl) a 30 s window
     *  yields ~6–15 candidate fixes — enough to select the best accuracy. */
    val initialStopWindowMs: Long = 30_000L,
    /** Maximum number of GPS fixes retained during [initialStopWindowMs] for
     *  best-accuracy selection. At the 2 s HIGH_ACCURACY cadence, 20 fixes
     *  span 40 s — more than the default window needs. */
    val maxStoppedFixes: Int = 20,
    /** Speed (m/s) at or below which the vehicle is considered fully stopped.
     *  1 m/s ≈ 3.6 km/h — above pure GPS noise, below the slowest real
     *  creep speed that would produce a false-negative. Must be strictly
     *  less than [repositionSpeedMps]. */
    val stoppedSpeedThresholdMps: Float = 1f,

    // ── FALSE-POSITIVE GUARD ──────────────────────────────────────────────────
    /** Minimum GPS speed (m/s) that must be reached at least once during a driving session
     *  before parking detection is allowed. Guards against spurious [IN_VEHICLE_ENTER]
     *  events fired when the user is stationary or cycling (e.g. sitting at a desk, parked
     *  car, or casual cycling). A genuine driving session will always exceed this threshold.
     *  Default ≈ 18 km/h — above comfortable cycling speed, below urban car speed. */
    val minimumTripSpeedMps: Float = 5f,
    /** Minimum displacement (metres) from the session-start location required — together with
     *  [minimumTripSpeedMps] — before [hasEverMoved] is set. A single GPS-noise speed spike
     *  while the device is stationary cannot satisfy both conditions simultaneously: even a
     *  large position jump (50 m) is well below this threshold, so spurious IN_VEHICLE_ENTER
     *  events at home are silently ignored. Default 150 m. */
    val minimumTripDistanceMeters: Float = 150f,
    /** Maximum duration (ms) to wait for [hasEverMoved] before aborting the session.
     *  If the device shows no real driving movement within this window the session is
     *  treated as a spurious IN_VEHICLE_ENTER (e.g. batched/delayed delivery while the
     *  user was already parked) and detection ends silently.
     *  Default 4 minutes — enough for a slow GPS warm-up but not long enough to drain
     *  battery on false starts. */
    val maxNoMovementMs: Long = 4 * 60_000L,

    // ── DEPARTURE DETECTION ───────────────────────────────────────────────────
    /** Maximum time (ms) between an IN_VEHICLE_ENTER transition and a GEOFENCE_EXIT for
     *  the departure to be considered intentional. 30 minutes covers the common case of
     *  a user sitting in their car (loading bags, finishing a call, waiting for AC) before
     *  driving away. The previous 5-minute default was too tight and silently rejected
     *  legitimate departures. A 30-minute window still correctly rejects the previous
     *  day's IN_VEHICLE signal (24+ hours >> 30 min). */
    val vehicleEnterWindowMs: Long = 30 * 60 * 1_000L,
    /** Maximum separation (ms) between a geofence-EXIT DELIVERY and an IN_VEHICLE_ENTER true
     *  transition for the pair to prove a drive-away by CONJUNCTION: two independent OS events
     *  agreeing that a vehicle trip broke this fence at this moment. In a real drive both fire
     *  within a couple of minutes of driving off (field 2026-07-08: Redmi 73 s, Oppo 3 m 55 s
     *  apart); walking out breaks the fence minutes BEFORE any later bus boarding, so a tight
     *  pairing window is what separates the two. Deliberately much tighter than
     *  [vehicleEnterWindowMs] — that one bounds enter→exit for a fence crossing observed AT the
     *  boundary; this one pairs two deliveries with no positional trust at all. [DET-CONJUNCTION-001] */
    val exitEnterPairWindowMs: Long = 5 * 60_000L,
    /** Minimum speed (km/h) that confirms the user is driving away. Speed check is skipped
     *  when GPS is unavailable. Default 10 km/h. */
    val minimumDepartureSpeedKmh: Float = 10f,
    /** Maximum age (ms) of the geofence exit for the freed spot to still be PUBLISHED to the
     *  community. A departure recovered later than this (offline device, queued worker — field
     *  incident 2026-07-06, Redmi: processed 5 h late) still clears the session and geofence,
     *  but publishing the spot would advertise a hole that is long gone. [DET-RECONCILE-001] */
    val spotPublishMaxAgeMs: Long = 10 * 60_000L,
    /** Maximum age (ms) a one-shot fix may have for a detection DECISION to reason over it.
     *  The fused provider's first emission is often a cached last-known location; in the field
     *  (2026-07-07) it served the SAME coordinate with speed=0 for 4 minutes mid-drive (Redmi
     *  13:56) and reported "inside the fence" after the car had already left (Oppo 12:14 —
     *  which then poisoned the anchor). A decision made over hours-old data is worse than no
     *  decision: stale candidates are skipped and the check waits for a live fix or times out.
     *  [DET-RECONCILE-001] */
    val freshFixMaxAgeMs: Long = 30_000L,

    // ── REPARK PLAUSIBILITY GUARD [DET-SOLID-001] ─────────────────────────────
    /** Age (ms) under which an existing active session is considered "recent" by the
     *  repark-plausibility guard in ConfirmParkingUseCase: an auto-confirm that would REPLACE a
     *  session younger than this, at short range, without the confirming session having observed
     *  driving, is rejected (degraded to a user prompt). 10 min comfortably covers the
     *  park-then-walk-away window where the pedestrian false positive lives. */
    val reparkPlausibilityWindowMs: Long = 10 * 60_000L,
    /** Distance (meters) under which the replacement park is "nearby" for the guard above.
     *  300 m ≈ walking range within the window; a real repark after driving normally lands
     *  farther or with driving observed (either disarms the guard). */
    val reparkPlausibilityRadiusMeters: Float = 300f,

    // ── EVIDENCE POLICY [DET-SOLID-001] ───────────────────────────────────────
    /** When true, an auto-confirm whose ONLY vehicle evidence is an AR IN_VEHICLE_ENTER (the
     *  arm was `verified_enter` and the session itself never observed driving speed) degrades
     *  to the user prompt instead of silently saving at 0.90 — AR ENTER fires on ANY vehicle
     *  (bus, taxi), so it alone cannot distinguish "moved my car a short hop" from "boarded a
     *  bus beside my parked car". Costs one tap on legitimate no-GPS short-hops; flip to false
     *  if that UX cost outweighs the bus/taxi false-positive risk in the field. */
    val autoConfirmRequiresStrongEvidence: Boolean = true,
    /** [B4] Enter-arm step veto window (ms). When > 0 and a `verified_enter` arm sees its FIRST
     *  pedestrian step within this window with no driving observed, the ENTER is treated as
     *  spurious (walking) and the evidence degrades to self_observed, re-arming the false-ENTER
     *  abort guard. 0 = disabled (default) — enable only after validating against replay traces
     *  that real short-hops don't produce a first step this early. [DET-SOLID-001] */
    val enterArmStepVetoMs: Long = 0L,
    /** [ANCHOR-LOCK-001] Post-stop pedestrian steps that LOCK the park anchor: once this many
     *  steps are counted while stopped (the user provably exited the car), `bestStopLocation`
     *  freezes — later pedestrian stops can no longer re-capture it and only REAL driving
     *  (≥ [minimumTripSpeedMps] with credible accuracy — the user came back and drove off) can
     *  clear it. Without the lock, wandering inside a building after parking re-captured the
     *  anchor at each indoor re-stop and the pin drifted off the car (field incident 2026-07-04,
     *  supermarket on Avda. Alcalde Eduardo Ruiz: pin saved inside the store, car in the lot).
     *
     *  **Why 8 (= [minStepsToConfirm]), not lower:** phone jiggle at a traffic-light stop
     *  produces 1–3 spurious steps (observed in the Calle Gavia field trace) — locking there
     *  would pin the park at the light. A real exit produces ≥ 8 steps within seconds. */
    val anchorLockEgressSteps: Int = 8,
    /** [DET-AR-FIRST-001 F3] Meters one counted step may account for when deciding whether an
     *  ambiguous-speed movement away from the park anchor is a PERSON (steps cover the
     *  displacement → the anchor must survive) or the CAR (displacement outruns what the counted
     *  steps could walk → clear + re-anchor at the next stop, flushing any phantom steps).
     *  Deliberately LONG (a brisk adult stride is ~0.8 m): the asymmetry of errors demands a
     *  pro-person bias — wrongly keeping the anchor costs meters at the previous stop; wrongly
     *  clearing it sends the pin wherever the pedestrian ends up (field 2026-07-10, Camelias:
     *  the walk into the house cleared the kerb anchor captured 3 steps earlier and the pin
     *  re-anchored INDOORS). The reconcile's [strideMeters] keeps its own value: its bias runs
     *  the other way (inflate the expected count so "did not walk here" stays conservative). */
    val anchorStrideMeters: Float = 1.0f,
    /** [DET-ANCHOR-FREEZE-001] Stop duration (ms) after measured in-session driving beyond which
     *  the park anchor FREEZES at that stop: the car provably came to rest there, so later
     *  pedestrian-range movement (with or without counted steps) can neither clear it nor
     *  re-capture it at a later stop — only re-measured REAL driving
     *  (≥ [minimumTripSpeedMps], credible accuracy) unfreezes. This is the mute-step-counter
     *  complement of [anchorLockEgressSteps]: on hardware whose step stream delivers late or
     *  never (field 2026-07-11, Redmi: ZERO steps for the whole walk home), the unlocked anchor
     *  followed the pedestrian and the pin landed at the user's front door, 95 m from the car.
     *  Under the asymmetric-error principle a rare slow-creep repark pinned one stop early beats
     *  a systematic pin at wherever the user walks to. Must sit BELOW the shortest observed
     *  park→walk-away gap (78 s in the field trace) and clears harmlessly at traffic lights
     *  because resumed driving unfreezes. */
    val anchorFreezeStopMs: Long = 60_000L,
    /** [DET-ANCHOR-FREEZE-001] Maximum pedestrian-band fixes (moving, below a resolved CAR
     *  verdict) tolerated between the last driving movement and a stop for that stop to count as
     *  DRIVE-ENTERED and be allowed to freeze. The freeze asserts "the CAR rests here"; a stop
     *  reached after a stretch of walking-range fixes is the pedestrian standing still (the
     *  2026-07-11 front-door stop arrived after ~20 walking fixes; the real park had 0). Kept
     *  small on purpose: a slow final maneuver that exceeds it merely skips the freeze and
     *  degrades to the ask-the-user paths — a false negative, per the asymmetric-error rule. */
    val anchorFreezeMaxWalkFixes: Int = 3,

    // ── CANDIDATE PHASE ────────────────────────────────────────────────────────
    /** Speed (m/s) above which [bestStopLocation] (and the CANDIDATE phase) is cleared when
     *  the vehicle resumes motion. Chosen above typical walking speed (~1.4 m/s) so the car's
     *  last known position is preserved when the user exits on foot, while being cleared when
     *  the vehicle drives off. Default 2.5 m/s (~9 km/h). */
    val clearBestStopSpeedMps: Float = 2.5f,
    /** GPS horizontal accuracy (meters) at or below which a high-speed fix is trusted as
     *  evidence of genuine driving. On noisy hardware (Redmi Note 11) a single bad fix can
     *  report apparent speed above [clearBestStopSpeedMps] with accuracy in the 50–200 m range;
     *  treating that as "the vehicle drove away" wipes the parked-car location captured during
     *  the initial-stop window. Default 50 m — generous enough that urban GPS noise (typical
     *  10–30 m) still counts, strict enough that clearly degraded fixes do not. [LOC-002] */
    val minGpsAccuracyForDriving: Float = 50f,
    /** Minimum speed (m/s) that a single GPS fix must report to count as a candidate
     *  reposition signal. Below [clearBestStopSpeedMps] but above sustained walking pace
     *  (~1.4 m/s) — slow-parking-maneuver territory. Used together with [repositionFixCount]
     *  to detect the "wait + maneuver to spot" scenario: the user stops 10–15 m short of
     *  the real plaza waiting for another car, the initial-stop window freezes
     *  [bestStopLocation] there, then the brief maneuver to the actual plaza never crosses
     *  [clearBestStopSpeedMps] so the stale waiting position survives. With three or more
     *  consecutive fixes above this threshold (good accuracy required), the reposition is
     *  treated as real vehicle motion and [bestStopLocation] is cleared so the next stop
     *  window can capture the plaza. [PARKING-001] */
    val repositionSpeedMps: Float = 1.7f,
    /** Number of *consecutive* GPS fixes that must report speed ≥ [repositionSpeedMps]
     *  with accuracy ≤ [repositionMaxAccuracyMeters] before [bestStopLocation] is cleared
     *  as a reposition burst. Three filters a single 1.7 m/s noise spike and most
     *  two-fix GPS oscillation bursts (common on Redmi Note 11 near buildings) while
     *  still detecting real maneuvers given the HIGH_ACCURACY 5 s GPS cadence. [PARKING-001] */
    val repositionFixCount: Int = 3,
    /** GPS horizontal accuracy (meters) at or below which a sub-driving-speed fix is
     *  trusted as evidence of a real vehicle reposition. Intentionally stricter than
     *  [minGpsAccuracyForDriving] (50 m) because a slow maneuver at ~1.7 m/s with
     *  accuracy > 15 m is almost certainly GPS oscillation noise, not real motion.
     *  Field logs (Redmi Note 11 2026-05-30) showed sustained 5-burst storms at
     *  acc=22–48 m that cleared [bestStopLocation] while the user was parked, causing
     *  missed confirmations. Real repositions show acc < 10 m. Default 15 m. [PARKING-001] */
    val repositionMaxAccuracyMeters: Float = 15f,
    /** Observation window (ms) before auto-confirming when an activity-exit signal was observed.
     *  Shorter because the IN_VEHICLE→EXIT transition is strong evidence. Default 2 minutes. */
    val vehicleExitObservationWindowMs: Long = 2 * 60_000L,
    /** Observation window (ms) before auto-confirming on the slow path (no activity-exit signal).
     *  Requires the vehicle to remain stopped for the full duration to prevent false positives
     *  from package pickups or errand waits. Default 5 minutes. */
    val confirmationObservationWindowMs: Long = 5 * 60_000L,

    // ── LOW/MEDIUM NOTIFICATION FALLBACK ─────────────────────────────────────
    /** Maximum time (ms) to wait for an activity-exit or STILL signal before showing
     *  the Low/Medium parking-confirmation notification anyway. On hardware where
     *  Activity Recognition delivers the IN_VEHICLE→EXIT transition late (or not at all
     *  before the GPS stop resolves), the coordinator would otherwise suppress the
     *  notification indefinitely. After this window the notification fires regardless,
     *  so the user can still confirm manually. Default 90 s. [BUG-DETECT-310502] */
    val lowNotifTimeoutMs: Long = 90_000L,
    /** Maximum time (ms) the coordinator waits for a user response after showing the
     *  confirmation notification (any confidence level) before aborting the session
     *  silently. Without this guard, a session started on a short trip that returned to
     *  the same spot would run indefinitely: the user parks, walks home, the coordinator
     *  follows the phone's GPS to home, eventually re-fires the notification there, and
     *  keeps the detection foreground service alive for hours. Default 15 minutes.
     *  [BUG-STUCK-SESSION] */
    val confirmationResponseTimeoutMs: Long = 15 * 60_000L,

    // ── POST-CONFIRM HOLD (DET-C-02) ──────────────────────────────────────────
    /** Grace window (ms) after an auto egress-confirm during which the session stays alive
     *  ("tentatively parked") to make sure the user does not immediately drive off again. If
     *  driving resumes (speed > [clearBestStopSpeedMps] with a trustworthy fix) before this
     *  elapses, the tentative confirm is discarded and detection continues — so a quick errand
     *  stop (e.g. parking, walking to a kiosk to buy tobacco, then driving on to park properly)
     *  re-anchors the saved park at the FINAL spot instead of pinning the errand location.
     *  Once it elapses with the car still stopped, the park is finalised; an explicit user-yes
     *  finalises immediately. Set to 0 to disable the hold and confirm immediately (legacy
     *  behaviour / unit tests). Default 2 minutes — long enough to cover a quick errand, tune
     *  with field telemetry. [DET-C-02] */
    val confirmHoldMs: Long = 2 * 60_000L,

    // ── DETECTION RELIABILITY ─────────────────────────────────────────────────
    /** Reliability score [0.0, 1.0] assigned when the user manually confirms parking.
     *  Represents near-certain ground truth. */
    val reliabilityUserConfirmed: Float = 1.0f,
    /** Reliability score assigned when parking is auto-confirmed after observing an
     *  IN_VEHICLE→EXIT activity transition + [vehicleExitObservationWindowMs]. */
    val reliabilityVehicleExit: Float = 0.90f,
    /** Reliability score assigned when parking is auto-confirmed by the deterministic Bluetooth
     *  strategy (paired-device disconnect + GPS fix + ≥30 m walk). Higher than [reliabilityVehicleExit]
     *  because the MAC-address binding makes a real disconnect + walk unambiguous — the
     *  "neighbour's identical car" case is impossible. [DET-F-01, was BluetoothParkingDetector literal] */
    val reliabilityBluetooth: Float = 0.95f,
    /** Reliability score assigned when the confirmation prompt times out UNANSWERED and the
     *  session is saved anyway. [DET-RECONCILE-001] Asymmetry of costs: the prompt only shows
     *  after a real trip + stop + vehicle-exit signal, so the parking almost certainly happened;
     *  discarding it loses the user's car (field incident 2026-07-06, Redmi — a real parking was
     *  thrown away because a notification went unnoticed for 15 min), while saving it wrong costs
     *  one correction tap. Low enough that nothing community-facing trusts it on its own. */
    val reliabilityUnattendedSave: Float = 0.5f,
    // [DET-SOLID-001 C1] reliabilitySlowPath removed: the pure slow-path confirm no longer
    // exists (egress is mandatory for every auto-confirm — DET-C-01), so every Confirmed
    // carries reliabilityVehicleExit and this value was dead code.

    // ── STEP DETECTOR (BUG-GARAGE-COLA-001) ───────────────────────────────────
    /** Number of pedestrian steps observed while the car is stopped that count as
     *  strong evidence the user has exited the vehicle. Once reached, the coordinator
     *  auto-confirms immediately with [reliabilityVehicleExit] reliability, regardless
     *  of whether the IN_VEHICLE→EXIT activity transition has arrived.
     *
     *  **Why 8.** A real parking event produces ≥ 8 steps within ~5–8 s of the user
     *  getting out (slamming door, walking to elevator/sidewalk). 8 is small enough
     *  to fire quickly even in a tight garage (4 m to the lift) and large enough to
     *  ignore stray accelerometer noise. Tune with field telemetry if needed.
     *
     *  **Why this gate.** Without it, the slow path (5 min stopped, no activity-exit)
     *  auto-confirms parking in queues (garage gate, parking ticket booth) where the
     *  user is still sitting in the car. Requiring a pedestrian-step burst eliminates
     *  that false positive without affecting real parkings.
     */
    val minStepsToConfirm: Int = 8,

    /** Number of pedestrian steps observed *before* [hasEverReachedDrivingSpeed] becomes
     *  true that mark the current session as a spurious IN_VEHICLE_ENTER triggered by
     *  walking. When reached, the coordinator aborts immediately — same threshold as
     *  [minStepsToConfirm] for symmetry: 8 steps is unambiguous walking, not riding.
     *
     *  **Why this gate.** On real hardware Activity Recognition fires `IN_VEHICLE_ENTER`
     *  while the user is walking briskly (especially right after parking and getting
     *  out — door slam + walk to trunk + carry bags). Without an early abort, each false
     *  ENTER spins up a fresh coordinator session that waits the full [maxNoMovementMs]
     *  (4 min) before self-terminating. The FGS notification stays visible all that time
     *  and the cycle can repeat. With this gate the session aborts in ~6 s of walking.
     *
     *  **Why before driving speed.** Once the user has reached driving speed, the
     *  coordinator is in a legitimate session and steps must not abort it. After driving
     *  speed crosses, the step counter takes its normal role (confirm parking on stop).
     *
     *  **Trade-off.** A pathological case — phone in pocket bouncing in stop-and-go
     *  traffic for the first minute of a drive — could in theory accumulate 8 step
     *  events before reaching driving speed. Field telemetry has not surfaced this;
     *  if it does, raise this threshold or add a sliding-window timeout. [BUG-FALSE-ENTER-WALKING]
     */
    val falseEnterAbortSteps: Int = 8,

    // ── VEHICLE-MISMATCH GUARD (BUG-SCOOTER-001) ──────────────────────────────
    /** Maximum session top speed (km/h) below which an auto-confirm is treated as
     *  *suspicious* for a CAR vehicle — scooter/moped territory. Combined with
     *  [mismatchMinSessionDurationMs] so a short city errand at 25 km/h doesn't
     *  trigger; a sustained slow trip does. Default 28 km/h sits between the EU
     *  L1e moped cap (45 km/h) and typical urban car cruise (≥ 30 km/h), so cars
     *  in traffic naturally cross it but scooters typically don't. */
    val mismatchMaxSpeedKmh: Float = 28f,
    /** Minimum session wall-clock duration (ms) before the mismatch guard kicks
     *  in. Below this we don't have enough samples to trust [maxSpeedMps].
     *  Default 8 minutes — short enough to catch a typical scooter commute,
     *  long enough that a quick "drive to the kerb to drop someone off" car
     *  trip is exempt. */
    val mismatchMinSessionDurationMs: Long = 8 * 60_000L,
) {
    init {
        require(highConfidenceThreshold in 0f..1f) {
            "highConfidenceThreshold must be in 0..1, was $highConfidenceThreshold"
        }
        require(mediumConfidenceThreshold in 0f..highConfidenceThreshold) {
            "mediumConfidenceThreshold must be in 0..highConfidenceThreshold, was $mediumConfidenceThreshold"
        }
        require(fastPathMinStoppedMs > 0) {
            "fastPathMinStoppedMs must be > 0, was $fastPathMinStoppedMs"
        }
        require(slowPathGateMs > fastPathMinStoppedMs) {
            "slowPathGateMs ($slowPathGateMs) must be > fastPathMinStoppedMs ($fastPathMinStoppedMs)"
        }
        require(geofenceRadiusMotoMeters > 0) {
            "geofenceRadiusMotoMeters must be > 0, was $geofenceRadiusMotoMeters"
        }
        require(geofenceRadiusMeters >= geofenceRadiusMotoMeters) {
            "geofenceRadiusMeters ($geofenceRadiusMeters) must be >= geofenceRadiusMotoMeters ($geofenceRadiusMotoMeters)"
        }
        require(geofenceRadiusLargeMeters >= geofenceRadiusMeters) {
            "geofenceRadiusLargeMeters ($geofenceRadiusLargeMeters) must be >= geofenceRadiusMeters ($geofenceRadiusMeters)"
        }
        require(geofenceRadiusVanMeters >= geofenceRadiusLargeMeters) {
            "geofenceRadiusVanMeters ($geofenceRadiusVanMeters) must be >= geofenceRadiusLargeMeters ($geofenceRadiusLargeMeters)"
        }
        require(geofenceMaxRadiusMeters >= geofenceRadiusVanMeters) {
            "geofenceMaxRadiusMeters ($geofenceMaxRadiusMeters) must be >= geofenceRadiusVanMeters ($geofenceRadiusVanMeters)"
        }
        require(geofenceAccuracyPadFactor >= 0f) {
            "geofenceAccuracyPadFactor must be >= 0, was $geofenceAccuracyPadFactor"
        }
        require(vehicleEnterWindowMs > 0) {
            "vehicleEnterWindowMs must be > 0, was $vehicleEnterWindowMs"
        }
        require(minimumTripSpeedMps > 0) {
            "minimumTripSpeedMps must be > 0, was $minimumTripSpeedMps"
        }
        require(minimumTripDistanceMeters > 0) {
            "minimumTripDistanceMeters must be > 0, was $minimumTripDistanceMeters"
        }
        require(maxNoMovementMs > 0) {
            "maxNoMovementMs must be > 0, was $maxNoMovementMs"
        }
        require(minimumDepartureSpeedKmh > 0) {
            "minimumDepartureSpeedKmh must be > 0, was $minimumDepartureSpeedKmh"
        }
        require(strideMeters > 0f) {
            "strideMeters must be > 0, was $strideMeters"
        }
        require(walkedStepFraction > 0f && walkedStepFraction < 1f) {
            "walkedStepFraction must be in (0,1), was $walkedStepFraction"
        }
        require(frozenCounterSuspectSteps in 0 until maxBoardingSteps) {
            "frozenCounterSuspectSteps must be in 0..<maxBoardingSteps, was $frozenCounterSuspectSteps"
        }
        require(maxPedestrianSpeedMps > 0f) {
            "maxPedestrianSpeedMps must be > 0, was $maxPedestrianSpeedMps"
        }
        require(spotPublishMaxAgeMs > 0) {
            "spotPublishMaxAgeMs must be > 0, was $spotPublishMaxAgeMs"
        }
        require(freshFixMaxAgeMs > 0) {
            "freshFixMaxAgeMs must be > 0, was $freshFixMaxAgeMs"
        }
        require(maxBoardingSteps > 0) {
            "maxBoardingSteps must be > 0, was $maxBoardingSteps"
        }
        require(backfillMaxSteps in 1..maxBoardingSteps) {
            "backfillMaxSteps must be in 1..maxBoardingSteps, was $backfillMaxSteps"
        }
        require(reparkPlausibilityWindowMs > 0) {
            "reparkPlausibilityWindowMs must be > 0, was $reparkPlausibilityWindowMs"
        }
        require(reparkPlausibilityRadiusMeters > 0) {
            "reparkPlausibilityRadiusMeters must be > 0, was $reparkPlausibilityRadiusMeters"
        }
        require(anchorLockEgressSteps >= 1) {
            "anchorLockEgressSteps must be >= 1, was $anchorLockEgressSteps"
        }
        require(anchorFreezeStopMs > 0) {
            "anchorFreezeStopMs must be > 0, was $anchorFreezeStopMs"
        }
        require(anchorFreezeMaxWalkFixes >= 0) {
            "anchorFreezeMaxWalkFixes must be >= 0, was $anchorFreezeMaxWalkFixes"
        }
        require(anchorStrideMeters > 0f) {
            "anchorStrideMeters must be > 0, was $anchorStrideMeters"
        }
        require(initialStopWindowMs > 0) {
            "initialStopWindowMs must be > 0, was $initialStopWindowMs"
        }
        require(maxStoppedFixes >= 1) {
            "maxStoppedFixes must be >= 1, was $maxStoppedFixes"
        }
        require(stoppedSpeedThresholdMps > 0f) {
            "stoppedSpeedThresholdMps must be > 0, was $stoppedSpeedThresholdMps"
        }
        require(clearBestStopSpeedMps > stoppedSpeedThresholdMps) {
            "clearBestStopSpeedMps ($clearBestStopSpeedMps) must be > stoppedSpeedThresholdMps ($stoppedSpeedThresholdMps)"
        }
        require(minGpsAccuracyForDriving > 0) {
            "minGpsAccuracyForDriving must be > 0, was $minGpsAccuracyForDriving"
        }
        require(repositionSpeedMps > stoppedSpeedThresholdMps && repositionSpeedMps <= clearBestStopSpeedMps) {
            "repositionSpeedMps ($repositionSpeedMps) must be in (stoppedSpeedThresholdMps=$stoppedSpeedThresholdMps, clearBestStopSpeedMps=$clearBestStopSpeedMps]"
        }
        require(repositionFixCount >= 1) {
            "repositionFixCount must be >= 1, was $repositionFixCount"
        }
        require(repositionMaxAccuracyMeters > 0 && repositionMaxAccuracyMeters <= minGpsAccuracyForDriving) {
            "repositionMaxAccuracyMeters ($repositionMaxAccuracyMeters) must be in (0, minGpsAccuracyForDriving=$minGpsAccuracyForDriving]"
        }
        require(vehicleExitObservationWindowMs > 0) {
            "vehicleExitObservationWindowMs must be > 0, was $vehicleExitObservationWindowMs"
        }
        require(confirmationObservationWindowMs > vehicleExitObservationWindowMs) {
            "confirmationObservationWindowMs ($confirmationObservationWindowMs) must be > vehicleExitObservationWindowMs ($vehicleExitObservationWindowMs)"
        }
        require(reliabilityUserConfirmed in 0f..1f) {
            "reliabilityUserConfirmed must be in 0..1, was $reliabilityUserConfirmed"
        }
        require(reliabilityUnattendedSave in 0f..reliabilityVehicleExit) {
            "reliabilityUnattendedSave must be in 0..reliabilityVehicleExit, was $reliabilityUnattendedSave"
        }
        require(reliabilityVehicleExit in 0f..reliabilityUserConfirmed) {
            "reliabilityVehicleExit must be in 0..reliabilityUserConfirmed, was $reliabilityVehicleExit"
        }
        require(reliabilityBluetooth in reliabilityVehicleExit..reliabilityUserConfirmed) {
            "reliabilityBluetooth ($reliabilityBluetooth) must be in [reliabilityVehicleExit=$reliabilityVehicleExit, " +
                "reliabilityUserConfirmed=$reliabilityUserConfirmed] — BT is deterministic, stronger than AR-exit"
        }
        require(minStepsToConfirm >= 1) {
            "minStepsToConfirm must be >= 1, was $minStepsToConfirm"
        }
        require(minEgressDisplacementMeters > minGpsAccuracyMeters) {
            "minEgressDisplacementMeters ($minEgressDisplacementMeters) must be > minGpsAccuracyMeters " +
                "($minGpsAccuracyMeters) so a single in-envelope GPS noise fix cannot satisfy the gate"
        }
        require(falseEnterAbortSteps >= 1) {
            "falseEnterAbortSteps must be >= 1, was $falseEnterAbortSteps"
        }
        require(mismatchMaxSpeedKmh > 0) {
            "mismatchMaxSpeedKmh must be > 0, was $mismatchMaxSpeedKmh"
        }
        require(mismatchMinSessionDurationMs > 0) {
            "mismatchMinSessionDurationMs must be > 0, was $mismatchMinSessionDurationMs"
        }
        require(lowNotifTimeoutMs > 0) {
            "lowNotifTimeoutMs must be > 0, was $lowNotifTimeoutMs"
        }
        require(confirmationResponseTimeoutMs > lowNotifTimeoutMs) {
            "confirmationResponseTimeoutMs ($confirmationResponseTimeoutMs) must be > lowNotifTimeoutMs ($lowNotifTimeoutMs)"
        }
        require(confirmHoldMs >= 0) {
            "confirmHoldMs must be >= 0 (0 disables the post-confirm hold), was $confirmHoldMs"
        }
    }

    /**
     * Effective geofence radius (meters) for a parked car: a size-based base padded by the GPS
     * accuracy at park time and capped at [geofenceMaxRadiusMeters]. Single source of truth shared
     * by [io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase] /
     * [io.apptolast.paparcar.domain.usecase.parking.UpdateParkingLocationUseCase] (which register
     * the geofence) and the AR proximity re-arm gate
     * ([io.apptolast.paparcar.domain.usecase.detection.ShouldArmFromVehicleEnterUseCase], which must
     * use the SAME radius so AR and the geofence EXIT meet at the same boundary — no dead ring, no
     * extra bus surface). [DET-AR-REARM-001]
     */
    fun geofenceRadiusFor(sizeCategory: VehicleSize?, accuracyMeters: Float): Float {
        val base = when (sizeCategory) {
            VehicleSize.MOTORCYCLE -> geofenceRadiusMotoMeters
            VehicleSize.LARGE_SEDAN -> geofenceRadiusLargeMeters
            VehicleSize.VAN_HIGH -> geofenceRadiusVanMeters
            else -> geofenceRadiusMeters // MICRO_SMALL, MEDIUM_SUV, null
        }
        val padded = base + (accuracyMeters * geofenceAccuracyPadFactor)
        return padded.coerceAtMost(geofenceMaxRadiusMeters)
    }

    /**
     * THE canonical "this fix proves the user is driving" test — speed AND accuracy together,
     * single source of truth for every departure-side decision (pre-arm evidence, departure
     * verdict, safety-net evaluator). Speed from a degraded fix is not evidence: a single
     * acc=100 m cache jump reported 21.6 km/h with the phone motionless on a nightstand and
     * confirmed a departure that published a ghost spot (field 2026-07-08 04:18, Oppo) — the
     * accuracy gate existed in the coordinator and the evaluator but NOT in the departure
     * verdict, because each site carried its own copy of this rule. [DET-EXIT-TRUST-001]
     *
     * A null accuracy passes only because callers that have a speed always have its fix's
     * accuracy too — the permissive branch keeps legacy call shapes valid, it is not a loophole.
     */
    fun isCredibleDrivingSpeed(speedKmh: Float?, accuracyMeters: Float?): Boolean =
        speedKmh != null && speedKmh >= minimumDepartureSpeedKmh &&
            (accuracyMeters == null || accuracyMeters <= minGpsAccuracyForDriving)

    /**
     * Pedestrian-reach corroboration: could the user have gotten [distanceMeters] away from the
     * parked car ON FOOT within [elapsedMs]? Returns true only when they could NOT — i.e. only a
     * vehicle explains the displacement.
     *
     * This is the single physics check behind [DET-RIDE-PROOF-001]: an OS event (AR
     * `IN_VEHICLE_ENTER`, geofence EXIT) can only NOMINATE a departure; what CONFIRMS it is the
     * position running away from the car faster than legs allow. A spurious ENTER while walking
     * (field 2026-07-09 11:53, Redmi: phantom ENTER 14 s before a walking EXIT at 127 m released
     * the spot and seeded a phantom park at the hairdresser's) fails this check by construction.
     *
     * Conservative by design: the user may already have been anywhere inside the fence when the
     * clock started ([fenceRadiusMeters]) and the fix may err by its own [accuracyMeters] — both
     * count in favor of "walkable", so a `true` verdict is unambiguous.
     */
    fun isBeyondPedestrianReach(
        distanceMeters: Double,
        elapsedMs: Long,
        fenceRadiusMeters: Float,
        accuracyMeters: Float,
    ): Boolean {
        if (elapsedMs < 0) return false
        val walkableMeters =
            maxPedestrianSpeedMps * (elapsedMs / 1000.0) + fenceRadiusMeters + accuracyMeters
        return distanceMeters > walkableMeters
    }
}
