# Parking Detection — Reference Document

**Status:** living document. Update when detection logic, scoring thresholds, or persistence paths change.
**Audience:** solo developer + AI pair.
**Scope:** the end-to-end flow that decides a parking spot has been confirmed, persists it to Room + Firestore, registers a geofence for departure detection, and posts the user-facing notification.

This is the canonical reference for *how parking detection works today* and *what bugs we have already burned in trying to make it work*. Section 1 describes the algorithm. Section 2 logs every fix shipped, so future-you (or future-Claude) understands why a given guard exists before deleting it.

> **Related:** this document covers the *algorithm*. The *readiness / permission / banner* layer — what the app tells the user about detection (armed / running / blocked / not applicable), the CORE-vs-PRODUCER permission tiering, and the onboarding flow — is documented separately in [`DETECTION-READINESS.md`](./DETECTION-READINESS.md) (epic DET-READY-001).

---

## 1. Algorithm and architecture

### 1.1 Dual-strategy design

Paparcar detects the moment a user parks their car so it can publish the freshly-freed spot to the community. Two independent strategies converge on the same persistence step:

| Strategy | Trigger | Reliability | When |
|---|---|---|---|
| **BluetoothDetectionStrategy** | Car BT disconnects → debounce → GPS fix → user walks ≥ 30 m | 0.95 (deterministic) | The phone is currently **CONNECTED** to the paired car (you're driving THAT car) |
| **CoordinatorDetectionStrategy** | Activity Recognition + GPS stream → confidence scoring | 0.75 / 0.90 / 1.00 (probabilistic) | Everyone else — no BT, BT off, no paired device, **or driving a different car not currently connected** |

The choice is made in `ParkingStrategyResolver` and honours the **BT-owns-when-connected** invariant [DET-BT-CONNECTED-NOT-PAIRED-001]: BLUETOOTH is chosen only while the phone is **connected** (ACL link up) to a paired car — the ground truth of "I'm driving THIS car". A car that is merely paired-and-enabled (sitting at home while you drive a different, non-BT car) no longer hijacks the strategy: that resolves to COORDINATOR, whose resident FGS watches the car you're actually in. This decouples "primary vehicle for identity fallbacks" (`isActive`) from "vehicle detection is following right now" (the connected car). See ARCH-MONITORING-002 in §2.

> **History.** Until 2026-08-08 BLUETOOTH was chosen on mere pairing + adapter-on (`hasAnyBtPaired && isBluetoothEnabled()`). Field 08-08 (Málaga): with the Kamiq paired (BT) and the Focus active (no BT), driving the **Focus** resolved to BLUETOOTH → Coordinator suppressed, and the BT pipeline waited for the Kamiq's disconnect that never came → **the Focus was never detected**. Fixed by gating on live connection.

```kotlin
enum class ParkingStrategy { NONE, BLUETOOTH, COORDINATOR }

suspend fun resolve(): ParkingStrategy {
    val vehicles = vehicleRepository.observeVehicles().first()

    // BT owns detection only while CONNECTED to a paired car — not merely paired. [DET-BT-CONNECTED-NOT-PAIRED-001]
    val btPairedVehicleIds = vehicles
        .filter { it.bluetoothDeviceId != null && it.vehicleType !in NON_PARKING_TYPES }
        .map { it.id }.toSet()
    if (btPairedVehicleIds.isNotEmpty() &&
        bluetoothScanner.isBluetoothEnabled() &&
        bluetoothScanner.isConnectedToPairedCar(btPairedVehicleIds)   // ← live ACL connection
    ) {
        return ParkingStrategy.BLUETOOTH
    }

    // Coordinator path: keyed on the primary; suppress if primary doesn't park.
    val primary = vehicles.firstOrNull { it.isActive } ?: vehicles.firstOrNull()
    if (primary != null && primary.vehicleType in NON_PARKING_TYPES) {
        return ParkingStrategy.NONE        // SCOOTER, BIKE
    }
    return ParkingStrategy.COORDINATOR
}
```

Connection state is ground truth from `BtConnectionStore` (SharedPreferences), which the manifest `BluetoothConnectionReceiver` keeps current on every ACL connect/disconnect edge — it fires across the OEM process kills, so this survives where a live async profile-proxy poll could not. Safe because the BT car's own disconnect is caught by that receiver **independently of this resolver**; once disconnected the strategy falls back to COORDINATOR, which demands measured driving to pin (a walk from the car aborts) and whose mid-session BT edges are arbitrated by `EvaluateBtArbitrationUseCase`.

The strategies never mix signals. BLUETOOTH and COORDINATOR converge on `ConfirmParkingUseCase`. NONE skips parking detection entirely — scooters and bikes are dismounted on the sidewalk and never liberate a parking spot. See BUG-SCOOTER-001 in §2.

**The resolution is enforced in ONE choke point** [DET-STRATEGY-GATE-001]: every automatic arm of
the coordinator (geofence EXIT, AR ENTER, sentry significant-motion) funnels through
`CoordinatorDetectionService.startParkingDetection()`, which consults the pure rule
`coordinatorMayArm(strategy, trigger)` (`ParkingStrategyResolver.kt`). Under BLUETOOTH or NONE the
automatic arm is refused with a PARKDIAG trace; `MANUAL` (explicit user intent, and the
safety-net's arrival handoff which rides the same action) is always admitted — if the parked car
turns out to be the BT one, the disconnect arbitration supersedes the session
(`EvaluateBtArbitrationUseCase`). The geofence-EXIT lane additionally short-circuits early to skip
its pre-arm work. Before this gate only the EXIT lane checked, and the sentry/AR lanes armed
anyway: field 2026-08-01, the BT-paired Kamiq's trips were pinned on the primary (non-BT) Focus.

**SENTRY residency is strategy-aware** [DET-STRATEGY-GATE-001]: `resolvePostDetectionLifecycle`
(`SentryLifecycleDecision.kt`) only keeps the service resident when COORDINATOR owns detection.
Under BLUETOOTH the ACL broadcast wakes a dead process by itself (manifest receiver, exempt from
the Android 12+ FGS-from-background restriction), so the resident watcher would only cost battery
and a permanent notification. The strategy is re-resolved on every idle epilogue, so pairing or
unpairing a BT device — or toggling the adapter — self-corrects one detection cycle later.

**Mid-session BT edges are arbitrated by the pure `EvaluateBtArbitrationUseCase`** [DET-TIERS-001]
[DET-BT-WRONG-CAR-ABORT-001]: the receiver evaluates every ACL edge from an OWN paired car against
the running coordinator session and the service executes any non-NoOp verdict as an abort. BT never
scores — it overrides. The session's `coordinatorVehicleId` (the geofence-exit nominator) is a
HYPOTHESIS: the fence only proves the PHONE left the area, not that THAT car moved, and the phone
can only be in one car. Truth table:

| edge | session car vs BT car | phase | verdict |
|---|---|---|---|
| DISCONNECT | same / unknown | any | `SupersedeWithBluetooth` — user left the car; BT confirms deterministically |
| DISCONNECT | **different own car** | any | `SupersedeWithBluetooth` — the park belongs to the BT car; a coordinator pin would be a misattributed duplicate |
| CONNECT | same / unknown | Candidate | `VetoReturnToVehicle` — user is back in; discard the tentative pin |
| CONNECT | same / unknown | Driving | `NoOp` — reconnect is consistent with the trip |
| CONNECT | **different own car** | Driving | `YieldToConnectedCar` — the phone is provably in the other car; abort before any pin forms (BT owns detection while connected) |
| CONNECT | **different own car** | Candidate | `NoOp` — the pending pin was earned with measured pre-connect evidence (park car A, walk to car B); boarding B refutes the future, not the past |

Before DET-BT-WRONG-CAR-ABORT-001 the different-car rows were all NoOp ("an edge from car A never
vetoes a trip following car B"), which was correct against the neighbour's identical car (unknown
MACs never reach the arbiter) but wrong against the user's OWN other car. Field 2026-08-10 (Oppo,
parkdiag.log): manual Focus park at home → its geofence EXIT armed the coordinator with
`vehicleId locked: Focus` while the user drove the Kamiq, BT enabled mid-drive (CONNECT 19:44:18 →
NoOp), Kamiq DISCONNECT at destination 19:49:19 → NoOp by the old vehicle guard → BT pinned the
Kamiq 19:50:29 AND the coordinator's 120 s errand-hold finalized a phantom Focus pin 19:51:44,
replacing the Focus's real home session. The same evening's return trip proved the mechanism: the
coordinator re-armed with `nominator=null`, so the unknown-origin path let the DISCONNECT supersede
and a single BT pin resulted.

**Session attribution at the vehicleId lock is BT-aware** [DET-BT-OWNERSHIP-001]: when the
coordinator locks the session's vehicle on the first driving-speed fix, the pure
`VehicleFenceOwnershipPolicy.resolveSessionVehicleId(nominatingVehicleId, nominatingVehicleIsBtPaired,
activeVehicleId)` decides the owner:

1. A **non-BT nominator wins over the active vehicle** — the fence that fired identifies the car
   [VEH-ACTIVE-FENCE-001].
2. A **BT-paired nominator is VETOED** → attribution falls back to the ACTIVE vehicle. A vehicle
   with a `bluetoothDeviceId` belongs exclusively to the Bluetooth strategy: its identity is only
   ever established by the MAC (ACL edges), never by a fence — the fence only proves the PHONE
   left. The coordinator is the ACTIVE vehicle's strategy. Log provenance:
   `✓ vehicleId locked: <active> (nominator=<btCar> vetoed: bt-owned)`.
3. **No nominator** → active vehicle. **Nothing resolvable** (e.g. BT nominator vetoed and no
   active vehicle) → the session aborts (`aborted_no_vehicle`) — better a false negative than a
   misattributed pin.

Field 2026-08-11 (Firestore diagnostics): the user drove the ACTIVE Focus all day, but a 10-08 BT
trip had left a parked **Kamiq** session with a live fence; every arm nominated the Kamiq via
`TripContext(session.location, session.vehicleId)`, the old `nominating ?: active` lock stamped all
**8 coordinator parks on the Kamiq**, and each confirm re-fenced the Kamiq and re-armed the chain —
a self-feeding misattribution loop. Deliberate scope decisions: **arming is untouched** (any fence
or sentry may wake the coordinator — the nominator keeps travelling as a hypothesis, which the BT
arbitration above consumes); an **active vehicle that is itself BT-paired keeps its attribution**
(the veto falls back onto the same id — explicit user declaration; a possibly-redundant pin beats a
lost parking); the positive BT arbitration (`EvaluateBtArbitrationUseCase`) is unchanged.

### 1.2 BluetoothDetectionStrategy (deterministic)

**Runtime owner:** `BluetoothDetectionService` (`LifecycleService`, `START_NOT_STICKY`,
`foregroundServiceType="location"`). The Service keeps the process alive while detection runs.
`BluetoothConnectionReceiver` does minimum work: resolve vehicleId from DB, then
`startForegroundService(ACTION_BT_DISCONNECTED)` or `startService(ACTION_BT_CONNECTED)`. [BT-REFACTOR-FGS-001]

`BluetoothParkingDetector.detectParking()` (suspend):

1. **Debounce** — `delay(BT_DISCONNECT_DEBOUNCE_MS = 30 s)`. Cancellable — if BT reconnects, the Service cancels the coroutine here before the delay returns (BT-005).
2. **GPS fix** — sample the location stream until `accuracy ≤ GPS_ACCURACY_THRESHOLD_M = 50 m`, or `GPS_SAMPLE_TIMEOUT_MS = 60 s` elapses. The first fix that meets the accuracy bar is the candidate parking location.
3. **Walking confirmation** — keep watching GPS until the user has moved `≥ DISTANCE_THRESHOLD_M = 30 m` from the candidate fix. This rules out "BT dropped while still in the car" cases (passenger left, head-unit died, etc.).
4. **Confirm** — `confirmParking(candidateFix, config.reliabilityBluetooth = 0.95f)`. [DET-F-01]
5. **Timeout-save** [DET-BT-TIMEOUT-SAVE-001] — if the walk-away watch expires
   (`btWalkAwayTimeoutMs = 15 min`) with the stationary pin-grade candidate still standing, the
   OWN session is saved anyway at `reliabilityBluetoothTimeoutSave = 0.85f`, provenance
   `bt_timeout`. This is the home-park case (field 2026-08-06 01:46: parked at home, went inside,
   never covered 30 m — the abort lost the user's "where is my car", a regression vs the
   coordinator which confirms home parks via steps+egress). Safe because nothing community-facing
   publishes at confirm time (spots publish at departure), and a mid-drive BT drop cannot reach
   the timeout: a driving candidate never passes step 2, and vehicle-rate displacement during the
   watch aborts with no save. A garage park with no usable GPS still aborts (no candidate).

Abort-on-reconnect (BT-005): when `ACTION_ACL_CONNECTED` arrives, the Receiver starts the Service with `ACTION_BT_CONNECTED`. The Service calls `detectionJob?.cancel()` — the suspend function receives `CancellationException` at the active suspension point (`delay` or `Flow.first`) and exits cooperatively. The detector itself carries no cancellation flag.

> **DET-E-01 reverted (code review):** feeding `DepartureEventBus.onVehicleEntered` on BT connect
> made the `BUG-WALK-DEPART-001` fallthrough in `DepartureDetectionWorker` treat a BT user merely
> *sitting in* their parked car as a departure (enter present + no speed → publish phantom spot). AR
> `IN_VEHICLE_ENTER` (which detects real motion, not mere BT pairing) already covers BT users and is
> the stronger signal, so the BT connect no longer touches the departure bus.

This strategy has no scoring and no medium-confidence path: BT disconnect + GPS-anchored walk is treated as ground truth.

### 1.3 CoordinatorDetectionStrategy (probabilistic)

`CoordinatorParkingDetector.invoke(locations: Flow<GpsPoint>)` is the heart of the probabilistic path. It owns a single `MutableStateFlow<ParkingDetectionState>` updated atomically per location fix; external signals (`onVehicleExit`, `onStillDetected`, `onUserConfirmedParking`, `onUserDeniedParking`) feed in via thread-safe setters.

The coordinator is a Koin **single**, kept stateful across sessions so the foreground service can drive multiple invocations into the same instance; `reset()` runs at the top of every `invoke()`.

#### State machine

```
              ┌──────────────────────────────────────────────────────────┐
              │                  ParkingDetectionState                   │
              ├──────────────────────────────────────────────────────────┤
   hasEverMoved=false   ──speed≥5 m/s AND dist≥150 m──►   hasEverMoved=true
                                                                         │
                                                                         ▼
                                       ┌──── stopped (speed < 1 m/s) ────┐
                                       │                                 │
                                       │  initialStopWindow (30 s):      │
                                       │  - capture up to 20 stoppedFixes│
                                       │  - update bestStopLocation      │
                                       │    to the lowest-accuracy fix   │
                                       │                                 │
                                       │  scoring (CalculateParkingConf.)│
                                       │  ├── NotYet  → do nothing       │
                                       │  ├── Low/Med → notify user*     │
                                       │  └── High    → CANDIDATE phase  │
                                       │                                 │
                                       └─────────────────┬───────────────┘
                                                         │
                                                         ▼
                                       ┌─── CANDIDATE phase ─────────────┐
                                       │ observation window:             │
                                       │  - vehicleExit path: 2 min      │
                                       │  - slow path:        5 min      │
                                       │                                 │
                                       │ during window:                  │
                                       │  - clearBestStopSpeedMps with   │
                                       │    accuracy ≤ 50 m → reset      │
                                       │  - userConfirmed → confirm now  │
                                       │  - userDenied   → reset all     │
                                       │  - window expires → confirm     │
                                       └─────────────────────────────────┘
```

#### Movement gating: `hasEverMoved`

Detection is suppressed until a single fix proves the user **actually drove**:

```kotlin
val hasJustMoved = !s.hasEverMoved &&
    location.speed >= config.minimumTripSpeedMps        // 5 m/s ≈ 18 km/h
    && distFromOrigin >= config.minimumTripDistanceMeters  // 150 m
```

Both clauses must hold simultaneously. This kills spurious `IN_VEHICLE_ENTER` events fired when the user is parked in their car (engine off), cycling, or sitting at a desk — a single GPS speed spike or a single position jump cannot satisfy both at once. A `maxNoMovementMs = 4 min` watchdog ends the session quietly if `hasEverMoved` never trips.

#### Stop tracking and `bestStopLocation`

`updateStopTracking()` runs on every fix:

- **Stopped** (`speed < STOPPED_SPEED_THRESHOLD_MPS = 1 m/s`):
  - `stoppedSince` is set to `now` on the first such fix, then preserved.
  - Within `initialStopWindowMs = 30 s` of `stoppedSince`, fixes are accumulated into `stoppedFixes` (capped at 20) and `bestStopLocation` is updated whenever a fresh fix has *better* accuracy than the current best. **After 30 s the location is frozen** — see LOC-001 in §2.
  - **[DET-GAP-ANCHOR-001]** If the fix that *opens* the stop arrived more than `anchorGapMaxFixGapMs = 45 s` after a `previousFix` still at real driving speed (≥ `minimumTripSpeedMps`), the stop is flagged `stopEnteredAfterGap`: the car's arrival at rest fell inside a GPS hole and this position may be a drive-past point. Any anchor bound to such a stop is stamped `anchorGapEnteredAtCapture` — silent confirm degrades to a prompt, the unattended save to a nudge, and a user "Sí" re-anchors at the user's current stop (a gap-entered anchor never wins the user-confirm re-anchor either — see DET-CONFIRM-ANCHOR-001). Cleared with the anchor when real driving resumes.
- **Moving** (`speed ≥ 1 m/s`):
  - `stoppedSince = null`, `stoppedFixes = emptyList()`.
  - If `speed ≥ clearBestStopSpeedMps = 2.5 m/s` **AND** `accuracy ≤ minGpsAccuracyForDriving = 50 m`, the coordinator treats the fix as evidence the vehicle is driving away again: `bestStopLocation`, `vehicleExitConfirmed`, `activityStillDetected`, and `highConfidenceReachedAt` are all cleared. The accuracy gate exists because hardware GPS hallucinates apparent-driving speed in noisy fixes — see LOC-002 in §2.
  - If `speed ≥ repositionSpeedMps = 1.7 m/s` **AND** `accuracy ≤ repositionMaxAccuracyMeters = 15 m` for **three consecutive fixes**, `bestStopLocation` is cleared as a reposition burst. This is between sustained walking (~1.2 m/s, never crosses 1.7) and the driving threshold; it lets the coordinator distinguish a brief vehicle maneuver (wait + park into a freed spot) from GPS oscillation noise — see PARKING-001 in §2.

The 2.5 m/s driving ceiling is deliberately above typical walking speed (~1.4 m/s), so the captured parked-car position survives the user walking away on foot. The 1.7 m/s reposition floor is deliberately above walking too, gated by **three** consecutive fixes with accuracy ≤ 15 m. The reposition accuracy gate (15 m) is stricter than the driving gate (50 m) because at slow-maneuver speeds, GPS noise with acc > 15 m is far more common than genuine vehicle motion — field logs (Redmi Note 11, 2026-05-30) showed sustained 5-burst storms at acc=22–48 m that cleared `bestStopLocation` while the user was stationary. The 50 m gate is preserved only for the `isDriving` path (speed ≥ 2.5 m/s), where Redmi hardware can report legitimate driving at that accuracy level.

#### Confidence scoring

`CalculateParkingConfidenceUseCase` reads a `ParkingSignals` snapshot (`speed`, `stoppedDurationMs`, `gpsAccuracy`, `activityExit`, `activityStill`) and returns one of:

- `NotYet` — early or invalid signal combination.
- `Low(score)` — gates a confirmation notification (only if `vehicleExit` or `activityStill` signal present) but never auto-confirms.
- `Medium(score)` — same.
- `High(score)` — opens the CANDIDATE phase.

> **DET-D-03 (2026-06-26) — STILL removed as a fed signal.** Activity Recognition no longer registers
> STILL transitions and nothing calls `onStillDetected()` (`ActivityRecognitionManagerImpl`,
> `ActivityTransitionReceiver`, the iOS impl and the coordinator's `activityStillDetected` state were
> all stripped). STILL was redundant with the egress gate and fired in traffic jams (a fragile
> non-event signal). In production `ParkingSignals.activityStill` is now **always false**, so every
> `activityStill` branch in the scorer below is inert (the fast path tops at 0.65 = Medium; the
> slow-path `stillBonus` never applies). The scorer scaffolding is kept until the D-03c
> scorer→metadata rework removes it. Confirmation is unaffected — it is decided by the egress gate.

> **DET-C-02 (2026-06-26) — post-confirm hold.** An auto egress-confirm is now **tentative**: instead
> of ending the session, the coordinator records a `PendingConfirm` and stays alive for
> `confirmHoldMs` (default 2 min). If driving resumes (`speed > clearBestStopSpeedMps` with a
> trustworthy fix) before the window elapses, the tentative confirm is **discarded** and detection
> continues — so an errand stop (park → walk to a kiosk → drive on to park properly) **re-anchors at
> the final spot** instead of pinning the errand location. If the window elapses with the car still
> stopped (or the user taps "yes"), the park is finalised via the normal `confirmParking` path. The
> hold makes confirmation *stricter* (never weaker): the egress gate is still mandatory. `confirmHoldMs
> = 0` disables it (legacy immediate-confirm; used by the synchronous unit tests). The wall clock is
> injected (`clock`) so the hold is unit-testable. Tune `confirmHoldMs` with field telemetry.
> **[DET-CONFIRM-FRESHNESS-001 (2026-07-24)]** the auto settle now **re-validates** before pinning:
> if the current fix sits farther from the held pin than the counted steps could walk
> (stride + both accuracy envelopes + `egressBirthFloorMeters`), a vehicle covered that ground during
> the hold (a departure the drove-off discard missed — degraded fix or GPS gap) → the tentative
> confirm is discarded (`HOLD_STALE_DISCARDED` in diagnostics) and detection continues. The user-yes
> path is exempt. The clock-driven hold watchdog does NOT re-validate (no fix to validate against —
> a starved stream is a walk into a building, not a car driving off).

Two scoring paths feed the same threshold (`highConfidenceThreshold = 0.75`):

**Fast path** — requires `activityExit = true` (an `IN_VEHICLE → EXIT` Activity Recognition transition was observed):
- Base 0.50 + 0.15 if speed ≤ `maxSpeedMps (0.3)` + 0.10 if **`activityStill = true`** AND accuracy ≤ `minGpsAccuracyMeters (15 m)` = up to 0.75.
- Without `activityStill`, the maximum fast-path score is 0.65 (Medium) — the user must confirm manually. This prevents auto-confirmation at hospital entrances or drop-off stops where the activity-exit transition arrives before a STILL confirmation. [BUG-DETECT-310503]
- Requires the stop to have lasted `fastPathMinStoppedMs = 30 s`.

**Slow path** — no activity-exit signal, pure time-based:
- Stopped ≥ 5 min: base 0.70 (+ optional bonuses up to 0.90).
- Stopped ≥ 3 min: base 0.45 — capped so it never reaches High even with all bonuses (0.45 + 0.10 + 0.05 + 0.05 = 0.65 → Medium). This deliberately prevents auto-confirmation on short stops like errand pickups.
- Stopped ≥ `slowPathGateMs (90 s)`: base 0.40, scoring just begins. Below this gate the score is `NotYet`.

The slow path is meant for users without paired BT and without Activity Recognition support; it requires 5 full minutes of stop-quality signal to auto-confirm. Anyone shorter must either tap the notification or rely on the fast path.

#### CANDIDATE phase

When confidence first reaches `High`, the coordinator enters a CANDIDATE phase:

```kotlin
ParkingDetectionState(
    highConfidenceReachedAt = now,
    highCandidateHadVehicleExit = state.vehicleExitConfirmed,  // freeze path type
)
```

The observation window depends on which path opened the candidate:
- Vehicle-exit path: `vehicleExitObservationWindowMs = 2 min`.
- Slow path: `confirmationObservationWindowMs = 5 min`.

During the window, the only events that matter are:
1. `userConfirmedParking()` → confirm immediately with `reliabilityUserConfirmed = 1.0f`.
2. `userDeniedParking()` → full state reset (preserving `hasEverMoved`).
3. A trusted driving signal (speed ≥ 2.5 m/s, accuracy ≤ 50 m) → reset to scoring.
4. **Pedestrian steps** ≥ `minStepsToConfirm = 8` while stopped **AND** displacement ≥ `minEgressDisplacementMeters = 18 m` from the park anchor → confirm immediately with `reliabilityVehicleExit = 0.90f`. Steps + egress is unambiguous proof the user exited *and walked away from* the car; steps alone are not (a phone bouncing in stop-and-go traffic counts steps while the car never moved). See DET-A in §2. [BUG-GARAGE-COLA-001]
5. Window expires **with** vehicle-exit signal **AND** egress displacement ≥ `minEgressDisplacementMeters = 18 m` → confirm with `reliabilityVehicleExit = 0.90f`. **[DET-C-01]** AR-exit + dwell-time on their own no longer confirm: a spurious `IN_VEHICLE_EXIT` during a long traffic stop must not publish a phantom spot. Egress displacement is now mandatory for **every** candidate auto-confirm path (see §2 DET-C-01).
6. Window expires **without** the egress conjunction → discard the candidate (likely cola/atasco). The notification that fired on High entry remains the only chance to confirm; if the user did park and ignored it, the next session catches them.

The confirmation notification is **always** posted when the CANDIDATE phase opens, so the user has the option to override.

### 1.4 ConfirmParkingUseCase — the convergence point

Both strategies call `confirmParking(location, reliability, spotType?, sizeCategory?)`. This use case is the only place where parking state hits storage. Its responsibilities, in order:

1. Resolve the current user (`authRepository.getCurrentSession()`).
2. Resolve the default vehicle (`vehicleRepository.observeDefaultVehicle().first()`) — used to populate `UserParking.vehicleId` and to default `sizeCategory` for the geofence.
3. Build a `UserParking` domain object with the new `sessionId`, the chosen location, reliability score, spot type, and resolved size.
4. **Room write only** — `userParkingRepository.saveNewParkingSession(session)` clears any previously active row and inserts the new one. Returns the previous session's id (if any) so it can be reconciled remotely.
5. **Schedule Firestore sync** — `parkingSyncScheduler.enqueueSaveNewParkingSession(session, previousSessionId)` enqueues a `SaveNewParkingSessionWorker` job. The coordinator does **not** await network IO. See PIPE-001 in §2 for why.
6. **Schedule background enrichment** — `enrichmentScheduler.schedule(sessionId, lat, lon)` enqueues the geocoder + POI lookup worker.
7. **Register geofence** — adaptive radius based on vehicle size and current GPS accuracy (see §1.6).
8. **Show notification** — "Saved your parking spot" with deep-link to the map.

Step 4 is the only suspending operation that can fail in a way the caller cares about. Steps 5–8 are scheduled or fire-and-forget; their failures are logged but do not propagate.

### 1.5 Persistence pipeline

```
ConfirmParkingUseCase
     │
     ├── Room (saveNewParkingSession)                                   ◄── synchronous, local
     │
     ├── ParkingSyncScheduler.enqueueSaveNewParkingSession()             ◄── WorkManager
     │      └── SaveNewParkingSessionWorker.doWork()
     │             ├── Firestore set(newSession DTO)
     │             └── Firestore update(prev.isActive = false)
     │
     ├── ParkingEnrichmentScheduler.enqueueEnrichSession()               ◄── WorkManager
     │      └── EnrichParkingSessionWorker.doWork()
     │             ├── reverseGeocode(lat, lon)
     │             ├── lookupPoi(lat, lon)
     │             └── userParkingRepository.updateParkingSessionAddressAndPlace()
     │                    ├── Room update (address + placeInfo)
     │                    └── UpdateParkingSessionAddressAndPlaceWorker (Firestore reconcile)
     │
     ├── GeofenceManager.createGeofence()       ◄── GMS Geofencing API
     │
     └── notificationPort.showParkingSpotSaved()
```

On departure (`onGeofenceExit`):

```
GeofenceExitReceiver
     │
     └── ReleaseActiveParkingSessionUseCase
            ├── schedule ReportSpotReleasedUseCase   ◄── WorkManager: publishes the spot
            │      └── ReportSpotWorker.doWork()
            │             └── Firestore set(spot)
            │
            └── userParkingRepository.clearActiveParkingSession(sessionId)
                   ├── Room update (isActive=0)
                   └── ClearActiveParkingSessionWorker (Firestore reconcile)
```

Every Firestore write lives in a WorkManager job. The foreground service path is bounded by local IO + GMS Geofencing only; no Firestore call can hang `confirmParking`.

### 1.6 Geofence radius adaptation

`computeGeofenceRadius(sizeCategory, accuracyMeters)` in `ConfirmParkingUseCase`:

```kotlin
val base = when (sizeCategory) {
    VehicleSize.MOTO  -> 60f
    VehicleSize.LARGE -> 100f
    VehicleSize.VAN   -> 120f
    else              -> 80f         // SMALL, MEDIUM, null
}
val padded = base + (accuracyMeters * 1.5f)
return padded.coerceAtMost(200f)     // geofenceMaxRadiusMeters
```

A moto parked with 5 m GPS accuracy gets a 67.5 m geofence — tight enough to detect a real departure without false-positives from parked-but-shifting GPS. A van parked with 40 m accuracy gets 180 m. The cap of 200 m exists so a single garbage fix can't cover a whole neighborhood.

### 1.7 Departure detection — step-by-step flow

When the user leaves with their car, departure detection runs through two parallel signal chains that must agree before the parking session is cleared and the spot is published.

#### Step 1 — Geofence exit

When the user drives far enough from the saved parking location, Google Play Services fires a geofence exit event **directly to `CoordinatorDetectionService` (`ACTION_GEOFENCE_EXIT`)** via the privileged `getForegroundService` PendingIntent (DET-G-01). `handleGeofenceExit` extracts `GeofencingEvent.fromIntent(intent)`, reads `triggeringGeofences`, looks up the active session for the geofence (orphan geofences are self-removed, not armed), and enqueues `DepartureDetectionWorker` via WorkManager with `KEY_GEOFENCE_ID` and `KEY_EXIT_TIMESTAMP`. The old `GeofenceBroadcastReceiver` fallback was removed once device-validated. [DET-AR-REARM-001]

> **Important:** the geofence `PendingIntent` **must** use `FLAG_MUTABLE`. Play Services fills `GeofencingEvent` extras into the intent at delivery time; `FLAG_IMMUTABLE` blocks this on Android 12+ — `triggeringGeofences` arrives as `null` and the handler silently returns without enqueuing the worker. See BUG-GEOFENCE-001 in §2.

#### Step 2 — Activity Recognition: IN_VEHICLE_ENTER

Independently, `ActivityRecognitionManagerImpl` is subscribed to `IN_VEHICLE_ENTER` transitions. When Play Services fires this event, it delivers directly to `CoordinatorDetectionService` via `PendingIntent.getForegroundService()` (ACTION_VEHICLE_TRANSITION). The service records `departureEventBus.onVehicleEntered(epochMs)` — an in-memory timestamp marking the moment the user entered a vehicle.

#### Step 3 — DepartureDetectionWorker: three-signal check

`DepartureDetectionWorker.doWork()` calls `DetectParkingDepartureUseCase` with the geofence id, the exit timestamp, and the current GPS speed (fresh fix via `GetOneLocationUseCase`). The use case checks:

1. **Active session exists** and its `geofenceId` matches the one that fired — prevents false cross-vehicle triggers. Returns `Rejected` if no match.
2. **IN_VEHICLE_ENTER window** — `departureEventBus.lastVehicleEnteredAt` must be within `vehicleEnterWindowMs = 30 min` of the exit timestamp. Stale signals (yesterday's drive) are ignored. Returns `Inconclusive` if no recent signal.
3. **GPS speed** — if a fresh fix is available, speed must exceed `minimumDepartureSpeedKmh = 10 km/h`. Returns `Inconclusive` if below threshold.

If any check is `Inconclusive` (AR not yet delivered, user still slow), the worker retries with exponential backoff up to `MAX_INCONCLUSIVE_RETRIES = 3` times (total ~2 min window). After exhausting retries the fallthrough behaviour depends on whether `departureEventBus.lastVehicleEnteredAt` is set:
- **Non-null** (IN_VEHICLE_ENTER was recorded after parking, but speed stayed low throughout retries): `Confirmed`. Covers slow garage exit where the vehicle never exceeds the departure threshold.
- **Null** (no vehicle signal at all): `Result.success()` is returned without confirming — the user was likely walking near the car. [BUG-WALK-DEPART-001]

#### Step 4 — Session clear + spot release

On `Confirmed`:

1. `userParkingRepository.getActiveSessionByGeofence(geofenceId)` — resolves the exact session from Room.
2. `reportSpotReleased(lat, lon, spotId, spotType, confidence, sizeCategory)` — geocodes and enqueues `ReportSpotWorker` to publish the freed spot to Firestore.
3. `userParkingRepository.clearActiveParkingSession(session.id)` — removes the active session from Room and enqueues `ClearActiveParkingSessionWorker` for Firestore reconciliation.
4. `departureEventBus.reset()` — clears the in-memory `lastVehicleEnteredAt` state.
5. `geofenceService.removeGeofence(geofenceId)` — deregisters the GMS geofence so Play Services stops monitoring it.

Note: `reportSpotReleased` is called **before** `clearActive` — the WorkManager job is durably enqueued even if the worker is killed before the clear, and `REPLACE` policy on retries prevents duplicate publications.

#### DepartureEventBus lifecycle [BUG-WALK-DEPART-001]

`DepartureEventBus.lastVehicleEnteredAt` is reset in **two** places:
1. `ConfirmParkingUseCase` — immediately after a parking session is successfully saved. This erases the IN_VEHICLE_ENTER from the arrival trip so that departure detection cannot confuse "user just parked and walked away" with "user drove off". Without this reset, any geofence exit within the 30-minute `vehicleEnterWindowMs` would appear to be a valid departure.
2. `DepartureDetectionWorker` — after a confirmed departure is fully processed.

If the process is killed between parking confirmation and the geofence exit, the bus is null. `DetectParkingDepartureUseCase` returns `Inconclusive` (no vehicle signal). After `MAX_INCONCLUSIVE_RETRIES` without a vehicle signal, the worker silently returns `success` rather than confirming departure — a missed departure is preferable to falsely releasing the spot. The user can release the spot manually.

### 1.8 Diagnostic logging — `PARKDIAG`

Debug builds enable `FileAntilog` (`composeApp/src/androidMain/.../logging/FileAntilog.kt`). Every Napier log line tagged `PARKDIAG/*` is appended to `${context.filesDir}/parkdiag.log` (5 MB rotating). Tags used:

- `PARKDIAG/Service` — `CoordinatorDetectionService` lifecycle.
- `PARKDIAG/Coord` — `CoordinatorParkingDetector` state transitions.
- `PARKDIAG/Confirm` — `ConfirmParkingUseCase` steps.
- `PARKDIAG/Notify` — `NotifyParkingConfirmationUseCase`.
- `PARKDIAG/SyncScheduler`, `PARKDIAG/SaveNewParkingSessionWorker`, `PARKDIAG/ClearActiveParkingSessionWorker`, `PARKDIAG/UpdateParkingSessionAddressAndPlaceWorker` — WorkManager pipeline.

Pulling logs from the device:

```bash
adb shell run-as io.apptolast.paparcar cat files/parkdiag.log > <local-path>
adb shell run-as io.apptolast.paparcar cat files/parkdiag.log.old > <local-path-old>
```

See `diagnostics/README.md` at the repo root for the recommended layout when archiving captures.

---

## 2. Fix history

Each entry is one issue we shipped a fix for. Listed roughly in dependency order (mappers first, then pipeline, then algorithm). Every entry should explain *what was wrong*, *why it was wrong*, and *what guard exists today*. If you ever want to remove a guard, find its entry here first.

### COM-002 — Adaptive geofence radius by vehicle size + GPS accuracy

**Commit:** `c7b67ae`.

A fixed 80 m geofence was either too tight for vans (~5 m car position vs ~10 m van centerline + 30 m parking maneuver) or too loose for motos squeezed into tight gaps. Worse, it ignored GPS accuracy entirely: an 80 m geofence built around a fix with `accuracy=40 m` could trigger a fake exit while the car was still in the slot.

**Fix.** Base radius per `VehicleSize` (60/80/100/120 m for MOTO/default/LARGE/VAN), plus `accuracy * 1.5f` of dynamic padding, capped at 200 m. Captured as the `computeGeofenceRadius()` helper in `ConfirmParkingUseCase`.

### FIX-001 — `ConfirmParkingUseCase` propagates a typed error

**Commit:** `98a194a`.

The use case used to throw raw exceptions on save failure; the foreground service swallowed them silently and the spot quietly disappeared. Conversion to `Result<UserParking>` with `PaparcarError.Parking.SaveFailed` made the failure path observable and testable.

### FND-002 — Magic numbers extracted

**Commit:** `9324caa`.

The 80 m geofence radius, 15 m accuracy bonus threshold, 30 s initial stop window, 5 min slow-path window, etc. were inlined across half a dozen files. Moved every threshold into `ParkingDetectionConfig` so future tuning is one diff, not a treasure hunt. The config is Koin-injected — tests can override it.

### MAPPER-001 — `detectionReliability` not written to Room

**Commit:** `1a97dea`.

`UserParking.toEntity()` mapper omitted `detectionReliability = detectionReliability`. Every saved row had `detectionReliability = NULL` despite the use case computing a real value, which silently killed reliability-based analytics in the history screen.

**Fix.** Add the missing line; round-trip test added.

**Latent companion bug.** `toParkingHistoryDto()` had the same omission on the write path — it surfaced later under MAPPER-002 and was fixed there.

### MAPPER-002 — `vehicleId` lost in the Firestore round-trip

**Commit:** `2d7348d`.

Same omission class as MAPPER-001, but on the Firestore write/read path. `ParkingHistoryDto` had no `vehicleId` field, neither `toParkingHistoryDto()` nor `dto.toEntity()` mapped it, and the manual Firestore deserialization in `RemoteUserProfileDataSourceImpl` did not read it either. New sessions started life with `vehicleId` set in Room, but `GetOrCreateUserProfileUseCase.invoke()` runs `syncParkingHistoryFromRemote(userId)` at every splash bootstrap and re-inserts every Firestore row via `REPLACE` conflict — wiping the local `vehicleId`. Then `VehiclePageContent`'s per-vehicle history tab (introduced in HIST-001) showed empty under every tab.

**Fix.** Five surface points needed updating: `ParkingHistoryDto` field, the two mappers, the `SaveNewParkingSessionWorker` payload (`KEY_NEW_SESSION_VEHICLE_ID`), and `RemoteUserProfileDataSourceImpl.toParkingHistoryDto()`. Also fixed the latent `detectionReliability` write-path omission in `toParkingHistoryDto()`. No data backfill — pre-release state, user wiped Firestore manually.

### FND-009 — `runBlocking` removed from `NotifyParkingConfirmationUseCase`

**Commit:** `b05ef61`.

The notify use case was non-suspend and wrapped `vehicleRepository.observeDefaultVehicle().firstOrNull()` in `runBlocking` to read the vehicle name for the notification. PARKDIAG captures showed 1.2–1.4 s of Main-thread blocking inside an otherwise-async coordinator loop, well within ANR territory on cold Room or contended IO.

**Fix.** `suspend operator fun invoke(...)`. The ripple stayed inside the coordinator (`evaluateConfidence`) since it's already inside a coroutine.

### PIPE-001 — Firestore writes off the confirm-parking critical path

**Commit:** `2f4eef2` (merge), `371ce85` (work).

The original `confirmParking` did Room save + Firestore set + geofence registration + notification, all in a `withContext(NonCancellable)` block inside `CoordinatorParkingDetector.evaluateConfidence`. Firestore writes can hang for tens of seconds on bad networks; the foreground service can hang with them. PARKDIAG captures during the "blue notification stays forever" bug pointed to Firestore as the long pole.

**Fix.** Introduce `ParkingSyncScheduler` + `SaveNewParkingSessionWorker`. `confirmParking` now does Room write only and enqueues the Firestore reconciliation in WorkManager. The critical path is bounded by Room + Geofence + Notification, none of which can hang indefinitely. Full plan in `docs/refactors/PIPE-001-confirm-parking-pipeline.md`.

### PIPE-002 — `clearActiveParkingSession` and `updateParkingSessionAddressAndPlace` also use workers

**Commit:** `ec89592`.

Same hang-on-Firestore risk on departure and enrichment paths. `UserParkingRepositoryImpl.clearActiveParkingSession()` and `updateParkingSessionAddressAndPlace()` were calling the remote data source inside `runCatching` — fine for the user-departure case (already off the foreground service), worse for the enrichment worker (could be killed mid-Firestore-write, leaving Room and Firestore inconsistent).

**Fix.** Both methods are Room-only; `ClearActiveParkingSessionWorker` and `UpdateParkingSessionAddressAndPlaceWorker` handle Firestore. Also fixed a PIPE-001 follow-up: previously a partial DTO with `lat=0.0` could overwrite coordinates via `set()` — the workers now use `update()` for partial field changes.

### PIPE-003 — Sync worker tests

**Commit:** `daeeb2d`.

`doWork()` is exercised only by manual smoke tests — too easy to regress in a refactor. Added 9 Robolectric tests via `androidx.work:work-testing` covering all 3 workers: success path, retry on Firestore failure, permanent failure after max retries, missing input.

### LOC-001 — Freeze `bestStopLocation` after the initial-stop window

**Commit:** `e153d6e`.

User report: saved parking spot lands at the user's home, ~5 m from their front door, instead of the actual parking position a few hundred meters away. Reproducible on Redmi Note 11 and Samsung A53. PARKDIAG captures showed the coordinator was continuing to update `bestStopLocation` for the entire stop duration: any GPS fix with `speed < 1 m/s` and better accuracy than the running best would overwrite. Walking speed is ~1.4 m/s, but periodic fixes during the walk dropped below 1 m/s (waiting for traffic, picking up the phone, etc.) — and once the user sat down at their destination, indoor GPS regularly gave decent accuracy. The walking-destination fix would beat the parked-car fix and become the saved spot.

**Fix.** Gate `bestStopLocation` updates by `withinInitialWindow = (now - stoppedSince) < initialStopWindowMs (30 s)`. After 30 s of being stopped, the spot is locked. The companion `stoppedFixes` list was already gated; `bestStopLocation` had drifted out of sync with that contract.

### LOC-002 — Trust driving signal only on good-accuracy fixes

**Commit:** `9d43f02`.

User report follow-up to LOC-001: even with the 30 s freeze, a Redmi Note 11 parking landed ~100–150 m off (at the user's house, walking distance from the parking spot). PARKDIAG showed the bug: at session age 21:35:03, a single fix with `speed=2.94 m/s` and `accuracy=85 m` triggered the "vehicle is driving away" branch (`location.speed >= clearBestStopSpeedMps (2.5)`) and wiped `bestStopLocation`, `vehicleExitConfirmed`, and `highConfidenceReachedAt` mid-CANDIDATE. The user was actually stationary on foot at that moment; the GPS hallucinated the speed. The next stop window opened wherever the user sat down (home) and re-captured `bestStopLocation` there.

**Fix.** Combine the speed threshold with an accuracy threshold:

```kotlin
val isDriving = location.speed >= config.clearBestStopSpeedMps &&
                location.accuracy <= config.minGpsAccuracyForDriving  // 50 m
```

50 m is generous enough that normal urban GPS (10–30 m) still counts as a trusted driving signal, strict enough that the kind of hardware hallucinations seen on Redmi Note 11 (85 m, 190 m fixes) don't survive the gate. Logged when filtered so future captures show the gate firing.

LOC-001 protects against walking destinations overwriting `bestStopLocation`; LOC-002 protects against noisy fixes wiping the entire CANDIDATE state. Both guards exist for different failure modes and should not be conflated.

### PARKING-001 — Reposition-burst detection for "wait + maneuver" scenario

**Commit:** pending (Option A initial; accuracy-gate split 2026-05-31; B and C deferred).

User report (`diagnostics/2026-05-14/redmi-note-11.log`, drive of 2026-05-13): when the user stops 10–15 m short of the actual parking spot, waits for another car to leave, then maneuvers into the freed spot, the app saves the *waiting* position as the final parking location instead of the actual plaza.

**Root cause.** The maneuver to the real plaza is short (~10 m) and slow (peak ~1.5–2 m/s), so it never crosses `clearBestStopSpeedMps = 2.5 m/s` with `accuracy ≤ 50 m`. LOC-002's single-fix gate correctly preserves `bestStopLocation` against noisy spikes, but as a side effect also preserves the stale waiting-position bestStopLocation through the maneuver. Then LOC-001 freezes the new initial-stop window without ever overwriting the stale value (since its accuracy was already very good — the user was idle there long enough for GPS to converge).

**Fix (Option A).** Introduce a *consecutive* reposition signal between sustained walking pace (~1.4 m/s) and `clearBestStopSpeedMps`. Config:

```kotlin
val repositionSpeedMps: Float = 1.7f               // single-fix speed threshold
val repositionFixCount: Int = 3                    // consecutive fixes needed
val repositionMaxAccuracyMeters: Float = 15f       // stricter accuracy gate vs isDriving (50 m)
```

In `updateStopTracking()` moving branch:

```kotlin
val isRepositionCandidate = location.speed >= config.repositionSpeedMps &&
        location.accuracy <= config.repositionMaxAccuracyMeters    // 15 m, not 50 m
val newConsecutive = if (isRepositionCandidate) state.consecutiveRepositionFixes + 1 else 0
val isRepositionBurst = newConsecutive >= config.repositionFixCount
val shouldClearBestStop = isDriving || isRepositionBurst
```

`consecutiveRepositionFixes` is reset to 0 on any stopped fix and on any moving fix that drops below the reposition threshold (sustained walking at ~1.2 m/s).

**Why the differentiation works.**
- **Walking** sustains ~1.2 m/s and never crosses 1.7 m/s reliably — counter stays at 0.
- **GPS noise storm** (Redmi Note 11, acc=22–48 m): fails the `repositionMaxAccuracyMeters=15 m` gate — counter never increments. **Field-confirmed** from `diagnostics/2026-05-30/redmi.log` 19:23 session: 5 consecutive bursts at acc=22–48 m were clearing `bestStopLocation` while the user was parked; none would pass the new 15 m gate.
- **Single GPS spike** at >1.7 m/s with acc ≤ 15 m — increments counter to 1, next fix returns to stopped or below reposition threshold → counter resets, bestStopLocation preserved.
- **Vehicle maneuver** crosses 1.7 m/s with acc ≤ 15 m for ≥3 consecutive fixes (≥10 s at HIGH_ACCURACY cadence) — counter reaches 3, bestStopLocation cleared, the next stop window captures the real plaza.

**Why the accuracy gate is split from `isDriving`.** The `isDriving` path (speed ≥ 2.5 m/s) intentionally uses `minGpsAccuracyForDriving=50 m` because Redmi Note 11 hardware reports acc=50–200 m during genuine fast driving; without a 50 m gate, those fixes would not trigger CANDIDATE phase clearing and the user would see a stale location. At slow-maneuver speeds (1.7 m/s), noise at 22–48 m is commonplace even while stationary; a 15 m gate filters it while real slow motion (outdoor maneuvering) produces acc < 10 m.

**Accepted trade-off.** Jogging with the phone (>1.7 m/s, acc ≤ 15 m, sustained ≥3 fixes) after parking but before HIGH is reached would clear `bestStopLocation`. This is a niche scenario; deferred until evidence warrants a separate guard.

**Companion options considered (Section 3, deferred).** Option B (1 s GPS sampling boost during CANDIDATE) and Option C (lowering `clearBestStopSpeedMps` to 2.0) were both proposed. Option A is the cheapest and most surgical; ship it first and fold in B/C only if a captured failure shows A is insufficient.

### ADD-PARKING-PIN — manual park becomes a positionable pin (2026-05-19)

**Before.** The "Aparcar manualmente" CTA on the parking empty-state card emitted `HomeIntent.ManualPark` → `manualPark()` → `confirmParking(userGpsPoint, 1.0f, MANUAL_REPORT)`. Snap-to-GPS, no chance to correct if the user was already walking away from the car.

**After.** The CTA now emits `HomeIntent.EnterAddParkingMode(initialGps = userGpsPoint)`, which opens `HomeMode.AddingParking` — same dim + centre-pin + peek molde as Reporting / AddingZone, with the new `ParkingCenterPin` (white teardrop + inner disc + car glyph). The user drags the map to position the pin and taps "Aparcar aquí" to confirm. Confirm path runs `confirmParking(pinGps, 1.0f, MANUAL_REPORT)` (same use case as before — only the pin location differs).

**Plus** — a new "Mover ubicación" action on the active-parking peek opens the same mode with `editingParkingId = parking.id` and `initialGps = parking.location`. Confirm in edit mode dispatches to `UpdateParkingLocationUseCase` instead of `ConfirmParkingUseCase`. The use case mirrors confirm-parking's side-effects on an existing row: cancel old geofence → repository `updateLocation` (lat/lon + clears address/POI for re-geocoding) → schedule Firestore sync (existing `ParkingSyncScheduler.schedule`) → schedule enrichment → recreate geofence at new location (same id). No notification — the user took the action explicitly.

**Retired.** `HomeIntent.ManualPark` + `manualPark()` are gone (the empty-state CTA is the only emitter and it now uses `EnterAddParkingMode`). Test coverage migrated: `should_emit_ShowError_on_ManualPark_when_no_GPS` → `should_emit_ShowError_on_ConfirmAddParking_when_no_GPS`, ditto for the offline variant. Same `ProviderDisabled` / `OfflineActionBlocked` guards live in `confirmAddParking()`.

**ADD-ZONE-PIN restyle** shipped alongside as a pure visual change — `ZoneCenterPin` now reuses the same `TeardropPinScaffold` as Report / Parking pins (white teardrop + inner disc + chosen zone icon overlay) so all three add-modes read as one family with only the inner silhouette varying.

### BUG-DETECT-ENTER-DEBOUNCE-001 — Duplicate `IN_VEHICLE_ENTER` from Activity Recognition was cancelling in-flight detection (2026-05-28)

**Commit:** to be filled after merge.

**Symptom.** Field test on 2026-05-27 with two phones (Oppo CPH2371 + Redmi Note 11) on the same trip: 3 of 6 parking events failed to auto-confirm. The pattern in `diagnostics/2026-05-27/{oppo,redmi-note-11}.log` was always the same — multiple `→ VEHICLE_TRANSITION IN_VEHICLE ENTER` events arriving within seconds of each other, each followed by `✗ detection cancelled: StandaloneCoroutine was cancelled` and a fresh `▶ detection coroutine entered`. The coordinator never reached its CANDIDATE phase before the trip ended, so no Notify and no Confirm fired even though the EXIT eventually arrived correctly. Real-world driving (yields, traffic lights, brief idle) is enough for Play Services Activity Recognition to fire IN_VEHICLE ENTER bursts; the service treated each burst as a new trip and reset state.

**Root cause.** `CoordinatorDetectionService.handleVehicleTransition()` guarded the restart with:

```kotlin
if (detectionJob?.isActive != true || !parkingDetectionCoordinator.hasDetectedMovement) {
    detectionJob?.cancel(); startParkingDetection()
} else {
    /* skip */
}
```

The `OR` meant "restart if (job inactive) **OR** (no movement detected yet)". In the first seconds after ENTER, `hasDetectedMovement = false` because the coordinator needs several GPS fixes that pass `minimumTripSpeedMps` + `minimumTripDistanceMeters` before flipping the flag. Any duplicate ENTER arriving in that window restarted the job even though it was actively running. The `↻ Coordinator already active + hasDetectedMovement=true` log (the else branch) never appeared in any field log — the guard never engaged.

**Fix.** Move the debounce upstream into the service itself with a binary state:

```kotlin
private enum class VehicleState { OUT, IN }
private var currentVehicleState: VehicleState = VehicleState.OUT

// IN_VEHICLE_ENTER branch — first thing inside the `when`:
if (currentVehicleState == VehicleState.IN) {
    PaparcarLogger.d(DIAG, "  ↻ IN_VEHICLE_ENTER ignored — already IN (AR noise debounce)")
    return@forEach
}
currentVehicleState = VehicleState.IN
// … strategy resolution + startParkingDetection as before

// IN_VEHICLE_EXIT branch:
currentVehicleState = VehicleState.OUT
parkingDetectionCoordinator.onVehicleExit()
```

The `hasDetectedMovement`-based guard inside the COORDINATOR strategy branch was removed — the upstream state machine guarantees we only reach that code on a real OUT→IN transition. Spurious `IN_VEHICLE_ENTER` events that don't lead to actual movement are still caught by `maxNoMovementMs` inside the coordinator (line 239), which kills phantom sessions from inside.

**Why a binary state and not a time-based debounce.** A time-based "ignore ENTERs within N seconds" approach loses the distinction between (a) AR noise within an active trip and (b) legitimate re-entry after an out-of-vehicle gap (e.g., trip 5 in the field log: user stopped at an ATM for 2 min, walked out, came back, drove on). The binary state handles both correctly: re-entry only fires `ENTER` after the previous `EXIT` has set state back to `OUT`.

**Field validation.** New log line `↻ IN_VEHICLE_ENTER ignored — already IN (AR noise debounce)` makes the debounce visible in `parkdiag.log` — the next field test confirms whether the duplicate-ENTER bursts are now absorbed. No unit test was added; Robolectric-wrapping the foreground service to exercise this 4-line state machine has a poor cost/benefit when the log line is unambiguous.

**Files touched.**
- `composeApp/src/androidMain/.../detection/service/CoordinatorDetectionService.kt` — state field, enum, debounce check in ENTER branch, OUT reset in EXIT branch, removal of stale `hasDetectedMovement` guard in COORDINATOR.

`hasDetectedMovement` itself is still used by the `ACTION_START_TRACKING` path and by the coordinator's internal `maxNoMovementMs` guard, so it stays on `CoordinatorParkingDetector`.

### Field validation: `minStepsToConfirm=8` correctly rejects in-car social/idle stops (2026-05-28)

**Context.** During the 2026-05-27 field test, trip 5 on the Oppo had a long stationary period (22:00-22:08) where the coordinator entered CANDIDATE with `score=High(0.8)` (5 minutes stopped + speed=0 + excellent GPS accuracy + `vehicleExit=false`). The trip then resumed and only ended at 22:19 when the user actually parked at home.

**What the algorithm did.** During the 22:00-22:08 stop, `stepCount` rose to 5 (spurious accelerometer events from people moving inside the parked car) and froze there for the remaining ~3 minutes. The CANDIDATE-phase log line `⏳ CANDIDATE phase — elapsed=Nms window=300000ms steps=5/8` repeated identically until the car resumed motion, at which point `clearBestStopSpeedMps` cleared the candidate cleanly. **No `confirmParking` fired.** At 22:19 the real `IN_VEHICLE EXIT` arrived, the user walked to the door (90 steps in 60 s), and confirmation completed in **4 s** (22:20:24 HIGH → 22:20:28 SUCCESS via `hasStepsProof`).

**User confirmed scenario.** The 22:00-22:08 stop was a chat with a friend from the car — nobody got out. So the "5 steps" were noise, the threshold of 8 was the **only** thing standing between a phantom parking record and a correct rejection.

**Conclusion.** Keep `minStepsToConfirm = 8`. Field evidence shows:

1. The threshold blocks the most common false-positive class (long social/traffic stops in the car) without help from AR EXIT.
2. Real parkings still confirm within seconds because 8 steps takes ~6 s of normal walking.
3. The dual-path `confirmNow = hasStepsProof || (windowElapsed && highCandidateHadVehicleExit)` in `CoordinatorParkingDetector` is the right shape — step proof gates fast confirms, AR EXIT + 2-min window remains the slower fallback.

Resolved without code changes. Ticket `BUG-DETECT-EXIT-LAG-VS-STEPS-001` closed in `docs/backlog/parking-detection-real-world-2026-05-28.md`.

### HEARTBEAT-001 — DetectionHeartbeatWorker was restarting coordinator during active trips (2026-05-30)

**Commit:** `3a9701b`.

`DetectionHeartbeatWorker` fired every 15 minutes (WorkManager periodic job) and called `startForegroundService(ACTION_START_TRACKING)` unconditionally. This restarted the coordinator even when a detection session was actively running, creating a continuous stream of service restart events every 15 minutes throughout any drive.

**Root cause.** The worker was conceived as a "make sure the service is alive" watchdog, without differentiating between "user is mid-drive (coordinator running)" and "user is parked (coordinator stopped, session in Room)". Both cases received the same restart signal.

**Fix.** `doWork()` reads `db.parkingSessionDao().getAllActive()` from Room:
- `activeSessions.isEmpty()` → skip restart (user is mid-drive or idle; IN_VEHICLE_ENTER via PendingIntent.getForegroundService() handles restarts).
- `activeSessions.isNotEmpty()` → also skip (user is parked; departure detection runs independently via geofence + AR).

The worker stays enrolled (WorkManager KEEP policy) so OEM Doze restrictions cannot silently cancel the periodic job, but its body is now a no-op in both states. The rationale: if the process was killed mid-drive, START_STICKY + IN_VEHICLE_ENTER via Play Services PendingIntent.getForegroundService() are sufficient to restart detection without this worker.

**Field evidence.** Logs from `diagnostics/2026-05-30` showed the 15-minute heartbeat firing while the coordinator was active (PARKDIAG timestamps align with :00/:15/:30/:45 min boundaries), each time triggering the DETECT-SERVICE-RACE-001 race (see below).

### DETECT-SERVICE-RACE-001 — `finally { stopSelf() }` in superseded detection job killed the replacement coordinator (2026-05-30)

**Commit:** pending (fix applied 2026-05-31).

**Symptom.** Field test 2026-05-30: 5-stop trip (Decathlon → Hospital Puerto Real → Jerez → Puerto1 → Puerto2). Redmi detected 3/5, Oppo 2/5. PARKDIAG shows the race pattern at Oppo 19:31:08, 19:35:53 and Redmi 18:33:32, 18:38:47 — each an instance of a missed detection.

**Root cause.** `CoordinatorDetectionService.startParkingDetection()` launched the coordinator in a `lifecycleScope.launch { }` block with:

```kotlin
detectionJob = lifecycleScope.launch {
    try {
        parkingDetectionCoordinator(observeAdaptiveLocation())
    } catch (e: CancellationException) {
        throw e
    } finally {
        stopSelf()    // ← fired unconditionally
    }
}
```

Sequence when a new IN_VEHICLE_ENTER (or heartbeat START_TRACKING) arrived while the coordinator was running:

1. Old coordinator job is running.
2. New intent: `detectionJob?.cancel()` cancels the old job; `detectionJob = null`; `startParkingDetection()` launches new job.
3. Old job's `finally` fires → `stopSelf()` — this targets the **service** (not the job), calling `onDestroy()`.
4. `onDestroy()` cancels `detectionJob` (the **new** job).
5. Service dies. No detection until the next AR or heartbeat event restarts it.

The race was **amplified by HEARTBEAT-001** (pre-fix): the heartbeat created a restart collision every 15 minutes throughout a drive.

**Fix.** Capture the job reference from inside the coroutine and guard the `stopSelf()` call:

```kotlin
detectionJob = lifecycleScope.launch {
    val thisJob = coroutineContext[Job]
    try {
        parkingDetectionCoordinator(observeAdaptiveLocation())
    } catch (e: CancellationException) {
        throw e
    } finally {
        // Only stop the service if this job was not superseded by a newer one.
        // If detectionJob !== thisJob, a newer session has taken ownership and
        // calling stopSelf() here would destroy it. [DETECT-SERVICE-RACE-001]
        if (detectionJob === thisJob) {
            stopSelf()
        }
        // else: superseded — skip stopSelf, newer job manages lifecycle.
    }
}
```

After the replacement, `detectionJob` is either `null` (briefly, between cancel and re-assign) or the new job. In either case `detectionJob !== thisJob`, so superseded jobs skip `stopSelf()` and the new coordinator runs uninterrupted.

**Field evidence.** Classic log pattern confirmed at ≥4 timestamps across both devices:
```
■ finally → calling stopSelf()           ← old job
▶ coordinator.invoke() entry             ← new job starts
■ Service onDestroy — cancelling job     ← stopSelf kills new job
```
After fix, expect only `■ finally → superseded by newer job, skipping stopSelf()` in the log for any superseded job.

### BT-REFACTOR-FGS-001 — BluetoothConnectionReceiver → ForegroundService pattern (2026-06-02)

**Commit:** to be filled after merge.

**Problem A — orphan scopes in the Receiver.** `BluetoothConnectionReceiver` held a
`CoroutineScope(SupervisorJob() + Dispatchers.IO)` that launched the long detection job and was
never cancelled. Android instantiates the Receiver fresh for every ACL event. Each invocation
created a new scope, accumulating orphan scopes across BT events throughout the day.
The `single(named("btDetectorScope"))` Koin fix (§13 `BUGS_AND_DEBT.md`) moved the scope to
app-global lifetime but did not give it a lifecycle owner.

**Problem B — process killed during 5-minute detection window.** The BT detection flow
(30 s debounce + 60 s GPS + distance watch) ran in an unprotected background process. Android
can kill background processes in that window. A kill silently discarded the in-flight session
— no confirmation, no spot published.

**Fix.** Three-part change:
1. `BluetoothConnectionReceiver` reduced to minimum work: vehicle lookup (ms) + fire Service intent.
   The Receiver's scope terminates immediately after the `startForegroundService()` call.
2. New `BluetoothDetectionService` (`LifecycleService`, `START_NOT_STICKY`,
   `foregroundServiceType="location"`) owns `lifecycleScope`. Launches `detector.detectParking()`
   and calls `stopSelf()` when it returns or throws.
3. `BluetoothParkingDetector` made stateless: `scope` constructor param and `detectionJob` removed.
   `onCarDisconnected()` → `suspend fun detectParking()`. Abort-on-reconnect now handled via
   cooperative cancellation: the Service cancels `detectionJob` on `ACTION_BT_CONNECTED`; `delay()`
   and `Flow.first()` inside `detectParking()` are cancellation points.

**Why not `CoordinatorDetectionService`?** The Coordinator Service uses `START_STICKY` because Play
Services can re-deliver `IN_VEHICLE_ENTER` to restart detection. BT detection cannot resume
after a kill (in-memory `parkingFix` coordinates are lost), so `START_NOT_STICKY` is the correct
contract. Merging two independent trigger sources (AR vs BT ACL) into one Service would also
break the clean `VehicleState` machine that guards `BUG-DETECT-ENTER-DEBOUNCE-001`.

**Files:** `BluetoothConnectionReceiver`, `BluetoothParkingDetector`, new `BluetoothDetectionService`,
`AndroidDetectionModule` (btDetectorScope removed), `AndroidManifest.xml` (new service entry),
`AppNotificationManager` (BT_DETECTION_NOTIFICATION_ID = 1003).

Full design rationale: `docs/refactors/BT-REFACTOR-FGS-001-bluetooth-detection-foreground-service.md`.

---

## 3. Open questions / future work

- **GPS sampling boost during CANDIDATE (PARKING-001 Option B).** Switch the LocationDataSource to a 1 s `minUpdateIntervalMillis` request when entering the CANDIDATE phase, returning to 2 s on exit. Increases density of fixes that refine `bestStopLocation` within the new initial-stop window after a reposition burst. Adds the complexity of swapping the location source mid-session — hold off until A is validated in the field.
- **Lower `clearBestStopSpeedMps` to ~2.0 (PARKING-001 Option C).** Single-fix tightening of the existing LOC-002 gate. Same effect as the reposition burst for fast maneuvers, but reintroduces the noise-spike risk that LOC-002 mitigated. Bundle with Option B if needed.
- **Per-device noise floor.** Redmi Note 11 routinely emits acc > 50 m even outdoors; OPPO CPH2371 rarely does. If the user base widens, consider a remote-config table of per-device `minGpsAccuracyForDriving` values, or compute a rolling-median accuracy and gate against a multiple of it.
- **AUTH-002 — parking lost when `getCurrentSession()` returns null.** Observed in the same Redmi log at `05-13 19:42:20`: the CANDIDATE window expired and `ConfirmParkingUseCase` aborted because the auth cache was empty. The parking was never written to Room either, so it is fully lost. Distinct from AUTH-001 (which was the `observeAuthState()` race in `observeDefaultVehicle`). Pending: design a fallback path that either persists userId on first successful login and reads from local cache, or defers the confirm via a Worker that retries on auth failure.
- **iOS port.** The coordinator is in `commonMain` and platform-agnostic; only the GPS / Activity / Geofence platform wrappers need iOS implementations. The PARKDIAG infrastructure is androidMain-only — when iOS arrives, decide whether to mirror `FileAntilog` or rely on OSLog.


### BUG-FGS-001 / BUG-FGS-002 — Activity Recognition → FGS delivery via PendingIntent.getForegroundService()

**Commit:** to be filled after merge.

On Android 12+ (API 31+), calling `startForegroundService()` from a BroadcastReceiver triggered by Activity Recognition throws `ForegroundServiceStartNotAllowedException` — Google Play Services AR broadcasts are not on the system's FGS exemption list. On Android 14+, the same call site also throws `SecurityException` if `ACCESS_FINE_LOCATION` is not granted at call time.

**24 crashes (9 users, BUG-FGS-001) + 5 crashes (1 user, BUG-FGS-002)** in Crashlytics before this fix.

**Fix.** Remove `ActivityTransitionReceiver` as a foreground-service launcher entirely. `ActivityRecognitionManagerImpl` now registers two separate subscriptions:

- `STILL_ENTER` → `PendingIntent.getBroadcast()` → `ActivityTransitionReceiver` (no FGS needed — fires `coordinator.onStillDetected()`).
- `IN_VEHICLE_ENTER` + `IN_VEHICLE_EXIT` → `PendingIntent.getForegroundService()` → `CoordinatorDetectionService` (Play Services delivers with system privileges, bypassing the restriction).

`CoordinatorDetectionService.onStartCommand(ACTION_VEHICLE_TRANSITION)` extracts the `ActivityTransitionResult` from the intent, guards permissions, and routes:
- **IN_VEHICLE_ENTER** → `departureEventBus.onVehicleEntered(epochMs)` + `strategyResolver.resolve()` → start coordinator (`COORDINATOR`), `stopSelf()` (`BLUETOOTH` is owner), or `stopSelf()` (`NONE` — scooter/bike opts out).
- **IN_VEHICLE_EXIT** → `coordinator.onVehicleExit()` + `stopSelf()` if no active detection job.

`startForeground()` is always called first (before routing) to satisfy the Android 8+ 5-second contract. `StartDetectionWorker` (the WorkManager bridge that was the provisional fix) was deleted.

**Guard today.** `hasRequiredPermissions()` runs immediately after `startForeground()` in `ACTION_VEHICLE_TRANSITION`. If permissions were revoked between the transition firing and delivery, the service calls `notificationPort.showPermissionRevoked()` + `stopSelf()` + returns `START_NOT_STICKY`.

### BUG-GEOFENCE-001 — FLAG_MUTABLE required for geofence PendingIntent

**Commit:** to be filled after merge.

`GeofenceManagerImpl.buildPendingIntent()` was using `PendingIntent.FLAG_IMMUTABLE`. On Android 12+ (API 31+), `FLAG_IMMUTABLE` prevents Google Play Services from filling `GeofencingEvent` extras into the intent at delivery time. `GeofencingEvent.fromIntent(intent).triggeringGeofences` arrived as `null` in `GeofenceBroadcastReceiver.onReceive()`, causing the receiver to return at line 48 without enqueuing `DepartureDetectionWorker`. Departure detection silently did nothing.

**Fix.** Changed to `PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT`. `FLAG_MUTABLE` is required here for the same reason it is required for Activity Recognition: Play Services must write into the intent at delivery time. This is the documented requirement from the GMS Geofencing API.

**Companion fix.** `DepartureDetectionWorker` now forwards session metadata to `reportSpotReleased()` — `spotType`, `detectionReliability` (as `confidence`), and `sizeCategory` — instead of defaulting all three. Session is resolved via `userParkingRepository.getActiveSessionByGeofence(geofenceId)` which is already called to get `lat`/`lon`; fields were already present, just not forwarded.

### BUG-3 — False-positive Low/Medium notification during traffic stops

**Observed:** 2026-05-27 test drive. At 12:11:22, user stopped 93 s at a traffic light (acc=1.8 m) → `ParkingConfidence.Low` scored → notification fired. User continued driving 30+ more minutes before actually parking.

**Root cause.** `CoordinatorParkingDetector.evaluateConfidence()` showed the Low/Medium notification whenever `!state.mediumNotificationShown`, with no check for an activity-exit or STILL signal. A traffic stop long enough to pass the `slowPathGateMs` gate (90 s) was sufficient to trigger the notification even when the user was still in a moving vehicle.

**Fix.** Gate the Low/Medium notification on `vehicleExitConfirmed || activityStillDetected`. Without either activity-transition signal, brief stops are treated as traffic/errand stops and no notification is shown. The High-confidence CANDIDATE phase is unaffected — it always notifies.

### BUG-DETECT-310502 — Low/Medium notification suppressed indefinitely without STILL/exit (2026-05-31, Redmi)

**Observed:** 2026-05-31 field test. Redmi Note 11: coordinator scored Low from 19:12:25 but the notification was suppressed for 4+ minutes waiting for `vehicleExit` or `activityStill`. The `vehicleExit` signal arrived at 19:13:50 and the notification appeared at 19:14:10 — but by 19:14:42 the vehicle was already moving at 1.5 m/s and the session was cancelled without confirmation.

**Root cause.** BUG-3's fix (gate on exit/still) is correct for filtering traffic stops, but has no timeout. On hardware where Activity Recognition delivers the `IN_VEHICLE→EXIT` transition late relative to the physical stop, the notification can arrive after the user has already re-entered the vehicle, making confirmation impossible.

**Fix.** Track `lowFirstReachedAt` (epoch-ms when Low/Medium was first reached in the current stop) in `ParkingDetectionState`. After `lowNotifTimeoutMs = 90 s` elapses without an exit or STILL signal, the notification fires anyway. The traffic-stop guard (BUG-3) remains active for the first 90 s; the timeout is only a safety net for sluggish AR delivery. `lowFirstReachedAt` resets whenever the vehicle moves (same lifecycle as `mediumNotificationShown`).

### BUG-DETECT-310503 — Fast-path auto-confirms without STILL at hospital entrance (2026-05-31, Oppo)

**Observed:** 2026-05-31 field test. Oppo CPH2371: coordinator auto-confirmed at 19:14:05 with `activityExit=true, activityStill=false, speed=0.10 m/s, stopped=30 s, acc=3.1 m`. The user had stopped briefly at a hospital entrance — the activity-exit AR transition arrived, GPS was excellent, and 8 pedestrian steps within 4 s confirmed via `hasStepsProof`. Sequence: HIGH(0.75) at 19:14:01 → CANDIDATE → steps ≥ 8 → `confirmParking` at 19:14:05.

**Root cause.** The fast path reached `High` with only `activityExit + speed + accuracy` — `activityStill` was not required. With GPS accuracy < 15 m and speed < 0.3 m/s, the score was 0.50 + 0.15 + 0.10 = 0.75 exactly, opening the CANDIDATE phase. The step detector then fired immediately as the user walked into the hospital, completing confirmation before the STILL signal could arrive or the user could dismiss.

**Fix.** Gate `fastPathAccuracyBonus` on `activityStill` in `CalculateParkingConfidenceUseCase`:

```kotlin
// Before:
if (signals.gpsAccuracy < config.minGpsAccuracyMeters) score += config.fastPathAccuracyBonus
// After:
if (signals.activityStill && signals.gpsAccuracy < config.minGpsAccuracyMeters) score += config.fastPathAccuracyBonus
```

Without STILL, the fast-path maximum is now 0.65 (Medium). A Medium score does NOT open the CANDIDATE phase, so step-based auto-confirmation cannot trigger. The user sees the Medium notification and must confirm manually. With STILL, the score reaches 0.75 (High) and auto-confirmation proceeds as before — STILL is a strong signal that the user is no longer inside a moving vehicle.

**Trade-off.** Real parking sessions where Activity Recognition delivers STILL late (common on some devices) will now show a Medium prompt instead of auto-confirming. The user taps once. Preferred over spurious parking records at hospital entrances or other brief stops with good GPS.

### REFACTOR-DETECT-001 — Clean-up of service / coordinator / receiver flow

**Commit:** to be filled after merge.

Mechanical clean-up of the three classes that own the detection runtime: `CoordinatorParkingDetector`, `CoordinatorDetectionService`, `ActivityTransitionReceiver`. No threshold or scoring changes; behaviour-preserving except for the `collectLatest → collect` swap noted below.

- **M1 — `collectLatest` → `collect` in coordinator.** The inner per-location block has no suspending I/O that should be cancelled when a newer fix arrives, so `collectLatest` was adding cancellation hazards (notifications could be cancelled mid-flight) without any benefit. With `collect`, each fix runs to completion before the next is processed, and the `withContext(NonCancellable) { notifyParkingConfirmation(...) }` workarounds added earlier became dead weight and were removed.
- **M2 — atomic state snapshot.** `_detectionState.update { ... }` followed by `val state = _detectionState.value` is racy: between the two lines another collector could mutate the state. Replaced with `val state = _detectionState.updateAndGet { ... }`, which returns the post-update snapshot atomically.
- **M3 — shared label helpers.** `activityLabel(Int)` and `transitionLabel(Int)` were duplicated inline in `CoordinatorDetectionService` and `ActivityTransitionReceiver`. Extracted to `composeApp/src/androidMain/.../detection/ActivityRecognitionLabels.kt` (internal helpers).
- **M4 — co-locate PendingIntent request codes.** `REQUEST_CODE = 101` lived in `ActivityTransitionReceiver` and was referenced by `ActivityRecognitionManagerImpl` — non-obvious coupling. Moved to `ActivityRecognitionManagerImpl.companion` as `STILL_REQUEST_CODE` alongside `VEHICLE_REQUEST_CODE`, with a comment explaining why both codes must remain distinct (`FLAG_UPDATE_CURRENT` would otherwise collide).
- **C2 — `guardPermissions(actionLabel)` helper in the service.** The same three-line "check permissions → showPermissionRevoked → stopSelf → return false" appeared inline in START_TRACKING, ACTION_VEHICLE_TRANSITION, and IN_VEHICLE_ENTER paths. Consolidated into a single method; call sites now read `if (!guardPermissions("LABEL")) return …`.

**Deferred.** Two larger questions surfaced during this refactor and are tracked in `docs/backlog/detection-improvements-2026-05-27.md`:
- *When does it make sense to kill the service?* — needs telemetry data before deciding (DECISION-SERVICE-LIFECYCLE-001).
- *Should BluetoothDetectionStrategy be folded into the Coordinator?* — architectural change; debate pending (DECISION-MERGE-BT-COORDINATOR-002).

### BUG-GARAGE-COLA-001 — Step Detector as canonical "user exited the car" signal

**Commit:** to be filled after merge.

**Symptom.** Long stops inside the car (queue at a garage entrance, traffic jam ≥ 5 min, drive-through line) were being auto-confirmed by the slow path. Pre-fix, once stopped duration ≥ 5 min, the Coordinator scored `High` and after the 5-minute observation window expired it confirmed with `reliabilitySlowPath`. The user was still in the car.

**Why "walking ≥ 30 m" was not the answer.** The Bluetooth strategy uses a 30 m walk as proof the user left the car, which works for outdoor street parking but fails in garages — the user typically walks ~4 m from the parking slot to a door, then takes an elevator. Distance is too coarse and venue-dependent to be the canonical signal in the Coordinator.

**Fix.** Introduce `StepDetectorSource` (`Sensor.TYPE_STEP_DETECTOR` on Android, empty stub on iOS — `CMPedometer` port deferred) and wire it as a sibling coroutine inside `CoordinatorParkingDetector.invoke()`. Steps that arrive while `stoppedSince != null` increment `stepCount`. When `stepCount ≥ minStepsToConfirm = 8` during the CANDIDATE phase, confirm immediately with `reliabilityVehicleExit = 0.90f` — pedestrian steps are unambiguous evidence the user has exited the car, stronger than the AR exit transition (which is noisy on real hardware).

**Behaviour change.** The slow path no longer auto-confirms purely on time. CANDIDATE expiry now requires **either** step proof **or** the vehicle-exit signal; otherwise the candidate is discarded as likely cola/atasco. This trades a small surface of "user parked and ignored the notification" cases (still recovered next session) for elimination of the long-stop false positives.

**Wiring.**
- `commonMain/.../domain/sensor/StepDetectorSource.kt` — domain interface.
- `androidMain/.../detection/sensor/AndroidStepDetectorSource.kt` — `Sensor.TYPE_STEP_DETECTOR` via `callbackFlow`; returns `emptyFlow()` if hardware missing. ACTIVITY_RECOGNITION permission covers it (already required for AR transitions).
- `iosMain/.../detection/IosStepDetectorSource.kt` — `emptyFlow()` stub. CMPedometer backing tracked in the same backlog file.
- Koin: `AndroidDetectionModule` + `IosDetectionModule` provide the platform impl; `DomainModule` injects into `CoordinatorParkingDetector`.
- `stepCount` reset to 0 whenever a driving signal arrives (`updateStopTracking` clears it alongside `stoppedSince`).

### BUG-SCOOTER-001 — VehicleType-aware detection + mismatch guard

**Commit:** to be filled after merge.

**Symptom.** Two failure modes for non-car users:
1. *User has a scooter/e-bike registered as default vehicle.* Activity Recognition fires `IN_VEHICLE_ENTER` (the API is noisy for two-wheeled microvehicles) → the Coordinator runs → after 5 min stopped at a destination the slow path auto-confirms a "parking" → the spot is published to the community. Scooters and e-bikes are dismounted on the sidewalk and never liberate a real parking slot, so every one of these confirmations is a false-positive published to the map.
2. *User has a car as default but rides their scooter to work today.* Same outcome — the active vehicle is `Ford Focus`, but the trip was actually on a Xiaomi Mi Pro. The app confirms a parking and saves it against the car.

**Fix — Level 1: vehicleType awareness.** `Vehicle` now carries `vehicleType: VehicleType ∈ { CAR, MOTORCYCLE, SCOOTER, BIKE }`. Persisted in Room (schema v4 via `MIGRATION_3_4`, column `vehicle_type` default `'CAR'`) and Firestore (`ifBlank → "CAR"` on read for backwards compatibility). UI exposes the choice via `VehicleTypeSelector` in vehicle registration/edit, mirroring the existing `VehicleSizeSelector` pattern.

`ParkingStrategyResolver.resolve()` short-circuits to `ParkingStrategy.NONE` when `vehicleType ∈ { SCOOTER, BIKE }` — the Coordinator never starts. `MOTORCYCLE` still resolves to BLUETOOTH/COORDINATOR (motorcycles do park). `CoordinatorDetectionService.handleVehicleTransition()` switches on the enum: COORDINATOR starts detection, BLUETOOTH and NONE both `stopSelf()`.

**Fix — Level 2: vehicle-mismatch guard (covers case 2).** The Coordinator now tracks per-session velocity profile:

```kotlin
data class ParkingDetectionState(
    val sessionStartMs: Long? = null,
    val maxSpeedMps: Float = 0f,
    // …
) {
    val maxSpeedKmh: Float get() = maxSpeedMps * 3.6f
    fun sessionDurationMs(now: Long): Long = sessionStartMs?.let { now - it } ?: 0L
}
```

`sessionStartMs` is set on the first fix of the session and `maxSpeedMps` is `max(location.speed, prev)` on every update. Before auto-confirming, the coordinator applies a mismatch heuristic:

```kotlin
val isMismatch = activeVehicleType == VehicleType.CAR &&
    state.sessionDurationMs(now) >= config.mismatchMinSessionDurationMs &&  // 8 min
    state.maxSpeedKmh <= config.mismatchMaxSpeedKmh                          // 28 km/h
// [DET-A] hasStepsProof now ANDs egress displacement:
//   hasStepsProof = stepCount >= minStepsToConfirm && hasEgressDisplacement(state, location)
val confirmNow = when {
    isMismatch -> false                                              // suppress auto-confirm
    !hasEgress -> false                                              // [DET-C-01] egress mandatory for ALL paths
    hasStepsProof -> true                                            // BUG-GARAGE-COLA-001 + DET-A
    windowElapsed && state.highCandidateHadVehicleExit -> true       // exit + dwell + egress
    else -> false
}
```

28 km/h sits between the EU moped speed cap (~25 km/h) and typical urban car cruise (~40–50 km/h). 8 min is long enough that a real car trip would have hit at least one stretch above 28 km/h. When both thresholds hold AND the active vehicle is a `CAR`, auto-confirm is suppressed but the user-facing notification from CANDIDATE entry remains — the user can still tap "Yes I parked" to confirm manually, which is the desired manual-override path for the corner case where a user is genuinely riding a friend's scooter while their `CAR` is the default.

**Trade-off accepted.** A real car trip in extreme bumper-to-bumper traffic that never exceeds 28 km/h for 8+ min triggers the same gate. The notification still fires, so the user can override — we prefer "ask the user" over "publish a wrong spot." Thresholds live in `ParkingDetectionConfig.mismatchMaxSpeedKmh` / `mismatchMinSessionDurationMs` for future tuning once telemetry is available.

**Tests.** `ParkingStrategyResolverTest` covers all enum branches: SCOOTER → NONE (even with BT config), BIKE → NONE, MOTORCYCLE without BT → COORDINATOR, CAR with BT → BLUETOOTH, no default vehicle → COORDINATOR. Multi-vehicle cases per ARCH-MONITORING-002: BT-paired secondary forces BLUETOOTH even if primary has no BT; scooter primary + BT-paired car still resolves to BLUETOOTH; BT-only single vehicle with `isActive=false` resolves to BLUETOOTH. Mismatch-guard unit tests deferred to a future integration ticket — they need `now` mocking + a CANDIDATE-phase fixture which the current test setup does not yet support.

### BUG-STUCK-SESSION — Confirmation notification re-fires at home, service runs for hours (2026-06-03)

**Observed:** User took the car for a short trip (~5 min), could not find an alternative spot, and returned to the same parking location. After walking home, the detection foreground notification remained visible for 1+ hour and the confirmation notification ("¿Acabas de aparcar?") fired again.

**Root cause (2 bugs).**

1. `mediumNotificationShown = false` was unconditionally written to state on every non-stopped GPS fix (speed > 1 m/s), including ordinary walking pace. After the user walked home and stopped for 90 s (`lowNotifTimeoutMs`), the flag had been cleared by an intermediate walking fix, so the notification re-fired at home. The fix: clear the flag only when `isDriving` (speed ≥ 2.5 m/s + accuracy ≤ 50 m), i.e. when the car actually drove away.

2. There was no upper bound on session duration once `hasEverMoved = true`. The `maxNoMovementMs` guard only applies before movement is detected. A session where the car drove out and back remained active forever, running the coordinator loop against the user's home GPS position indefinitely.

3. High-confidence notifications had no deduplication: each time `highConfidenceReachedAt` was reset (candidate phase expired) and high confidence was reached again at home, `notifyParkingConfirmation` fired again.

**Fix.**

- Replace `mediumNotificationShown: Boolean` with `confirmationNotificationShownAt: Long?`. Set to `now` on first notification (Low, Medium, or High). Cleared only on `isDriving`. A single flag covers all confidence levels.
- Add `confirmationResponseTimeoutMs = 15 min` to `ParkingDetectionConfig`. After the notification has been shown, if no user response arrives within this window, the coordinator aborts the session silently (dismisses notification + sets `completed = true`).
- High-confidence notification is gated on `confirmationNotificationShownAt == null`, same as Low/Medium.

### BUG-SHORT-TRIP — No parking detection on short trips within 150 m of original spot (2026-06-04)

**Observed:** User parks at spot A, drives out looking for a new spot, returns to within ~100 m of A (either same spot or very nearby). `hasEverMoved` (requires speed ≥ 18 km/h AND displacement ≥ 150 m simultaneously) never becomes `true` on a short radius trip. The session aborts via `maxNoMovementMs` after 4 min with no detection. The original parking session at A remains active even if the car is now at B.

**Root cause.** `hasEverMoved` served double duty: (1) "did the user really drive?" and (2) "gate confidence evaluation". The distance requirement (150 m) was added for duty 1 to filter GPS-noise speed spikes while stationary, but it inadvertently blocks duty 2 for genuine short trips.

**Fix.** Introduce `hasEverReachedDrivingSpeed: Boolean` — activated by speed alone (`speed >= minimumTripSpeedMps`), no distance requirement. All logic gates that previously used `hasEverMoved` now use `hasEverReachedDrivingSpeed`: the `maxNoMovementMs` abort guard, the vehicleId lock, the confidence-evaluator skip, and `hasDetectedMovement` exposed to the Service. `hasEverMoved` (speed + distance) is retained purely as a state data field. A genuine GPS-noise spike while stationary cannot sustain driving speed across multiple GPS fixes, so `hasEverReachedDrivingSpeed` alone is sufficient to confirm real driving intent.

### REFACTOR-300 — Unified notification + revertible auto-confirm (2026-06-08)

**Observed.** Two redundant notifications were posted for a single parking event:
1. `PARKING_CONFIRMATION_NOTIFICATION_ID` (2002, DETECTION channel) — "¿Has aparcado tu Toyota?" with Sí/No.
2. `UPLOAD_NOTIFICATION_ID` (1002, UPLOAD channel) — "Aparcamiento guardado" toast-style acknowledgment, posted by `ConfirmParkingUseCase.invoke()` after the save completed.

When the user manually tapped "Sí" on (1), the coordinator dismissed (1) and then (2) immediately appeared — two notifs back-to-back for one event. The redundancy was UX-bad and, more importantly, after **auto-confirm** (vehicle-exit window or step proof) (1) was dismissed silently and (2) appeared as a fait accompli — the user had no way to say "wait, that wasn't my car, I was a passenger".

**Fix — one notification, two states, same ID.**

| State | Title | Body | Actions |
|---|---|---|---|
| A · Pre-save (prompt) | "¿Has aparcado tu Toyota?" | confirmation_text | "Sí, he aparcado" → `ACTION_PARKING_CONFIRMED`<br>"No, no he aparcado" → `ACTION_PARKING_DENIED` |
| B · Post-save (savedConfirm) | "Toyota aparcado" (or "Vehículo aparcado") | "Toca para abrir el mapa, o cancela si no era tu vehículo." | "Sí, confirmar" → `ACTION_PARKING_ACK`<br>"No, cancelar" → `ACTION_PARKING_REVERT` + `EXTRA_PARKING_ID` |

Both states post on `PARKING_CONFIRMATION_NOTIFICATION_ID` (DETECTION channel, IMPORTANCE_LOW so the morph doesn't buzz). State B replaces state A by re-posting on the same id.

**Implementation.**
- `AppNotificationManager.showParkingSavedConfirm(parkingId, vehicleName, lat, lon)` — new method, Android impl in `AppNotificationManagerImpl`. The `parkingId` is baked into the REVERT PendingIntent as an extra.
- `ConfirmParkingUseCase.invoke(..., silent: Boolean = false)` — new param. When `silent=true` the use case skips its own `showParkingSaved` notification. The Coordinator passes `silent=true` because it owns the unified notification via `showParkingSavedConfirm`. All other callers (HomeViewModel manual/auto-accept, BluetoothParkingDetector, manual report screen) leave the default `silent=false` and keep the legacy `showParkingSaved` behaviour.
- `CoordinatorParkingDetector.runConfirm.onSuccess` — replaced `dismiss(PARKING_CONFIRMATION_NOTIFICATION_ID)` (BUG-FGS-103's original fix) with `notificationPort.showParkingSavedConfirm(...)`. This morphs the prompt into the saved+revert card. The stale-tap protection of BUG-FGS-103 remains intact because the receiver routes to a different action (`ACTION_PARKING_ACK`/`ACTION_PARKING_REVERT`) and the Service handles them with their own teardown.
- `CoordinatorDetectionService.ACTION_PARKING_ACK` — handler dismisses the notif + `stopForegroundAndSelf()`.
- `CoordinatorDetectionService.ACTION_PARKING_REVERT` — handler reads `EXTRA_PARKING_ID`, calls `RevertParkingUseCase`, then `stopForegroundAndSelf()`.
- `RevertParkingUseCase` — composes `userParkingRepository.clearActiveParkingSession(parkingId)` + `geofenceService.removeGeofence(parkingId)` + `notificationPort.dismiss(...)`. Best-effort, each step logs its own failure.

**No community spot to retract.** The public Spot is published by `ReportSpotWorker`, which is enqueued by `DepartureDetectionWorker` on geofence EXIT — *not* at confirm time. At the moment of revert the spot has not yet been published, and because we just removed the geofence it never will be. ✓

**Open follow-ups.**
- **TODO-REVERT-P1:** Add `UserParkingRepository.deleteSession(parkingId)` so the reverted session disappears from the history list entirely. Currently `clearActiveParkingSession` only flips `isActive=false`; the user still sees the cancelled session in their history.
- **TODO-REVERT-P2:** Auto-dismiss the state-B notification after `confirmationResponseTimeoutMs` (15 min) via WorkManager so abandoned cards don't linger.
- **TODO-REVERT-P2:** Test coverage for the revert flow (currently exercised only by manual smoke). Wire `FakeUserParkingRepository.clearActiveParkingSession` + a fake `notificationPort.dismissCalls` assertion in `CoordinatorParkingDetectorTest`.

### REFACTOR-300-FIX — Coordinator was wiping the post-save card (2026-06-09)

**Observed.** Field test on 2026-06-09: the unified "Vehículo aparcado · Cancelar" notification flashed visibly and disappeared within ~1–2 s of auto-confirm. The revert window REFACTOR-300 was designed to give the user (taxi / passenger / neighbour's car bonded by mistake) was effectively zero.

**Two related defects.**

1. **Finally wiped the card.** `CoordinatorParkingDetector.reset()` dismissed `PARKING_CONFIRMATION_NOTIFICATION_ID` as part of its state-clear, and `reset()` was called both at session-start AND in the session-end `finally`. After auto-confirm: `runConfirm.onSuccess` posted `showParkingSavedConfirm` on `PARKING_CONFIRMATION_NOTIFICATION_ID` → `completed = true` → `takeWhile` closed the flow on the next location tick → `finally { reset() }` ran → dismissed the id we just posted onto. The old contract ("this id only ever carries the prompt; dismiss freely on session end") predated REFACTOR-300 which reused the id for the morph-to-saved card, but the cleanup path was never adjusted.
2. **Naive session-start dismiss would still wipe the card.** A simple fix that moved the dismiss to session-start only was insufficient: if Activity Recognition fires a spurious `IN_VEHICLE_ENTER` while the user is walking from the parked car, the service restarts the coordinator → new `invoke()` → session-start dismiss → revert card gone within seconds. The 4-minute `maxNoMovementMs` guard inside the spurious session would have run for the whole window with no card visible to the user.

**Fix — timestamp gate at session-start.**

- New field `savedConfirmPostedAt: Long?` on the coordinator singleton. Set to `Clock.System.now()` inside `runConfirm.onSuccess` immediately after `showParkingSavedConfirm`.
- Session-start dismisses only when `savedConfirmPostedAt == null` OR `now - savedConfirmPostedAt > config.confirmationResponseTimeoutMs (15 min)`. Otherwise the dismiss is skipped and the card survives the new session-start. The flag resets to `null` whenever a dismiss fires.
- Session-end `finally` never touches notifications. Explicit dismisses live in the paths that legitimately end the prompt: `onUserConfirmedParking`, `onUserDeniedParking`, response-timeout abort, `runConfirm.onFailure`.

**Process-death behaviour.** `savedConfirmPostedAt` lives in memory only. A coordinator created after process restart sees `null` → next session-start dismisses whatever is still showing. Reasonable: we have no way to verify the lingering notification's age, and the user has had at least one full cold-start delay to act on it.

**Why the same timeout as the prompt response.** `confirmationResponseTimeoutMs = 15 min` was already the budget for "user must respond to the pre-save prompt". Reusing it for the post-save card means the user gets the same 15-minute revert budget — symmetrical and lets the single config knob tune both. Also folds in TODO-REVERT-P2's "15-min auto-dismiss" intent without needing a WorkManager job.

**Tests.**
- `should_keep_post_save_card_after_session_finally`: regression for defect 1 — asserts `savedConfirm` is the last op on the id after a user-confirm + session-end.
- `should_preserve_post_save_card_across_immediate_new_session`: regression for defect 2 — runs two back-to-back sessions and asserts the second's session-start did not append a `dismiss` op to the confirmation id.
- `FakeAppNotificationManager` now tracks `parkingSavedConfirmCallCount` and an ordered `confirmationNotifOps` log; closes part of the TODO-REVERT-P2 test gap.

**Side bug surfaced.** In the user-confirm path, `locationToConfirm = state.bestStopLocation ?: state.bestFix(location)` falls back to `location` (the current GPS fix) when no stop has been recorded yet. In a spurious-ENTER session the user is walking — a stale tap on a leftover prompt would save parking at the walking position. Out of scope for this fix; ticketed separately (TODO-CONFIRM-NO-STOP-LOCATION).

### BUG-FALSE-ENTER-WALKING — Steps-before-driving abort (2026-06-10)

**Observed.** Redmi Note 11 field test 2026-06-10: in the hospital scenario the user reported the detection foreground notification reappearing 1 minute after parking (so a fresh session had restarted) and the cycle repeating until the user reached home. AR was firing spurious `IN_VEHICLE_ENTER` events while the user was walking from the car (door slam + walk to trunk + carry bags). Each false ENTER spun up a fresh coordinator session that ran the full `maxNoMovementMs = 4 min` watchdog before self-terminating — and could restart immediately as AR misfired again.

**Root cause.** `maxNoMovementMs` is the only abort gate before driving speed is reached, and 4 minutes is a long time to keep a foreground service alive on a misfire. There was no cheaper signal that the session was bogus.

**Fix.** Add a step-detector-driven early abort. The `stepJob` already counts pedestrian steps; before the BUG-FALSE-ENTER-WALKING fix it only counted them when `stoppedSince != null` (i.e. during the eventual park stop). Now the rule is:

- **Before** `hasEverReachedDrivingSpeed` becomes true: count every step regardless of `stoppedSince`. These are the walking steps that prove the ENTER was spurious.
- **After** `hasEverReachedDrivingSpeed` becomes true: original behaviour — only count while stopped. Driving with the phone in a pocket still produces sensor events; we don't want them to interfere with the parking-confirm steps proof.

Once `state.stepCount >= falseEnterAbortSteps = 8` and `!state.hasEverReachedDrivingSpeed`, the location collector aborts the session: `completed = true; return@collect`. The coordinator's `finally` runs, the service stops. Subsequent real ENTERs (when the user actually gets in the car) start a fresh session with `stepCount = 0`.

**Why 8 steps.** Symmetrical with `minStepsToConfirm = 8` and unambiguous walking (≈ 6 s at normal cadence). Below 8 the threshold gets noisy; above 8 the abort is unnecessarily slow.

**Trade-off.** Phone bouncing in a pocket during the first minute of stop-and-go traffic could in theory accumulate 8 step events before crossing driving speed; field telemetry has not surfaced this case. If it does, raise the threshold or add a sliding-window timeout.

**Test.** `should_abort_session_when_steps_burst_before_driving_speed` + regression guard `should_not_abort_session_when_steps_arrive_after_driving_speed`.

### CONFIRM-NO-NOTIF-CLEANUP — Notification responsibility removed from `ConfirmParkingUseCase` (2026-06-10)

REFACTOR-300 introduced a `silent: Boolean = false` flag on `ConfirmParkingUseCase` so the coordinator could suppress the legacy `showParkingSaved` and own its unified state-B card via `showParkingSavedConfirm`. The flag worked but encoded a boolean-trap smell (4 callers, 2 with `true` and 2 with `false`, decision invisible at the call site) and mixed two responsibilities (persistence + UI) in one use case.

**Fix.** The use case now does *only* persistence + geofence + enrichment + `departureEventBus.reset()`. The notification call is gone. Each caller posts its own notification at the call site:

| Caller | Notification posted on success |
|---|---|
| `CoordinatorParkingDetector.runConfirm.onSuccess` | `showParkingSavedConfirm` (state-B card with REVERT, on id 2002) |
| `BluetoothParkingDetector.detectParking` | `showParkingSaved` (legacy tap-to-open-map, on UPLOAD_CHANNEL) — see `BT-NOTIF-LEGACY-CLEANUP` |
| `HomeViewModel.confirmDetectedParking` | `showParkingSaved` (manual auto-accept) |
| `HomeViewModel.confirmAddParking` | `showParkingSaved` (manual map-pin save) |

Single-purpose use case, no boolean flag, each call site documents its own UI intent. The test `should not post any notification (caller's responsibility)` in `ConfirmParkingUseCaseTest` is the regression boundary — if a future contributor re-adds a notification call inside the use case, that test fires.

### BT-NOTIF-LEGACY-CLEANUP — Bluetooth path no longer posts the REVERT card (2026-06-10)

`BluetoothParkingDetector` was posting the unified `showParkingSavedConfirm` state-B card (with `Sí confirmar / No cancelar`) on auto-confirm, mirroring the coordinator path. This created a cross-strategy lifecycle bug: the coordinator's `savedConfirmPostedAt` timestamp (introduced by REFACTOR-300-FIX) lives on the coordinator instance, but a BT-posted card has no way to register itself there. A next coordinator session-start would wipe the BT-posted card before its 15-min revert window expired.

**Decision.** Bluetooth detection is bound to a specific MAC address (the user's configured `bluetoothDeviceId`). The "neighbour's identical Toyota" failure mode is impossible — MAC addresses don't collide. The remaining edge cases (passenger in a paired vehicle, spurious BT drop mid-drive) are rare and bounded: a wrongly-saved BT parking only pollutes the community map IF the user drives out of the geofence radius after the wrong save, and community spots have a TTL anyway. The REVERT card was over-engineering for a 0.95-reliability path.

**Fix.** `BluetoothParkingDetector` now posts the legacy `showParkingSaved` notification (tap → open map, no actions). The cross-strategy timestamp problem evaporates because the coordinator is the only emitter of the state-B card. `BluetoothParkingDetector` no longer takes `vehicleRepository` (used only to look up the vehicle name for the card).

Users with a misfire can clean up from the history screen. Field telemetry will tell us if that's enough; if not, we revisit by introducing a shared `SavedConfirmCardTracker` Koin single.

### BUG-OPPO-LATE-CONFIRM — EXIT + steps fast path (2026-06-10)

> **Superseded by DET-D-03 (2026-06-26).** The fast path no longer requires AR `IN_VEHICLE_EXIT` —
> the guard is now `stepCount >= minStepsToConfirm` alone, and `EvaluateParkingDecisionUseCase`
> confirms on **steps + egress** (the egress gate is the decisive signal; AR EXIT was a redundant
> extra gate). A field trace (2026-06-26) showed the confirm waiting ~16 s for the AR EXIT while
> steps+egress were already satisfied — and on hardware where EXIT is late or never fires, the old
> guard would miss the park entirely. AR EXIT is now a non-decisive hint. `pathLabel` is `steps+egress`.

**Observed.** Oppo CPH2371 field test 2026-06-09, session 3: physical park at ~19:42, foreground service stayed visible until 20:02:54 (confirm via steps proof inside CANDIDATE). 20 minutes of FGS visible after the user had already parked. The saved location was offset from the actual parked-car position.

**Root cause.** The slow path (no STILL, no fast-path bonuses) requires 5 minutes of *continuous* stop before reaching `High`. The user was already out of the car at 19:45:50 (AR EXIT delivered then) but kept walking briefly between stops (speed oscillated between 0 m/s and ~1 m/s for ~12 min). Every `speed >= stoppedSpeedThresholdMps = 1 m/s` fix resets `stoppedSince`, so the 5-min window never accumulated until the user finally sat still around 19:57. Worse, by then `bestStopLocation` had been overwritten each time a new stop opened a fresh initial-stop window, so the location anchored at the user's destination rather than the parked car.

`activityStill` would have triggered the fast path (Medium → High via the STILL+exit bonuses) but on this device it arrived at 19:58:03, 12 minutes after EXIT — too late to anchor `bestStopLocation` at the car.

**Fix.** Insert a short-circuit check after the candidate-phase decision tree but before scoring: when **both** `state.vehicleExitConfirmed == true` AND `state.stepCount >= minStepsToConfirm`, confirm immediately with `reliabilityVehicleExit`. The confirm uses `bestStopLocation ?: bestFix(location)` — same location anchoring as every other auto-confirm path.

**Honours mismatch guard.** A CAR profile with sustained slow speed could be a scooter; we still apply the `BUG-SCOOTER-001` heuristic (`maxSpeedKmh <= mismatchMaxSpeedKmh && sessionDurationMs >= mismatchMinSessionDurationMs`) and suppress the fast confirm in that case. The slow-path fallback still runs and the user-prompt notification still fires.

**Why this is safe.** EXIT + steps = "user got out of car" with as much evidence as the existing CANDIDATE-phase steps proof. The difference is only the gate: CANDIDATE requires the slow path to first reach HIGH (≥ 5 min stop + STILL/exit bonuses); this path skips that wait. We're not lowering the evidence bar, we're removing an unnecessary timer.

**Why this doesn't fire spuriously.** Steps count only when stopped (post-drive) and require `vehicleExitConfirmed`. A long queue-in-car scenario (BUG-GARAGE-COLA-001) won't trigger because the user hasn't actually gotten out — no steps fire (sensor accumulator inside-car noise was field-measured at ≤ 5 in 8 min).

**Test.** `should_fast_confirm_when_exit_and_steps_arrive_before_slow_path_matures` + regression guard `should_not_fast_confirm_when_only_exit_without_steps`.

> **Superseded by DET-A (below).** The "steps count only when stopped + require vehicleExit"
> argument above turned out **not** to be sufficient: a spurious AR `IN_VEHICLE_EXIT` mid-trip plus
> a phone bouncing in stop-and-go traffic produces both `vehicleExitConfirmed` and ≥ 8 steps while
> the car never moved. DET-A adds the missing second signal — egress displacement.

### DET-A — Egress displacement gate (the Prague false positive, 2026-06-25)

**Symptom.** A Bolt ride in Prague published a phantom free spot. Root cause confirmed in code:
1. AR emitted a spurious `IN_VEHICLE_EXIT` mid-trip → `vehicleExitConfirmed = true`.
2. Stuck in stop-and-go traffic, `stoppedSince != null`, so the step accumulator counted every
   pocket vibration as a step (`shouldCount = !hasEverReachedDrivingSpeed || stoppedSince != null`).
3. `stepCount` reached `minStepsToConfirm = 8`.
4. **Path 8** (`vehicleExitConfirmed && stepCount >= minStepsToConfirm`) confirmed, skipping the
   slow path and STILL — **with no displacement check**. `evaluateCandidatePhase.hasStepsProof` had
   the same hole.

**Fix.** A new immutable `egressAnchor` is pinned at the moment the vehicle first stops
(`stoppedSince` null→non-null) and held — never refined within the initial-stop window, preserved
across walking-pace fixes, cleared only on genuine drive-away / reposition burst so the next stop
re-pins it. `hasEgressDisplacement(state, current)` is true only when the current fix is
≥ `minEgressDisplacementMeters = 18 m` from that anchor. Both confirm paths now AND it:
- **Path 8** (EXIT + steps fast confirm) and
- **candidate `hasStepsProof`**.

**Why a separate anchor (not `bestStopLocation`).** `bestStopLocation` is refined by accuracy during
`initialStopWindowMs = 30 s`; the 8 steps arrive in ~5–8 s while that anchor can still move with the
user. `egressAnchor` is captured once and pinned, so displacement is measured from the parked car.

**Why 18 m.** Strictly above `minGpsAccuracyMeters = 15 m` (enforced by `require` in
`ParkingDetectionConfig.init`) so a single in-envelope GPS noise fix cannot satisfy the gate.

**GPS cadence is sufficient.** `AndroidLocationDataSourceImpl` requests HIGH_ACCURACY at a 5 s
interval with a 2 s fastest-update floor → ~2–5 s cadence → 5–8 fixes during an 18 m egress walk.
The gate adds ~10–15 s to the fast-confirm but does not block it.

**Tests.** `should_not_fast_confirm_when_exit_and_steps_arrive_without_egress_displacement` (Prague
replay → no save) + `should_fast_confirm_when_exit_and_steps_arrive_before_slow_path_matures` updated
to walk past the anchor before confirming.

### DET-C-01 — Egress is mandatory for every auto-confirm (2026-06-25)

DET-A gated the two **steps** confirm paths (Path 8 + candidate `hasStepsProof`). One soft path
remained ungated: the candidate's `windowElapsed && hadVehicleExit` branch auto-confirmed on an
AR `IN_VEHICLE_EXIT` + dwell-time, **with no displacement check** — exactly what a spurious AR exit
during a long traffic stop would trigger.

**Fix.** A single `!hasEgress -> false` guard at the top of the candidate `confirmNow` decision makes
egress displacement a precondition for **every** auto-confirm path. Consequence — the invariant the
asymmetric-failure principle wants: **no auto-confirm can happen without the user having physically
walked ≥ `minEgressDisplacementMeters` from the parked car** (the one signal impossible to fake at a
stop). STILL, dwell-time and AR-exit-time on their own now only open the candidate and surface the
prompt; the decision falls to the user or to a later steps/exit **+ egress**. The `user` tap path is
unaffected (it bypasses the candidate tree entirely).

### DET-D-02 — Candidate decision extracted to a pure function (2026-06-25)

The candidate `confirmNow` logic (above) now lives in `EvaluateParkingDecisionUseCase`, a pure
function of `ParkingDecisionInput` (primitive corroboration signals, not the coordinator's private
state) returning `ParkingDecision { Confirmed(pathLabel, reliability) / Rejected / Inconclusive }` —
the mirror of `DepartureDecision`. The coordinator's `evaluateCandidatePhase` is now a thin
orchestrator: build the input → `when (decision)` → run confirm / discard / keep waiting. Behaviour
is identical; the win is that the wall-clock-driven `windowElapsed` paths (previously impossible to
drive through the real-`Clock` collect loop) are now unit-tested in `EvaluateParkingDecisionUseCaseTest`,
including the Prague replay (steps without egress → Inconclusive, then Rejected once the window
expires). The slow-path/STILL confidence reconversion (DET-D-03) is deferred — it changes prompt
*timing*, not just structure.

> **Path 8** (the invoke-level EXIT+steps fast confirm) is intentionally left outside the use case
> for now — it is already egress-gated (DET-A) and covered by `should_fast_confirm…`. Unifying it
> with the candidate decision is a future cleanup.

### REFACTOR-301 — Bluetooth strategy: lifecycle + unified post-save notification (2026-06-08)

Companion refactor to REFACTOR-300, applied to the Bluetooth detection flow (`BluetoothDetectionService` + `BluetoothParkingDetector` + `BluetoothConnectionReceiver`).

**BT bugs closed.**

| ID | Description | Fix |
|---|---|---|
| BT-BUG-100 | Every `stopSelf()` path skipped `stopForeground(STOP_FOREGROUND_REMOVE)` → BT_DETECTION FGS notification (id 1003) could persist. | `BluetoothDetectionService` now uses `ForegroundServiceController.stopForegroundAndSelf()` on every teardown path (handleConnected, missing-extras, null-intent, detection-finally). |
| BT-BUG-101 | `DETECT-SERVICE-RACE-001` ported to BT: a superseded detection job could call `stopSelf()` after a replacement job had just promoted. | `thisJob === detectionJob` guard in the detection-coroutine's `finally`. |
| BT-BUG-102 | `BluetoothConnectionReceiver` held a `CoroutineScope(SupervisorJob() + Dispatchers.IO)` as a property — completed jobs accumulated as child garbage of a parent that was never cancelled. | Per-delivery local scope, explicit `scope.cancel()` in the `finally`. |
| BT-BUG-103 | Vehicle-name fetch was a side-launch outside `detectionJob`; a fast BT_CONNECTED could cancel the detection while the side-launch was still resolving, then `updateDetectionVehicle.notify(...)` would re-post the FGS notification AFTER `stopForeground` (ghost notif). | Fetch moved INSIDE the detection coroutine; same lifetime as `detectionJob`. |
| BT-BUG-104 | Name fetch used `observeActiveVehicle()` (the *default* vehicle) instead of the vehicle whose BT actually disconnected. In multi-vehicle setups the notification displayed the wrong name. | Resolve via `vehicleRepository.getVehicleById(userId, vehicleId)` where `vehicleId` came from the BT_DISCONNECTED intent extras. |
| BT-BUG-105 | BT auto-confirm fired silently with no user-facing affordance to revert (user was a passenger / neighbour's car was bonded by accident → permanent unwanted parking event). | `BluetoothParkingDetector` now calls `confirmParking(silent=true)` then `notificationPort.showParkingSavedConfirm(parkingId, vehicleName, lat, lon)` — same unified state-B notif as the Coordinator path. ACK / REVERT both work via the existing `ParkingConfirmationReceiver`. |
| BT-BUG-106 | `runCatching { device.address }.getOrNull()` silenced SecurityException on revoked BLUETOOTH_CONNECT. | Adds a `.onFailure { PaparcarLogger.w(...) }` so revocation produces a trace. |
| BT-REFACTOR-200 | No `onDestroy` safety net for the FGS notification. | Mirrors BUG-FGS-113 fix from `CoordinatorDetectionService` — `onDestroy` calls `fgs.removeForegroundNotification()` defensively (idempotent). |

**Open follow-ups (BT).**
- **TODO-BT-CONFIG-P2:** Move `BluetoothParkingDetector.PARKING_DETECTION_RELIABILITY = 0.95f` to `ParkingDetectionConfig.reliabilityBluetooth` for parity with the existing `reliabilityUserConfirmed`/`reliabilityVehicleExit`/`reliabilitySlowPath`. Cosmetic; no behaviour change.
- **TODO-BT-IOS-P3:** When iOS BT detection lands it should follow the same `silent=true` + `showParkingSavedConfirm` pattern. `IosAppNotificationManagerImpl` will need to implement `showParkingSavedConfirm` (currently default `{}` from the interface).

### NOTIF-CLEANUP-310626 — Trim non-actionable departure notifications + per-tier channels (2026-06-26)

**Motivation.** The notification surface had grown to ~9 user-facing posts, several of them non-actionable noise. Audit removed three and reorganised importance.

**Removed.**
- `showSpotUploading` ("Subiendo") — cosmetic ~1-2 s ongoing toast around the Firebase write; `ReportSpotWorker` is a plain `CoroutineWorker` and never needed it. The community spot is still published to Firestore (`reportSpotReleased` is untouched) — only the notification is gone.
- `showSpotPublished` ("Plaza publicada") — transparency-only, not actionable.
- `showHomeParkingLeft` ("Has salido de tu plaza") — the private-zone branch of `ProcessConfirmedDepartureUseCase`. **Behaviour change:** leaving a private zone no longer has any user-facing effect (it never published a community spot — it only posted this notification). Public-spot departures are unaffected.
- Interface methods, both Android/iOS impls, both fakes, `SPOT_PUBLISHED_NOTIFICATION_ID` / `HOME_PARKING_NOTIFICATION_ID`, the `community_channel`, and the now-orphaned `notif_spot_published_*` / `notif_uploading_*` / `notif_home_parking_left_*` strings (all 9 locales) were deleted. `ProcessConfirmedDepartureUseCase` lost its `zoneRepository` + `notificationPort` deps; `ReportSpotWorker` lost `notificationPort`; `IosReportSpotScheduler` lost `notificationPort`.

**Importance reorg (per-channel).** Confirmation prompts (`showParkingConfirmation`, `showParkingSavedConfirm`) moved off the LOW `detection_channel` onto a new HIGH `action_channel` (heads-up) — they are the only button-interaction notifications. `showParkingSavedConfirm` adds `setOnlyAlertOnce(true)` so morphing the "¿Has aparcado?" prompt at the same id does not re-buzz, while the auto-confirm path still alerts once. `showPermissionRevoked` moved from LOW to the DEFAULT `upload_channel` so it is visible rather than silent-at-bottom. Detection FGS stays LOW/silent.

**Icon.** All notifications (except debug) now use `ic_notification_logo` — the app's car glyph wrapped in a circle, monochrome — as the status-bar small icon, replacing the per-type contextual icons (which were deleted).

### DET-AR-REARM-001 — AR proximity re-arm for short trips + missed-exit watchdog (2026-06-28)

**Problem.** Since DET-G-01 the detection loop re-arms ONLY on `GEOFENCE_EXIT` (AR was demoted to a non-decisive corroborator). The loop is serial and geofence-gated: after a park is confirmed the service goes idle and only re-arms when the user leaves their own parked-car geofence. Two failure modes leave the loop **stalled** — and because arming is single-pathed, a stalled loop misses not one park but *every* subsequent park until the next genuine long departure:
- **(a) Short trip within the radius.** Moving the car less than the ~95–120 m effective geofence radius never fires an EXIT, so detection never re-arms.
- **(b) Platform-dropped EXIT.** Doze / aggressive OEM killers (Xiaomi/Oppo) can swallow the EXIT even on a real drive-away.

Reducing the geofence radius was rejected: below ~100 m Android geofencing gets *less* reliable and GPS jitter produces false exits (which now also falsely arm detection). The radius is calibrated to platform reliability and stays.

**Fix — a second, precision-preserving arming path (AR + proximity gate).** The geofence's power is a two-part signal: the departure *originates where the car is parked* (anchor) AND happens *at vehicle speed* (speed/egress gates). "Far from the car" is only half — it cannot tell *drove away* from *walked / took a bus / got picked up*, which is exactly the bus/taxi/train false-positive class the geofence kills. So:
- `IN_VEHICLE_ENTER` is delivered DIRECTLY to `CoordinatorDetectionService.handleArVehicleEnter` via a privileged `getForegroundService` PendingIntent (`ACTION_AR_VEHICLE_ENTER`), **scoped to the parked window** so the FGS-promote only happens when a car is actually parked — not on every bus ride. Registration is wired in `ConfirmParkingUseCase` (after geofence create) and torn down in `ProcessConfirmedDepartureUseCase` only when no parked session remains; restored after reboot/reinstall by `GeofenceJanitorWorker` alongside the geofences.
- `ShouldArmFromVehicleEnterUseCase` reconstructs the **anchor** in software: arm only if a GPS fix is within the nearest parked car's **own geofence radius** (`ParkingDetectionConfig.geofenceRadiusFor(size, accuracy)` — the same value the geofence was registered with) — boarding a vehicle *where the car is parked* is overwhelmingly the user's own car. Equal-by-construction to the geofence boundary so AR and the EXIT meet on one line: **no dead ring** between them (which a smaller flat constant left for vans/poor GPS) and **no extra bus surface** (which a larger one opened for motorcycles). Crucially, the **proximity gate — not the egress gate — is the decisive bus/taxi defence**: a bus ride satisfies the egress gate (drive + walk away), so the anchor must do the rejecting. Fails closed (no session / no fix → do not arm). The Coordinator's speed + egress gates remain the final filter, so a false arm cannot produce a phantom spot.
- AR registration is now **split**: `registerTransitions()` is EXIT-only (always-on, plain broadcast → `ActivityTransitionReceiver`, no flash); the scoped ENTER goes to the service. Each transition reaches exactly one PendingIntent (no double-delivery). The ENTER timestamp corroborator (`DepartureEventBus.onVehicleEntered`) moved from the receiver to the service handler.

**Fix — missed-exit watchdog (last resort for case (b)).** `DetectionHeartbeatWorker` (previously a no-op) now surfaces a **low-confidence** "still parked?" prompt (`showStillParkedPrompt`, ACTION channel, single "I've left" action → `ACTION_DEPARTURE_CONFIRMED` → `ProcessConfirmedDepartureUseCase`). It NEVER auto-releases — at poll time the departure speed is gone, so only the user can disambiguate, and a silent release would re-introduce the bus/taxi false positives. It does **not nag**: it fires only when an active session exists, detection is idle, the phone is beyond `watchdogFarThresholdMeters` (300 m) from the nearest car, AND an `IN_VEHICLE_ENTER` was recorded within `vehicleEnterWindowMs` (30 min). The vehicle-signal requirement excludes the normal "park and walk away" case (far all day, no vehicle signal), and the 30-min window self-bounds the prompt; when the conditions lapse the prompt is dismissed.

**Trigger diagnostics.** `startParkingDetection(trigger: DetectionTrigger)` logs which signal armed the loop — `GEOFENCE_EXIT` / `AR_PROXIMITY` / `MANUAL` — to three sinks: a Crashlytics custom key (`det_trigger`), the remote `DetectionEventLogger` (Firestore, `SessionStarted` with strategy `ARM:<trigger>`), and a debug notification (DEBUG builds only) so a field tester sees on-device which trigger fired.

**Idempotency / race.** Two triggers (GEOFENCE_EXIT + AR_ENTER) can arm concurrently. The early `detectionJob?.isActive` guard skips AR when a job is already running; additionally, because the AR proximity gate does an async ~15 s GPS fix, the `Arm` branch **re-checks `isActive` immediately before arming** so a GEOFENCE_EXIT that armed during the fix window is not superseded by the (less specific) AR trigger.

**Geofence radius dedup.** `computeGeofenceRadius` (previously duplicated in `ConfirmParkingUseCase` and `UpdateParkingLocationUseCase`) is extracted to `ParkingDetectionConfig.geofenceRadiusFor(sizeCategory, accuracy)` — the single source of truth shared by both geofence registrations and the AR proximity gate.

**New config.** `watchdogFarThresholdMeters = 300f`. (The AR proximity threshold is derived per-session via `geofenceRadiusFor`, not a constant — an earlier flat `arRearmProximityMeters = 120f` was removed because it left a dead ring for vans and was loose for motorcycles.)

**Tests.** `ShouldArmFromVehicleEnterUseCaseTest` covers the four decisions (no-session / no-fix / within-proximity / too-far).

**Open follow-ups.**
- **Device validation required.** Two AR transition registrations with distinct PendingIntents (EXIT global + ENTER scoped) must be confirmed to coexist on real Play Services — especially on the OEM killers (Xiaomi/Oppo). Detection-core changes are not proven by green compile/tests.
- `RevertParkingUseCase` and the sign-out drain do not explicitly unregister the scoped ENTER arming; it is self-correcting (the handler fails closed when no session exists) but could be wired for tidiness.

### DET-TOGGLE-001 — master enable/disable from Settings

Auto-detection has a **master intent flag** (`AppPreferences.autoDetectParking`, default `true`) toggled from Settings. It is **orthogonal to permissions**: revoking a permission is not the same as turning the feature off, but **either one stops detection**. Detection runs only when `autoDetectParking == true` **AND** the producer permissions are granted.

- **Reactive source.** `AppPreferences.observeAutoDetectParking(): Flow<Boolean>` (Android DataStore-mapped; iOS/fakes via `MutableStateFlow`) so the UI and the Android arming both react live to the toggle.
- **Home banner.** `ObserveDetectionReadinessUseCase` reads the flag and emits `DetectionReadiness.Disabled(TURNED_OFF)` with **top precedence** (after the structural NO_VEHICLE / NON_PARKING, before Blocked/Parked): if you turned it off, Home shows a one-tap "activate detection" nudge (`DetectionUiState.TurnedOff`) instead of nagging for permissions. The CTA dispatches `HomeIntent.EnableAutoDetection` → `setAutoDetectParking(true)` + a confirmation snackbar; the reactive flag flips the banner away automatically.
- **Android gating (two layers).**
  1. **Chokepoint guard:** `ActivityRecognitionManagerImpl.registerTransitions()` returns early (and calls `unregisterTransitions()`) when the flag is off, so every caller (MainActivity, `BootCompletedReceiver`, `RegisterActivityTransitionsWorker`) respects it for free. `BluetoothConnectionReceiver` ignores ACL connect/disconnect when off (the deterministic BT path must not arm either).
  2. **Runtime toggle:** `MainActivity` arms/disarms from `combine(hasProducerPermissions, observeAutoDetectParking())` — both true → `registerTransitions()`; either false → `unregisterTransitions()`.
- **First-run.** The flag defaults `true`, so granting the producer permissions (the "Activate detection" step) is what brings detection online; the user can disable it manually from Settings afterwards.
- **Open follow-up — device validation required.** Like all detection-core changes, the OFF path (no arming + runtime disarm) is not proven by green compile/tests: confirm on device that toggling off actually stops AR/BT arming and toggling back on re-arms.

### DET-TOGGLE-002 — detection feedback (dialog + snackbars + cold-start nudge)

User-facing feedback derived from the readiness state machine, so the user always knows *why* detection isn't running and can fix it in one tap.

- **"Maybe later" dialog (permissions).** Tapping the deferral CTA opens an educational `PapAlertDialog` ("Skip automatic detection?" — Activate now / Maybe later) before continuing with CORE only, so users don't silently skip the core value. State `PermissionsState.showSkipDetectionDialog`; intents `RequestSkipDetection` / `DismissSkipDetectionDialog`.
- **In-app snackbars (with one-tap re-activation).**
  - *Settings:* toggling auto-detect OFF emits `SettingsEffect.DetectionTurnedOff` → snackbar "Auto-detection stopped · Turn on" at the point of action (reliable — `MutableSharedFlow` effects are dropped when Home is not the foreground collector).
  - *Home:* a **working→stopped** readiness transition (`DetectionUiState.isDetectionWorking` → `isDetectionStopped`) fires `HomeEffect.DetectionStopped` → the same snackbar, reusing `EnableAutoDetection`.
- **Cold-start nudge (worker).** `FirstParkNudgeWorker` (daily, low cadence) shows a gentle "park once to start auto-detection" notification only when `EvaluateFirstParkNudgeUseCase` allows: readiness is `Ready(COORDINATOR)` (the `AwaitingFirstPark` cold-start — which already encodes *flag on + producer perms + parking vehicle + no session + Coordinator strategy*, so **Bluetooth and inactive vehicles are never nudged**), the user has **never confirmed a park** (`AppPreferences.hasConfirmedFirstPark`, set by `ConfirmParkingUseCase`), a **3-day cooldown** has elapsed, and a **hard cap of 3** is not reached. Self-disables forever after the first park. The decision is a pure, unit-tested function.
  - **Deep-link → AddParking.** Both the notification body tap and the "Mark my spot" action carry `MainActivity.EXTRA_START_ADD_PARKING`; `MainActivity` (onCreate for cold start, onNewIntent when running) raises `StartAddParkingEventBus`, which `HomeViewModel` consumes to dispatch `EnterAddParkingMode(initialGps = userGpsPoint, targetVehicleId = active vehicle)` — dropping the user straight into manual add-parking mode (mirrors the detection banner's "mark spot"), not just Home. The bus is a **CONFLATED `Channel`** (not a `replay = 0` `SharedFlow` like `MapFocusEventBus`) so the request survives the cold start that the nudge almost always triggers: it is buffered until Home subscribes and consumed exactly once.
- **Deferred — Phase 3 (background "detection stopped" notification).** Folded into the OEM-kill watchdog task: the permission-revoked-at-departure case is already handled by `guardPermissions()` → `showPermissionRevoked()`, and a flag-off does **not** stop an already-parked car's geofence departure (only future parks), so there is no extra "spot won't free" risk to notify. The remaining silent-lapse case needs the (currently disabled) heartbeat re-enabled + device testing.
- **Device-validated (2026-06-30).** On an Oppo (ColorOS, Android 13): the "Maybe later" dialog renders correctly (apostrophe fix confirmed), the Settings snackbar + "Turn on" re-activation works, and the nudge fires only at `Ready(COORDINATOR)` (verified via a temporary debug breakdown log: `Blocked` → no fire, `Ready(COORDINATOR)` → fire; cooldown + `hasConfirmedFirstPark` gates both block). The deep-link lands in `AddingParking` from a cold start. Still pending: the Home-side working→stopped transition snackbar (timing-dependent, non-blocking) and iOS.

### DET-G-04 — A GEOFENCE_EXIT-armed session is a confirmed departure: skip the driving-speed gate (2026-07-02)

**Observed.** Field trace (2026-07-01, El Puerto de Santa María), reproduced on **two devices** with the same trip shape: the user parks, drives a **short hop (~300 m)** to a second spot, and parks again. The freed first spot publishes correctly, but the **second park is never saved** — the user is left with no active parking.

**Root cause.** Since DET-G-01 a `GEOFENCE_EXIT` arms the next Coordinator session (`CoordinatorDetectionService.handleGeofenceExit` — the same handler that dispatches the departure/publish). But the Coordinator treats *every* session identically and still requires `hasEverReachedDrivingSpeed` (a fix ≥ `minimumTripSpeedMps = 5 m/s ≈ 18 km/h`) before any confirm path runs, and feeds the `falseEnterAbortSteps = 8` guard off that same flag. On a short hop the fast driving is **over before this session's GPS stream warms up**: the diagnostics session (`arm ARM:GEOFENCE_EXIT d=317m`) only ever saw fixes ≤ 2.86 m/s, so `hasEverReachedDrivingSpeed` stayed false; the user got out, 14 egress steps accumulated, and the session aborted `aborted_false_enter` — discarding a real park. Confirmed via the Firestore diagnostics trace (`diagnostics/{uid}/sessions/1782923300713`).

The `hasEverReachedDrivingSpeed` / `falseEnterAbortSteps` machinery is **legacy from when AR `IN_VEHICLE_ENTER` was the primary arm** — a spurious ENTER (bus, taxi, sitting at a desk) genuinely needs the coordinator to independently re-observe driving speed. A `GEOFENCE_EXIT` does not: the car provably left its own parked-car geofence (≥ radius) to fire the exit — that IS the driving proof, and it is the same signal that already published the freed spot.

**Fix.** `CoordinatorParkingDetector.invoke(locations, armedByConfirmedDeparture: Boolean = false)`. When `true`, the session seeds `hasEverReachedDrivingSpeed = true` at entry, so the false-ENTER abort and the "waiting for driving signal" skip no longer apply and the confirm paths (steps+egress, candidate, slow) run normally, anchored at the real spot via `bestStopLocation`. The service maps `armedByConfirmedDeparture = trigger == DetectionTrigger.GEOFENCE_EXIT` at the single call site (`CoordinatorDetectionService`).

**Why gated on GEOFENCE_EXIT only (not unconditional).** The real discriminator is **not "user action vs machine"** but **when the trigger arms relative to the drive** — i.e. whether the coordinator's own GPS stream can be relied on to observe driving speed:

| Trigger | Arms… | Stream sees ≥ `minimumTripSpeedMps`? | Seed? |
|---|---|---|---|
| `GEOFENCE_EXIT` | **mid-trip** (car already crossed its geofence radius, ≥ ~80–120 m) | maybe **not** — on a short hop the fast driving is over before the stream warms up | **yes** |
| `MANUAL` ("I'm driving") | **before** the trip | yes — the stream is already running when the car accelerates | no |
| `AR_PROXIMITY` (boarding the car) | **at the start** of the trip | yes | no |

So `MANUAL` and `AR_PROXIMITY` don't *need* the seed (their stream catches the speed and `hasEverReachedDrivingSpeed` flips on its own), and keeping the guard is also what protects them: a premature "I'm driving" tap with no actual drive still aborts cleanly at `maxNoMovementMs`, and a spurious AR ENTER (bus/taxi/at a desk) is still rejected. `AR_PROXIMITY` is disabled today (`AR_REARM_ENABLED = false`) but is a re-enable contingency — an unconditional seed would silently reintroduce that bus/taxi false positive.

The mapping (`GEOFENCE_EXIT → true`) lives in the service (which owns trigger taxonomy); the coordinator stays trigger-agnostic behind the single `armedByConfirmedDeparture` boolean.

**Trade-off.** A spurious `GEOFENCE_EXIT` while the car is genuinely parked (poor-accuracy fix drifting past the radius) will now run the session to the slow-path/response-timeout instead of the 4-min no-movement abort. Bounded by `confirmationResponseTimeoutMs = 15 min` and rare (geofences are accuracy-padded; the departure worker independently speed-gates the publish side). Accepted.

**Tests.** `should_confirm_geofence_armed_session_even_when_it_never_reaches_driving_speed` (replays the trace: geofence-armed, never ≥ 5 m/s, 8 steps + egress → **confirms**) + regression guard `should_still_abort_false_enter_when_session_is_not_a_confirmed_departure` (same input without the flag → **aborts**, so the seed can't leak to AR/MANUAL).

**Pending.** Device validation of the short-hop park on the two field devices; iOS (`observeAdaptiveLocation` + geofence path unchanged, but detection-core changes are never proven by green tests alone).

### DET-G-05 — Verify the departure before seeding a GEOFENCE_EXIT arm (2026-07-04, fixes BUG-REPARK-WALK-001)

**Observed.** Field trace (2026-07-03 22:11–22:15Z, El Puerto de Santa María, `diagnostics/{uid}/sessions/1783116798598`): the user parks (session saved correctly, `Calle la Angelita 3`, accuracy 1.25 m), walks home, and ~4.5 min later the app **re-confirms a new "park" ~120 m away at the pedestrian's position** (`confirmed_steps+egress`, reliability 0.9), deactivating the real session and re-planting the geofence on the walking path. No phantom spot was published (the departure worker's speed gate held), but the real car position was lost — and the new geofence can chain the same failure on the next exit.

**Root cause.** DET-G-04's premise — *"the car provably left its own geofence to fire the exit"* — is wrong: **the geofence tracks the PHONE, not the car**, and walking > radius away from a fresh park is what every user does, every time. The unconditional `GEOFENCE_EXIT → armedByConfirmedDeparture = true` seeded `hasEverReachedDrivingSpeed`, disarming `falseEnterAbortSteps` + `maxNoMovementMs`; the walk supplied the rest (all fixes < 1 m/s kept `stoppedSince` alive → 93 steps counted, first pedestrian fix became `bestStopLocation`, 44 m of egress) and steps+egress confirmed. Note the trap: from the coordinator's own GPS stream, a walking exit and DET-G-04's short-hop are **indistinguishable** (neither ever sees `minimumTripSpeedMps`) — so the discriminator must be external, not a re-instated speed gate.

**Fix (two coordinated parts).**
1. **Pre-arm verifier** — `VerifyDepartureEvidenceUseCase` (pure): the exit may seed only when backed by *vehicle evidence* — a recent AR `IN_VEHICLE_ENTER` (≤ `vehicleEnterWindowMs`, covers the short-hop where the user provably boarded) **or** a one-shot fix at ≥ `minimumDepartureSpeedKmh` (covers the common mid-drive exit). `handleGeofenceExit` samples `getOneLocation()` and passes `departureVerified` into `startParkingDetection`; the seed becomes `trigger == GEOFENCE_EXIT && departureVerified`. Unverified exits **still arm**, but with the legacy anti-walking guards active — a walking exit now aborts at 8 steps as it did pre-DET-G-04.
2. **Late-evidence upgrade** — `CoordinatorParkingDetector.notifyDepartureConfirmed()`: when `DepartureDetectionWorker` later confirms the departure (AR ENTER can deliver ~2 min late; its retries sample speed at ~15/30/60 s), it seeds the RUNNING session, unlocking the confirm paths for a real drive whose evidence arrived after the arm. No-ops between sessions so a stale verdict can't leak into the next arm.

**Residual risk.** A spurious AR `IN_VEHICLE_ENTER` fired while walking away (the documented `BUG-FALSE-ENTER-WALKING` hardware quirk) would still verify the exit and re-open this hole for that walk. Not observed in the incident trace (`lastVehicleEnteredAt` was null); if field telemetry surfaces it, tighten the verifier (e.g. require the ENTER to *precede* the exit, or corroborate with a step-cadence veto). A short-hop with **neither** signal (no AR, drive over before the speed sample, worker retries all inconclusive) reverts to the pre-DET-G-04 outcome — park lost, but never relocated; the losing case is rare and strictly safer than the false re-park.

**Tests.** `VerifyDepartureEvidenceUseCaseTest` (5 cases incl. the walking trace and the stale-ENTER window) + `should_confirm_when_late_departure_verdict_upgrades_an_unverified_session` + `should_ignore_departure_verdict_between_sessions`. DET-G-04's pair still passes — the coordinator-level flag semantics are unchanged; only the service's mapping got the verifier.

**Pending.** Device validation: real park + walk away (must NOT re-confirm; session aborts `aborted_false_enter`), real drive-away (seed via speed), short-hop repark (seed via AR ENTER or worker upgrade); iOS.

### DET-SOLID-001 — Evidence-based detection redesign (2026-07-04)

The system stopped being a pile of point guards (DET-G-01..05) and became **evidence + write-side invariants + a replayable decision core**. Full plan/context: three exhaustive code sweeps (triggers/lifecycle, coordinator state machine, effects/invariants) + the BUG-REPARK-WALK-001 field incident.

**Architecture after the redesign:**

1. **Arming is exclusive to `GEOFENCE_EXIT` + `MANUAL`.** `DetectionTrigger.AR_PROXIMITY` and the whole AR-arming machinery (`registerVehicleEnterArming`, `handleArVehicleEnter`, `ShouldArmFromVehicleEnterUseCase`, `AR_REARM_ENABLED`) were purged — AR is an **indicator only** (user-confirmed design rule: in the field AR failed both ways, spurious AND missing, so it must never be load-bearing).
2. **Typed `ArmEvidence`** (`Manual | VerifiedBySpeed | VerifiedByVehicleEnter | Unverified`) replaces the `armedByConfirmedDeparture` boolean. Verified evidence seeds `hasEverReachedDrivingSpeed`; unverified/manual arms keep every anti-walking guard. The label is persisted on the session (`UserParking.armEvidence` + `tripMaxSpeedMps`, Room v11, local-only) and logged in `SessionStarted.evidence`.
3. **AR ENTER rides the passive broadcast receiver** (same request as EXIT, zero FGS, zero arming) and stamps `DepartureEventBus` with the TRUE transition time (`elapsedRealTimeNanos` → epoch). Both evidence windows are strict **enter-precedes-exit** — an ENTER after the exit is a bus/taxi boarded outside the radius, never departure proof. The bus timestamp survives process death (SharedPreferences mirror).
4. **Evidence policy** (`EvaluateParkingDecisionUseCase`): ENTER-only evidence with no observed driving → `ParkingDecision.Prompt` (ask, don't assert) — flag `autoConfirmRequiresStrongEvidence`. BIKE/SCOOTER profiles never auto-confirm. The B4 step-cadence veto exists behind `enterArmStepVetoMs` (default OFF until replay-validated).
5. **Write-side invariants**: atomic `replaceActiveSession` (Room `@Transaction`); repark-plausibility guard in `ConfirmParkingUseCase` (recent + near + no driving observed + unverified arm → `ImplausibleRepark`, degraded to a prompt); geofence registration failure is loud + janitor-retried (session ⟺ geofence); janitor self-repairs duplicate actives; boot runs an immediate geofence restore.
6. **Observability**: `DepartureVerdict` (pre-arm + every worker attempt), `DepartureProcessed`, `Reverted` (user-labelled false positive — the gold datum), `OrphanCleaned`, `GeofenceRegistration`.
7. **Replay harness** (`DetectionTraceReplayer` + `tools/trace2fixture`): every field bug becomes a permanent fixture against the REAL detector. First fixture: the BUG-REPARK-WALK-001 walking trace — asserts clean `aborted_false_enter`, no save, no prompt.

**Scenario matrix (post-redesign):**

| # | Scenario | Outcome |
|---|---|---|
| S1 | Park → walk away (the 2026-07-03 incident) | NOTHING — unverified arm, false-ENTER abort (replay-pinned) |
| S2 | Short-hop repark | speed→silent confirm; ENTER-only→prompt; no evidence→lost (accepted FN, rare) |
| S3 | Stroll near the car inside the radius | NOTHING |
| S4 | Bus/taxi/train beside a parked car | ENTER stamps the bus but nothing arms; enter-after-exit never verifies |
| S5 | Spurious ENTER while walking / GPS speed spike | enter-precedes-exit + accuracy gates (arm + in-session crossing) |
| S6 | Bike/scooter crossing 18 km/h | never auto-confirms — always prompts |
| S7 | GPS drift past the radius while parked | orphan-clean or unverified arm → guards abort |
| S8 | Real departure, late evidence | worker upgrade via `DepartureConfirmationListener` (verified_late) |
| S9 | OEM-kill | ENTER timestamp survives (prefs mirror); session+geofence durable |
| S10 | Reboot | immediate janitor restore (no 12 h blind window) |
| S11 | Multi-vehicle overlap | active-preferred attribution (known gap, accepted) |
| S12 | Prompt unanswered | 15-min response timeout (clock-tested) |

**Findings pinned during the work:**
- The scorer's STILL branches, `reliabilitySlowPath`, and the tests exercising them were dead/fake — purged (C1a). The real confirm surface is exactly **steps+egress or the user's tap**.
- The `vehicleExit+window+egress` decision branch is **structurally unreachable** end-to-end: `activityExit=true` routes the scorer to the fast path (ceiling Medium), so a Candidate only ever opens with `hadVehicleExit=false` (5-min window ⇒ steps required). The pure-function branch remains (unit-tested) but never fires in production — candidate for removal if a future sweep confirms no plans for it.
- Bus/train after REAL driving remains indistinguishable by design (drive+alight ≡ park+alight for every available sensor). Accepted; mitigated by the arm being anchored to the user's own parked-car geofence, the ENTER ordering rule, and `Reverted` telemetry to measure it.

**Deliberately deferred:** `WindowState` call-site consolidation in the decision engine (cosmetic; `elapsedSinceHighMs=0` hack documented); expanding the mismatch guard (wait for 2–4 weeks of `Reverted`+`armEvidence` field data); partial unique index / SQLite trigger (transaction + janitor sweep cover it); B4 veto ON (needs replay validation of the first-step timing window).

**Device validation protocol (the gate before trusting the redesign):**
1. Park for real, walk home past the radius → expect NOTHING (diagnostics: arm `self_observed`/pre-arm verdict `unverified`, outcome `aborted_false_enter`).
2. Drive away normally → expect verdict `verified_speed`, freed spot published, next park confirmed silently.
3. Short-hop repark → expect `verified_enter`/`verified_late` → silent confirm, or the "¿Has aparcado?" prompt on ENTER-only evidence.
4. Board a bus with the car parked (outside the radius) → expect the ENTER stamped but nothing armed, nothing saved.
5. Reboot with an active park → geofence restored immediately (janitor one-shot).
6. Upgrade install over Room v10 with an active session → session survives (MIGRATION_10_11).

### DET-ARRIVAL-DOUBLE-PIN-001 — safety-net backfill duplicated the live coordinator's arrival (2026-07-20, Redmi)

**Symptom.** One physical park produced **two** history pins ~96 m apart: `Calle Pantoque 2B` at 02:14 (reliability **0.5**) and `Avenida Rosa de los Vientos 35` at 02:17 (reliability 0.9). The 0.5 pin was a false positive; the 0.9 pin was the real spot.

**Diagnosis (Firestore `diagnostics` + `parkingHistory`).** Two independent pipelines confirmed the SAME arrival:
- The **live coordinator** (session `…442292`) armed 02:14:02, followed measured egress, and confirmed at the settled anchor (Rosa, 0.9) 02:17.
- The **15-min safety net** ran in the idle window between the previous session ending (02:11:37) and this one arming (02:14:02) — so `detectionRuntime.isRunning` was `false` at its tick. It saw far-from-old-anchor + a trusted step budget, dispatched the departure, and **chained `ParkingBackfillWorker`**, which placed a 0.5 pin at its coarse wake-up fix (Pantoque).

The safety-net worker already skips its whole check when detection is running (`isRunning` guard), but that guard is evaluated at the *tick*, not when the chained backfill actually *executes*. The live session armed 300 ms later, inside the race window, and the backfill never re-checked before writing.

**Fix.** `ParkingBackfillWorker.doWork()` now re-reads `DetectionRuntimeState.isRunning` at execution time and **defers** (skips the pin) when a live coordinator session is running — the same `isRunning` skip the safety-net worker uses, applied at the actual placement site. The departure was already dispatched before the chain, so the OLD spot is freed regardless; the live session (or, if it aborts, its mark-parking nudge) owns the NEW placement at full quality. This closes the "**BOTH** placers" gap the `DET-ARRIVAL-HANDOFF-001` invariant left open (it only guarded against "neither" — the orphaned arrival).

**Residual (accepted, low-risk).** The reverse ordering — the backfill fully completing *before* the live session arms — would still leave the replaced backfill pin in history when the live confirm runs. It requires the live arm to lag the same movement by more than the chained departure-worker duration, which the field ordering does not exhibit; deferred until data shows it.

**Not a code bug (device-side).** The Oppo false negatives the same night (whole 02:00 trip + the current park missed) were ColorOS OEM kill: sessions stamped `requiresOemBatteryFreeze=true`, `batteryUnrestricted=false`, `strategy=COORDINATOR` (no car BT). Remedy is setup — battery exemption + autostart, and pairing the car Bluetooth (the deterministic revive), not detection logic. The Redmi (`requiresOemBatteryFreeze=false`) survived and caught everything.

### DET-PIN-PROVENANCE-001 — every pin records which trigger placed it (2026-07-21)

**Why.** Diagnosing the 2026-07-20 double-pin required reverse-engineering that the phantom came from the safety-net backfill, by cross-referencing `detectionReliability=0.5` with session timing — the persisted pin carried no provenance, and `armEvidence` was local-only (never synced).

**What.** `UserParking` gains `detectionPath: String?` — the confirmation path that placed the pin: `steps+egress` / `kinematic+egress` / `vehicle-exit` / `unattended_timeout` / `user` (live coordinator, = the coordinator's `pathLabel`), `bt`, `manual`, or `safety_net_backfill`. It pairs with `armEvidence` (the arm trigger), which is now **also synced to Firestore** (was local-only). A pin now reads e.g. `path=steps+egress · arm=verified_enter` (real live confirm) vs `path=safety_net_backfill · arm=null` (the reconstructed backstop pin).

**Surface (DTO parity, all serializers).** Domain `UserParking` + Room `UserParkingEntity` (MIGRATION_12_13, additive `ALTER TABLE parking_sessions ADD COLUMN detectionPath TEXT`, schema v13) + both entity↔domain mappers + `ParkingHistoryDto` (adds `detectionPath` **and** `armEvidence`) + both DTO↔domain/entity mappers + the manual Firestore deserializer (`FIELD_DETECTION_PATH`/`FIELD_ARM_EVIDENCE`) + `SaveNewParkingSessionWorker` workData (`KEY_NEW_SESSION_DETECTION_PATH`/`_ARM_EVIDENCE`) + the LWW reconcile carry (`UserParkingReconcile`). `tripMaxSpeedMps` stays local-only. `ConfirmParkingUseCase` gains a `detectionPath` param; each call site passes its own (coordinator = `pathLabel`, BT = `bt`, manual = `manual`/`user`, backfill = `safety_net_backfill`). Legacy pins read `null` (pre-provenance). Pure observability — no detection decision changes.

### DET-HONEST-CLOSE-001 — approximate zone instead of silence when a trip is proven but the anchor isn't (2026-07-21)

**Why.** The two aborts that dominate the field (`aborted_false_enter` after a late-delivered EXIT; `aborted_no_movement` when the EXIT lands with the user already 1 km away on foot) end in **total silence** — discarding verified departure evidence the session already held, leaving the old pin stale and the car unpinned (audit 2026-07-14/15, El Puerto).

**What.** A pure ladder `EvaluateHonestCloseUseCase` (trip proof by step budget) + `RunHonestCloseUseCase` orchestration: when the car provably drove away from its last pin but there is no pin-grade anchor at the new spot, release the stale pin and open an **APPROXIMATE ZONE** — a `UserParking` with `zoneRadiusMeters` (rendered as a circle, never a deceptively precise pin) — then nudge the user to confirm/refine. Never silence. `UserParking.zoneRadiusMeters` + `isApproximate` getter, Room **v13→v14** (MIGRATION_13_14, additive `zoneRadiusMeters REAL`, local-only like `tripMaxSpeedMps`). The zone save stamps its provenance `detectionPath = closed_approximate_pin | closed_approximate_zone` (DET-PIN-PROVENANCE-001). Wired into `CoordinatorDetectionService` (seals the step budget at confirm) + `CoordinatorParkingDetector` (surfaces the abort outcome + last fix). ⏳ Pending: device field-test + the approximate-zone circle UI.

### DET-FGS-REAPER-001 — reap the ghost detection FGS when the OS freezes the process (2026-07-22)

**Why (field 2026-07-21, Oppo/ColorOS).** After a park, a late geofence EXIT armed a session that aborted `false_enter` in 12 s (the user was walking) — correct. But the **foreground-service detection notification** (`DETECTION_NOTIFICATION_ID`) stayed **glued ~2 h** showing "detection active" with nothing behind it, while telemetry went completely silent. The silence is the proof: ColorOS **froze** the process (freeze, not kill), so the service's teardown (`finally → DetectionEnded → stopForegroundAndSelf`) never ran and Android never reaped the notification. The only self-heal today is the sticky-restart null-intent strip (DET-B-02), which fires only if the OS *restarts* the service — a frozen-not-restarted process leaves the ghost. A hung FGS-LOCATION also **penalizes the app** (higher kill/battery-restriction priority), feeding the OEM-kill loop.

**What.** The safety net already detects the process-death signal (a `PendingDetectionStore` pending whose heartbeat went stale) in `checkStalePendingDetections()`. It now **also reaps the ghost**: `notificationPort.dismiss(DETECTION_NOTIFICATION_ID)`. The reap only ever DISMISSES an orphan notification — it never kills a process. A pure evaluator `shouldReapGhostDetectionFgs(isPeriodicTick, isDetectionRunning, hasStalePending)` gates it behind two independent locks that make it **impossible to touch a live session**:
- **`!isDetectionRunning`** — a live coordinator session sets the in-memory flag true at arm / false in its finally; a running session vetoes the reap (an old stale pending must never collateral-kill a new live session's FGS).
- **`SOURCE_PERIODIC` only** — the worker posts its OWN copy of the notification (via `getForegroundInfo`) only on EXPEDITED runs; the background periodic tick never does, so a live notification there is ALWAYS the ghost. Expedited runs already self-heal the ghost through WorkManager's foreground lifecycle, so they must not reap (dodging that collision).

Reap latency ≤ 15 min (periodic) vs the ~2 h observed. Silent by design (respects OEM-KILL-001 "earn the ask": no nudge when nothing was lost; the `shouldNudgeForStalePending` real-trip nudge is unchanged and independent). **F2 (teardown lean of the false trigger) was audited 2026-07-23 and closed without code changes** (`docs/backlog/det-fgs-reaper-001.md`): the `stopSelfResult` veto is correct by design (the field ghost was a process freeze, not a veto), `observeAdaptiveLocation` is already released when the coordinator returns before honest-close, and the `KeepSilent` honest-close does no heavy I/O. The only real lever — shortening the 4-min `maxNoMovementMs` window (which only bites the *stationary* misfire; the walking one already aborts fast via `falseEnterAbortSteps`) — trades against false negatives on slow-GPS real departures and is deferred until replay-harness + field data justify it. ⏳ Pending: build + device field-test (force a walking `false_enter`, confirm the ghost clears within 15 min without a nudge).

### DET-ENDED-VETO-RACE-001 — the teardown request no longer vetoes itself (2026-07-23)

**Why (field 2026-07-23, BOTH devices — not OEM).** A silent abort (`aborted_no_movement`) left the detection FGS notification glued **with the process alive** (safety-net running, pending correctly cleared — so the FGS reaper, keyed on the process-death signal, rightly never fired). Instrumented repro on the Redmi showed the smoking gun: the job's finally logged `■ finally → DetectionEnded(startId=1) → intake` and the `stopIfIdle(detection-ended)` line — which prints AFTER its job-active guard — **never appeared**. Mechanism: the finally sent `DetectionEnded` from *inside the still-active job*; on `Main.immediate` the channel resumes the intake consumer **inline within the `trySend`**, so `stopIfIdle` ran while the job was mid-finally (*Completing* → `isActive == true`), vetoed its own teardown, and — the intake being one-command-one-decision — nothing ever retried. Latent since DET-INTAKE-001; invisible in active use because any later intent (AR tick, EXIT, app open) tears down via its own epilogue; visible when the phone goes still after an abort (e.g. at home after an app-relaunch fence EXIT, whose 4-min doomed session is the accepted [DET-RIDE-PROOF-001] cost).

**What.** The `DetectionEnded` send moved out of the finally into `detectionJob.invokeOnCompletion` (registered at `startParkingDetection`): the runtime invokes it only once the job is in a **terminal** state, so even an inline-resumed `stopIfIdle` sees `isActive == false` and the stop proceeds. The identity guard (`detectionJob === startedJob`) preserves the supersede rule ([DETECT-SERVICE-RACE-001]); a newer in-flight intent still vetoes via the `stopSelfResult` startId mismatch, exactly as before. `setRunning(false)`, heartbeat cancel and pending clear stay in the finally (per-job state must flip immediately). Detail + repro script: `docs/backlog/det-ended-veto-race-001.md`.

### DET-STEP-BUDGET-ORIGIN-001 — the step budget and its displacement share ONE origin (2026-07-23)

**Why (field 2026-07-22 01:47, Redmi — the pin-inside-the-home FP).** After a real park at la Angelita (0.9, `steps+egress`) the user walked ~243 m home. A false trigger armed at home, aborted `no_movement`, and the honest close asked "did the car leave its pin?" — comparing **steps since the seal** against **distance from the PIN**. But the seal happens at CONFIRM time, mid-egress (~160 m from the pin): the egress walk was excluded from the count yet included in the distance, so the remaining ~85 m walk (110 steps) fell short of the 129 the pin distance demanded → "driven" → a 0.5 approximate pin planted INSIDE the user's home, deposing the real 0.9 park. Structural — a perfect counter falls the same way; exactly the BUG-WALK-DEPART-001 assertion rung 3 exists to prevent.

**What.** *A step baseline is a (counter, position, moment) triple; it is only comparable against a displacement measured from THAT position.* The seal now persists WHERE the body was when the counter was read (`DetectionStepAnchors.seal(geofenceId, sealPoint)` → `anchor_seal_pos_<id>`, atomic with the baseline; `stepsSinceSeal` returns `StepsSinceSeal(steps, sealPoint)`). The honest-close walk budget compares against `distance(sealPoint → abortFix)`; a seal without an origin (legacy) refuses the walked-vs-rode verdict → `KeepSilent` (the safety net stays the backstop). The too-close-to-be-a-trip guard remains pin-based on purpose (it asks about the CAR). `ConfirmParkingUseCase` takes `sealPoint` with NO default — every caller states where the body is: coordinator → `previousFix` (the real egress position), honest-close → `abortFix`, manual → the pin, BT → the walk-settling fix, backfill → null. The safety-net cure re-anchor writes its at-car fix as the seal position too (same storage contract). The safety-net *evaluator* is unchanged: its auto-release already requires `anchoredToCar` (cure seal, AT the car → same origin de facto) plus the absolute `maxBoardingSteps` cap. Detail + regression arithmetic: `docs/backlog/det-step-budget-origin-001.md`.

### DET-CONFIRM-FRESHNESS-001 — confirm evidence must still be true when the pin is planted (2026-07-24)

**Why (field 2026-07-23, Redmi — 2 FP + 1 FN in one evening, all with every prior fix installed).** Full forensics in `docs/backlog/det-confirm-freshness-001.md` and Firestore sessions `1784829489071` / `1784839287970` / `1784846935112`. Three failures, one broken invariant — *the decision and the pin were not bound to the same moment*:
1. **FP "Bodegas Osborne" (traffic light).** The anchor quick-froze at a 27-s light; the parking-search creep (6–16 km/h, GPS under-reading — never ≥ `minimumTripSpeedMps` with credible accuracy) could not move a frozen anchor, and the real egress walk then confirmed `kinematic+egress` AT the light, 160 m from the car. The creep ran with **zero steps on a live counter** — evidence nobody read.
2. **FP Calle Abeto (pick-up).** 19 incidental steps (all stopped) + a 127-m-accuracy drift fix 36 m out opened a tentative confirm; the departure's only rolling fix carried acc 71 m (> the 50-m trust gate) and GPS then starved for 95 s; the hold settled with the car at another light 570 m away and finalized **on the 2-minute-old snapshot**.
3. **FN Vista Hermosa.** Detection was RIGHT (locked anchor at the car, `steps+egress` at 01:09) but the final parking maneuver (pedestrian-band deceleration fixes, zero step events) had tainted the anchor *walk-entered* → degraded to a 1 AM prompt, and 15 min later the same false taint refused the unattended save.

**What — one invariant, three seams.** *A live step counter's silence during measured movement is evidence of the CAR (a mute counter's silence is noise), and a confirm's evidence is re-validated at pin time.*
- **Hold settle re-validation** (§1 DET-C-02 note): auto-settle discards when the position outran the counted steps from the held pin (`HOLD_STALE_DISCARDED`); the same walk-reach ceiling now guards the unattended timeout save (`aborted_unattended_vehicular_egress` → nudge).
- **Stepless departure unfreezes the anchor.** New state `sessionSawSteps` + `pinnedSteplessMovingFixes`: with a PINNED anchor and a proven-alive counter counting nothing for the stop, fixes at ≥ `clearBestStopSpeedMps` provably outside the anchor envelopes accumulate; at `frozenAnchorSteplessDepartureFixes = 4` the movement resolves as CAR and the anchor clears — the real stop re-captures it. Any step event resets the run; a mute counter can never trip it (Camelias-Oppo laundering stays impossible).
- **Walk-entered taint requires corroboration, with a bounded maneuver exemption.** The taint's inputs are stamped at capture (`anchorStepEventsAtCapture` — raw step EVENTS since driving, not the gated `stepCount` — and `anchorSawStepsAtCapture`). A stop entered with zero step events on an alive counter within `maneuverEntryMaxWalkFixes = 8` pedestrian-band fixes is a parking GLIDE, not a walk-in → no taint → silent confirm. The length cap is load-bearing: the Camelias-Oppo counter was alive earlier and went mute exactly for its ~13-fix walk back, so step silence is only trusted across short stretches (the replay fixture enforces this).

Regression fixtures: 5 new coordinator tests replaying the three field traces plus the Camelias mute-walk and live-counter walk-in controls; full suite green (945).

### DET-NUDGE-PERSIST-001 — the "where did you leave your car?" ask survives as app state (2026-07-25)

**Why (field 2026-07-25, Redmi — session `1784939810210`).** The farmacia→home drive fell entirely
inside a MIUI freeze. On wake the coordinator did everything right — processed the real EXIT,
released and published the freed spot, refused to guess the new pin (walk-entered anchor) and
posted the mark-parking nudge at 03:11 — but the **notification was the only trace of the
question**. The user slept through it; the next morning Home said "no car parked" with zero
recovery path. The session was lost not by detection but by an ephemeral ask.

**What.** *Every mark-parking ask exists twice: as the notification AND as a durable
`PendingParkNudge` the Home banner renders until answered.* The invariant lives in the single
choke point every ask path already goes through — `AppNotificationManager.showMarkParkingNudge()`
— whose Android impl persists (slot único in `AppPreferences`, DataStore) BEFORE notifying.
Resolution from any direction clears both surfaces: any confirmed parking
(`ConfirmParkingUseCase`), a session for the nudged vehicle reappearing (Home's reactive janitor —
covers sync restores and confirm/clear races), or the banner's explicit "Descartar"
(`ClearParkNudgeUseCase` → record + notification 2008). The honest close opts out
(`persistPending = false`): its approximate pin/zone IS its durable trace, and a pending record
would resurface as a ghost "car lost" banner after that session's normal release. Detail:
`docs/backlog/det-nudge-persist-001.md`.

### DET-ZOMBIE-PROBE-001 — short no-movement probe for stale-delivered EXITs (2026-07-25)

**Why (field 2026-07-24/25).** Every zombie EXIT delivery (the OS holds the event for hours and
hands it over with the phone parked at home) armed the coordinator and burned the full
`maxNoMovementMs` — 4.1 min of GPS, 33–58 fixes — before folding `aborted_no_movement` (Oppo:
22:25, 00:00 and 15:44; Samsung: 22:42). That guaranteed per-event cost feeds the OEM's
power-abuse scoring, which freezes the app harder — the vicious circle the field test surfaced.

**What.** *Physics splits the far-delivery cases: a real mid-drive exit is delivered far because
the car is MOVING — its first credible fixes show driving speed (and verified evidence seeds the
session past the guard entirely); a zombie delivery is stationary from the first fix and can
never satisfy the guard.* Sessions armed from the stale (far-delivered) lane run the no-movement
guard on `staleExitNoMovementMs` (75 s — GPS warm-up + margin) instead of 4 min
(`CoordinatorParkingDetector.invoke(staleExitDelivery = true)`, flag born at the service's
stale-lane arm). Same outcome label, honest-close and tooling untouched; boundary/manual/AR arms
keep the full budget. Accepted residual: an unverified real far exit landing during a ≥75-s stop
aborts the live session early — the departure worker's speed-sampled retries and the reconcile
backfill still cover the release and the arrival pin. Detail: `docs/backlog/det-zombie-probe-001.md`.

### DET-FROZEN-COUNTER-001 — a step budget is only evidence while its counter is provably ALIVE; an unattended timeout keeps the park as a bounded zone (2026-07-26)

**Why (field 2026-07-25/26, Jerez — 1 FP + 1 FN in one dinner, full forensics in
`docs/backlog/det-frozen-counter-001.md`).**
1. **FP restaurante (Redmi 22:29, `closed_approximate_pin` Avenida JMC).** After a correct
   `steps+egress` pin at Calle Cobre, the 150-m walk to the restaurant tripped the own-fence EXIT;
   the session aborted `false_enter` correctly (8 detector steps, 0 driving fixes) — and the
   honest close then "proved" a ride because the MIUI **cumulative counter was FROZEN in
   background** (delta ≈ 0, non-zero cached value → passes the mute-only guard) and planted an
   approximate pin inside the restaurant, deposing the 6-minute-old correct pin. The Oppo beside
   it, counter alive, stayed correctly silent — the differential proof.
2. **FN vuelta a casa (Redmi 00:17–00:50, `aborted_unattended_egress_mismatch`).** 92 driving
   fixes, prompt at ~00:35 unanswered, and at the 15-min timeout the egress-mismatch guard
   degraded to a nudge nobody saw: departure released the old spots and NOTHING was saved — the
   user's car ended pinless after a fully-measured 33-min drive.

**What — one invariant, plus a bounded-zone fallback and full trace visibility.**
- ***The trip-proof step budget is only admissible while the counter is provably alive.***
  `EvaluateHonestCloseUseCase` now receives the aborting session's own evidence: its step-DETECTOR
  count is the liveness witness (a live cumulative delta can never be below what the detector saw
  in a shorter window → below it = FROZEN → treated as mute → silence), and measured driving
  speed outranks the inference outright (defensively unreachable today, decisive for future
  callers). The evaluator returns a `HonestCloseVerdict` — decision + reason + every number.
- ***An unattended timeout may only refuse the exact pin, not the park — when the doubt is
  BOUNDABLE it saves an approximate ZONE (reliability 0.5, never community-facing) instead of
  nudge-only.*** `unpinned_anchor` (live counter: radius bounds the walk via steps × stride),
  `egress_mismatch` (radius covers birth↔anchor; center follows counter liveness — live → egress
  birth, mute → frozen anchor), `walk_entered_anchor` (live counter: walked-in bound). Unbounded
  cases (mute-counter unpinned/walk-entered, `vehicular_egress`, `unattended_no_drive`) keep the
  nudge-only exit — a zone that cannot promise to contain the car is a lie. (The no-drive case
  later earned its own bounded lane behind a triple conjunction — see DET-NODRIVE-ZONE-001.)
  Radius ∈
  [`honestCloseMinZoneRadiusMeters`, `unattendedZoneMaxRadiusMeters` 250 m]. Outcomes:
  `confirmed_unattended_zone_<reason>`, provenance `detectionPath = unattended_zone_<reason>`.
- ***No mute zones in the trace.*** New `HONEST_CLOSE` diagnostics event (verdict, reason,
  pin-distance, walk-distance, steps delta/required, session steps, session vmax, zone radius)
  logged under the aborted session's id; `PROMPT_SHOWN` Decision at BOTH prompt lanes (the 00:35
  prompt was invisible in forensics); `Decision` events carry `distanceMeters`/`radiusMeters` for
  the spatial guards. DTO columns are additive/nullable (wire-tolerant).

Regression fixtures: frozen-counter (restaurant) + liveness-absent control + measured-driving in
the evaluator; frozen-counter orchestration in `RunHonestCloseUseCaseTest`; unpinned-zone and
egress-mismatch-zone (Enamorados replay) + mute-walk control in the coordinator suite. Full suite
green (954). ⏳ Pending: device field-test; the approximate-zone circle UI remains pending from
DET-HONEST-CLOSE-001.

### DET-WALK-FLOOR-001 — the step budget needs distance to mean anything, and inference never deposes a user-asserted pin (2026-07-27)

**Why (field 2026-07-26 20:28, Oppo/Glorieta Juan de Austria — first night on the
DET-FROZEN-COUNTER build; full forensics in `docs/backlog/det-walk-floor-001.md`).** The user
hand-placed a manual pin on the car from ~90 m away; 12 minutes later a false-ENTER abort ran the
honest close. The new `HONEST_CLOSE` trace showed every number ("no mute zones" paid off on night
one): pin→abort 100.4 m (passes the pin-distance trip floor), seal→abort **31.8 m**, required
steps **17**, live cumulative delta **16** (liveness check passed, 16 ≥ 13 detector steps) →
`trip_proven` on a ONE-STEP margin → the correct manual pin was released and re-planted 100 m
away at the walker's position. Knock-on: the displaced geofence sat ~100 m from the real car for
the next departure.

**What — two admissibility gates in `EvaluateHonestCloseUseCase`, no new machinery.**
- ***Walk floor.*** The budget shortfall only proves a ride when the budget is statistically
  meaningful: if the SEAL-origin displacement is within the accuracy envelopes plus
  `honestCloseMinTripMeters` (same bar the too-close guard applies to the pin), the required
  count sits inside the counter's quantization noise → `KeepSilent(walk_too_short)`. The pin
  distance and the body displacement diverge legitimately (a pin placed on the map from afar),
  so the floor is checked on the budget's OWN origin — the DET-STEP-BUDGET-ORIGIN principle,
  completed.
- ***User-asserted pin shield.*** A stale pin with `detectionReliability ≥
  reliabilityUserConfirmed` (stamped 1.0 only by hand-placement or the "yes, parked" tap; BT and
  vehicle-exit sit strictly below by config invariant) is an ASSERTION, and the step budget is an
  INFERENCE — inference never deposes assertion → `KeepSilent(user_asserted_pin)`. Only measured
  driving (`session_measured_driving`, evaluated before the shield) may release such a pin; the
  safety net remains the backstop if the car truly left.

Both reasons flow through the existing `HonestCloseVerdict` → `HONEST_CLOSE` telemetry (string
column, no wire change). Regression fixtures: Glorieta one-step-margin with an AUTO pin (floor on
trial) and with the manual pin (shield on trial), plus a measured-driving release of a
user-asserted pin (assertion is outranked only by measurement).

### DET-DRIVE-PROOF-001 — "measured driving" must be a TRACK, not a Doppler number (2026-07-27)

**Why (field 2026-07-27 14:56, Oppo AT HOME, stationary indoors — session `1785157018067`; full
forensics in `docs/backlog/det-drive-proof-001.md`).** A 10-second GPS mirage claimed 45 m/s
(162 km/h) at declared accuracy 5 m from 216 m away, exited the geofence (arming with
`dep=verified_speed` off the same phantom Doppler), and — because `maxSpeedMps` recorded ANY
credible-accuracy fix — satisfied `sessionSawDriving` for the whole session on that single fix.
Indoor drift then froze the anchor (the seeded arm let it pass as drive-entered), accumulated
pedestrian-band "kinematic egress" fixes past the 18-m displacement floor, and at 15:04 the
session **CONFIRMED `kinematic+egress` 0.85 with 1 step: a pin in the living room**. The
kinematic path is deliberately exempt from the pedestrian ceiling and the step proof — its only
defence is `sessionSawDriving`, and that gate was one fix wide. Three other false arms the same
weekend all aborted correctly; the system only fell when the glitch carried *speed*.

**What — the session speed statistic only turns on when the TRACK proves a drive
(`CoordinatorParkingDetector.corroboratesDrive`).** `maxSpeedMps` stays ZERO until `driveProven`
latches; then the accumulated credible peak (`pendingMaxSpeedMps`) promotes, so proven sessions
report the same vmax as before. Proof is judged at every credible driving-speed fix against a
look-back fix aged `driveProofWindowMinMs..MaxMs` (20–60 s): net displacement beyond both
accuracy envelopes + the hop pathology margin, ≥ `minimumTripDistanceMeters` (150 m — trip
ground, not drift), window rate ≤ `sustainedDepartureMaxRateMps` (cache teleports claim absurd
rates), and **in-window progression** — every late-half fix must already sit ≥ 25 % of the
displacement from the look-back position. The mirage signature is *flat-then-jump* (every
in-window fix at home, all "movement" in the burst); a real drive progresses through its window.
Everything downstream inherits without change: the evaluator's `sessionSawDriving` (kinematic
gate + weak-evidence policy + scooter mismatch), the unattended-save `measuredDriving` gate, the
honest-close zone, the persisted `tripMaxSpeedMps`, the enter-arm step veto. Arm seeding and
session lifecycle (`hasEverReachedDrivingSpeed`) are untouched: the event NOMINATES, only
corroborated movement CONFIRMS.

**Why per-hop corroboration was rejected.** The replay harness vetoed the obvious design twice:
Calle Gavia (a CORRECT detection) drives on ONE 36-s hop of 255 m — any per-hop Δt cap or
hop-count floor kills it; the MIUI-starved Enamorados leg never escapes joint per-hop accuracy
envelopes (~50 m real hops vs ~60 m of stacked noise) yet proves itself over 25-s windows of
~200 m. The Firestore session summary (`vmax`/`drivingFixes`) is computed from raw fixes in
`FirestoreDetectionEventLogger` and still shows the mirage — forensics keep the glitch visible
even though the algorithm no longer believes it.

### DET-DEPART-PROOF-001 — the freed-spot release needs a fix INDEPENDENT of the EXIT (2026-07-27)

**Why (field 2026-07-27 18:30, Oppo AT HOME again, stationary — session `1785169816161`; full
forensics in `docs/backlog/det-depart-proof-001.md`).** The phantom pin from the 14:56 incident
was still an active session with a live fence around the living room. A single indoor mirage fix
(4 m/s = 14.4 km/h, acc 21.5 m, 121 m away) broke that fence; the departure check sampled the
SAME cached fix 140 ms later, `isCredibleDrivingSpeed` passed (≥ 10 km/h, acc ≤ 50 m), and a
**phantom freed spot was published to the community at the user's home** (conf 0.85 inherited
from the phantom session, 2-h TTL). One fix both FIRED the exit and "confirmed" it. The
detection side held: DET-DRIVE-PROOF-001 saw no corroborated driving and degraded to
`PROMPT_SHOWN 0.55` — first field validation of that fix — but spot publication was the second
authority still trusting a lone Doppler sample.

**What — an independence gap on the confirming speed sample (`departureProofMinGapMs`, 20 s).**
In `DetectParkingDepartureUseCase`, a credible driving-speed sample only counts as
`speedConfirmsMovement` when its timestamp postdates the exit event by ≥ 20 s — i.e. it is a
genuinely NEW measurement, not the trigger's cache echo. A real driver is still at speed on the
worker's retry ladder (~15/45/105 s), so a real departure confirms one retry later (~45 s to
publish); observed mirage bursts die within ~10 s (the 14:56 burst still carried credible
Doppler at +9 s — 20 s buys 2× margin) and the stationary follow-up samples leave every attempt
`Inconclusive` → `Dismissed`, nothing published, session intact. The gate sits in the ONE
decision all non-preconfirmed release authorities converge on: boundary EXITs, stale EXITs, and
the safety-net's `DispatchDeparture(preconfirmed=false)`. Preconfirmed dispatches (step-budget
proof) carry their own physics and skip it. Rejected echoes stamp
`DepartureVerdict = Inconclusive(exit_echo)` so field telemetry separates "no evidence yet"
from "credible speed rejected as the exit's own fix". Pre-arm `verified_speed`
(`VerifyDepartureEvidenceUseCase`) stays one-fix on purpose: arming is a nomination with no
public side-effect, and the pin is guarded downstream by DET-DRIVE-PROOF-001.

### DET-NODRIVE-ZONE-001 — a no-drive timeout with live, bounded egress keeps the park as a zone (2026-07-27)

**Why (field 2026-07-27 20:36, Redmi — session `1785177396935`; full forensics in
`docs/backlog/det-nodrive-zone-001.md`).** A real 4.3 km drive home from a just-confirmed park —
and MIUI delivered the previous spot's GEOFENCE_EXIT **4 110 m late**, with the car already
arriving. The session was born after the trip: its only "driving" was a 3-fix burst (vmax
25 km/h, the final approach) that `corroboratesDrive` rightly never latches on (no look-back
window exists — DET-DRIVE-PROOF-001, shipped the same day, doing its job). Steps+egress degraded
to a prompt (correct, and it DID show — user-confirmed), the prompt went unanswered for the
15-min window, and the timeout took the `unattended_no_drive` branch: nudge-only,
`aborted_unattended_no_drive` — a REAL park lost with 176 live egress steps, an AR
`IN_VEHICLE→EXIT` and a locked kerb anchor on the table. The same-morning mirage (14:56: indoor
drift, ONE step, no AR exit) is what forged the nudge-only rule — but the two sessions are
separable by evidence the branch never read.

**What — extend the DET-FROZEN-COUNTER-001 bounded-zone ladder to the no-drive branch, behind a
triple conjunction.** Before exiting nudge-only, the `unattended_no_drive` branch now attempts
`saveUnattendedZone("no_drive_egress")` centered on the anchor iff ALL of:
1. **anchor exists** (`bestStopLocation` — where the fixes settled before the first step);
2. **live egress at human scale**: `sessionSawSteps` ∧ `stepCount ≥ anchorLockEgressSteps` ∧
   walked displacement anchor→current ≥ `minEgressDisplacementMeters` (18 m) — a live counter
   bounds the walk from the car, a real displacement proves the body left the anchor (the mirage
   dies here: 1 step);
3. **in-session vehicular signal**: `vehicleExitConfirmed` (AR vehicle-exit) ∨
   `pendingMaxSpeedMps ≥ minimumTripSpeedMps` (a credible raw driving fix the track could not
   corroborate) — something beyond the arm ties the walk to a drive (a stale seeded arm plus a
   stroll dies here).
Radius = max(anchor→current, steps × stride), clamped to
[`honestCloseMinZoneRadiusMeters`, `unattendedZoneMaxRadiusMeters`] — the walk bound keeps the
car inside the promise. Reliability `reliabilityUnattendedSave`, provenance
`unattended_zone_no_drive_egress`, outcome `confirmed_unattended_zone_no_drive_egress`; any
failed candado or failed save falls back to the existing nudge. Accepted residual (documented in
the ticket): a bus/taxi ride home after a late EXIT passes the conjunction and plants a
low-reliability zone at the drop-off — the saved-parking card is the ask and one tap corrects
it; the alternative (nudge-only) provably loses real parks. Replay fixture
`Trace_RedmiLateExitHome001` (1:1 from the 279 diagnostics events) pins the zone; unit guards
pin the mirage and the no-vehicular-signal stroll to nudge-only.

### DET-GAP-ANCHOR-001 — the anchor needs a witnessed arrival at rest, not an orphan fix after a GPS hole (2026-07-29)

**Why (field 2026-07-29 05:24, Redmi — session `1785294055249`; full forensics in
`docs/backlog/det-gap-anchor-001.md`; first seen field 2026-07-26, 72 m error).** Driving home
at night, the last moving fix (61 km/h, acc 44 m) was followed by a **100-s MIUI hole**, then
ONE speed-0 fix mid-route — and nothing else. The stop opened on that orphan fix, the anchor
bound to it, the egress walk home satisfied steps+egress, and a reliability-0.9 pin landed
**315 m before the real park**, at a point the car merely drove past. The Oppo on the same trip
(healthy stream, 64 driving fixes) pinned the real spot at 1.7 m accuracy — the only difference
was the hole.

**What — the symmetric twin of DET-DRIVE-PROOF-001: driving needs a corroborated track, and the
ANCHOR needs a witnessed deceleration to rest.** A stop whose opening fix arrives >
`anchorGapMaxFixGapMs` (45 s) after a `previousFix` still at ≥ `minimumTripSpeedMps` taints any
anchor bound to it as GAP-ENTERED (speed-only on the pre-gap fix on purpose: Doppler stays
credible at accuracies that would fail the driving-accuracy bar, and requiring accuracy would
exempt exactly the degraded streams that produce the hole). Same class as the walk-entered
taint: the proofs hold, the ANCHOR doesn't — `EvaluateParkingDecisionUseCase` degrades every
auto-confirm path to `Prompt`; the unattended timeout exits **nudge-only**
(`aborted_unattended_gap_anchor` — unlike walk-entered the forward error is unboundable, the car
may have driven arbitrarily far into the hole, so no zone is honest); a user "Sí" anchors at
the user's current stop (unconditionally — a gap-entered anchor is excluded from the
DET-CONFIRM-ANCHOR-001 re-anchor). Normal cadence (stop opens ≤ 45 s after the last driving fix)
is untouched — the control replay confirms silently exactly as before.

### DET-TRIP-WITNESS-001 — the honest-close step budget EXPIRES: a stale seal cannot prove a trip (2026-08-04)

**Why (field 2026-07-30 17:53, Redmi — session `1785426554477`, parkingHistory `00d513ed`; full
forensics in `docs/backlog/det-trip-witness-001.md`).** 16 hours after the real Angelita park
(01:47), MIUI delivered an **EXIT echo** of that fence to a phone sitting at HOME. The session
measured nothing (25 fixes at rest, 0 steps, 0 driving) and aborted `no_movement` — but the
honest close then read the cumulative counter's delta since the seal as **0 over last night's
~200 m walk home** (frozen through sleep + process deaths) and, with `sessionStepEvents = 0`,
the DET-FROZEN-COUNTER liveness cross-check had **no witness**. "198 m without steps" became
`trip_proven`: the correct pin was released and an approximate pin planted on the user's home.
The night before (29-07 23:49) the SAME stimulus stayed silent because that session witnessed
7 detector steps → `frozen_counter`; the opener was the witness going blind, not any code change.

**What.** *The step budget is only interpretable within a window where the cumulative counter is
trustworthy — and a trip is never provable from a delta spanning hours.* The seal is now a
(counter, position, **moment**) triple: both sealers (`AndroidDetectionStepAnchors.seal`, the
safety-net cure's `writeAnchorSteps`) stamp `anchor_seal_at_<id>` atomically with the baseline,
and `StepsSinceSeal` carries `sealedAtMs`. `EvaluateHonestCloseUseCase` takes `sealAgeMs`
(no default — every caller states it) and refuses the whole step-budget inference with
`KeepSilent`/`stale_seal` when the age exceeds `honestCloseMaxSealAgeMs` (2 h) **or is unknown**
(legacy undated seal — indistinguishable from old). The legit closes the ladder exists for
(Camelias hop, D2 return) all abort MINUTES after their real trip; measured session driving
(`session_measured_driving`) sits above the gate and is untouched — it needs no counter.
Residual: a real EXIT delivered > 2 h late now stays silent instead of leaving a zone; the
15-min safety net remains the declared backstop for that class (asymmetric-failure doctrine).

### DET-BACKFILL-TAINT-001 — the safety net cannot re-decide an arrival the coordinator resolved as nudge-only (2026-08-04)

**Why (field 2026-07-30 20:42, Redmi — session `1785434857650`, parkingHistory `57ea4afe`; full
forensics in `docs/backlog/det-backfill-taint-001.md`).** A real trip to Jerez ended with a
MIUI-holed stream → GAP-ANCHOR vetoed the pin: `aborted_unattended_gap_anchor`, nudge shown,
NO pin — "the forward error is unboundable, no place is honest". One minute later the 15-min
net's departure chain reached `ParkingBackfillWorker`, which only checks the live-session guard
(`isRunning`, DET-ARRIVAL-DOUBLE-PIN-001) — the coordinator's RESOLUTION died with the session
state — and it planted a `safety_net_backfill` 0.5 pin at the wake fix anyway. It landed right
by luck (short hole); over a 2 km hole it lands 2 km wrong with the same confidence. Two
deciders, one arrival; the second, blinder one contradicted the first.

**What.** *An arrival the coordinator resolved (nudge-only, GAP-ENTERED anchor) cannot be
re-decided by the safety net.* The service teardown stamps the resolution to the safety net's
own prefs (`arrival_resolution_at` + `arrival_resolution_pos` — one slot, latest wins, survives
process death) when the outcome is `aborted_unattended_gap_anchor`. `ParkingBackfillWorker`
consults the pure `EvaluateBackfillDeferralUseCase` before placing: stamp fresh
(`arrivalResolutionWindowMs`, 20 min — the field gap is ~1 min; the next trip's legit backfill
at +37 min falls outside) AND fix within `arrivalResolutionMatchRadiusMeters` (500 m, same
arrival) → skip placement, log `BACKFILL_DEFERRED_TO_NUDGE` (Decision event, system bucket).
The nudge remains the honest exit and the Home CTA the manual path. Untouched: the departure
chain (the OLD spot is still freed), the net's cure/reseal, the `isRunning` guard, and every
backfill without a prior resolution (the legit 2026-07-06 class). Residual: a genuinely new
arrival within 500 m AND 20 min of a resolved one defers too — bounded, and the nudge is
already on screen for exactly that neighborhood. This closes the face DET-ARRIVAL-DOUBLE-PIN-001
left open: that guard closed "both place"; this one closes "one vetoed, the other places".

### DET-EXACT-HEARTBEAT-001 — exact-alarm polling net while parked (2026-07-30)

**Why (Driversnote decompile + field).** Our reconciliation net wakes on events (AR / geofence /
significant motion — all needing Play Services to deliver into a possibly-dead process) with the
15-min WorkManager periodic as the only clock — and Doze batches that periodic into maintenance
windows. The field FNs live exactly there: no event delivered, worker hours late. Driversnote's
decompiled config runs a 300-s AlarmManager exact heartbeat with `SCHEDULE_EXACT_ALARM`: a
notification-less tick that polls "did we move away from the anchor?" — their crutch for living
without a resident FGS.

**What — a TRIGGER, not a second brain.** `ExactHeartbeatScheduler` arms a one-shot
`setExactAndAllowWhileIdle` (~5 min) whose receiver re-arms the chain and enqueues the standard
`ParkingSafetyNetWorker.enqueueCheckNow(source="exact-alarm")` — the same evaluator, anchors,
step-budget and pedestrian-physics proofs as every other source; waking never confirms anything.
Lifecycle mirrors the significant-motion sync: every worker tick calls
`ExactHeartbeatScheduler.sync(parkedAndIdle)` — arm/re-arm/disarm live in ONE place, self-heal
through process kills, and the first arm after a park is seeded by the existing check-now mesh
(detection-end / bt-park / app-start / boot) with zero new call sites. `SCHEDULE_EXACT_ALARM` is
auto-granted up to targetSdk 32 and user-revocable 33+: the scheduler gates on
`canScheduleExactAlarms()` and degrades to inexact `setAndAllowWhileIdle` (still Doze-piercing).
The scheduled→fired delta is persisted per tick — THE per-device metric of real Doze stretch.
Honest residual: force-stop (OEM deep-kill) cancels alarms like everything else; this net's
terrain is Doze + cached-kill + starved Play Services, where the worker used to arrive late.
Converts "AR never fired and the process was dead" from a lost departure into a ≤5-15 min delay —
which DET-ROUTE-ORIGIN-001's backdated origin then hides from the drawn route entirely.

### DET-JAM-WINDOW-001 — recent creep extends the no-movement budget; a jam at the exit no longer folds the session (2026-07-30)

**Why (piece 3 of the Driversnote plan).** `maxNoMovementMs` (4 min) silently folds any session
that never reaches driving speed — correct against spurious arms (ghost AR at home, zombie
EXITs), but it also killed the innocent cohort: leaving the spot into stop-go traffic or a long
red light, crawling below 18 km/h past the 4-min mark. The post-drive half of "abort→prompt"
already exists (DET-NODRIVE-ZONE / DET-GAP-ANCHOR unattended paths); this covers the PRE-drive
hole.

**What — RECENT creep is the discriminator.** A rolling 2-min window of credible fixes
(accuracy ≤ 50 m) measures displacement oldest→newest. A jam KEEPS advancing ≥ 30 m per window;
a walker who came to rest (replay `Trace_LateExitOnFoot001`: 42 m walk tail, then 3.5 min still
— the fixture that killed the naive origin-displacement design) and a zombie arm at home both
show ~zero recent creep. Recent creep at the 4-min check extends the watch to 10 min
(Driversnote's stopTimeout spirit); the creep stopping folds it ~one window later; the 10-min
ceiling folds with the distinct outcome `aborted_no_movement_jam` + a `NO_MOVEMENT_JAM_FOLD`
decision event so field data can size the cohort before deciding whether it deserves a nudge
(deliberately NO prompt in v1 — never nag a driver mid-jam, never resurrect the nightly
spurious-arm nag of 2026-07-24/25). The stale/zombie 75-s probe NEVER extends. Watching longer
confirms nothing — the confirmation ladder is untouched.

### DET-DRIVE-FILTER-001 — driving distanceFilter CLOSED by analysis (2026-07-30)

Driversnote can mute GPS at stops because their stop detection is Motion-API-based. Ours is the
deliberate inverse: HIGH_ACCURACY 5-s fixes while slow/stopped ARE the parking detector
(Candidate opening, anchor capture, kinematic egress), and in BALANCED (>18 km/h, 30-s beats
covering >150 m) a 20-m filter suppresses nothing. A distance filter would blind the stop
transition itself — the intended wins are already delivered elsewhere (SENTRY GPS-off parked,
MapTrail decimation + street-routed matcher for the trace, DET-JAM-WINDOW-001 for jams). Reopen
only if stop detection ever moves off GPS.

### DET-ROUTE-ORIGIN-001 — the trip's route is born at the parked spot, not at the first live fix (2026-07-30)

**Why (user field observation 2026-07-30 + Driversnote/Transistor analysis).** When detection
wakes mid-trip (AR/geofence latency, dead process), Home drew the route from wherever fixes
started appearing — trips visually born 0.5–4 km from the real departure. Driversnote solves
this without waking earlier: the trip origin is **backdated** to the stored stationary anchor
and the gap is map-matched onto streets. We hold a better anchor than they do: the departing
session's CONFIRMED parked location, already resolved by the service into `TripContext`
(geofence-exit / AR-enter / sentry-wake all pass `session.location`) and carried to the UI on
`DetectionReadiness.Monitoring.departurePoint` — Home just ignored it for the trail.

**What — presentation-only seeding in `HomeTripController`.** A NEW trip (empty trail) with a
service-resolved `departurePoint` prepends it as the trail's first point; `TripUpdate.departurePoint`
keeps being `trail.firstOrNull()`, which now IS the parked spot. The existing ROUTE-SNAP-001
map-matcher snaps the parking→first-fix chord onto streets. Plausibility ceiling
`MAX_BACKDATED_ORIGIN_METERS` (5 km): beyond it the session is presumed stale and the trip falls
back to first-fix origin (better a short route than an invented one). The seed is injected ONCE
per trip (a mid-trip supersede never rewrites the drawn route) and exists ONLY in the assembled
`TripUpdate`: the decision pipeline (`EvaluateParkingDecisionUseCase`, step budgets, egress,
`TripTrailImpl` forensics) reads measured fixes upstream and is untouched — zero detection-side
code changed. The wake-lateness gap (anchor→first-fix meters) is logged per trip: it is THE
metric of how late we woke. Cosmetic rider: trail polyline width 14→20 px (navigation-app
weight; residual jitter reads as a confident line). Inverts the DRIVE-PUCK-NATIVE-001 "origin =
first measured fix" test lock, with the map-matcher + ceiling making the chord honest.

**v2 rider — the gap is ROUTED along streets, not chorded.** `TrailMapMatcher` v1 snapped each
point to the nearest road but drew a straight chord between distant points — the backdated
parking→first-fix stretch (and OEM GPS holes) would cut across blocks. v2: consecutive snapped
points > 60 m apart are filled by A* over the fetched OSM ways (joined at shared nodes), accepted
only when the road path is ≤ 3× the straight distance (no invented scenic detours); a
disconnected/implausible graph keeps the honest straight chord. Pure commonMain, runs off the main
thread inside the existing debounced pipeline.

### ROUTE-LINE-CLEAN-001 — noisy fixes are corrected onto the followed street, spikes dropped (2026-08-06)

**Why (user field screenshots 2026-07-30, taken WITH v1 active).** The drawn route still wobbled
off the street: v1's per-point nearest-road snap kept any fix > 30 m from a road raw (a visible
spike bending the line) and could flip a single noisy fix onto a parallel street. Visual-only
noise, but it reads as a broken route.

**What — `TrailMapMatcher` v3, same single seam.** (a) Snapping is continuity-aware: a small
Viterbi over per-way candidates (≤ 6 nearest ways within `MAX_SNAP_METERS`, raised 30→60 m for
urban multipath) minimises fix→street distance + `TRANSITION_WEIGHT` × |snapped step − measured
step| — a parallel street adds travel the GPS never measured, so the trip stays on the street it
is actually following, while a genuine corner (shared node, snapped step ≈ measured step) costs
nothing. (b) Off-road runs of ≤ `MAX_OUTLIER_RUN` (2) fixes between on-road neighbours are
DROPPED — the existing v2 gap-fill routes the hole along streets when it is long. Off-road trail
ends (backdated origin in a car park) and longer runs are kept raw: no street there, inventing one
would lie. Cosmetic rider: trail polyline width 20→28 px. Pure commonMain; the drawing pipeline,
decimation and detection evidence are untouched.

### ROUTE-LINE-PRO-001 — the drawn line is the street geometry of the most likely route (2026-08-07)

**Why.** v1–v3 corrected the FIXES and joined them with chords — the line was still "fixed-up GPS
points". The industry standard (OSRM `match`, Valhalla/Meili, Newson & Krumm 2009) instead decides
the most likely ROUTE along the road graph and draws the road's own geometry. The visible
difference is every bend: two fixes either side of a corner used to be joined by a straight cut
across the block (v2's gap-fill only routed holes > 60 m); now the line turns AT the corner,
always, because every transition between matched points is routed.

**What — `TrailMapMatcher` v4, full HMM, same single seam.** Trail points are decimated to
`MATCH_SPACING_METERS` (25 m — a transition only discriminates streets when the measured step is
large vs GPS noise; the routed geometry restores the skipped detail). Per measurement the
candidates are its projections onto road edges within `MAX_SNAP_METERS` (60 m, max 4, 5 m dedupe);
Viterbi minimises Gaussian emission (`EMISSION_SIGMA_METERS` 10 — urban phone traces) plus the
Newson–Krumm transition |routed − straight| / `TRANSITION_BETA_METERS` (3, Valhalla's default),
with real bounded-Dijkstra routing over the `RoadGraph` (ways joined at shared OSM nodes; bound =
3× straight + 120 m slack; one Dijkstra per previous-layer edge endpoint shared across pairs). The
output concatenates the winning road paths, so the line follows bends and roundabouts even when
every fix is metres off the street, and a parallel street connected only at the far ends demands
an absurd detour for a small measured step — the transition kills it where nearest-distance would
not. Viterbi being global, each re-match self-corrects earlier stretches. Honesty rules unchanged:
off-road fixes stay raw (interior runs ≤ 2 dropped as spikes), an implausible/disconnected route
breaks the HMM into an honest straight chord. Supersedes v1 (per-point snap), v2 (A* gap-fill —
routing is now inherent) and v3 (straight-line-transition Viterbi). Pure commonMain; pipeline,
decimation and detection evidence untouched.

### ROUTE-QUALITY-001 — the stored route starts at the real origin; service ways never steal the line (2026-08-11)

**Why (field 2026-08-10, Redmi/Coordinator).** Three visible defects in the stored history route:
(a) the line began on the A-491 ~500 m past the real departure — the store's first element is only
the first fix the tracker saw after arming (first fixes also arrive with 50–70 m accuracy and are
dropped by the matcher); (b) on a straight CA-603 stretch the line bulged onto a parallel school
drop-off loop — `highway=service` ways were fetched as candidates with NO tags and NO class weight,
indistinguishable from the main road (a connected parallel loop has near-identical routed length,
so the transition term is ~0 and flattened emission noise decided); (c) the line started mid-map
with no marker, reading as cut off.

**What.**
- **Origin seed** (`ConfirmParkingUseCase.encodeFreshRoute`): the vehicle's still-active previous
  parking is prepended to the stored polyline as the trip's true origin, plausibility-capped
  (15 m – 5 km vs the first tracked fix, mirroring Home's live backdated-origin ceiling). The
  polyline carries lat/lon only, so the origin's old timestamp is irrelevant; the matcher's
  `ORIGIN_SNAP_METERS` (300 m, index 0 only) then routes the line from the real spot.
- **Minor-way handicap** (`TrailMapMatcher` + `OverpassRoadNetworkDataSourceImpl`): the Overpass
  query now returns tags (`out tags geom`), `RoadWay.isMinor` marks `highway=service`, and minor
  candidates pay `MINOR_WAY_EMISSION_PENALTY` (4.5 ≈ a 30 m emission handicap) — a parallel service
  way can no longer win on noise, while a trail genuinely on an aisle/forecourt (main road ≥ 40–50 m
  away) still matches it. Candidates are ordered by penalized cost (not raw distance) so dense
  forecourt segments don't crowd the main road out of the top-4, and a major way sharing a segment
  upgrades the edge (no tax on dual-mapped stretches). Service ways stay IN the graph — excluding
  them would chord over every trip that starts or ends in a forecourt.
- **Origin vertex** (`ParkingLocationScreen`): history detail passes the route's first point as
  `departurePoint`, reusing Home's departure-dot marker, so the line visibly starts somewhere.

Pure commonMain except the Overpass query change; detection evidence untouched. The snap remains
once-and-stored — routes snapped before this ticket keep their old geometry.

### ROUTE-END-AT-CAR-001 — the stored route ends at the parking anchor, never at the pedestrian (2026-08-13)

**Why (field 2026-08-13 17:33, Calle Mar de Alborán, Coordinator; mirror case Ebro 2026-08-11
21:43 with the anchor left BEHIND the polyline's end).** The pin was correct but the stored line
continued PAST the car, following the user's walk after parking: the `DrivingRouteStore` keeps
receiving fixes while GPS stays live during the egress (step proof, hold window), and
`encodeFreshRoute` encoded the whole buffer — the pedestrian tail then went through the worker's
map-match like any other stretch. The final cut was also abrupt: no terminating vertex mark
(the origin has had one since ROUTE-QUALITY-001).

**Invariant.** The routePolyline is the DRIVING route: it ends at the parking anchor. Fixes
recorded after the measured end of driving are the walk, not the trip, and never belong to the line.

**What.**
- **Tail trim** (`DrivingRoute.endAtAnchor`, applied in `ConfirmParkingUseCase.encodeFreshRoute`
  BEFORE encoding — and therefore before the one-time snap): fixes whose timestamp is later than
  the anchor fix's are dropped, and the anchor is appended as the final vertex when the remaining
  line stops short of it (plausibility window 15 m – 5 km, `MIN/MAX_ANCHOR_APPEND_METERS`,
  mirroring the origin prepend). The anchor is the `location` every confirm path passes — for the
  Coordinator that is `bestStopLocation`/the refined pin, whose fix timestamp IS the measured end
  of driving (the stop the anchor was captured/frozen at, ANCHOR-LOCK/DET-ANCHOR-FREEZE). No new
  heuristics: a "last driving-speed fix" cut was rejected because it would eat the slow final
  approach. A user-stamped pin (manual/nudge/"Sí") carries timestamp = now → nothing trims, but
  the append still caps the line at the pin. Freshness stays evaluated on the RAW buffer's last
  fix; the origin prepend and the matcher are untouched.
- **End vertex** (`PaparcarMapView.arrivalPoint` + `ParkingLocationScreen`): the stored route's
  last vertex gets the SAME dot as the origin (white ring, drive-blue fill), tight against the
  parked-car marker, so the line terminates instead of dying in a cut. Saved routes only — a live
  trip's end is the moving puck.

Pure commonMain; detection evidence untouched. Acts at confirm time — routes stored before this
ticket keep their tails.

### DET-RESIDENT-FGS-001 — resident SENTRY FGS between parkings (F1 lifecycle, F2 telemetry) (2026-07-28 / 2026-08-04)

**Why (chronic FN class; plan derived from the decompiled Driversnote/Transistor stack).** The
service used to tie "detection active" to "process alive": after every park it died
(`stopSelfResult`), and the departure watch fell to Play-Services-delivered events plus a one-shot
significant-motion sensor that does NOT survive process death. When an OEM killed the cached
process, Android 12+ forbids restarting an FGS from background — the departure trigger arrived
and found nobody home (recurring Redmi/Oppo field FNs).

**What (F1, flag `SENTRY_ENABLED`).** `ServicePresence { Dead, Sentry, Active }` on the runtime
state; after a park the pure `resolvePostDetectionLifecycle` decides whether the service dies
(today's behaviour, flag off / nothing parked) or degrades to **SENTRY**: alive and foreground,
GPS off, significant-motion listener armed in-process. A motion trigger `startService`s the live
process directly (`ACTION_SENTRY_WAKE`, no WorkManager latency; worker stays as fallback) and arms
with `Unverified` evidence — the confirmation doctrine is untouched, this is lifecycle only.
Confirmation doctrine, anchors and asymmetric failure are byte-identical. Field 28–30/07: 0 FN on
both OEMs; MIUI deep-kill still defeats residency (≈ force-stop; the lever is autostart, not code).

**What (F2, telemetry — 2026-08-04).** New `DetectionEvent.Sentry` (`SENTRY` wire type, sessionId =
watched geofenceId): `entered` (epilogue reason as signal), `woke` (arm trigger as signal +
time-in-SENTRY), `killed` (+ heartbeat-gap dark window and residency duration). Backed by a durable
residency stamp (`SentryResidenceStore`, same prefs as the safety net): stamped on `enterSentry`,
cleared on every DELIBERATE exit (wake to ACTIVE, idle/error teardown) — so a stamp that outlives
the process is proof the OS killed the resident watcher. The pure `resolveSentryKillVerdict`
(commonMain, tested) is shared by both detection lanes: the safety-net worker's periodic tick
(measures the gap since the pre-stamp heartbeat, `KEY_LAST_ALIVE_AT`) and the service's own arm
path (a trigger reviving a dead process with the stamp set witnesses the kill live). A reboot
explains the stamp innocently (silent clear), same rule as `BackgroundKillSuspected`. Q1 resolved:
SENTRY keeps `FOREGROUND_SERVICE_TYPE_LOCATION` (the type declares capability, not constant use).

**What (F3, product gating — 2026-08-06).** *The sentry has NO switch of its own* (user decision):
residency is HOW detection stays ready, so the existing Settings auto-detect toggle governs it —
`resolveIdleEpilogue` reads `AppPreferences.autoDetectParking` at the fork (the F1/F2
`SENTRY_ENABLED` experiment const is gone), and deliberately NO tier gating: the ASSISTED tiers
(no BT receiver to revive a dead process) are exactly where residency saves departures, and its
cost is one silent notification. While resident, `watchSentryPreconditions` observes the toggle
and the active sessions; either going false routes through the ordinary serialized STOP command
(same intake, same epilogue re-reads both facts) — so "turn detection off" or "free my spot"
tears the resident watcher down immediately, stamp cleared as the deliberate exit it is. The FGS
notification swaps in SENTRY to a low-profile one (own `sentry_channel`, IMPORTANCE_MIN, silent,
no badge; plain-language copy in 9 locales — what the app does for you + where to turn it off,
never internals); the next wake's promote swaps the active-detection one back. Dev Catalog: no
change needed — no new screen/state/routing (system notification + existing toggle only).

### DET-NUDGE-PIN-PROVENANCE-001 — a pin confirmed from a DETECTION nudge keeps detection provenance (2026-08-10)

**Why (field 2026-08-05/08, Arcos + Cañada del Real Tesoro).** When detection nominates a park but
cannot place it, it degrades to the nudge ("Marcar mi plaza" notification / the sheet's pending-nudge
row) per the asymmetric-failure doctrine. Answering the nudge lands in the shared pin mode
(`EnterAddParkingMode` → `SaveManualParkingUseCase`), which stamped EVERY pin
`MANUAL_REPORT` + `detectionPath="manual"`. Consequence downstream: the freed spot published at
departure inherits the session's `spotType`, so every nudge-assisted park sold the community a
"Manual" spot with the 15-min manual TTL instead of the 2-h auto one (Firestore sessions `296b1018`,
`bc10cc94`). Asymmetry: the in-app detection prompt "Sí, he aparcado" (`confirmDetected`) already
stamped `AUTO_DETECTED` + path `user`.

**What.** The pin mode's ORIGIN travels with the entry and the use case stamps by origin — one
invariant, no downstream guards:
- `StartAddParkingRequest(fromDetectionNudge)` on the bus; `AppNotificationManagerImpl` marks it
  per notification: `showMarkParkingNudge` (coordinator unattended / honest-close / safety-net
  watchdog) → `true`; `showFirstParkNudge` (onboarding, no detection event) → `false`. New
  `MainActivity.EXTRA_ADD_PARKING_FROM_DETECTION` carries it through the PendingIntent.
- `HomeIntent.EnterAddParkingMode.fromDetectionNudge` (default false) → mode-scoped
  `HomeState.addingParkingFromDetectionNudge` (cleared in `clearedModeFields()`); the sheet's
  pending-nudge row (`onMarkNudgeSpot`) passes `true`. Cold-start "mark my spot" CTA, the vehicle
  card "Aparcar" pill and the edit pencil stay manual/unchanged.
- `SaveManualParkingUseCase(fromDetectionNudge)`: nudge → `AUTO_DETECTED` + `detectionPath="nudge"`;
  spontaneous pin → `MANUAL_REPORT` + `"manual"` as before. Reliability stays 1.0 (user ground
  truth) and the pin stays the `sealPoint` [DET-STEP-BUDGET-ORIGIN-001]. Provenance vocabulary is
  now `manual` / `user` / `nudge` [DET-PIN-PROVENANCE-001].

Detection decisions are untouched — this is provenance + community-spot classification only.
⏳ Pending: field-test (answer a nudge, verify `spotType=AUTO_DETECTED`, `detectionPath=nudge`, and
an AUTO spot with 2-h TTL at departure).

### DET-CONFIRM-ANCHOR-001 — a late user "Sí" anchors at the car, not at the user (2026-08-13)

**Why (field 2026-08-11 16:08, Firestore + telemetry).** A Coordinator session with measured
driving (32 driving fixes, vmax 55 km/h) came to rest at a witnessed stop; the step counter was
essentially mute (2 steps), so the confirm degraded to a prompt. The user answered "Sí" AFTER
having walked to their destination — and the pin planted at the pedestrian destination, not where
the drive ended. Root cause: in the user-confirm branch of `CoordinatorParkingDetector`, when the
session fell outside the born-at-anchor happy path (`!isEgressBornAtAnchor || gap-entered`), the
save anchored at `state.bestFix(location)` — the user's CURRENT stop — on the assumption "they
answer near the car". A late answer breaks that assumption: the current fix then IS the
pedestrian, exactly what ANCHOR-LOCK/FREEZE forbids the pin to follow.

**What — a bounded distance guard in the else branch only.** On a user "Sí" outside the
born-at-anchor path, the save re-anchors at the WITNESSED stop (`bestStopLocation`, the frozen
end of driving) when ALL of:
- a stop anchor exists AND it is **not** gap-entered (`!anchorGapEnteredAtCapture` — a gap-born
  anchor may be a drive-past point with unboundable forward error [DET-GAP-ANCHOR-001], so it
  never wins this re-anchor);
- the answer arrives more than `USER_CONFIRM_NEAR_CAR_MAX_METERS` (100 m — between the standard
  near-car radii 80/120 m, under the 150 m egress-birth floor) from the stop anchor;
- the answer is ALSO farther than that threshold from the recorded egress birth
  (`egressOriginFix`). This last clause is the minimal adjustment that keeps
  [DET-ANCHOR-EGRESS-001] intact: with an egress born AWAY, the birth — not the anchor — may be
  where the car is (Enamorados: frozen at a light 1.11 km back, the user answering AT the car),
  so an answer near the birth keeps today's behavior (the user's current stop).

Everything else is byte-identical: answers near the stop, gap-entered anchors, sessions without a
stop anchor, and the whole born-at-anchor branch. The USER-CONFIRMED log line now carries
`stopDistance`/`birthDistance`/`gapEntered` and which witness won (provenance is mandatory).
Full spec + invariants in `docs/backlog/det-confirm-anchor-001.md`.

### DET-UNVERIFIED-CONFIRM-001 — a self-observed arm with no proven drive never confirms silently (2026-08-14)

**Field FP 2026-08-13 20:56 (Oppo, Calle Góndola):** the user left home ON FOOT and detection
confirmed a fresh parking ~7 m from the previous one, deactivating it. Session `1786647238401`:
a sentry-wake arm (`ArmEvidence.Unverified` → `self_observed`), whose FIRST cold-start fix
carried a Doppler mirage — **24.8 km/h at claimed acc 2.9 m** with the phone in a pocket at the
parked car. That single fix passed `credibleSpeedFix` and flipped `hasEverReachedDrivingSpeed`
(deliberately outside DET-DRIVE-PROOF-001's gate), which disarmed the anti-walking aborts; 270
walking steps + egress ≥ 18 m then confirmed `steps+egress` at reliability 0.9. The drive proof
itself held (`driveProven=false`, `maxSpeedKmh=0`) — but the weak-evidence policy only listed
`verified_enter`/`verified_late`, so `self_observed` confirmed silently without it ever being
consulted. The repark guard was out of range (10-min window; the previous park was 2 h 37 m old).

**Fix.** `ArmEvidence.LABEL_SELF_OBSERVED` joins `weakLabels` in `EvaluateParkingDecisionUseCase`:
an arm with NO external witness whose own stream never proved a drive (`sessionSawDriving=false`,
the drive-proof-gated statistic) degrades every auto-confirm to a **Prompt**. A real trip unlocks
the statistic within seconds and keeps the silent confirm; the rare legit no-proof short hop costs
one tap — the asymmetric failure the doctrine demands. `enter_at_car` deliberately stays out (its
own arm ladder already ties boarding to the own car); if the field shows the same hole there, the
same one-line move closes it. Spec: `docs/backlog/det-unverified-confirm-001.md`.

### DET-SENTRY-COOLDOWN-001 — repeated walking-refuted sentry wakes cool the sensor re-arm down (2026-08-14)

**Same field day:** with the service resident in SENTRY, a walk near the parked car re-fired the
significant-motion trigger on every stride — one armed-and-refuted session every **~18 s for over
an hour** (≈130 `aborted_false_enter` sessions). Battery burn, telemetry flood, and every wake was
a fresh lottery ticket for the first-fix mirage above.

**Fix.** A storm damper whose POLICY is pure commonMain (`SentryWakeCooldown.kt`):
`nextSentryWakeAbortStreak` counts CONSECUTIVE sentry-wake sessions refuted as walking aborts
(`aborted_false_enter`/`aborted_no_movement`, shared labels in `DetectionSessionOutcomes`; any
other ended session resets), and `sentryWakeRearmCooldownMs` maps the streak to a quiet period —
0 below `sentryWakeAbortStreakForCooldown` (3: a real departure's first wakes are never delayed),
then 3 min doubling per further refuted wake, capped at 15 min. The deadline is ENFORCED inside
`SignificantMotionMonitor` because three independent mirrors call `sync()` (service epilogue,
safety-net tick, exact heartbeat) — the first mirror past the deadline re-arms on its own. The
service folds each ended session into the streak in `resolveIdleEpilogue` (consume-once
`lastEndedArmTrigger`) and logs a `SENTRY wake_cooldown` telemetry event with streak + duration.
Detection contract preserved: only the significant-motion NOMINATOR sleeps — geofence EXIT
(PendingIntent, works from a dead process), the AR ENTER lane and the periodic safety net keep
watching, so a real departure mid-cooldown loses only the immediacy lane. State is in-memory by
design: the storm needs a live resident process to exist, and a process death resets both.
Spec: `docs/backlog/det-sentry-cooldown-001.md`.

### PARK-DELETE-NO-DECLARE-001 — deleting a parking record no longer declares that car active (2026-08-14)

**Field report (14-08).** The user deleted the Kamiq's parking record from the edit sheet and the
**Focus stopped being the active vehicle**. Detection cares: the active vehicle is the coordinator's
attribution fallback (§ "Session attribution at the vehicleId lock", rule 3) and the owner of the
registered fences (`SwapActiveVehicleFencesUseCase`) — so a stolen active flag silently moves the
watch to a car the user isn't driving and strips the one they are.

**Cause.** `HomeViewModel.releaseParking()` declared the session's vehicle active unconditionally.
That is right for "I'm leaving in this car", but the same intent backed the edit sheet's **Delete
record** button; its only parameter (`publishSpot`) described the consequence, not the motive, so a
deletion was indistinguishable from a private departure.

**Second half of the same bug: the car matters as much as the motive.** Even a *real* departure in
the Kamiq shouldn't have touched the flag. The active vehicle is the identity declaration for cars
that have **no other one** — the asymmetry `shouldOwnFence` (`activo || btPaired`) and
`resolveSessionVehicleId` (BT nominator vetoed → falls back to the ACTIVE car, because *the
coordinator is the active vehicle's strategy*) already encode. A BT-paired car is identified by its
MAC, so making it active buys it nothing and costs the coordinator's car its only identity signal.

**Fix.** `ParkingReleaseReason` (commonMain domain model) replaces the boolean: `publishesSpot` +
`isDeparture` — `DEPARTURE_PUBLISHED`, `DEPARTURE_UNPUBLISHED`, `RECORD_DELETED` (publishes nothing,
declares nothing, still removes the session's fence). The intent has no default, so a new call site
must state its motive. The verdict itself composes motive and car in the policy that already owns
this asymmetry: `VehicleFenceOwnershipPolicy.shouldDeclareActiveOnRelease(reason, isBtPaired) =
reason.isDeparture && !isBtPaired`. Only the INFERRED declaration is vetoed — an explicit one ("make
active" in Vehicles, "I'm driving this car") is the user speaking and stands for any car, BT
included, exactly as DET-BT-OWNERSHIP-001 assumes when the active car is itself the BT nominator.
The reason is stamped on `DetectionEvent.Released` (reusing the existing `reason` DTO column), so a
replay can tell a deleted record from a departure the user chose not to share — both carry
`published=false`. Doctrine: only the user declares which car they drive; a deletion says the
parking never happened, and a BT car never needed declaring. Spec:
`docs/backlog/park-delete-no-declare-001.md`.

### DET-WATCH-REACTIVATE-001 — the "Reactivate" CTA rebuilds the watcher instead of asking for a permission (2026-08-14)

**Field report (14-08, Ford Focus, right after installing `prodDebug` over `prodRelease` — a clean
install: Room wiped, login redone).** The red *"Vigilancia detenida de tu Ford Focus"* row appeared;
its button opened the battery-exemption dialog; granting it left the row exactly where it was, and
on the second tap the button did nothing at all.

**Cause 1 — the CTA pulled the wrong lever.** `ParkedWatchBadge.WATCH_INTERRUPTED` is decided by ONE
signal, `servicePresence == Dead` (the resident SENTRY watcher is gone). Its row was wired to
`onRequestBatteryExemption`, i.e. `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, which starts no
service — presence stayed `Dead`, so the row never cleared. And once the package IS whitelisted the
system activity finishes immediately without showing a dialog, which is the "mute button" the user
saw. One wiring error, both symptoms. The exemption belongs to `WATCHING_FRAGILE` (a live but
killable watch), which does heal itself: `MainActivity.onResume` → `refreshPermissions` →
reliability leaves REDUCED.

**Cause 2 — the self-heal missed exactly this scenario.** `PaparcarApp.onCreate` →
`resumeSentryIfCoordinatorParked()` read `observeActiveSessions().first()`: on a clean install Room
is still empty while the Firestore sync lands, so it saw "nothing parked", returned early, and left
the watch dead until the next process launch. It also ran before any Activity was resumed — the
least foreground-eligible moment for an FGS start on Android 12+ — and swallowed the refusal in a
`runCatching {}.onFailure`.

**Fix — one invariant, one place.** *If the watcher SHOULD be live and presence is `Dead`, the app
closes that gap the moment it has a legal foreground window.* The "should be live" half is not
re-derived: it is `resolvePostDetectionLifecycle`, the same pure rule the service's idle epilogue
applies, so the two can never disagree about whether the watcher must exist.
`ObserveDepartureWatchGapUseCase` (commonMain) combines sessions + vehicles/strategy + the detection
toggle + `presence` into that gap; `DepartureWatchResumer` (interface in commonMain, Android impl,
iOS no-op) fires `ACTION_RESUME_SENTRY` with the same gate re-checked fresh, plus a 60 s cooldown on
AUTOMATIC attempts only — an explicit tap always tries. Two senders, one path: `MainActivity`
collecting the gap under `repeatOnLifecycle(STARTED)`, and `HomeIntent.ResumeWatch` from the row's
CTA. Because it is a STREAM, the clean install heals itself the moment the sync delivers the session
— the failure mode the one-shot read had. `EXTRA_RESUME_SOURCE` names the sender in the service log
(`foreground-gap` / `home-cta`). A resume that gets refused now raises
`PaparcarError.Detection.WatchResumeFailed` → snackbar, because a tap that achieves nothing must say
so. `PaparcarApp`'s copy was deleted, not left alongside.

**Copy.** *"Tu móvil detuvo la detección"* overstated the failure: geofence EXIT, AR ENTER, the
15-min safety net and the exact heartbeat stay armed when the resident watcher dies — what is lost is
immediacy, not detection. The row now reads (ES) *"Vigilancia inmediata en pausa de tu %1$s / Tu
móvil la pausó. Seguimos comprobando en segundo plano, pero tu salida puede detectarse tarde."* —
cause, consequence, remedy, no internals. 9 locales.

**No new confirmation path**, so `detectionPath` / `armEvidence` are untouched: resuming the sentry
rebuilds a WATCHER, it never confirms a parking. Doctrine intact — the event still only nominates,
measured movement still confirms. Spec: `docs/backlog/det-watch-reactivate-001.md`.

### DET-SHORT-HOP-PROOF-001 — a short hop proves its drive by DISPLACEMENT from the pin (2026-08-14)

**Field report (14-08 ~23:00, Oppo, Ford Focus).** "Volví a donde estaba y no se puso" — the last
parking of the night was never recorded. Session `1786740987649`: armed PUNCTUALLY on a
`GEOFENCE_EXIT` 159 m from the car with VERIFIED evidence, ran 16.4 min, logged **303 fixes**, peaked
at **30 km/h**, counted **104 steps** on arrival… and summarised `drive 3/303` →
`aborted_unattended_no_drive` + a nudge nobody answered. The same physical trip WAS confirmed by the
Redmi at 23:05, whose session came from the outbound leg and had already proven a drive at 73 km/h.

**Cause — the only proof of driving we had is shaped for long, fast legs.** `corroboratesDrive`
([DET-DRIVE-PROOF-001]) demands a credible driving-speed fix corroborated by a look-back fix aged
20–60 s across which the position covered `minimumTripDistanceMeters` (150 m). A night hop of ~900 m
through stop-and-go streets never holds 150 m inside any single 60-s window on a sparse stream, so
`maxSpeedMps` stayed 0 — and `maxSpeedMps` is the statistic every confirm path reads as "did this
session witness driving?". The trip was measured in full; the *shape* of the proof simply could not
see it. A guard against a mirage had become a guard against a real drive.

**Fix — a second, independent proof: measured ground covered, anchored to the PIN.**
`EvaluateShortHopDriveProofUseCase` (pure, commonMain) unlocks the same statistic when the position
sits, over `shortHopProofFixes` consecutive credible fixes, more than `shortHopProofFloorMeters`
(400 m) from **the pin the car left**, beyond the fence radius and both accuracy envelopes, and
beyond `isBeyondPedestrianReach` for the elapsed time — the same physics as [DET-RIDE-PROOF-001].
Anchoring to the pin (never to the session's own first fix) is what makes the Doppler-mirage class
impossible by construction: a phone drifting indoors next to its own pin measures ~0 m of
displacement from it, whatever the chipset claims — had the anchor been the session's first fix, the
2026-07-27 mirage would have used its own burst as the origin and called the trip home a "drive".

**Doctrine intact.** The verified EXIT is still only a NOMINATION — required (an unverified arm proves
nothing by displacement: a long walk or a bus shows the same geometry) and never sufficient. What
CONFIRMS is measured, sustained, unwalkable ground. Every bound errs toward "not proven", and nothing
here weakens the anchor-quality guards: the same field session, replayed with a hole before the stop,
still refuses to pin (`aborted_unattended_gap_anchor`) — proving the drive says the car went
somewhere, not that we know exactly where it stopped.

**No new confirmation path**, so `detectionPath` / `armEvidence` are untouched: this unlocks the
existing paths (the field session then ends `confirmed_unattended_timeout` instead of
`aborted_unattended_no_drive`). Regression test replays the field shape and is verified RED without
the fix. Spec: `docs/backlog/det-short-hop-proof-001.md`.
