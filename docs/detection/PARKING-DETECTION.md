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
| **BluetoothDetectionStrategy** | Car BT disconnects → debounce → GPS fix → user walks ≥ 30 m | 0.95 (deterministic) | User has paired BT with their car AND BT is on |
| **CoordinatorDetectionStrategy** | Activity Recognition + GPS stream → confidence scoring | 0.75 / 0.90 / 1.00 (probabilistic) | Everyone else — no BT, BT off, or no paired device |

The choice is made in `ParkingStrategyResolver` and honours the **BT-supersedes** invariant: any vehicle in the fleet with a paired BT device routes through BLUETOOTH, decoupling "primary vehicle for identity fallbacks" (`isActive`) from "vehicle the Coordinator monitors" (derived). See ARCH-MONITORING-002 in §2.

```kotlin
enum class ParkingStrategy { NONE, BLUETOOTH, COORDINATOR }

suspend fun resolve(): ParkingStrategy {
    val vehicles = vehicleRepository.observeVehicles().first()

    // BT wins independently of which vehicle is primary.
    val hasAnyBtPaired = vehicles.any {
        it.bluetoothDeviceId != null && it.vehicleType !in NON_PARKING_TYPES
    }
    if (hasAnyBtPaired && bluetoothScanner.isBluetoothEnabled()) {
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
4. **Confirm** — `confirmParking(candidateFix, config.reliabilityBluetooth = 0.95f)`. [DET-F-01]

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
