# Diagnostic session — 2026-05-11

Tickets surfaced during the debug session for the "blue foreground-service notification stays forever" bug. The original bug stopped reproducing after a clean reinstall of the debug APK on both an OPPO CPH2371 and a Redmi Note 11 — likely a stale state from local DB migrations that cleared itself when the new build force-stopped + re-initialized the app. Even so, the session uncovered five concrete issues worth addressing as separate tickets.

Branch names follow the project convention (`bugfix/PREFIX-NNN-…`, `feature/PREFIX-NNN-…`). Numbers are suggested; renumber if they collide with anything in flight.

## Status legend
✅ **Done** — merged to master (commit/branch noted).
🔵 **Branch ready** — work complete on its branch, awaiting review/merge.
⚪ **Pending** — not started.
🟡 **Blocked** — waiting on the user (data wipe, design call, etc.).

---

## 1. `bugfix/LOC-001-parked-spot-captures-walk-destination` — ✅ Done

**Merged:** 2026-05-11, commit `e153d6e` (branch deleted post-merge).

**Priority:** High — directly degrades the core product (wrong spot saved).
**Reproducible on:** Redmi Note 11 (master) and Samsung A53 (master), tests on 2026-05-11. Saved spot was the user's home (~5 m from front door) instead of the actual parking position a few hundred metres away. Same symptom on both devices → not an OEM quirk, a coordinator-logic bug.

**Where:** `composeApp/src/commonMain/kotlin/io/apptolast/paparcar/domain/coordinator/ParkingDetectionCoordinator.kt:251-283` — `updateStopTracking`.

**What:** `bestStopLocation` is updated for the entire duration of the stop (any GPS fix with `speed < STOPPED_SPEED_THRESHOLD_MPS = 1 m/s` and better `accuracy` overwrites the running best). The sibling `stoppedFixes` list IS gated to `initialStopWindowMs = 30 s`, but `bestStopLocation` is not. Walking speed (~1.4 m/s) is below `clearBestStopSpeedMps = 2.5 m/s`, so walking from the car to the user's destination does **not** reset the candidate location — it shadows it with whatever the user's new GPS reads when they sit down at home.

**Fix:** gate `bestStopLocation` updates with `withinInitialWindow` so it freezes after 30 s, matching `stoppedFixes`. Optionally, additionally reject updates whose distance from the existing `bestStopLocation` exceeds ~30 m as a defence-in-depth.

**Effort:** Small (1-line change + a coordinator test for the walking-after-park scenario).

---

## 2. `bugfix/MAPPER-001-detection-reliability-not-mapped` — ✅ Done

**Merged:** 2026-05-11, commit `1a97dea` (branch deleted post-merge). Added 4 round-trip tests covering set/unset cases.

**Priority:** Low — does not break functionality, but it kills reliability-based analytics.
**Evidence:** Database Inspector dump on 2026-05-05 — every row in `parking_sessions` had `detectionReliability = NULL`, including new ones written by the current coordinator.

**Where:** `composeApp/src/commonMain/kotlin/io/apptolast/paparcar/data/mapper/ParkingSessionMapper.kt:53-70` — `UserParking.toEntity()`.

**What:** The domain → entity mapper omits `detectionReliability = detectionReliability`. The entity falls back to its default (`null`). The reverse mapping (`UserParkingEntity.toDomain`) and the Firestore-DTO mapping (`ParkingHistoryDto.toEntity`) DO map it. Pre-existing bug — not introduced this week.

**Fix:** Add the missing line. Add a mapper test that round-trips reliability.

**Effort:** Trivial.

---

## 3. `feature/HIST-001-vehicles-screen-rework` — ✅ Done

**Merged:** 2026-05-12, commit `2d7e44f` (branch deleted post-merge). HorizontalPager + ScrollableTabRow replaces LazyColumn card list; VehiclePageContent hosts scoped HistoryViewModel per tab; VEHICLE_DETAIL route removed; HistoryContent accepts modifier param.



**Priority:** Medium — UX rework bundled with the legacy-session backfill discovered in this session.
**Scope:** Two parts in one branch — the data fix and the screen redesign go together because they touch the same surface.

### 3.a — Data: legacy sessions with `vehicleId = NULL`

**Evidence:** All `parking_sessions` rows on the OPPO have `vehicleId = NULL`. The new "Mis Vehículos" screen filters with `observeSessionsByVehicle(vehicleId)` so legacy rows never appear anywhere.

**Where:**
- Migration: `composeApp/src/androidMain/kotlin/io/apptolast/paparcar/di/AndroidPlatformModule.kt` — `MIGRATION_9_10` adds the column nullable, no backfill.
- Filter: `composeApp/src/commonMain/kotlin/io/apptolast/paparcar/data/datasource/local/room/UserParkingDao.kt:30-31` — `observeByVehicle`.

**Fix:** Backfill at first launch post-migration: rows with `vehicleId = NULL` get assigned to the user's `isDefault = true` vehicle. If no default exists, fall back to the only vehicle (single-vehicle accounts). If multiple vehicles and no default, leave NULL and surface them in an "Unassigned" tab (see 3.b). Idempotent — re-runs are no-ops.

Made obsolete by DB-001 if you choose to reset the schema before this lands; in that case skip 3.a entirely.

### 3.b — UX: flatten the Vehicles screen

**Current state:** Top-level `Column` with N elevated cards (one per vehicle). Tap a card → nested screen with `Details` / `Historial` tabs. Two layers of navigation for the same primary action.

**Desired state:**
- Top-level `HorizontalPager` + `TabRow` — one tab per vehicle, swipe between them. No card → details → tabs chain.
- Each tab page is **a single scrollable layout** containing, top-down:
  - Vehicle details (brand, model, plate, size, active marker, edit/delete actions) — flat surface, minimal elevation, just visual grouping. Drop the heavy elevated card look.
  - Parking history for that vehicle below, no nested screen, no separate tab.
- Match Material 3 minimalist style: tonal containers over shadow elevation, generous spacing, no redundant titles per section.

**Where:** `composeApp/src/commonMain/kotlin/io/apptolast/paparcar/presentation/vehicles/` — `VehiclesScreen.kt`, `VehicleDetailScreen.kt`, `VehicleDetailsTab.kt`, plus relevant components in `composeApp/src/commonMain/kotlin/io/apptolast/paparcar/ui/components/VehicleCard.kt` (probably delete or repurpose).

**Out of scope for this ticket:** changing the VM contract (`VehiclesViewModel` stays as-is; `HistoryViewModel` reuse per tab as the pager pages need their own scope).

**Effort:** Medium (UX rework + tests for both data backfill and the pager structure).

---

## 4. `feature/PIPE-001-confirm-parking-pipeline` — ✅ Done

**Merged:** 2026-05-11, merge commit `2f4eef2` (branch deleted post-merge). Work commit `371ce85` + docs follow-up `a8d1a14`. Tests green, Android compile clean.

**Priority:** Medium — preventive refactor, not a bug. Eliminates the root cause class of the "service hangs forever" failure mode.

**Where:** Full plan in `docs/refactors/PIPE-001-confirm-parking-pipeline.md`. Open the branch from `master` **after** the diagnostic-logger branch is merged.

**What:** Move Firestore writes (the only network-bound suspending step in `confirmParking`) into a `ParkingSyncWorker`. Coordinator's `withContext(NonCancellable) { confirmParking(...) }` becomes bounded by Room + GMS Geofence + a local notification — none of which can hang indefinitely.

**Effort:** Medium. Detailed step-by-step in the refactor doc.

**Follow-ups deliberately scoped out — see tickets 9 and 10 below.**

---

## 5. `bugfix/FND-009-runblocking-in-notify-usecase` — ✅ Done

**Merged:** 2026-05-11, commit `b05ef61` (branch deleted post-merge). `invoke` is now `suspend`; ripple confined to `evaluateConfidence` in the coordinator.

**Priority:** Medium-High — latent ANR. We captured 1.2-1.4 s of Main-thread blocking in the diagnostic logs.

**Evidence:** Diagnostic captures on 2026-05-10 (emulator):
```
PARKDIAG/Notify → entering runBlocking { observeDefaultVehicle.firstOrNull() }
PARKDIAG/Notify ← runBlocking returned, vehicleName=Seat Ibiza     (1.35 s later)
```
That delay was on a stationary, well-fed emulator. On a real device with cold Room, contended IO, or auth-token refresh, this can spike past 5 s and trigger an ANR.

**Where:** `composeApp/src/commonMain/kotlin/io/apptolast/paparcar/domain/usecase/notification/NotifyParkingConfirmationUseCase.kt:14-23`.

**What:** The use case is non-suspend; it wraps `vehicleRepository.observeDefaultVehicle().firstOrNull()` in `runBlocking` to read the vehicle name for the notification title. The caller (`ParkingDetectionCoordinator.evaluateConfidence`) is already inside a coroutine — `runBlocking` is gratuitous.

**Fix:** Convert `invoke` to `suspend operator fun invoke(...)`. Make `evaluateConfidence` and its caller suspending — the change ripples a couple of frames up but they're all already inside coroutines. The same fix was already part of the abandoned `gemini-changes` branch (commit `d0a4ece`); cherry-pick that hunk if convenient.

**Effort:** Small. Ripple confined to coordinator's internal calls.

---

## 6. `chore/DB-001-reset-room-schema-baseline` — ✅ Done

**Merged:** 2026-05-12, commit `ecf352b`. Version reset to 1, all MIGRATION_* objects removed, fallbackToDestructiveMigration already in place.



**Priority:** Medium — opportunistic cleanup while the app is still pre-release.
**Trigger:** Pre-production state. No external users have data we need to preserve.

**Where:**
- `composeApp/src/commonMain/kotlin/io/apptolast/paparcar/data/datasource/local/room/AppDatabase.kt` — version + entities list.
- `composeApp/src/androidMain/kotlin/io/apptolast/paparcar/di/AndroidPlatformModule.kt` — `MIGRATION_3_4`, `MIGRATION_4_5`, `MIGRATION_5_6`, `MIGRATION_7_8`, `MIGRATION_8_9`, `MIGRATION_9_10`.

**What:** Collapse the Room schema history. Delete all `MIGRATION_*` objects and the `.addMigrations(...)` chain. Reset `@Database(version = N)` to `1` (or whatever number you prefer as the new baseline). Existing devices nuke their DB on next launch via `fallbackToDestructiveMigration(true)` — that flag is already there, so legacy installs reset cleanly. The user will manually wipe accounts + Firestore data to start clean.

**Why it's worth it:** Six migrations in commonMain that nobody benefits from. They're code, they're risk, and the chain has already been the indirect cause of `HIST-001` and our zombie-session investigation. Resetting now buys us a clean slate for `PIPE-001` and any future schema changes.

**Pre-requisites:**
- Confirm with the user that **all** existing test accounts have been wiped (Firestore + local installs).
- HIST-001 sub-task 3.a (data backfill) becomes unnecessary if this lands first.

**Effort:** Trivial code change, but coordinate with user-data wipe so we don't lose track of devices mid-flight.

---

## 7. `chore/ARCH-002-architecture-and-modularization-review` — ✅ Done

**Merged:** 2026-05-11, commit `4288d71` (branch deleted post-merge). Output lives at `docs/architecture/ARCH-002-modularization-review.md`. Recommendation: stay monolithic, add lint rules — see ARCH-003 below if you act on it.

**Priority:** Low (exploratory), but high leverage if acted on.
**Note:** `ARCH-001` was the cancelled `CurrentUserProvider` over-engineering attempt; numbering forward.

**What:** Write up a short analysis (1-2 pages) covering:
- Current architecture honest assessment: Clean + MVI in commonMain, platform layer in androidMain, Koin for DI. Where it's pulling its weight, where it's overkill, where boundaries blur.
- Module structure today: one `:composeApp` module with sourcesets. Compare against options:
  - **Status quo** — keep monolithic `:composeApp`, just discipline package boundaries.
  - **Mixed modularization** — extract `:domain` (pure Kotlin), `:data`, `:detection`, `:presentation` as separate Gradle modules. Faster incremental builds, enforced dependency rules, better testability isolation.
  - **Feature modules** — split by feature (`:feature-home`, `:feature-vehicles`, …) on top of a `:core` set.
- Concrete recommendation with trade-offs (build time, ceremony, ROI given solo-dev context).

**Inputs to gather first:**
- Current `clean + assemble` time for the app.
- Pain points the user feels day-to-day (slow builds? hard to test in isolation? circular deps?).

**Output:** `docs/architecture/ARCH-002-modularization-review.md`. No code changes — that's a separate ticket if the recommendation is acted on.

**Effort:** 2-4 hours of analysis + write-up.

---

## 8. `chore/REL-001-release-build-pipeline` — ⚪ Pending

**Priority:** Medium — gates being able to share the app publicly.

**Scope:** Everything needed to produce a signed release artifact reliably.

- **Signing config**: generate an upload keystore, wire it into `composeApp/build.gradle.kts` `signingConfigs` (read from `gradle.properties` / env vars, NEVER commit the keystore).
- **R8 / ProGuard**: review `isMinifyEnabled = false` in the release block — flip on minification and shrinking, add keep rules for Koin, GitLive Firebase, Compose, Napier, kotlinx-serialization. Verify nothing in release builds crashes due to obfuscation (especially reflection-based DI).
- **Version scheme**: define `versionCode` / `versionName` strategy. Tag commits, automate bumping (e.g. via Gradle task or CI).
- **Release-only configs**: confirm `BuildConfig.DEBUG = false` paths are right — no debug notifications, no `FileAntilog` (already gated), no verbose Napier antilogs.
- **Distribution**: today there's a Firebase App Distribution workflow (`.github/workflows/distribute.yml` from QA-007). Decide whether release goes through App Distribution → internal testers → Play Store internal track, or straight to Play Console.
- **Play Console listing**: store listing assets, privacy policy URL, data-safety form, permissions justification (especially `ACCESS_BACKGROUND_LOCATION`, `ACTIVITY_RECOGNITION`).
- **Smoke test**: install a signed release APK on a real device, run a parking detection end-to-end, verify Crashlytics still receives events and Firestore writes succeed.

**Pre-requisites:**
- DB-001 ideally lands first so the first release ships with a clean schema baseline.
- PIPE-001 strongly recommended before any "first impression" public release — it removes the worst class of hang we've seen.

**Effort:** Medium-large. Signing + R8 keep rules are the highest-risk steps (a single missing keep rule can crash release on first launch).

---

## 9. `feature/PIPE-002-sync-clear-active-and-update-location` — ✅ Done

**Merged:** 2026-05-12, commit `ec89592` (branch deleted post-merge). clearActive + updateLocationInfo are Room-only; ClearActiveSyncWorker + LocationUpdateSyncWorker handle Firestore. Also fixed PIPE-001 latent bug (partial DTO with lat=0.0 overwriting coordinates).

**Priority:** Low — same failure mode as PIPE-001 (in-flight Firestore call can hang), but on much less critical paths.

**What:** `UserParkingRepositoryImpl.clearActive()` and `updateLocationInfo()` still call `userProfileDataSource.saveParkingSession` / `updateParkingSessionLocation` directly inside `runCatching`. Same hang-on-Firestore risk as the original PIPE-001 case, but the contexts are less critical:
- `clearActive()` runs on the release path (user departs → spot becomes public). Currently invoked from `ReleaseActiveParkingSessionUseCase`, which is not called from a foreground service. ANR risk is low, but inconsistency with PIPE-001 is real.
- `updateLocationInfo()` is already invoked from inside a worker (`EnrichParkingSessionWorker`), so it's already async from the user's perspective — but the worker itself can be killed mid-Firestore-write, leaving Room and Firestore inconsistent.

**Fix sketch:**
- Add `ParkingSyncScheduler.scheduleClearActive(sessionId, userId)` or extend the existing `schedule()` signature with a clear-active variant.
- Add `ParkingSyncScheduler.scheduleLocationInfoUpdate(sessionId, userId, address, placeInfo)`.
- Have `clearActive()` do only the Room write (clear `isActive=1`) and schedule the Firestore reconciliation via the worker.
- Same for `updateLocationInfo()`.

**Effort:** Small once PIPE-001 lands — same pattern, fewer fields per payload.

---

## 10. `chore/PIPE-003-parking-sync-worker-test` — ✅ Done

**Merged:** 2026-05-12, commit `daeeb2d` (branch deleted post-merge). 9 tests via Robolectric + work-testing covering all 3 workers: success, retry, permanent failure, missing input.

**Priority:** Low — `ParkingSyncWorker.doWork()` is currently exercised only by manual smoke testing.

**What:** Unit test the worker body. The challenge: `doWork()` runs inside a real `CoroutineWorker` with `WorkerParameters` injected by WorkManager. Two viable approaches:
- **Robolectric in `androidUnitTest`**. Use `androidx.work:work-testing` (`TestWorkerBuilder`, `TestListenableWorkerBuilder.from(...)`). Verify: success path writes new session to Firestore; previous-id path writes inactive marker first; retry on `Result.retry()` after fake failure; final failure after `MAX_RETRY_ATTEMPTS`.
- **Instrumented test** under `androidInstrumentedTest`. Heavier infra; only worth it if Robolectric mismatches production behaviour.

**Effort:** Small-medium. Robolectric setup for KMP+Compose Multiplatform projects sometimes needs nudging (compose dependencies, manifest merger) — budget some friction time.

---

## 11. `chore/ARCH-003-add-architecture-lint-rules` — ✅ Done

**Merged:** 2026-05-12, commit `e38ab18` (branch deleted post-merge). Konsist 0.17.3 in `androidUnitTest`; 5 rules: presentation→data, data→presentation, domain→data/presentation, runBlocking in commonMain, UseCase placement.

**Priority:** Low-medium — preventive. Catches discipline gaps at compile time.

**Where:** Recommended by `docs/architecture/ARCH-002-modularization-review.md`, section 5.

**What:** Add 4–5 layering rules via Konsist (recommended — KMP-friendly, runs as commonTest) or Detekt custom rules:
- `presentation` cannot import `data.*`.
- `data` cannot import `presentation.*`.
- `commonMain` cannot import `androidMain.*` (defence in depth; Kotlin already errors but documents intent).
- `domain` cannot import `data.*` or `presentation.*`.
- Optional: ban `runBlocking` outside `androidMain`/`iosMain` test helpers (we just shipped FND-009 to fix one — a rule prevents the next).

**Effort:** 2–3 hours including CI wiring.

---

## 12. `feature/FLOW-001-splash-driven-first-run-flow` — ✅ Done

**Merged:** 2026-05-12, commit `ca3debf` (branch deleted post-merge). Splash now sequences auth → profile sync (remote-first) → vehicle sync → startRoute. New first-run order: Login → Onboarding → PermissionsRationale → Permissions → VehicleSizeExplainer → VehicleRegistration → Home. UserProfile.defaultVehicleId replaces the DataStore flag. observeVehicles/observeDefaultVehicle made auth-reactive at the repo level — fixes a NoSuchElementException crash on Google sign-in caused by a race between AuthState.Authenticated and BaseLogin's session cache. UserProfileDataSource renamed to RemoteUserProfileDataSource. SplashViewModel logs each bootstrap step.



**Priority:** High — current flow shows VehicleRegistration to existing users on data-clear/re-login and lets users reach Home without a vehicle via the PermissionsRationale "Skip" button.

**Root cause:** `appPreferences.hasVehicleRegistered` is a local DataStore flag that diverges from the real repository state. The startRoute computed in `MainActivity` is based on this flag, not on the actual presence of vehicles in Room/Firestore.

**Design (approved 2026-05-12):**

- **Splash decides everything.** `SplashViewModel` waits for: (a) AuthState.Authenticated, (b) `getOrCreateUserProfile()` OK, (c) first emission of `observeVehicles()` (already does lazy sync from Firestore), (d) `permissionState.value`, (e) `isOnboardingCompleted` from DataStore. Exposes `startRoute: String?` — native splash stays up until non-null. Zero flash.

- **First-run flow:** Login → Onboarding → VehicleRegistration → PermissionsRationale → Permissions → Home.

- **Returning user:** Splash computes the correct start straight to Home if everything is in place.

- **Eliminate `hasVehicleRegistered`** from DataStore. Truth is `vehicleRepository.observeVehicles().isNotEmpty()`.

- **Gate in `MainAppNavigation` only handles permissions** (mid-session permission revocation). No mid-session guard for vehicle/onboarding because: (a) splash guarantees correct entry, (b) the only vehicle cannot be deleted (UX rule below).

- **Delete-last-vehicle UX rule:** When the user has exactly 1 vehicle, the delete icon stays visible but with reduced alpha (~0.4f). Tapping it surfaces a snackbar in the user's locale explaining they must keep at least one registered vehicle. New string `my_car_cannot_delete_last_vehicle` added to all 9 supported languages.

- **`origin=onboarding`** removed from `VEHICLE_REGISTRATION` route (decision post-completion moves to a central `nextRouteAfter(appState)` helper). `origin=vehicles` kept — vehicles-tab entry still pops back contextually.

- **`PermissionsRationaleScreen.onSkip` removed** — it bypassed the vehicle invariant.

**Where:**
- `composeApp/src/commonMain/kotlin/io/apptolast/paparcar/presentation/app/SplashViewModel.kt` — extend with startRoute calc.
- `composeApp/src/androidMain/kotlin/io/apptolast/paparcar/MainActivity.kt` — remove startRoute calc, switch `keepOnScreenCondition` to `splashViewModel.isReady`.
- `composeApp/src/commonMain/kotlin/io/apptolast/paparcar/App.kt` — drop startRoute param, simplify gate, add `nextRouteAfter`.
- `composeApp/src/commonMain/kotlin/io/apptolast/paparcar/domain/preferences/AppPreferences.kt` — drop `hasVehicleRegistered` + `setVehicleRegistered`.
- `VehicleRegistrationViewModel` and test — drop `setVehicleRegistered` call.
- `VehiclesViewModel` + `VehiclePageContent` + `VehiclesEffect` — implement translucent-delete + snackbar UX.
- `composeResources/values*/strings.xml` (×9 locales).

**Effort:** Medium. Largest risk is the SplashViewModel test matrix (5 invariant combinations).

---

## 13. `feature/UI-001-vehicle-registration-redesign` — ⚪ Pending

**Priority:** Medium — the form works but feels utilitarian compared to the rest of the post-FLOW-001 first-run flow (Onboarding, PermissionsRationale, VehicleSizeExplainer all share a polished pattern; the registration form does not).

**Where:** `composeApp/src/commonMain/kotlin/io/apptolast/paparcar/presentation/vehicle/VehicleRegistrationScreen.kt`.

**Scope:** Visual + UX rework of the form. Keep the existing `VehicleRegistrationViewModel`/State/Intent/Effect contract — this is presentation-only.

**Ideas worth exploring (decide during design):**
- Hierarchy: size is the only required field (per the explainer); brand/model are optional. Make size the visually dominant input — large segmented selector or icon picker per `VehicleSize`. Brand/model relegated to a collapsed "optional details" group.
- Iconography per size (🛵 MOTO, 🚙 SMALL, 🚗 MEDIUM, 🚐 LARGE, 🚚 VAN) instead of plain text radios.
- Primary CTA at the bottom, navigation-bar-aware, mirroring the explainer's pattern.
- Consider whether the BT pairing step lives here (currently in a separate screen) or stays separate. Probably stays separate — keep this ticket scoped.

**Non-goals:**
- Changing the data model.
- Adding new vehicle attributes (color, license plate UI changes, etc.) — separate ticket if wanted.

**Effort:** Small-medium. Pure UI iteration.

---

## 14. `feature/UI-002-my-vehicles-redesign` — ⚪ Pending

**Priority:** Medium — the Vehicles tab post-HIST-001 works (HorizontalPager + inline history) but visual treatment can be tightened.

**Where:**
- `composeApp/src/commonMain/kotlin/io/apptolast/paparcar/presentation/vehicles/VehiclesScreen.kt`
- `composeApp/src/commonMain/kotlin/io/apptolast/paparcar/presentation/vehicles/VehiclePageContent.kt`

**Scope:** Visual rework of the pager page header (`VehicleDetailsHeader`), the empty state, and the per-vehicle action chips. ViewModel/State stay as-is.

**Ideas worth exploring:**
- The vehicle header card currently uses tonal elevation + brand/model + size + detection chip + edit/delete icons. Iterate towards a more "identity card" feel: large size icon, prominent brand/model, smaller secondary actions.
- The active-vehicle badge could be promoted (e.g. coloured border on the card, not just an inline chip).
- Empty state could include an explainer link to remind why a vehicle is needed (consistency with VehicleSizeExplainer copy).
- ScrollableTabRow → consider PrimaryScrollableTabRow / SecondaryScrollableTabRow (current is deprecated per compiler warning).
- Translucent delete icon when there's only 1 vehicle is already in place — keep it.

**Non-goals:**
- Changing the multi-vehicle paging model (HorizontalPager stays).
- Touching the history list (already reworked in HIST-001).

**Effort:** Small-medium. Pure UI iteration.

---

## 15. `bugfix/MAPPER-002-vehicleid-lost-in-firestore-roundtrip` — ⚪ Pending

**Priority:** High — breaks the per-vehicle history tabs introduced in HIST-001. Every cold start nulls every row's `vehicleId`, so `VehiclePageContent` shows an empty history under every tab.

**Reproducible on:** Redmi Note 11 + OPPO CPH2371, every cold start since FLOW-001 wired `syncParkingHistoryFromRemote` into the splash bootstrap.

**Where:**
- `composeApp/src/commonMain/kotlin/io/apptolast/paparcar/data/datasource/remote/dto/ParkingHistoryDto.kt` — DTO has no `vehicleId` field.
- `composeApp/src/commonMain/kotlin/io/apptolast/paparcar/data/mapper/ParkingSessionMapper.kt:85-118` — `UserParking.toParkingHistoryDto` and `ParkingHistoryDto.toEntity` silently drop `vehicleId`.
- `composeApp/src/androidMain/kotlin/io/apptolast/paparcar/detection/worker/ParkingSyncWorker.kt:99-141` — `buildRequest` + `Data.toParkingHistoryDto` don't carry `vehicleId` in the work payload.

**What:** Same mapper-omission pattern as `MAPPER-001` (detection reliability). `vehicleId` is written correctly to Room by `UserParking.toEntity()` on the confirm-parking path, but every Firestore round-trip drops it. On the next cold start, `GetOrCreateUserProfileUseCase` → `syncParkingHistoryFromRemote` → `dao.insert(dto.toEntity())` with `REPLACE` conflict overwrites the local row with `vehicleId = null`. After that, `observeSessionsByVehicle(vehicleId)` matches nothing and the per-vehicle history tab is empty.

**Fix:**
1. Add `val vehicleId: String? = null` to `ParkingHistoryDto`.
2. Map `vehicleId` both ways in `ParkingSessionMapper.kt` (`UserParking.toParkingHistoryDto` and `ParkingHistoryDto.toEntity`). Also fix the latent `detectionReliability` omission in `toParkingHistoryDto` — same bug class as MAPPER-001, just on the write path.
3. Add `KEY_NEW_SESSION_VEHICLE_ID` to `ParkingSyncWorker` so the payload survives WorkManager restart.
4. Round-trip tests (mirror the `MAPPER-001` tests): DTO ↔ Entity preserves `vehicleId`, worker payload preserves `vehicleId`.
5. No backfill — the user wipes any pre-fix sessions from Firestore once (pre-release state, same approach as DB-001). All post-fix writes carry `vehicleId` correctly.

**Effort:** Small. Same surface area as MAPPER-001.

---

## Out of scope for this list

- The actual cause of the original "blue notification hangs forever" symptom is unconfirmed. Once any of the above fixes ships and the user has driven a few times in production without recurrence, mark this session as closed.
- The diagnostic logging infrastructure itself (`FileAntilog`, `PARKDIAG` log lines, debug manifest override) stays as long as the branch `debug/parking-hang-diag` lives. Strip before merging anything diagnostic-related to master, OR keep `FileAntilog` behind `BuildConfig.DEBUG` (already the case) if you want it permanently available.
