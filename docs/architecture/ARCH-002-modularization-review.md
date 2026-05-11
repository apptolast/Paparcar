# ARCH-002 — Architecture & Modularization Review

**Status:** analysis only — no code changes.
**Date:** 2026-05-11.
**Audience:** solo developer (you), occasional AI pair.

This document is an honest read of the current architecture, the pain points it does or doesn't have at this stage of the project, and a comparison of three modularization paths. It ends with a recommendation tuned to a solo-dev KMP project in pre-production state. No code in this branch; if the recommendation is acted on it becomes its own ticket.

---

## 1. State of the codebase

**Source layout (rooted at `composeApp/src/`):**

| Source set | Files | Notes |
|---|---:|---|
| `commonMain` | ~201 | `domain` 58, `presentation` 89, `data` 25, `ui` 25, `di` 3, `core` 1 |
| `androidMain` | ~44 | `detection` 15, `presentation` 15, rest split across platform integrations |
| `iosMain` | ~30 | Most stubs now replaced (LOC, AR, BG, GEOCODE, GEOFENCE, NET, NOTIF, CRASH-bridge) |
| `commonTest` | rest | Fakes + use-case/coordinator/mapper/integration tests |
| Total `.kt` | 330 | One Gradle module: `:composeApp` |

**Architectural choices (per `CLAUDE.md`):**

- Clean Architecture in `commonMain`: `domain → data → presentation`.
- Domain layer is pure Kotlin (no Android/iOS imports). Use cases return `Flow<T>` or `AppResult<T>`.
- MVI in `presentation`: `State + Intent + Effect` sealed classes per screen.
- DI via Koin (one module per concern in `commonMain/di/` + per-platform overlays).
- Compose Multiplatform UI in `commonMain/presentation` and `commonMain/ui`.
- Persistence dual: Room KMP (offline-first) + Firestore (real-time sync).
- Detection pipeline split by strategy (Bluetooth deterministic vs Coordinator probabilistic).

**Build time (this branch, 2026-05-11):**

- `./gradlew :composeApp:assembleDebug` from clean: **~10 s** on Windows host. Configuration cache enabled (`Configuration cache entry stored.`).
- This is **not** a pain point at the project's current size. KMP+Compose builds typically scale super-linearly once modules and dependencies grow; 10 s today does not predict 10 s at twice the size.

---

## 2. Where the architecture pulls its weight

- **`domain` as pure Kotlin** is genuinely useful: use cases are platform-agnostic, every business rule is unit-testable without Android. The 58-file domain layer covers detection scoring, parking confirmation, geofencing contracts, location, notifications, preferences, error model. Tests in `commonTest` exercise them with fakes; this has caught real bugs (MAPPER-001, LOC-001 were both found via the testable domain).
- **MVI in presentation** is consistent across the app: every screen has its `State/Intent/Effect` trio. Predictability outweighs the boilerplate. No screen is "different from the others".
- **Platform interfaces in `domain/`** (`GeofenceManager`, `LocationDataSource`, `AppNotificationManager`, `BluetoothScanner`, `ConnectivityObserver`, `ActivityRecognitionManager`, `ParkingEnrichmentScheduler`, `CrashReporter` via `expect/actual` for the last one) — this is what made the recent iOS native implementations a series of straightforward swaps instead of rewrites. The contract-first approach is paying off.
- **Koin** stays out of the way. `androidPlatformModule` / `iosPlatformModule` overlay platform impls cleanly. Tests use fakes injected by hand, no Koin in tests — which is the right call.
- **Strategy separation in detection** (BluetoothDetectionStrategy vs CoordinatorDetectionStrategy) is well-bounded: they never mix signals, and the resolver makes the choice explicit. This document choice has held up under the last 2 weeks of bugfixing.

## 3. Where the architecture is overkill, blurred, or actively a problem

- **`expect/actual` is used sparingly** (5 contracts in `docs/ios-contracts.md`). Most platform variation is handled via Koin-bound interfaces (the "stub pattern"). That's a good thing — `expect/actual` is more rigid and the team converged on the right tool. No action needed, just an honest note.
- **`presentation` is the heaviest package (89 files in commonMain + 15 in androidMain)** and it's monolithic — `home`, `vehicles`, `history`, `settings`, `addspot`, `bluetooth`, `permissions`, `onboarding`, `splash`, `auth`, `app`. No internal boundaries; every screen can transitively reach every other screen's State/Intent/Effect. In practice you've avoided that, but the compiler doesn't know.
- **The `di` package mixes scopes**: `presentationModule`, `domainModule`, `dataModule` are in `commonMain/di`, the platform overlays sit in `androidMain/di`. Adding a new use case touches one module file, adding a new ViewModel touches another — that's expected for Koin and no fix is needed, but if the project ever migrates to `kotlin-inject` or Hilt the centralised lists become friction.
- **`UserParkingRepositoryImpl.saveSession`** does Room + Firestore writes inside a single `runCatching`. We've already filed PIPE-001 to extract Firestore to a worker; this is recognised tech debt.
- **`NotifyParkingConfirmationUseCase` was using `runBlocking` on Main** — fixed in FND-009, but it lingered for weeks. A symptom of a single big module: nobody fenced off `commonMain` against blocking primitives.
- **Migration chain in Room is 6 deep** (3→4, 4→5, 5→6, 7→8, 8→9, 9→10) for a pre-production app. Each migration is risk surface. DB-001 already files the cleanup.
- **Diagnostic coupling**: `PARKDIAG/*` log lines are now interleaved with production code in coordinator, service, ConfirmParking, NotifyParking. They're harmless (Napier is no-op without an antilog registered in release), but they're also code that has to be read every time someone opens these files. A more careful structure would have kept diagnostic instrumentation in decorators / aspect classes, registered only in debug. Not worth fixing now; flag for the eventual cleanup pass.
- **No module boundary between `data` and `presentation`** means ViewModels could, in principle, import a Room entity or a Firestore DTO directly. They don't (today), but the compiler doesn't enforce it. This is the single biggest "discipline gap" in the current layout.

## 4. Three modularization paths

### Option A — Status quo, harden boundaries inside `:composeApp`

Keep one Gradle module. Enforce layering with:
- Detekt/lint rules disallowing cross-package imports (`presentation` → `data`, `data` → `presentation`, etc.).
- KMP-aware Compose Konsist or ArchUnit-style rules in `commonTest` that fail the build on illegal imports.
- Document the layering in `CLAUDE.md` (already partially there) and rely on review discipline.

**Pros:**
- Zero structural change. No git churn, no risk of breaking the Koin DI graph.
- Build time stays at 10 s. Incremental builds stay fast (Kotlin Compiler's IC works well within one module).
- Solo-dev friendly: no module-per-feature mental overhead, no `api`/`implementation` decisions per dependency.

**Cons:**
- Rules-as-discipline doesn't replace structure: the linter has to be configured and respected, and a CI miss leaks through.
- Refactors that "feel local" can have global blast radius — touching `data/repository/UserParkingRepositoryImpl` recompiles every consumer in `presentation` because they're all in the same module.
- Build time will grow linearly with file count: at 500 files it'll be 20 s clean. Not a crisis, but worth noting.

### Option B — Mixed modularization (recommended for this project)

Three or four Gradle modules. Suggested split:

```
:composeApp                   (Android app + iOS framework entry, DI wiring)
  ├─ :presentation            (Compose UI, ViewModels, navigation, MVI types)
  ├─ :domain                  (pure Kotlin: models, use cases, interfaces)
  └─ :data                    (Room, Firestore, mappers, repository impls)
```

`:domain` is pure Kotlin (no Android, no Compose). `:data` depends on `:domain`. `:presentation` depends on `:domain` (NEVER on `:data` — repos are resolved by interface from `:domain`). `:composeApp` depends on all three and binds them via Koin.

**Pros:**
- The "discipline gap" becomes a compile error: `presentation` literally cannot `import io.apptolast.paparcar.data.…`. Same for the reverse. The boundary is enforced by the toolchain, not by a linter you might disable.
- Incremental builds get faster: editing a use case in `:domain` recompiles `:domain` + consumers only, not the whole presentation layer. With Gradle's parallel module compilation, this scales much better than IC inside a single module as the codebase grows.
- `:domain` becomes a candidate for a published artifact later (e.g. if you ever want to share business rules with a backend or a CLI).
- Forces awareness when adding cross-module dependencies — a deliberate `api`/`implementation` choice, which leads to cleaner public surfaces.

**Cons:**
- Real ceremony cost: 4 `build.gradle.kts` files instead of 1, KMP source-set declarations per module, `libs.versions.toml` referenced from each.
- Koin module wiring has to be split — each module exposes a `module { … }`, the app combines them. Manageable but a one-time refactor.
- KMP-specific gotchas: each module needs its own `kotlin { androidTarget(); iosX64(); iosArm64(); iosSimulatorArm64() }` block. With Compose Multiplatform, the `compose.runtime` dependency has to live in the right place per module.
- Tests have to be moved (commonTest in each module). The current single `commonTest` becomes 2-3 separate test source sets. Coverage doesn't change, but the cmdline to run "all tests" becomes a `--rerun-tasks` across modules.
- One-shot migration risk: a half-done split (e.g. `data` extracted but `presentation` still in `:composeApp`) leaves a worse state than either pure option.

### Option C — Feature modules

Split by feature on top of a `:core` set:

```
:composeApp
  ├─ :core-domain     (cross-feature contracts, GpsPoint, UserParking, …)
  ├─ :core-data       (Room database, Firestore, mappers shared across features)
  ├─ :feature-home    (HomeScreen + HomeViewModel + home-specific use cases)
  ├─ :feature-vehicles
  ├─ :feature-history
  ├─ :feature-settings
  ├─ :feature-addspot
  ├─ :feature-permissions
  └─ :feature-detection  (the parking detection pipeline as a feature module)
```

**Pros:**
- Maximum parallelism in builds — independent features compile in parallel.
- Clearest boundary: a feature owns its UI, VMs, and feature-specific business rules. Cross-feature accidental coupling becomes a compile error.
- Standard pattern in large Android teams. Plenty of references.

**Cons:**
- Heavy ceremony. For 330 files split across ~8 feature modules, the per-module overhead (gradle file + KMP target block + Koin module + test source set) is non-trivial.
- Feature boundaries here are not obvious: `detection` is consumed by `home` (active session display), `history`, `vehicles` (per-vehicle list). What's a "feature module" when features overlap on the same data?
- Solo dev = no team-scaling justification. This pattern's main payoff is letting two engineers work on independent features without merge conflicts. With one developer, it's pure ceremony.
- Premature for the size: cross-feature contract churn is high until the product stabilises. Re-drawing module boundaries every two months costs more than the build-time savings.

## 5. Recommendation

**Now (Q2 2026):** stay on **Option A** (one module, harden internal boundaries). Add Konsist or Detekt rules that catch:
- `presentation` importing `data.*`.
- `data` importing `presentation.*`.
- `commonMain` importing `androidMain.*` (Kotlin would error anyway, but the rule documents intent).
- Use of `runBlocking` outside `androidMain`/`iosMain` test helpers.

This is a 2-hour task. Pays for itself the first time it catches a wrong import.

**When to move to Option B:** any of these signals trips, take the cost:
- Clean `:composeApp:assembleDebug` exceeds 30 s on this hardware.
- A change to `data` causes a noticeable recompile lag in `presentation` (a couple of seconds extra in incremental builds).
- A second developer joins and needs to work in parallel without stepping on each other.
- The Room schema reset (DB-001) and the WorkManager refactor (PIPE-001) are merged — that's the natural seam to also split.

**Skip Option C entirely** at this size, with this team size. Reconsider only if the product grows to 4+ distinct features with negligible overlap (e.g. a separate "parking marketplace" feature added to the current "find/share spot" feature).

## 6. Action items

If you adopt the recommendation as stated:

1. Open `chore/ARCH-003-add-architecture-lint-rules` after this doc lands. Pick Konsist (simpler, KMP-friendly) or Detekt custom rules. Add 4–5 layering rules. Wire into CI.
2. Schedule a re-read of this doc in 3 months OR after PIPE-001 + DB-001 land — whichever comes first. Trigger Option B if signals are tripped.
3. No code action required from this ticket. Close once reviewed.

---

*Last reviewed: 2026-05-11. Re-evaluate after PIPE-001 + DB-001 ship.*
