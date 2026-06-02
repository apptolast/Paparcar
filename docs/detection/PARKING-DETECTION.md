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

**Runtime owner:** `BluetoothDetectionService` (`LifecycleService`, `START_NOT_STICKY`,
`foregroundServiceType="location"`). The Service keeps the process alive while detection runs.
`BluetoothConnectionReceiver` does minimum work: resolve vehicleId from DB, then
`startForegroundService(ACTION_BT_DISCONNECTED)` or `startService(ACTION_BT_CONNECTED)`. [BT-REFACTOR-FGS-001]

`BluetoothParkingDetector.detectParking()` (suspend):

1. **Debounce** — `delay(BT_DISCONNECT_DEBOUNCE_MS = 30 s)`. Cancellable — if BT reconnects, the Service cancels the coroutine here before the delay returns (BT-005).
2. **GPS fix** — sample the location stream until `accuracy ≤ GPS_ACCURACY_THRESHOLD_M = 50 m`, or `GPS_SAMPLE_TIMEOUT_MS = 60 s` elapses. The first fix that meets the accuracy bar is the candidate parking location.
3. **Walking confirmation** — keep watching GPS until the user has moved `≥ DISTANCE_THRESHOLD_M = 30 m` from the candidate fix. This rules out "BT dropped while still in the car" cases (passenger left, head-unit died, etc.).
4. **Confirm** — `confirmParking(candidateFix, PARKING_DETECTION_RELIABILITY = 0.95f)`.

Abort-on-reconnect (BT-005): when `ACTION_ACL_CONNECTED` arrives, the Receiver starts the Service with `ACTION_BT_CONNECTED`. The Service calls `detectionJob?.cancel()` — the suspend function receives `CancellationException` at the active suspension point (`delay` or `Flow.first`) and exits cooperatively. The detector itself carries no cancellation flag.

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
  - If `speed ≥ repositionSpeedMps = 1.7 m/s` **AND** `accuracy ≤ repositionMaxAccuracyMeters = 15 m` for **three consecutive fixes**, `bestStopLocation` is cleared as a reposition burst. This is between sustained walking (~1.2 m/s, never crosses 1.7) and the driving threshold; it lets the coordinator distinguish a brief vehicle maneuver (wait + park into a freed spot) from GPS oscillation noise — see PARKING-001 in §2.

The 2.5 m/s driving ceiling is deliberately above typical walking speed (~1.4 m/s), so the captured parked-car position survives the user walking away on foot. The 1.7 m/s reposition floor is deliberately above walking too, gated by **three** consecutive fixes with accuracy ≤ 15 m. The reposition accuracy gate (15 m) is stricter than the driving gate (50 m) because at slow-maneuver speeds, GPS noise with acc > 15 m is far more common than genuine vehicle motion — field logs (Redmi Note 11, 2026-05-30) showed sustained 5-burst storms at acc=22–48 m that cleared `bestStopLocation` while the user was stationary. The 50 m gate is preserved only for the `isDriving` path (speed ≥ 2.5 m/s), where Redmi hardware can report legitimate driving at that accuracy level.

#### Confidence scoring

`CalculateParkingConfidenceUseCase` reads a `ParkingSignals` snapshot (`speed`, `stoppedDurationMs`, `gpsAccuracy`, `activityExit`, `activityStill`) and returns one of:

- `NotYet` — early or invalid signal combination.
- `Low(score)` — gates a confirmation notification (only if `vehicleExit` or `activityStill` signal present) but never auto-confirms.
- `Medium(score)` — same.
- `High(score)` — opens the CANDIDATE phase.

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

**Root cause.** `ParkingDetectionService.handleVehicleTransition()` guarded the restart with:

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
- `composeApp/src/androidMain/.../detection/service/ParkingDetectionService.kt` — state field, enum, debounce check in ENTER branch, OUT reset in EXIT branch, removal of stale `hasDetectedMovement` guard in COORDINATOR.

`hasDetectedMovement` itself is still used by the `ACTION_START_TRACKING` path and by the coordinator's internal `maxNoMovementMs` guard, so it stays on `ParkingDetectionCoordinator`.

### Field validation: `minStepsToConfirm=8` correctly rejects in-car social/idle stops (2026-05-28)

**Context.** During the 2026-05-27 field test, trip 5 on the Oppo had a long stationary period (22:00-22:08) where the coordinator entered CANDIDATE with `score=High(0.8)` (5 minutes stopped + speed=0 + excellent GPS accuracy + `vehicleExit=false`). The trip then resumed and only ended at 22:19 when the user actually parked at home.

**What the algorithm did.** During the 22:00-22:08 stop, `stepCount` rose to 5 (spurious accelerometer events from people moving inside the parked car) and froze there for the remaining ~3 minutes. The CANDIDATE-phase log line `⏳ CANDIDATE phase — elapsed=Nms window=300000ms steps=5/8` repeated identically until the car resumed motion, at which point `clearBestStopSpeedMps` cleared the candidate cleanly. **No `confirmParking` fired.** At 22:19 the real `IN_VEHICLE EXIT` arrived, the user walked to the door (90 steps in 60 s), and confirmation completed in **4 s** (22:20:24 HIGH → 22:20:28 SUCCESS via `hasStepsProof`).

**User confirmed scenario.** The 22:00-22:08 stop was a chat with a friend from the car — nobody got out. So the "5 steps" were noise, the threshold of 8 was the **only** thing standing between a phantom parking record and a correct rejection.

**Conclusion.** Keep `minStepsToConfirm = 8`. Field evidence shows:

1. The threshold blocks the most common false-positive class (long social/traffic stops in the car) without help from AR EXIT.
2. Real parkings still confirm within seconds because 8 steps takes ~6 s of normal walking.
3. The dual-path `confirmNow = hasStepsProof || (windowElapsed && highCandidateHadVehicleExit)` in `ParkingDetectionCoordinator` is the right shape — step proof gates fast confirms, AR EXIT + 2-min window remains the slower fallback.

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

**Root cause.** `ParkingDetectionService.startParkingDetection()` launched the coordinator in a `lifecycleScope.launch { }` block with:

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

**Why not `ParkingDetectionService`?** The Coordinator Service uses `START_STICKY` because Play
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
