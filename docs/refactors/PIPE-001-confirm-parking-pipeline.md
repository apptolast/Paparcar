# PIPE-001 — Move `confirmParking` side-effects out of the ForegroundService

> **Status**: pending — open `feature/PIPE-001-confirm-parking-pipeline` from master when ready.
> **Do not start until**: the foreground-service-hang bug (current `debug/parking-hang-diag` work) is resolved and merged. This refactor must not be entangled with the bugfix; mixing them obscures blame.

> **Nota 2026-06-05 — Rename Fase 1.5:** los nombres en este documento histórico han sido renombrados en el código actual. Equivalencia:
> - `ParkingSyncWorker` → `SaveNewParkingSessionWorker`
> - `saveSession` (repository) → `saveNewParkingSession`
> - `ParkingSyncScheduler.schedule()` → `ParkingSyncScheduler.enqueueSaveNewParkingSession()`

## 1. Why

The `ParkingDetectionService` foreground service today does two unrelated things:

1. **Live sensor work** — collect GPS, evaluate confidence, post the confirmation notification.
2. **Side effects on confirmation** — Room insert, Firestore upsert, geofence registration, "spot saved" notification, enqueue enrichment.

Bullet (2) is invoked synchronously from inside the coordinator's `collectLatest`, wrapped in `withContext(NonCancellable) { confirmParking(...) }`. That `NonCancellable` exists *precisely because* a half-saved confirmation must not be torn down if the user kills the app or the OS reclaims the service. But it has a brutal failure mode: if anything inside `confirmParking` hangs (Firestore offline, GMS misbehaving, BaseLogin auth deadlock), the coroutine **cannot be cancelled by anyone**. The service stays alive, the foreground notification persists indefinitely, and `stopSelf()` is never reached. We have already burned multiple debugging cycles on exactly this class of bug.

Pulling persistence + geofence + remote sync into a `WorkManager` pipeline makes the failure mode robust: WorkManager retries on its own schedule, survives process death, and the foreground service can stop the moment the coordinator decides parking is confirmed — no waiting for I/O.

## 2. Current flow (master, as of this doc)

```
ActivityTransitionReceiver (IN_VEHICLE_ENTER)
    ↓ startForegroundService(ACTION_START_TRACKING)
ParkingDetectionService.onStartCommand
    ↓ startForeground(DETECTION_NOTIFICATION_ID)
    ↓ lifecycleScope.launch
ParkingDetectionCoordinator.invoke(observeAdaptiveLocation())
    ↓ collectLatest { location ->
    ↓     evaluateConfidence(...)
    ↓     // confidence reaches HIGH or user taps Sí
    ↓     withContext(NonCancellable) {
    ↓         confirmParking(...)             ← can hang indefinitely
    ↓             dao.insert
    ↓             userProfileDataSource.saveParkingSession (Firestore)
    ↓             enrichmentScheduler.schedule (WorkManager)
    ↓             geofencingClient.addGeofences().await() (GMS Task)
    ↓             notificationPort.showParkingSpotSaved
    ↓     }
    ↓ }
    finally { stopSelf() }
```

## 3. Target architecture

Split the side effects into three buckets by latency tolerance:

| Latency | Action | Where |
|---|---|---|
| **Must be instant** | `dao.insert` (active session for UI), `geofencingClient.addGeofences` (must arm before user walks 100m), `showParkingSpotSaved` (UX feedback) | **Inline in `confirmParking` — but keep this short**. No network, no GMS Task `.await()` inside `withContext(NonCancellable)`. |
| **Tolerable async** | Firestore session upsert, mark previous session inactive in Firestore | **`ParkingSyncWorker`** (new) — `OneTimeWorkRequest`, `NETWORK_CONNECTED` constraint, exponential backoff, `setExpedited` so it runs ASAP. |
| **Already async** | Geocoder + POI enrichment | `EnrichmentWorker` (already exists, untouched). |

Result:

```
ParkingDetectionCoordinator.invoke
    ↓ on confirmation:
    ↓ dao.insert(session)                          ← Room, ~ms
    ↓ geofencingClient.addGeofences().await()      ← GMS, normally <1s
    ↓ notificationPort.showParkingSpotSaved        ← sync
    ↓ WorkManager.enqueue(ParkingSyncWorker)        ← non-suspending enqueue, <1ms
    ↓ WorkManager.enqueue(EnrichmentWorker)         ← already exists
    ↓ // coroutine returns immediately
    finally { stopSelf() }   ← service dies fast, notification removed
```

The `withContext(NonCancellable)` block stays — but its body is now bounded by Room + GMS Geofence + a notification post. Each of those has tighter native timeouts than Firestore. The Firestore call (which historically had unbounded blocking) has moved out.

## 4. Migration plan

### Step 1 — Extract `ParkingSyncWorker`

Create `composeApp/src/androidMain/kotlin/io/apptolast/paparcar/detection/worker/ParkingSyncWorker.kt`:

- Inputs (via `Data`): `userId`, `sessionId`, all fields needed to rebuild a `ParkingHistoryDto` without touching Room (so worker is self-contained — same pattern as `ReportSpotWorker`).
- Body:
  - Fetch the previous-active row from Room (or accept the previous session's ID via `Data`, mark it inactive in Firestore by ID — even cleaner).
  - `userProfileDataSource.saveParkingSession(userId, prevAsInactive)` if there was a previous.
  - `userProfileDataSource.saveParkingSession(userId, currentSession)`.
- Constraints: `NETWORK_CONNECTED`. Backoff: `EXPONENTIAL`, 30 s base, 5 retries.
- Tag: `"parking_sync"`. Unique work name: `"parking_sync_$sessionId"` with `REPLACE` policy.

Mirror the structure of `ReportSpotWorker` — same conventions, same `KoinComponent` injection, same `Data` schema discipline.

### Step 2 — Add a scheduler abstraction

Create `domain/service/ParkingSyncScheduler.kt`:

```kotlin
interface ParkingSyncScheduler {
    fun schedule(session: UserParking, previousSessionId: String?)
}
```

Android impl: `WorkManagerParkingSyncScheduler` — wraps `WorkManager.enqueueUniqueWork`. iOS stub returns Unit.

DI: bind in `AndroidDetectionModule` and `IosDetectionModule` next to the existing `ParkingEnrichmentScheduler` bindings.

### Step 3 — Refactor `UserParkingRepositoryImpl.saveSession`

Today it does Room + Firestore atomically (well, in one `runCatching`). Split:

- Keep `saveSession` doing **only Room** (`dao.getActive` → `dao.clearActive` → `dao.insert`). Returns `Result<Pair<UserParking, UserParking?>>` — `(saved, previous)` — so the caller knows what to push to Firestore.
- Add `syncSessionRemote(session, previousId)` *or* (preferred) move the Firestore writes entirely into `ParkingSyncWorker` and **delete** the remote-write code paths from the repository. The repo becomes Room-only; remote writes are workers.

### Step 4 — Wire `ConfirmParkingUseCase` to the new pipeline

Inside `ConfirmParkingUseCase.invoke`:

```kotlin
val saved = userParkingRepository.saveSession(session)        // Room only
if (saved.isFailure) return Result.failure(...)

geofenceService.createGeofence(...)                            // GMS, fast
notificationPort.showParkingSpotSaved(...)                     // sync

parkingSyncScheduler.schedule(session, previousSessionId)      // Firestore-bound
enrichmentScheduler.schedule(sessionId, lat, lon)              // Geocoder-bound
```

The `withContext(NonCancellable)` wrapper in the coordinator stays (Room + Geofence still need it), but its body runs in well-bounded time.

### Step 5 — Tests

- `ParkingSyncWorkerTest` (commonTest if possible, otherwise androidTest): given input data, calls `userProfileDataSource.saveParkingSession` correctly for both previous and current; retries on Firestore failure.
- Update `ConfirmParkingUseCaseTest`: replace direct Firestore write expectations with assertions that `parkingSyncScheduler.schedule(...)` was called.
- New fake: `FakeParkingSyncScheduler` mirroring `FakeParkingEnrichmentScheduler` (just records calls).

### Step 6 — Migration cleanup

- Remove unused `userProfileDataSource.saveParkingSession` calls from `UserParkingRepositoryImpl` once the worker fully owns Firestore.
- The repo's `syncParkingHistoryFromRemote(userId)` (login-time backfill from Firestore) **stays** — it's the read path.

## 5. Things to watch out for

1. **Geofence timing.** The geofence MUST be armed before the user walks ~50m away (depends on accuracy). `geofencingClient.addGeofences().await()` typically returns in <1s, but there's no timeout guard. Consider adding `withTimeoutOrNull(5_000) { ... }` around the await — if GMS doesn't respond, log + continue (worker will sync the session anyway, just no geofence-triggered departure).

2. **Don't merge `EnrichmentWorker` into `ParkingSyncWorker`.** They have different constraints (sync just needs network; enrichment can also benefit from `UNMETERED`). Different retry profiles. Different failure modes (sync failure = Firestore down; enrichment failure = Geocoder rate-limited). Keeping them separate is more diagnosable. They run in parallel; no chaining needed.

3. **Active session UI consistency.** `HomeViewModel` and friends observe `userParkingRepository.observeActiveSession()` — backed by Room. Since we still do `dao.insert` inline, UI sees the new active session immediately. No regression.

4. **Idempotency of `ParkingSyncWorker`.** Use `ExistingWorkPolicy.REPLACE` with unique name `"parking_sync_$sessionId"` so a duplicate enqueue (e.g. process restart between `dao.insert` and worker enqueue) overwrites cleanly. The session ID is already unique per parking event (UUID), so no collisions.

5. **Login-time history sync.** `syncParkingHistoryFromRemote` reads sessions from Firestore on login. After this refactor, sessions still land in Firestore via `ParkingSyncWorker`, so the read path is unchanged. Verify this in a multi-device test scenario before merging.

6. **The `var completed` + `takeWhile` issue in coordinator.** That's a separate problem (the foreground service won't stop until the next location emits even after `completed=true`). Either fix it as part of the bug we're chasing, or include the `combine(locations, _detectionState)` fix from the abandoned `gemini-changes` branch as part of PIPE-001. Don't ignore it; with this refactor, the service should stop fast on confirm — that's only true if the flow actually terminates promptly.

## 6. Out of scope (do NOT include in PIPE-001)

- Fixing the `var completed` flow termination — separate ticket.
- Removing `runBlocking` from `NotifyParkingConfirmationUseCase` — separate ticket (but if this turns out to be the cause of the current hang, may already be fixed by then).
- Mapping `detectionReliability` in `UserParking.toEntity()` — pre-existing bug, separate ticket.
- Changing the BT detection path — `BluetoothParkingDetector` also calls `ConfirmParkingUseCase` and benefits from the same change for free, but doesn't need its own work in this refactor.

## 7. Acceptance criteria

- [ ] `ParkingSyncWorker` exists with constraints + backoff + tag.
- [ ] `ConfirmParkingUseCase` no longer makes Firestore calls inline.
- [ ] `withContext(NonCancellable)` body in coordinator is bounded by Room + Geofence + notification only.
- [ ] Killing the app between `confirmParking` and `ParkingSyncWorker` running still results in the session reaching Firestore once network is available.
- [ ] All existing tests pass; new tests cover the worker.
- [ ] Manual smoke test: drive → park → confirm → notification stops within <2s of confirmation tap (vs. current >10s).

## 8. Open questions to resolve before starting

- Does GitLive Firestore SDK already buffer writes when offline, making explicit retry partially redundant? If yes, the worker is still useful for surviving process death, but retry config can be relaxed.
- Should `ParkingSyncWorker` also write the `Spot` (departed-spot) report, currently in `ReportSpotWorker`? They're conceptually paired (park + release). Probably no — keep separate workers per event type. Revisit if both become trivial.
