# Parking Detection — Reference Document

**Status:** living document. Update when detection logic, scoring thresholds, or persistence paths change.
**Audience:** solo developer + AI pair.
**Scope:** the end-to-end flow that decides a parking spot has been confirmed, persists it to Room + Firestore, registers a geofence for departure detection, and posts the user-facing notification.

This is the canonical reference for *how parking detection works today* and *what bugs we have already burned in trying to make it work*. Section 1 describes the algorithm. Section 2 logs every fix shipped, so future-you (or future-Claude) understands why a given guard exists before deleting it.

---

## 1. Algorithm and architecture

### 1.1 Dual-strategy design

Paparcar detects the moment a user parks their car so it can publish the freshly-freed spot to the community. Two independent strategies converge on the same persistence step:

| Strategy | Trigger | Reliability | When |
|---|---|---|---|
| **BluetoothDetectionStrategy** | Car BT disconnects → debounce → GPS fix → user walks ≥ 30 m | 0.95 (deterministic) | User has paired BT with their car AND BT is on |
| **CoordinatorDetectionStrategy** | Activity Recognition + GPS stream → confidence scoring | 0.75 / 0.90 / 1.00 (probabilistic) | Everyone else — no BT, BT off, or no paired device |

The choice is made in `ParkingStrategyResolver`:

```kotlin
fun resolveStrategy(vehicle: Vehicle, isBluetoothEnabled: Boolean): ParkingDetectionStrategy {
    return if (vehicle.bluetoothDeviceId != null && isBluetoothEnabled) {
        BluetoothDetectionStrategy(vehicle.bluetoothDeviceId)
    } else {
        CoordinatorDetectionStrategy()
    }
}
```

The strategies never mix signals. Each one ends in `ConfirmParkingUseCase`, which owns the persistence pipeline.

### 1.2 BluetoothDetectionStrategy (deterministic)

`BluetoothParkingDetector.onCarDisconnected()`:

1. **Debounce** — wait `BT_DISCONNECT_DEBOUNCE_MS = 30 s` to ignore brief BT oscillation (traffic lights, aftermarket head-units). If BT reconnects during the window, abort.
2. **GPS fix** — sample the location stream until `accuracy ≤ GPS_ACCURACY_THRESHOLD_M = 50 m`, or `GPS_SAMPLE_TIMEOUT_MS = 60 s` elapses. The first fix that meets the accuracy bar is recorded as the candidate parking location.
3. **Walking confirmation** — keep watching GPS until the user has moved `≥ DISTANCE_THRESHOLD_M = 30 m` from the candidate fix. This rules out "BT dropped while still in the car" cases (passenger left the car, head-unit died, etc.).
4. **Confirm** — `confirmParking(candidateFix, PARKING_DETECTION_RELIABILITY = 0.95f)`.

`onCarConnected()` cancels any pending detection job — the user re-boarded before walking far enough.

This strategy has no scoring and no medium-confidence path: BT disconnect + GPS-anchored walk is treated as ground truth.

### 1.3 CoordinatorDetectionStrategy (probabilistic)

`ParkingDetectionCoordinator.invoke(locations: Flow<GpsPoint>)` is the heart of the probabilistic path. It owns a single `MutableStateFlow<ParkingDetectionState>` updated atomically per location fix; external signals (`onVehicleExit`, `onStillDetected`, `onUserConfirmedParking`, `onUserDeniedParking`) feed in via thread-safe setters.

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
                                       │  ├── Low/Med → notify user      │
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
- **Moving** (`speed ≥ 1 m/s`):
  - `stoppedSince = null`, `stoppedFixes = emptyList()`.
  - If `speed ≥ clearBestStopSpeedMps = 2.5 m/s` **AND** `accuracy ≤ minGpsAccuracyForDriving = 50 m`, the coordinator treats the fix as evidence the vehicle is driving away again: `bestStopLocation`, `vehicleExitConfirmed`, `activityStillDetected`, and `highConfidenceReachedAt` are all cleared. The accuracy gate exists because hardware GPS hallucinates apparent-driving speed in noisy fixes — see LOC-002 in §2.
  - If `speed ≥ repositionSpeedMps = 1.7 m/s` **AND** `accuracy ≤ minGpsAccuracyForDriving = 50 m` for **two consecutive fixes**, `bestStopLocation` is cleared as a reposition burst. This is between sustained walking (~1.2 m/s, never crosses 1.7) and the driving threshold; it lets the coordinator distinguish a brief vehicle maneuver (wait + park into a freed spot) from a single noisy spike or sustained walking — see PARKING-001 in §2.

The 2.5 m/s driving ceiling is deliberately above typical walking speed (~1.4 m/s), so the captured parked-car position survives the user walking away on foot. The 1.7 m/s reposition floor is deliberately above walking too, gated by two consecutive fixes so a single GPS spike at that speed (without sustained motion) does not clear state. The 50 m accuracy floor is shared by both gates, deliberately above typical urban GPS noise (~10–30 m), so legitimate traffic-light-then-resume-driving still clears state correctly.

#### Confidence scoring

`CalculateParkingConfidenceUseCase` reads a `ParkingSignals` snapshot (`speed`, `stoppedDurationMs`, `gpsAccuracy`, `activityExit`, `activityStill`) and returns one of:

- `NotYet` — early or invalid signal combination.
- `Low(score)` — gates a confirmation notification but never auto-confirms.
- `Medium(score)` — same.
- `High(score)` — opens the CANDIDATE phase.

Two scoring paths feed the same threshold (`highConfidenceThreshold = 0.75`):

**Fast path** — requires `activityExit = true` (an `IN_VEHICLE → EXIT` Activity Recognition transition was observed):
- Base 0.50 + 0.15 if speed ≤ `maxSpeedMps (0.3)` + 0.10 if accuracy ≤ `minGpsAccuracyMeters (15 m)` = up to 0.75.
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
4. Window expires → confirm with `reliabilityVehicleExit = 0.90f` (fast path) or `reliabilitySlowPath = 0.75f` (slow path).

The confirmation notification is **always** posted when the CANDIDATE phase opens, so the user has the option to override.

### 1.4 ConfirmParkingUseCase — the convergence point

Both strategies call `confirmParking(location, reliability, spotType?, sizeCategory?)`. This use case is the only place where parking state hits storage. Its responsibilities, in order:

1. Resolve the current user (`authRepository.getCurrentSession()`).
2. Resolve the default vehicle (`vehicleRepository.observeDefaultVehicle().first()`) — used to populate `UserParking.vehicleId` and to default `sizeCategory` for the geofence.
3. Build a `UserParking` domain object with the new `sessionId`, the chosen location, reliability score, spot type, and resolved size.
4. **Room write only** — `userParkingRepository.saveSession(session)` clears any previously active row and inserts the new one. Returns the previous session's id (if any) so it can be reconciled remotely.
5. **Schedule Firestore sync** — `parkingSyncScheduler.schedule(session, previousSessionId)` enqueues a `ParkingSyncWorker` job. The coordinator does **not** await network IO. See PIPE-001 in §2 for why.
6. **Schedule background enrichment** — `enrichmentScheduler.schedule(sessionId, lat, lon)` enqueues the geocoder + POI lookup worker.
7. **Register geofence** — adaptive radius based on vehicle size and current GPS accuracy (see §1.6).
8. **Show notification** — "Saved your parking spot" with deep-link to the map.

Step 4 is the only suspending operation that can fail in a way the caller cares about. Steps 5–8 are scheduled or fire-and-forget; their failures are logged but do not propagate.

### 1.5 Persistence pipeline

```
ConfirmParkingUseCase
     │
     ├── Room (saveSession)                     ◄── synchronous, local
     │
     ├── ParkingSyncScheduler.schedule()        ◄── WorkManager
     │      └── ParkingSyncWorker.doWork()
     │             ├── Firestore set(newSession DTO)
     │             └── Firestore update(prev.isActive = false)
     │
     ├── ParkingEnrichmentScheduler.schedule()  ◄── WorkManager
     │      └── EnrichParkingSessionWorker.doWork()
     │             ├── reverseGeocode(lat, lon)
     │             ├── lookupPoi(lat, lon)
     │             └── userParkingRepository.updateLocationInfo()
     │                    ├── Room update
     │                    └── LocationUpdateSyncWorker (Firestore reconcile)
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
            └── userParkingRepository.clearActive()
                   ├── Room update (isActive=0)
                   └── ClearActiveSyncWorker (Firestore reconcile)
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

### 1.7 Departure detection

`DetectParkingDepartureUseCase` (driven by `onCarConnected` on the BT path, or by GMS geofence-exit events on the coordinator path) decides whether the user really drove away:

- A `IN_VEHICLE_ENTER` Activity Recognition transition must precede the geofence exit by no more than `vehicleEnterWindowMs = 30 min`. Older signals are stale (yesterday's drive).
- GPS speed must reach `minimumDepartureSpeedKmh = 10 km/h` to confirm — skipped if GPS is unavailable.

When both clauses pass, `ReleaseActiveParkingSessionUseCase` runs.

### 1.8 Diagnostic logging — `PARKDIAG`

Debug builds enable `FileAntilog` (`composeApp/src/androidMain/.../logging/FileAntilog.kt`). Every Napier log line tagged `PARKDIAG/*` is appended to `${context.filesDir}/parkdiag.log` (5 MB rotating). Tags used:

- `PARKDIAG/Service` — `ParkingDetectionService` lifecycle.
- `PARKDIAG/Coord` — `ParkingDetectionCoordinator` state transitions.
- `PARKDIAG/Confirm` — `ConfirmParkingUseCase` steps.
- `PARKDIAG/Notify` — `NotifyParkingConfirmationUseCase`.
- `PARKDIAG/SyncScheduler`, `PARKDIAG/SyncWorker`, `PARKDIAG/LocationUpdateSyncWorker` — WorkManager pipeline.

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

**Fix.** Five surface points needed updating: `ParkingHistoryDto` field, the two mappers, the `ParkingSyncWorker` payload (`KEY_NEW_SESSION_VEHICLE_ID`), and `RemoteUserProfileDataSourceImpl.toParkingHistoryDto()`. Also fixed the latent `detectionReliability` write-path omission in `toParkingHistoryDto()`. No data backfill — pre-release state, user wiped Firestore manually.

### FND-009 — `runBlocking` removed from `NotifyParkingConfirmationUseCase`

**Commit:** `b05ef61`.

The notify use case was non-suspend and wrapped `vehicleRepository.observeDefaultVehicle().firstOrNull()` in `runBlocking` to read the vehicle name for the notification. PARKDIAG captures showed 1.2–1.4 s of Main-thread blocking inside an otherwise-async coordinator loop, well within ANR territory on cold Room or contended IO.

**Fix.** `suspend operator fun invoke(...)`. The ripple stayed inside the coordinator (`evaluateConfidence`) since it's already inside a coroutine.

### PIPE-001 — Firestore writes off the confirm-parking critical path

**Commit:** `2f4eef2` (merge), `371ce85` (work).

The original `confirmParking` did Room save + Firestore set + geofence registration + notification, all in a `withContext(NonCancellable)` block inside `ParkingDetectionCoordinator.evaluateConfidence`. Firestore writes can hang for tens of seconds on bad networks; the foreground service can hang with them. PARKDIAG captures during the "blue notification stays forever" bug pointed to Firestore as the long pole.

**Fix.** Introduce `ParkingSyncScheduler` + `ParkingSyncWorker`. `confirmParking` now does Room write only and enqueues the Firestore reconciliation in WorkManager. The critical path is bounded by Room + Geofence + Notification, none of which can hang indefinitely. Full plan in `docs/refactors/PIPE-001-confirm-parking-pipeline.md`.

### PIPE-002 — `clearActive` and `updateLocationInfo` also use workers

**Commit:** `ec89592`.

Same hang-on-Firestore risk on departure and enrichment paths. `UserParkingRepositoryImpl.clearActive()` and `updateLocationInfo()` were calling the remote data source inside `runCatching` — fine for the user-departure case (already off the foreground service), worse for the enrichment worker (could be killed mid-Firestore-write, leaving Room and Firestore inconsistent).

**Fix.** Both methods are Room-only; `ClearActiveSyncWorker` and `LocationUpdateSyncWorker` handle Firestore. Also fixed a PIPE-001 follow-up: previously a partial DTO with `lat=0.0` could overwrite coordinates via `set()` — the workers now use `update()` for partial field changes.

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

**Commit:** pending (Option A; B and C deferred).

User report (`diagnostics/2026-05-14/redmi-note-11.log`, drive of 2026-05-13): when the user stops 10–15 m short of the actual parking spot, waits for another car to leave, then maneuvers into the freed spot, the app saves the *waiting* position as the final parking location instead of the actual plaza.

**Root cause.** The maneuver to the real plaza is short (~10 m) and slow (peak ~1.5–2 m/s), so it never crosses `clearBestStopSpeedMps = 2.5 m/s` with `accuracy ≤ 50 m`. LOC-002's single-fix gate correctly preserves `bestStopLocation` against noisy spikes, but as a side effect also preserves the stale waiting-position bestStopLocation through the maneuver. Then LOC-001 freezes the new initial-stop window without ever overwriting the stale value (since its accuracy was already very good — the user was idle there long enough for GPS to converge).

**Fix.** Introduce a *consecutive* reposition signal between sustained walking pace (~1.4 m/s) and `clearBestStopSpeedMps`. New config:

```kotlin
val repositionSpeedMps: Float = 1.7f      // single-fix threshold
val repositionFixCount: Int = 2           // consecutive fixes needed
```

In `updateStopTracking()` moving branch:

```kotlin
val isRepositionCandidate = location.speed >= config.repositionSpeedMps &&
        location.accuracy <= config.minGpsAccuracyForDriving
val newConsecutive = if (isRepositionCandidate) state.consecutiveRepositionFixes + 1 else 0
val isRepositionBurst = newConsecutive >= config.repositionFixCount
val shouldClearBestStop = isDriving || isRepositionBurst
```

`consecutiveRepositionFixes` is reset to 0 on any stopped fix and on any moving fix that drops below the reposition threshold (sustained walking at ~1.2 m/s).

**Why the differentiation works.**
- **Walking** sustains ~1.2 m/s and never crosses 1.7 m/s reliably — counter stays at 0.
- **Single GPS spike** at >1.7 m/s with good accuracy is rare but possible — increments counter to 1, but the next fix returns to walking pace → counter resets, bestStopLocation preserved (LOC-002 semantics extended).
- **Vehicle maneuver** crosses 1.7 m/s with good accuracy for ≥2 fixes (≥5 s at HIGH_ACCURACY cadence) — counter reaches 2, bestStopLocation cleared, the next stop window captures the real plaza.

**Accepted trade-off.** Jogging with the phone (>1.7 m/s sustained) after parking but before HIGH is reached would now clear `bestStopLocation`. This is a niche scenario; deferred until evidence warrants a separate guard.

**Companion options considered (Section 3, deferred).** Option B (1 s GPS sampling boost during CANDIDATE) and Option C (lowering `clearBestStopSpeedMps` to 2.0) were both proposed. Option A is the cheapest and most surgical; ship it first and fold in B/C only if a captured failure shows A is insufficient.

### ADD-PARKING-PIN — manual park becomes a positionable pin (2026-05-19)

**Before.** The "Aparcar manualmente" CTA on the parking empty-state card emitted `HomeIntent.ManualPark` → `manualPark()` → `confirmParking(userGpsPoint, 1.0f, MANUAL_REPORT)`. Snap-to-GPS, no chance to correct if the user was already walking away from the car.

**After.** The CTA now emits `HomeIntent.EnterAddParkingMode(initialGps = userGpsPoint)`, which opens `HomeMode.AddingParking` — same dim + centre-pin + peek molde as Reporting / AddingZone, with the new `ParkingCenterPin` (white teardrop + inner disc + car glyph). The user drags the map to position the pin and taps "Aparcar aquí" to confirm. Confirm path runs `confirmParking(pinGps, 1.0f, MANUAL_REPORT)` (same use case as before — only the pin location differs).

**Plus** — a new "Mover ubicación" action on the active-parking peek opens the same mode with `editingParkingId = parking.id` and `initialGps = parking.location`. Confirm in edit mode dispatches to `UpdateParkingLocationUseCase` instead of `ConfirmParkingUseCase`. The use case mirrors confirm-parking's side-effects on an existing row: cancel old geofence → repository `updateLocation` (lat/lon + clears address/POI for re-geocoding) → schedule Firestore sync (existing `ParkingSyncScheduler.schedule`) → schedule enrichment → recreate geofence at new location (same id). No notification — the user took the action explicitly.

**Retired.** `HomeIntent.ManualPark` + `manualPark()` are gone (the empty-state CTA is the only emitter and it now uses `EnterAddParkingMode`). Test coverage migrated: `should_emit_ShowError_on_ManualPark_when_no_GPS` → `should_emit_ShowError_on_ConfirmAddParking_when_no_GPS`, ditto for the offline variant. Same `ProviderDisabled` / `OfflineActionBlocked` guards live in `confirmAddParking()`.

**ADD-ZONE-PIN restyle** shipped alongside as a pure visual change — `ZoneCenterPin` now reuses the same `TeardropPinScaffold` as Report / Parking pins (white teardrop + inner disc + chosen zone icon overlay) so all three add-modes read as one family with only the inner silhouette varying.

---

## 3. Open questions / future work

- **GPS sampling boost during CANDIDATE (PARKING-001 Option B).** Switch the LocationDataSource to a 1 s `minUpdateIntervalMillis` request when entering the CANDIDATE phase, returning to 2 s on exit. Increases density of fixes that refine `bestStopLocation` within the new initial-stop window after a reposition burst. Adds the complexity of swapping the location source mid-session — hold off until A is validated in the field.
- **Lower `clearBestStopSpeedMps` to ~2.0 (PARKING-001 Option C).** Single-fix tightening of the existing LOC-002 gate. Same effect as the reposition burst for fast maneuvers, but reintroduces the noise-spike risk that LOC-002 mitigated. Bundle with Option B if needed.
- **Per-device noise floor.** Redmi Note 11 routinely emits acc > 50 m even outdoors; OPPO CPH2371 rarely does. If the user base widens, consider a remote-config table of per-device `minGpsAccuracyForDriving` values, or compute a rolling-median accuracy and gate against a multiple of it.
- **AUTH-002 — parking lost when `getCurrentSession()` returns null.** Observed in the same Redmi log at `05-13 19:42:20`: the CANDIDATE window expired and `ConfirmParkingUseCase` aborted because the auth cache was empty. The parking was never written to Room either, so it is fully lost. Distinct from AUTH-001 (which was the `observeAuthState()` race in `observeDefaultVehicle`). Pending: design a fallback path that either persists userId on first successful login and reads from local cache, or defers the confirm via a Worker that retries on auth failure.
- **iOS port.** The coordinator is in `commonMain` and platform-agnostic; only the GPS / Activity / Geofence platform wrappers need iOS implementations. The PARKDIAG infrastructure is androidMain-only — when iOS arrives, decide whether to mirror `FileAntilog` or rely on OSLog.
