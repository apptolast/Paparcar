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
    /** Minimum speed (km/h) that confirms the user is driving away. Speed check is skipped
     *  when GPS is unavailable. Default 10 km/h. */
    val minimumDepartureSpeedKmh: Float = 10f,

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
        require(reparkPlausibilityWindowMs > 0) {
            "reparkPlausibilityWindowMs must be > 0, was $reparkPlausibilityWindowMs"
        }
        require(reparkPlausibilityRadiusMeters > 0) {
            "reparkPlausibilityRadiusMeters must be > 0, was $reparkPlausibilityRadiusMeters"
        }
        require(anchorLockEgressSteps >= 1) {
            "anchorLockEgressSteps must be >= 1, was $anchorLockEgressSteps"
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
}
