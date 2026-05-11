# iOS Contracts — Expect/Actual & Platform Stubs

Reference document for the iOS implementation layer. Updated as Phase 6 progresses.

---

## 1. Expect/Actual Contracts

These declarations use Kotlin's `expect/actual` mechanism and require a concrete `actual` in every target.

| Contract | commonMain | androidMain | iosMain | Status iOS |
|----------|-----------|-------------|---------|------------|
| `isDebugBuild: Boolean` | `Platform.kt` | `BuildConfig.DEBUG` | `kotlin.native.Platform.isDebugBinary` | ✅ Tracks Xcode build config (Debug/Release) |
| `CrashReporter.recordNonFatal()` | `core/crash/CrashReporter.kt` | Firebase Crashlytics | Bridge to Swift `CrashReporterBridge` | ✅ Bridge-ready — Swift impl + Firebase iOS SDK to land on Mac |
| `PermissionsScreen()` | `presentation/permissions/PermissionsScreen.kt` | `rememberLauncherForActivityResult` | `IosPermissionRequester` | ✅ Real implementation |
| `defaultDistanceUnit()` | `presentation/util/DistanceUnit.kt` | `java.util.Locale` | `NSLocale` | ✅ Real implementation |
| `AppDatabaseConstructor` | `data/datasource/local/room/AppDatabase.kt` | Room KMP codegen | Room KMP codegen | ✅ Auto-generated |

---

## 2. Platform Stubs (DI Pattern)

These implement domain interfaces directly and are bound via Koin. No `expect/actual` involved.
The stub pattern lets iOS compile and run while native implementations are deferred to Phase 6.

### Detection Module (`IosDetectionModule`)

| Interface | Stub | Android equivalent | iOS native (Phase 6) |
|-----------|------|--------------------|----------------------|
| `ActivityRecognitionManager` | `IosActivityRecognitionManagerImpl` ✅ | `ActivityRecognitionManagerImpl` (Google Activity Transitions API) | Done — `CMMotionActivityManager` (snapshots synthesised into transitions; pipeline dispatch deferred) |
| `GeofenceManager` | `StubGeofenceManager` | `GeofenceManagerImpl` (GeofencingClient) | `CLCircularRegion` + `CLLocationManager` |
| `GeofenceEventBus` | `StubGeofenceEventBus` | `GeofenceEventBusImpl` (BroadcastReceiver) | `CLLocationManagerDelegate` |
| `DepartureEventBus` | `StubDepartureEventBus` | `DepartureEventBusImpl` (in-memory singleton) | In-memory singleton (no native API needed) |
| `ParkingEnrichmentScheduler` | `IosParkingEnrichmentScheduler` ✅ | `WorkManagerParkingEnrichmentScheduler` | Done — coroutine scope + retry (BGTaskScheduler persistence deferred) |
| `ReportSpotScheduler` | `IosReportSpotScheduler` ✅ | `WorkManagerReportSpotScheduler` | Done — coroutine scope + retry (BGTaskScheduler persistence deferred) |

### Platform Module (`IosPlatformModule`)

| Interface | Stub / Impl | Android equivalent | iOS native (Phase 6) |
|-----------|-------------|--------------------|----------------------|
| `LocationDataSource` | `IosLocationDataSourceImpl` ✅ | `AndroidLocationDataSourceImpl` (FusedLocationProvider) | Done — `CLLocationManager` |
| `GeocoderDataSource` | `IosGeocoderDataSourceImpl` ✅ | `AndroidGeocoderDataSourceImpl` | Done — `CLGeocoder` (reverse + forward, rate-limited ~50/min) |
| `PlacesDataSource` | `StubPlacesDataSource` (stub) | `OverpassPlacesDataSourceImpl` (HTTP) | MapKit / same HTTP endpoint |
| `AppNotificationManager` | `StubAppNotificationManager` (stub) | `AppNotificationManagerImpl` | `UNUserNotificationCenter` |
| `PermissionManager` | `IosPermissionManagerImpl` ✅ | `PermissionManagerImpl` | Done — `CLLocationManager` + `CMMotion` + `UNUserNotificationCenter` |
| `AppPreferences` | `IosAppPreferences` ✅ | `AndroidAppPreferences` | Done — `NSUserDefaults` |
| `AppDatabase` | Room KMP ✅ | Room KMP | Done — `BundledSQLiteDriver` + document directory |
| `BluetoothScanner` | `IosBluetoothScanner` ✅ | `AndroidBluetoothScanner` | Partial — `CBCentralManager` for state; `getBondedDevices()` is empty by iOS design (no system-wide bonded list). The pair-your-car UX needs a redesign on iOS. |

---

## 3. Services With No iOS Equivalent Needed

| Android-only component | Reason |
|------------------------|--------|
| `BootCompletedReceiver` | iOS has no boot broadcast |
| `ParkingDetectionService` (Foreground Service) | iOS uses Background Tasks + CLLocationManager |
| `ActivityTransitionReceiver` | Replaced by CMMotionActivityManager callbacks |
| `ParkingConfirmationReceiver` | Notification actions handled differently in iOS (UNNotificationAction) |

---

## 4. Phase 6 Implementation Order (Suggested)

1. ~~`LocationDataSource` → `CLLocationManagerLocationDataSource`~~ ✅ Done [`feature/IOS-LOC-001-cl-location-manager`]
2. `AppNotificationManager` → `UNUserNotificationCenterNotificationManager`
3. `GeofenceManager` + `GeofenceEventBus` → `CLCircularRegionGeofenceManager` — see [IOS-GEOFENCE-001]
4. ~~`ActivityRecognitionManager` → `CMMotionActivityRecognitionManager`~~ ✅ Done [`feature/IOS-AR-001-cm-motion-activity`]
5. ~~`ParkingEnrichmentScheduler` + `ReportSpotScheduler` → `BGTaskScheduler` wrappers~~ ✅ Done [`feature/IOS-BG-001-bg-task-scheduler`] (coroutine-scope impl; BGTaskScheduler persistence is a future follow-up)
6. ~~`GeocoderDataSource` → `CLGeocoderDataSource`~~ ✅ Done [`feature/IOS-GEOCODE-001-cl-geocoder`]
7. ~~`CrashReporter` → Firebase iOS Crashlytics SDK~~ ✅ Bridge done [`feature/IOS-CRASH-001-firebase-crashlytics`] — Swift wiring + Firebase iOS SDK pod still needed on Mac
8. ~~`isDebugBuild` → Xcode build configuration flag~~ ✅ Done [`feature/IOS-DEBUG-001-is-debug-build-flag`] — uses K/N stdlib `Platform.isDebugBinary`

---

*Last updated: Phase 0 — Foundations*
