# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Paparcar** is a Kotlin Multiplatform (KMP) parking-spot sharing app. It uses Compose Multiplatform for UI and targets Android (primary) with iOS structure prepared. The app detects when users park/leave via Activity Recognition + GPS and shares spot availability with nearby users.

## Build & Run Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (ProGuard minification enabled)
./gradlew assembleRelease

# Install on connected device/emulator
./gradlew installDebug

# Run all unit tests
./gradlew test

# Run debug unit tests only
./gradlew testDebugUnitTest

# Run instrumented tests (requires connected device)
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

**Build config:** Compile SDK 36, Min SDK 26, Target SDK 36, JVM target 17, Kotlin 2.1.21.

## Architecture: Clean Architecture + MVI

The project strictly follows **Clean Architecture** with three layers plus a platform layer:

### Layer Structure

```
commonMain/kotlin/io/apptolast/paparcar/
├── presentation/   ← Compose screens + MVI ViewModels
├── domain/         ← Business logic, interfaces, use cases, models
├── data/           ← Repository implementations, datasources, mappers
└── di/             ← Koin modules (PresentationModule, DomainModule, DataModule)

androidMain/kotlin/io/apptolast/paparcar/
├── detection/      ← Activity Recognition, Accelerometer, Foreground Service
├── location/       ← FusedLocationProviderClient implementation
├── notification/   ← Android NotificationManager implementation
├── permissions/    ← Runtime permission handling
└── di/             ← AndroidPlatformModule, AndroidDetectionModule
```

### MVI Pattern (applied to every screen)

Each screen has three files: `*State`, `*Intent`, `*Effect`, and a `*ViewModel` extending `BaseViewModel<S, I, E>`:

- **State** — immutable data class representing the full UI state
- **Intent** — sealed class of user actions sent to the ViewModel
- **Effect** — sealed class of one-shot side effects (navigation, permission requests)

```kotlin
// Pattern used across HomeViewModel, MapViewModel, HistoryViewModel
class HomeViewModel(...) : BaseViewModel<HomeState, HomeIntent, HomeEffect>()
```

### Dependency Injection (Koin)

- `PresentationModule` — ViewModels
- `DomainModule` — UseCases
- `DataModule` — Repositories, Firebase, local/remote datasources
- `AndroidPlatformModule` — Room DB, Location, Notifications (androidMain)
- `AndroidDetectionModule` — Parking detection use cases (androidMain)

Koin is initialized in `PaparcarApp.kt` (Application class). ViewModels are injected with `koinViewModel()`.

### Platform Abstraction

Platform-specific features are defined as interfaces in `commonMain/domain/` and implemented in `androidMain/`:

- `PlatformLocationDataSource` → `AndroidLocationDataSourceImpl` (FusedLocationProviderClient)
- `AppNotificationManager` → `AppNotificationManagerImpl`
- `PermissionManager` → `PermissionManagerImpl`
- `ActivityRecognitionManager` → `ActivityRecognitionManagerImpl`

Room database uses `expect/actual` — builder defined in `androidMain/di/AndroidPlatformModule.kt`.

## Key Technologies

| Purpose | Library | Version |
|---|---|---|
| UI | Compose Multiplatform | 1.8.0 |
| DI | Koin | 4.1.1 |
| Local DB | Room KMP | 2.8.4 |
| Remote DB | Firebase (GitLive KMP SDK) | 2.4.0 |
| Location | Play Services Location | 21.3.0 |
| Async | Kotlin Coroutines + Flow | 1.9.0 |
| Time | kotlinx.datetime | 0.7.1 |
| Serialization | kotlinx.serialization | 1.8.1 |
| KSP | Room code generation | 2.1.21-2.0.1 |

All versions are managed via `gradle/libs.versions.toml`.

## Error Handling

All errors are typed via `PaparcarError` sealed class hierarchy in `commonMain/domain/error/`:

```
PaparcarError
├── Location (PermissionDenied, ProviderDisabled, Unknown)
├── Network (NoConnection, Timeout, ServerError, Unknown)
├── Database (NotFound, WriteError, Unknown)
└── Detection (ActivityRecognitionUnavailable, PermissionDenied)
```

`Result<T>` is used throughout the stack. Domain errors are mapped in repository implementations.

## Android-Specific Components

- **`DrivingTrackingService`** — `LifecycleService` foreground service for GPS polling. Entry point for active tracking sessions.
- **`ActivityTransitionReceiver`** — `BroadcastReceiver` that fires on `IN_VEHICLE`/`STILL` transitions and starts/stops `DrivingTrackingService`.
- **`BootCompletedReceiver`** — Restores tracking after device reboot.
- **Notification channels:** `DETECTION` (LOW), `UPLOAD` (DEFAULT), `DEBUG` (HIGH) — all require Min SDK 26.

Location streaming uses `callbackFlow` wrapping FusedLocationProviderClient callbacks. Two priority modes:
- High Accuracy: 5s interval, 2s min update
- Balanced Power: 30s interval, 15s min update

## Important Conventions

- **No platform imports in domain layer** — `commonMain/domain/` must stay pure Kotlin with no `android.*` imports.
- **Time:** Always use `Clock.System.now().toEpochMilliseconds()` with `@file:OptIn(ExperimentalTime::class)`.
- **Foreground services only** — background services are not used.
- **Naming:** Screens → `*Screen`, ViewModels → `*ViewModel`, Repo impls → `*RepositoryImpl`.
- **Room KSP** is configured for `kspAndroid`, `kspIosX64`, `kspIosArm64`, `kspIosSimulatorArm64`.

## Principles: SOLID & Clean Architecture

Rules enforced across the codebase:

### SRP — Single Responsibility
- One use case = one responsibility. Do not merge unrelated logic into a single use case.
- All mutable detection state lives in a single `private data class *State` updated atomically via `MutableStateFlow.update {}`. Never scatter multiple `var` fields across a class.

### OCP — Open/Closed
- Magic numbers and algorithm thresholds belong in an injectable `*Config` data class (e.g. `ParkingDetectionConfig`). Changing the algorithm does not require touching business logic — only the config.

### ISP — Interface Segregation
- Interfaces must have ≤ 5 cohesive methods. Split read-only queries from write commands if different clients need different subsets.

### DIP — Dependency Inversion
- **Zero `getKoin().get()` calls at runtime.** This is the Service Locator anti-pattern: hidden dependencies that are impossible to test.
- `BroadcastReceiver` subclasses that need DI must implement `KoinComponent` and declare dependencies as `private val foo: Foo by inject()` properties.
- Cross-component event buses (e.g. `GeofenceEventBus`) are defined as interfaces in `commonMain/domain/` and registered as `single<>` singletons in the DI module so both producer and consumer share the same instance.

### Flows
- Every `Flow` that is `collect`ed / `collectLatest`-ed / `launchIn`-ed **must** have a `.catch {}` operator immediately before the terminal call to prevent silent termination on upstream errors.

### MVI Intents
- Never declare an intent subclass without a corresponding `when` branch in `handleIntent`.
- `handleIntent` must be exhaustive — no `else -> {}` fallback. A sealed class with unhandled branches is a bug waiting to happen.