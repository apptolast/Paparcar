# iOS Contracts — Expect/Actual & Platform Stubs

Reference document for the iOS implementation layer. Updated as Phase 6 progresses.

---

## 1. Expect/Actual Contracts

These declarations use Kotlin's `expect/actual` mechanism and require a concrete `actual` in every target.

| Contract | commonMain | androidMain | iosMain | Status iOS |
|----------|-----------|-------------|---------|------------|
| `isDebugBuild: Boolean` | `Platform.kt` | `BuildConfig.DEBUG` | `false` (hardcoded) | ⚠️ Always false — use compiler flag in Phase 6 |
| `CrashReporter.recordNonFatal()` | `core/crash/CrashReporter.kt` | Firebase Crashlytics | No-op | ⏳ Needs Firebase iOS SDK (Phase 6) |
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
| `ActivityRecognitionManager` | `StubActivityRecognitionManager` | `ActivityRecognitionManagerImpl` (Google Activity Transitions API) | `CMMotionActivityManager` |
| `GeofenceManager` | `StubGeofenceManager` | `GeofenceManagerImpl` (GeofencingClient) | `CLCircularRegion` + `CLLocationManager` |
| `GeofenceEventBus` | `StubGeofenceEventBus` | `GeofenceEventBusImpl` (BroadcastReceiver) | `CLLocationManagerDelegate` |
| `DepartureEventBus` | `StubDepartureEventBus` | `DepartureEventBusImpl` (in-memory singleton) | In-memory singleton (no native API needed) |
| `ParkingEnrichmentScheduler` | `StubParkingEnrichmentScheduler` | `WorkManagerParkingEnrichmentScheduler` | `BGProcessingTaskRequest` |
| `ReportSpotScheduler` | `StubReportSpotScheduler` | `WorkManagerReportSpotScheduler` | `BGAppRefreshTask` + Firestore |

### Platform Module (`IosPlatformModule`)

| Interface | Stub / Impl | Android equivalent | iOS native (Phase 6) |
|-----------|-------------|--------------------|----------------------|
| `LocationDataSource` | `StubLocationDataSource` (stub) | `AndroidLocationDataSourceImpl` (FusedLocationProvider) | `CLLocationManager` |
| `GeocoderDataSource` | `StubGeocoderDataSource` (stub) | `AndroidGeocoderDataSourceImpl` | `CLGeocoder` |
| `PlacesDataSource` | `StubPlacesDataSource` (stub) | `OverpassPlacesDataSourceImpl` (HTTP) | MapKit / same HTTP endpoint |
| `AppNotificationManager` | `StubAppNotificationManager` (stub) | `AppNotificationManagerImpl` | `UNUserNotificationCenter` |
| `PermissionManager` | `IosPermissionManagerImpl` ✅ | `PermissionManagerImpl` | Done — `CLLocationManager` + `CMMotion` + `UNUserNotificationCenter` |
| `AppPreferences` | `IosAppPreferences` ✅ | `AndroidAppPreferences` | Done — `NSUserDefaults` |
| `AppDatabase` | Room KMP ✅ | Room KMP | Done — `BundledSQLiteDriver` + document directory |

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

1. `LocationDataSource` → `CLLocationManagerLocationDataSource` (blocks all GPS-dependent features)
2. `AppNotificationManager` → `UNUserNotificationCenterNotificationManager`
3. `GeofenceManager` + `GeofenceEventBus` → `CLCircularRegionGeofenceManager`
4. `ActivityRecognitionManager` → `CMMotionActivityRecognitionManager`
5. `ParkingEnrichmentScheduler` + `ReportSpotScheduler` → `BGTaskScheduler` wrappers
6. `GeocoderDataSource` → `CLGeocoderDataSource`
7. `CrashReporter` → Firebase iOS Crashlytics SDK
8. `isDebugBuild` → Xcode build configuration flag

---

*Last updated: Phase 0 — Foundations*
