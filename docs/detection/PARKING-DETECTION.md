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
enum class ParkingStrategy { NONE, BLUETOOTH, COORDINATOR }

suspend fun resolve(): ParkingStrategy {
    val vehicle = vehicleRepository.observeDefaultVehicle().first()
    if (vehicle != null && vehicle.vehicleType in NON_PARKING_TYPES) {
        return ParkingStrategy.NONE        // SCOOTER, BIKE
    }
    val hasBtConfig = vehicle?.bluetoothDeviceId != null
    return if (hasBtConfig && bluetoothScanner.isBluetoothEnabled()) {
        ParkingStrategy.BLUETOOTH
    } else {
        ParkingStrategy.COORDINATOR
    }
}
```

The strategies never mix signals. BLUETOOTH and COORDINATOR converge on `ConfirmParkingUseCase`. NONE skips parking detection entirely — scooters and bikes are dismounted on the sidewalk and never liberate a parking spot. See BUG-SCOOTER-001 in §2.

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
- **Moving** (`speed ≥ 1 m/s`):
  - `stoppedSince = null`, `stoppedFixes = emptyList()`.
  - If `speed ≥ clearBestStopSpeedMps = 2.5 m/s` **AND** `accuracy ≤ minGpsAccuracyForDriving = 50 m`, the coordinator treats the fix as evidence the vehicle is driving away again: `bestStopLocation`, `vehicleExitConfirmed`, `activityStillDetected`, and `highConfidenceReachedAt` are all cleared. The accuracy gate exists because hardware GPS hallucinates apparent-driving speed in noisy fixes — see LOC-002 in §2.
  - If `speed ≥ repositionSpeedMps = 1.7 m/s` **AND** `accuracy ≤ minGpsAccuracyForDriving = 50 m` for **two consecutive fixes**, `bestStopLocation` is cleared as a reposition burst. This is between sustained walking (~1.2 m/s, never crosses 1.7) and the driving threshold; it lets the coordinator distinguish a brief vehicle maneuver (wait + park into a freed spot) from a single noisy spike or sustained walking — see PARKING-001 in §2.

The 2.5 m/s driving ceiling is deliberately above typical walking speed (~1.4 m/s), so the captured parked-car position survives the user walking away on foot. The 1.7 m/s reposition floor is deliberately above walking too, gated by two consecutive fixes so a single GPS spike at that speed (without sustained motion) does not clear state. The 50 m accuracy floor is shared by both gates, deliberately above typical urban GPS noise (~10–30 m), so legitimate traffic-light-then-resume-driving still clears state correctly.

#### Confidence scoring

`CalculateParkingConfidenceUseCase` reads a `ParkingSignals` snapshot (`speed`, `stoppedDurationMs`, `gpsAccuracy`, `activityExit`, `activityStill`) and returns one of:

- `NotYet` — early or invalid signal combination.
- `Low(score)` — gates a confirmation notification (only if `vehicleExit` or `activityStill` signal present) but never auto-confirms.
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
4. **Pedestrian steps** ≥ `minStepsToConfirm = 8` while stopped → confirm immediately with `reliabilityVehicleExit = 0.90f`. Steps are unambiguous proof the user exited the car, stronger than the AR exit transition. [BUG-GARAGE-COLA-001]
5. Window expires **with** vehicle-exit signal → confirm with `reliabilityVehicleExit = 0.90f`.
6. Window expires **without** steps and without vehicle-exit → discard the candidate (likely cola/atasco). The notification that fired on High entry remains the only chance to confirm; if the user did park and ignored it, the next session catches them.

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

### 1.7 Departure detection — step-by-step flow

When the user leaves with their car, departure detection runs through two parallel signal chains that must agree before the parking session is cleared and the spot is published.

#### Step 1 — Geofence exit

When the user drives far enough from the saved parking location, Google Play Services fires a geofence exit event to `GeofenceBroadcastReceiver`. The receiver extracts `GeofencingEvent.fromIntent(intent)`, reads `triggeringGeofences`, and enqueues `DepartureDetectionWorker` via WorkManager with `KEY_GEOFENCE_ID` and `KEY_EXIT_TIMESTAMP`.

> **Important:** the geofence `PendingIntent` **must** use `FLAG_MUTABLE`. Play Services fills `GeofencingEvent` extras into the intent at delivery time; `FLAG_IMMUTABLE` blocks this on Android 12+ — `triggeringGeofences` arrives as `null` and the receiver silently returns without enqueuing the worker. See BUG-GEOFENCE-001 in §2.

#### Step 2 — Activity Recognition: IN_VEHICLE_ENTER

Independently, `ActivityRecognitionManagerImpl` is subscribed to `IN_VEHICLE_ENTER` transitions. When Play Services fires this event, it delivers directly to `ParkingDetectionService` via `PendingIntent.getForegroundService()` (ACTION_VEHICLE_TRANSITION). The service records `departureEventBus.onVehicleEntered(epochMs)` — an in-memory timestamp marking the moment the user entered a vehicle.

#### Step 3 — DepartureDetectionWorker: three-signal check

`DepartureDetectionWorker.doWork()` calls `DetectParkingDepartureUseCase` with the geofence id, the exit timestamp, and the current GPS speed (fresh fix via `GetOneLocationUseCase`). The use case checks:

1. **Active session exists** and its `geofenceId` matches the one that fired — prevents false cross-vehicle triggers. Returns `Rejected` if no match.
2. **IN_VEHICLE_ENTER window** — `departureEventBus.lastVehicleEnteredAt` must be within `vehicleEnterWindowMs = 30 min` of the exit timestamp. Stale signals (yesterday's drive) are ignored. Returns `Inconclusive` if no recent signal.
3. **GPS speed** — if a fresh fix is available, speed must exceed `minimumDepartureSpeedKmh = 10 km/h`. Returns `Inconclusive` if below threshold.

If any check is `Inconclusive` (AR not yet delivered, user still slow), the worker retries with exponential backoff up to `MAX_INCONCLUSIVE_RETRIES = 3` times (total ~2 min window). After exhausting retries, a geofence exit alone is treated as ground truth (`Confirmed`).

#### Step 4 — Session clear + spot release

On `Confirmed`:

1. `userParkingRepository.getActiveSessionByGeofence(geofenceId)` — resolves the exact session from Room.
2. `reportSpotReleased(lat, lon, spotId, spotType, confidence, sizeCategory)` — geocodes and enqueues `ReportSpotWorker` to publish the freed spot to Firestore.
3. `userParkingRepository.clearActiveById(session.id)` — removes the active session from Room and enqueues `ClearActiveSyncWorker` for Firestore reconciliation.
4. `departureEventBus.reset()` — clears the in-memory `lastVehicleEnteredAt` state.
5. `geofenceService.removeGeofence(geofenceId)` — deregisters the GMS geofence so Play Services stops monitoring it.

Note: `reportSpotReleased` is called **before** `clearActive` — the WorkManager job is durably enqueued even if the worker is killed before the clear, and `REPLACE` policy on retries prevents duplicate publications.

#### Caveat: in-memory AR state

`DepartureEventBus.lastVehicleEnteredAt` is in-memory only. If the process is killed between parking confirmation and the geofence exit, the timestamp is lost. `DetectParkingDepartureUseCase` then returns `Inconclusive` for steps 2 and 3. After `MAX_INCONCLUSIVE_RETRIES`, the worker falls through to `Confirmed` anyway — geofence exit is strong enough evidence on its own for an established parking session.

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


### BUG-FGS-001 / BUG-FGS-002 — Activity Recognition → FGS delivery via PendingIntent.getForegroundService()

**Commit:** to be filled after merge.

On Android 12+ (API 31+), calling `startForegroundService()` from a BroadcastReceiver triggered by Activity Recognition throws `ForegroundServiceStartNotAllowedException` — Google Play Services AR broadcasts are not on the system's FGS exemption list. On Android 14+, the same call site also throws `SecurityException` if `ACCESS_FINE_LOCATION` is not granted at call time.

**24 crashes (9 users, BUG-FGS-001) + 5 crashes (1 user, BUG-FGS-002)** in Crashlytics before this fix.

**Fix.** Remove `ActivityTransitionReceiver` as a foreground-service launcher entirely. `ActivityRecognitionManagerImpl` now registers two separate subscriptions:

- `STILL_ENTER` → `PendingIntent.getBroadcast()` → `ActivityTransitionReceiver` (no FGS needed — fires `coordinator.onStillDetected()`).
- `IN_VEHICLE_ENTER` + `IN_VEHICLE_EXIT` → `PendingIntent.getForegroundService()` → `ParkingDetectionService` (Play Services delivers with system privileges, bypassing the restriction).

`ParkingDetectionService.onStartCommand(ACTION_VEHICLE_TRANSITION)` extracts the `ActivityTransitionResult` from the intent, guards permissions, and routes:
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

**Root cause.** `ParkingDetectionCoordinator.evaluateConfidence()` showed the Low/Medium notification whenever `!state.mediumNotificationShown`, with no check for an activity-exit or STILL signal. A traffic stop long enough to pass the `slowPathGateMs` gate (90 s) was sufficient to trigger the notification even when the user was still in a moving vehicle.

**Fix.** Gate the Low/Medium notification on `vehicleExitConfirmed || activityStillDetected`. Without either activity-transition signal, brief stops are treated as traffic/errand stops and no notification is shown. The High-confidence CANDIDATE phase is unaffected — it always notifies.

### REFACTOR-DETECT-001 — Clean-up of service / coordinator / receiver flow

**Commit:** to be filled after merge.

Mechanical clean-up of the three classes that own the detection runtime: `ParkingDetectionCoordinator`, `ParkingDetectionService`, `ActivityTransitionReceiver`. No threshold or scoring changes; behaviour-preserving except for the `collectLatest → collect` swap noted below.

- **M1 — `collectLatest` → `collect` in coordinator.** The inner per-location block has no suspending I/O that should be cancelled when a newer fix arrives, so `collectLatest` was adding cancellation hazards (notifications could be cancelled mid-flight) without any benefit. With `collect`, each fix runs to completion before the next is processed, and the `withContext(NonCancellable) { notifyParkingConfirmation(...) }` workarounds added earlier became dead weight and were removed.
- **M2 — atomic state snapshot.** `_detectionState.update { ... }` followed by `val state = _detectionState.value` is racy: between the two lines another collector could mutate the state. Replaced with `val state = _detectionState.updateAndGet { ... }`, which returns the post-update snapshot atomically.
- **M3 — shared label helpers.** `activityLabel(Int)` and `transitionLabel(Int)` were duplicated inline in `ParkingDetectionService` and `ActivityTransitionReceiver`. Extracted to `composeApp/src/androidMain/.../detection/ActivityRecognitionLabels.kt` (internal helpers).
- **M4 — co-locate PendingIntent request codes.** `REQUEST_CODE = 101` lived in `ActivityTransitionReceiver` and was referenced by `ActivityRecognitionManagerImpl` — non-obvious coupling. Moved to `ActivityRecognitionManagerImpl.companion` as `STILL_REQUEST_CODE` alongside `VEHICLE_REQUEST_CODE`, with a comment explaining why both codes must remain distinct (`FLAG_UPDATE_CURRENT` would otherwise collide).
- **C2 — `guardPermissions(actionLabel)` helper in the service.** The same three-line "check permissions → showPermissionRevoked → stopSelf → return false" appeared inline in START_TRACKING, ACTION_VEHICLE_TRANSITION, and IN_VEHICLE_ENTER paths. Consolidated into a single method; call sites now read `if (!guardPermissions("LABEL")) return …`.

**Deferred.** Two larger questions surfaced during this refactor and are tracked in `docs/backlog/detection-improvements-2026-05-27.md`:
- *When does it make sense to kill the service?* — needs telemetry data before deciding (DECISION-SERVICE-LIFECYCLE-001).
- *Should BluetoothDetectionStrategy be folded into the Coordinator?* — architectural change; debate pending (DECISION-MERGE-BT-COORDINATOR-002).

### BUG-GARAGE-COLA-001 — Step Detector as canonical "user exited the car" signal

**Commit:** to be filled after merge.

**Symptom.** Long stops inside the car (queue at a garage entrance, traffic jam ≥ 5 min, drive-through line) were being auto-confirmed by the slow path. Pre-fix, once stopped duration ≥ 5 min, the Coordinator scored `High` and after the 5-minute observation window expired it confirmed with `reliabilitySlowPath`. The user was still in the car.

**Why "walking ≥ 30 m" was not the answer.** The Bluetooth strategy uses a 30 m walk as proof the user left the car, which works for outdoor street parking but fails in garages — the user typically walks ~4 m from the parking slot to a door, then takes an elevator. Distance is too coarse and venue-dependent to be the canonical signal in the Coordinator.

**Fix.** Introduce `StepDetectorSource` (`Sensor.TYPE_STEP_DETECTOR` on Android, empty stub on iOS — `CMPedometer` port deferred) and wire it as a sibling coroutine inside `ParkingDetectionCoordinator.invoke()`. Steps that arrive while `stoppedSince != null` increment `stepCount`. When `stepCount ≥ minStepsToConfirm = 8` during the CANDIDATE phase, confirm immediately with `reliabilityVehicleExit = 0.90f` — pedestrian steps are unambiguous evidence the user has exited the car, stronger than the AR exit transition (which is noisy on real hardware).

**Behaviour change.** The slow path no longer auto-confirms purely on time. CANDIDATE expiry now requires **either** step proof **or** the vehicle-exit signal; otherwise the candidate is discarded as likely cola/atasco. This trades a small surface of "user parked and ignored the notification" cases (still recovered next session) for elimination of the long-stop false positives.

**Wiring.**
- `commonMain/.../domain/sensor/StepDetectorSource.kt` — domain interface.
- `androidMain/.../detection/sensor/AndroidStepDetectorSource.kt` — `Sensor.TYPE_STEP_DETECTOR` via `callbackFlow`; returns `emptyFlow()` if hardware missing. ACTIVITY_RECOGNITION permission covers it (already required for AR transitions).
- `iosMain/.../detection/IosStepDetectorSource.kt` — `emptyFlow()` stub. CMPedometer backing tracked in the same backlog file.
- Koin: `AndroidDetectionModule` + `IosDetectionModule` provide the platform impl; `DomainModule` injects into `ParkingDetectionCoordinator`.
- `stepCount` reset to 0 whenever a driving signal arrives (`updateStopTracking` clears it alongside `stoppedSince`).

### BUG-SCOOTER-001 — VehicleType-aware detection + mismatch guard

**Commit:** to be filled after merge.

**Symptom.** Two failure modes for non-car users:
1. *User has a scooter/e-bike registered as default vehicle.* Activity Recognition fires `IN_VEHICLE_ENTER` (the API is noisy for two-wheeled microvehicles) → the Coordinator runs → after 5 min stopped at a destination the slow path auto-confirms a "parking" → the spot is published to the community. Scooters and e-bikes are dismounted on the sidewalk and never liberate a real parking slot, so every one of these confirmations is a false-positive published to the map.
2. *User has a car as default but rides their scooter to work today.* Same outcome — the active vehicle is `Ford Focus`, but the trip was actually on a Xiaomi Mi Pro. The app confirms a parking and saves it against the car.

**Fix — Level 1: vehicleType awareness.** `Vehicle` now carries `vehicleType: VehicleType ∈ { CAR, MOTORCYCLE, SCOOTER, BIKE }`. Persisted in Room (schema v4 via `MIGRATION_3_4`, column `vehicle_type` default `'CAR'`) and Firestore (`ifBlank → "CAR"` on read for backwards compatibility). UI exposes the choice via `VehicleTypeSelector` in vehicle registration/edit, mirroring the existing `VehicleSizeSelector` pattern.

`ParkingStrategyResolver.resolve()` short-circuits to `ParkingStrategy.NONE` when `vehicleType ∈ { SCOOTER, BIKE }` — the Coordinator never starts. `MOTORCYCLE` still resolves to BLUETOOTH/COORDINATOR (motorcycles do park). `ParkingDetectionService.handleVehicleTransition()` switches on the enum: COORDINATOR starts detection, BLUETOOTH and NONE both `stopSelf()`.

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
val confirmNow = when {
    isMismatch -> false                                              // suppress auto-confirm
    hasStepsProof -> true                                            // BUG-GARAGE-COLA-001
    windowElapsed && state.highCandidateHadVehicleExit -> true
    else -> false
}
```

28 km/h sits between the EU moped speed cap (~25 km/h) and typical urban car cruise (~40–50 km/h). 8 min is long enough that a real car trip would have hit at least one stretch above 28 km/h. When both thresholds hold AND the active vehicle is a `CAR`, auto-confirm is suppressed but the user-facing notification from CANDIDATE entry remains — the user can still tap "Yes I parked" to confirm manually, which is the desired manual-override path for the corner case where a user is genuinely riding a friend's scooter while their `CAR` is the default.

**Trade-off accepted.** A real car trip in extreme bumper-to-bumper traffic that never exceeds 28 km/h for 8+ min triggers the same gate. The notification still fires, so the user can override — we prefer "ask the user" over "publish a wrong spot." Thresholds live in `ParkingDetectionConfig.mismatchMaxSpeedKmh` / `mismatchMinSessionDurationMs` for future tuning once telemetry is available.

**Tests.** `ParkingStrategyResolverTest` covers all enum branches: SCOOTER → NONE (even with BT config), BIKE → NONE, MOTORCYCLE without BT → COORDINATOR, CAR with BT → BLUETOOTH, no default vehicle → COORDINATOR. Mismatch-guard unit tests deferred to a future integration ticket — they need `now` mocking + a CANDIDATE-phase fixture which the current test setup does not yet support.
