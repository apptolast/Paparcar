# Paparcar — Architecture

> Documento consolidado. Sustituye a `Paparcar_Arquitectura.md`, `docs/architecture/ARCH-002-modularization-review.md` y partes de `docs/ios-contracts.md` (todos movidos a `docs/archive/`).
> Última auditoría: **2026-05-24**.

---

## Stack

| Capa | Tecnología |
|------|------------|
| UI | Compose Multiplatform 1.10.2 (Material3) |
| Arquitectura | Clean Architecture + MVI (State + Intent + Effect) |
| Navegación | Navigation Compose KMP 2.9.2 |
| DI | Koin 4.1.1 (core + compose + viewmodel) |
| DB local | Room KMP 2.8.4 + sqlite-bundled |
| Backend | Firebase via GitLive 2.4.0 (Auth, Firestore, Crashlytics) |
| Maps | KMP Maps (Software Mansion) 0.8.1 — Google Maps Android, Apple Maps iOS |
| Auth | BaseLogin 1.0.16 (librería propia, JitPack) |
| Async | Kotlinx Coroutines 1.10.2 + Flow |
| Logging | Napier 2.7.1 |
| Background | WorkManager 2.11.1 (Android) — BGTaskScheduler pendiente en iOS |

**Targets:** Android `minSdk 26 / target 36 / compile 37`. iOS `arm64 + simulatorArm64`. Kotlin 2.3.10. JVM 17.

---

## Diagrama de capas

```
┌──────────────────────────────────────────────────────────────┐
│  UI (commonMain/ui + presentation)                           │
│  HomeScreen · VehiclesScreen · HistoryScreen · SettingsScreen│
│  PaparcarMapView · PapButton · PapCard · GlassSurface · ...  │
└────────────┬─────────────────────────────────────────────────┘
             │ State / Intent / Effect
┌────────────▼─────────────────────────────────────────────────┐
│  Presentation (commonMain/presentation)                      │
│  HomeViewModel · VehiclesViewModel · HistoryViewModel · ...  │
│  AppViewModel (auth + bootstrap) · SplashViewModel           │
└────────────┬─────────────────────────────────────────────────┘
             │ UseCases (Result<T> | Flow<T>)
┌────────────▼─────────────────────────────────────────────────┐
│  Domain (commonMain/domain) — KOTLIN PURO                    │
│  usecase/{location,parking,spot,user,zone,notification}      │
│  model/{Spot,Vehicle,UserParking,Zone,ParkingConfidence,…}   │
│  service/{GeofenceManager,EventBus,Scheduler interfaces}     │
│  coordinator/ParkingDetectionCoordinator                     │
└────────────┬─────────────────────────────────────────────────┘
             │ Repository interfaces
┌────────────▼─────────────────────────────────────────────────┐
│  Data (commonMain/data)                                      │
│  repository/*Impl  →  Room DAO  ⇄  Firestore (GitLive)       │
│  mapper/* (Entity ↔ Domain ↔ DTO)                            │
└────────────┬─────────────────────────────────────────────────┘
             │ expect/actual
┌────────────▼─────────────────────────────────────────────────┐
│  Platform                                                    │
│  androidMain: WorkManager · FusedLocation · ActivityRecog    │
│               · Foreground Service · BroadcastReceivers      │
│               · BluetoothScanner · GeofencingClient          │
│  iosMain:     CLLocation · CMMotion · CBCentralManager       │
│               · UNUserNotificationCenter · NWPathMonitor     │
└──────────────────────────────────────────────────────────────┘
```

---

## Flujo de datos canónico

**Lectura observable** (ej.: spots cercanos en mapa):
```
HomeScreen
  ↓ collectAsStateWithLifecycle
HomeViewModel.state: StateFlow<HomeState>
  ↑ combine
ObserveNearbySpotsUseCase: Flow<List<Spot>>
  ↑
SpotRepository (offline-first)
  ↳ Room SpotDao.observeNearby(...): Flow<List<SpotEntity>>   ← Source of Truth
  ↳ Firestore listener → upsert en Room                        ← Sync layer
```

**Comando con efecto** (ej.: confirmar parking):
```
BluetoothParkingDetector / ParkingDetectionCoordinator
  ↓
ConfirmParkingUseCase
  ├→ UserParkingRepository.insertActive(...)   (Room, sync)
  ├→ GeofenceManager.register(...)             (Play Services / CLLocationManager)
  ├→ AppNotificationManager.notifyConfirmed()
  └→ ParkingSyncScheduler.enqueueSaveNewParkingSession(session, previousSessionId)
         ↓
       SaveNewParkingSessionWorker (WorkManager / coroutine en iOS)
         ↓
       Firestore.collection("userParkings").set(...) + opcional update({isActive:false}) en previa
```

---

## Estructura de paquetes

```
io.apptolast.paparcar
├── domain/                          (commonMain, Kotlin puro, sin Android)
│   ├── model/                       Spot, Vehicle, UserParking, Zone,
│   │                                ParkingConfidence, ParkingSignals,
│   │                                ParkingDetectionConfig, …
│   ├── coordinator/                 ParkingDetectionCoordinator
│   ├── detection/                   ParkingStrategyResolver
│   ├── usecase/
│   │   ├── parking/                 Confirm·DetectDeparture·ObserveParked·
│   │   │                            ReleaseActiveSession·UpdateLocation·
│   │   │                            CalculateConfidence
│   │   ├── location/                GetOne·GetInfo·ObserveAdaptive·Search
│   │   ├── spot/                    ObserveNearby·ReportReleased·SendSignal
│   │   ├── user/                    GetOrCreate·Bootstrap·DeleteAccount
│   │   ├── zone/                    Observe·Save·Delete
│   │   └── notification/            NotifyParkingConfirmation
│   ├── repository/                  interfaces
│   ├── service/                     GeofenceManager, EventBus, Scheduler interfaces
│   ├── session/                     LocalSessionCache
│   ├── preferences/                 AppPreferences, ThemeMode
│   ├── connectivity/                ConnectivityObserver
│   └── error/                       PaparcarError
│
├── data/                            (commonMain)
│   ├── datasource/
│   │   ├── local/room/              AppDatabase v3, DAOs, Entities
│   │   └── remote/                  FirebaseDataSource, DTOs
│   ├── repository/                  *Impl (offline-first)
│   ├── mapper/                      Entity↔Domain↔DTO
│   └── session/                     RoomLocalSessionCache
│
├── presentation/                    (commonMain, MVI)
│   ├── home/                        HomeScreen + State/Intent/Effect/ViewModel
│   │   └── sections/                header · map · sheet (subcomponentes)
│   ├── history/
│   ├── vehicles/                    + vehicle/ (registration, size explainer)
│   ├── settings/
│   ├── bluetooth/                   BluetoothConfigScreen
│   ├── permissions/                 Permissions + Rationale + GpsDisclaimer
│   ├── onboarding/
│   ├── map/                         ParkingLocationScreen (in-app picker)
│   ├── app/                         AppViewModel (auth + bootstrap) + SplashViewModel
│   ├── base/                        BaseViewModel<S,I,E>
│   ├── preview/                     FakeData para Compose Preview
│   └── util/                        DistanceUnit, LocaleApplier, ExternalNav…
│
├── ui/                              (commonMain, design system)
│   ├── theme/                       Color, Typography, Shapes, Spacing,
│   │                                Borders, VehicleAccentPalette, Theme
│   ├── components/                  PapButton/Card/TextField/Badge,
│   │                                GlassSurface, PaparcarMapView,
│   │                                PaparcarMapMarkers, AppBottomNavigation,
│   │                                ConfirmationBottomSheet, ChipsPaparcar*…
│   ├── auth/                        PaparcarAuthSlots
│   └── icons/                       PaparcarIcons
│
├── di/                              (commonMain)
│   ├── DomainModule.kt              UseCases + ParkingDetectionConfig + Coordinator
│   ├── DataModule.kt                Repos + Room + Firestore wiring
│   └── PresentationModule.kt        ViewModels
│
└── (androidMain | iosMain)/
    ├── di/                          AndroidPlatformModule + AndroidDetectionModule
    │                                IosPlatformModule + IosDetectionModule
    ├── detection/                   service, workers, receivers (Android)
    │                                CL/CM bridges (iOS)
    ├── location/                    Fused/CL adapter
    ├── bluetooth/                   BluetoothScanner + ConnectionReceiver (Android)
    ├── notification/                AppNotificationManager impl
    ├── permissions/                 PermissionManager impl
    ├── preferences/                 AppPreferences impl
    └── connectivity/                ConnectivityObserver impl
```

---

## Decisiones técnicas clave

### 1. Domain puro Kotlin
`domain/` no debe importar `android.*` ni `platform.*` (iOS). Verificado: no hay violaciones. Las dependencias de plataforma se exponen como `interface` en domain y se implementan via `expect/actual` o Koin bindings en `androidMain`/`iosMain`.

### 2. Offline-first con dual write
**Room es Source of Truth.** `ConfirmParkingUseCase` escribe a Room **sincrónicamente**, y `SaveNewParkingSessionWorker` propaga a Firestore con reintentos. La lectura siempre observa Room; Firestore se merge upstream via listener.

Rationale: la app debe funcionar sin red (el usuario aparca y pierde cobertura en parking subterráneo).

### 3. Dual detection strategy
Dos estrategias **independientes**, nunca se mezclan (regla en `CLAUDE.md`):

- **`BluetoothParkingDetector`** — determinista: BT disconnect → GPS fix con accuracy ≤ 50m (timeout 60s) → distance threshold 30m → confirm con `reliability=0.95`. Anti-bounce de 30s contra paradas de tráfico.
- **`ParkingDetectionCoordinator`** — probabilístico: Activity Recognition + GPS stream → `CalculateParkingConfidenceUseCase` → fase CANDIDATE con ventana de observación (3 min si `vehicleExitConfirmed`, ~20 min slow path) → auto-confirm con `reliability=0.75–0.90`.

Resolución en `ParkingStrategyResolver` según `vehicle.bluetoothDeviceId != null && isBluetoothEnabled`.

Detalle algorítmico completo en `docs/PARKING_DETECTION.md` y `docs/detection/PARKING-DETECTION.md`.

### 4. WorkManager para side-effects diferidos
Eventos críticos (sync Firestore, geocoding, validación de departure) no se ejecutan inline porque pueden tardar y fallar por red. Se delegan a workers con `BackoffPolicy.EXPONENTIAL` y constraints (CONNECTED solo donde necesario).

Workers actuales (Android):
- `SaveNewParkingSessionWorker` — sesión nueva (`set()`) + desactiva previa (`update({isActive:false})`) en Firestore (constraint: CONNECTED)
- `ClearActiveParkingSessionWorker` — `update({isActive:false})` sobre la sesión liberada (constraint: CONNECTED)
- `UpdateParkingSessionAddressAndPlaceWorker` — `update({address, placeInfo})` tras enrichment (constraint: CONNECTED)
- `EnrichParkingSessionWorker` — geocoder + places lookup (sin constraint, best-effort) — encadena `UpdateParkingSessionAddressAndPlaceWorker`
- `DepartureDetectionWorker` — valida geofence + AR + sesión (3 retries max)
- `ReportSpotWorker` — publica spot liberado

En iOS estos están como **stubs** (sin BGTaskScheduler) — ver `docs/IOS_PLAN.md`.

### 5. MVI estricto
Cada screen tiene `<Name>State`, `<Name>Intent` (sealed class de acciones del usuario), `<Name>Effect` (efectos one-shot: navegación, toast). `BaseViewModel<S,I,E>` centraliza `state: StateFlow`, `handleIntent(intent: I)` y `emitEffect(effect: E)`.

### 6. Koin con módulos separados por capa y plataforma
- `commonMain/di/`: `DomainModule`, `DataModule`, `PresentationModule`
- `androidMain/di/`: `AndroidPlatformModule`, `AndroidDetectionModule`
- `iosMain/di/`: `IosPlatformModule`, `IosDetectionModule`

Permite arrancar app/test con el subconjunto necesario.

### 7. Una sola Activity, navegación Compose
`MainActivity.kt` carga `App()` composable que contiene `NavHost`. `singleTask` launchMode + `configChanges=orientation|screenSize|…` evita recrear la Activity en rotación.

---

## Modelos de datos clave

| Modelo | Resumen |
|--------|---------|
| `Spot` | Plaza comunitaria publicada. `location`, `type` (AUTO_DETECTED / MANUAL_REPORT), `status`, `confidence`, `enRouteCount`, `ttl` |
| `UserParking` | Sesión propia de aparcamiento. `vehicleId` (NN), `location`, `geofenceId`, `isActive`, `detectionMethod` (BLUETOOTH / COORDINATOR / MANUAL) |
| `Vehicle` | Vehículo del usuario. `brand`, `model`, `licensePlate?`, `bluetoothDeviceId?`, `isDefault`, `size`, accentColorIndex |
| `UserProfile` | Perfil Firebase. `userId`, `email`, `displayName`, `photoUrl` |
| `Zone` | Zona regulada / favorita. `location`, `radius`, `icon`, `name` |
| `ParkingConfidence` | `High` / `Medium` / `Low` con score y motivos |
| `ParkingSignals` | DTO de entrada al cálculo: `speed`, `stoppedDurationMs`, `gpsAccuracy`, `activityExit`, `activityStill` |
| `ParkingDetectionConfig` | Umbrales del coordinator (singleton inyectable) |

**Invariante crítica:** toda `UserParking` debe tener `vehicleId` no nulo. No existe "histórico sin vehículo".

---

## Persistencia

### Room (AppDatabase v3)
Entidades: `UserParkingEntity`, `UserProfileEntity`, `VehicleEntity`, `SpotEntity`, `ZoneEntity`. DAOs paralelos.

⚠️ **Sin migraciones explícitas definidas.** Cualquier cambio de esquema requiere `Migration(from, to)` o destruirá data en producción. Ver BUGS_AND_DEBT.md §11.

### DataStore Preferences (Android)
Theme mode, language, distance unit. Implementado en `AndroidDataStoreAppPreferences.kt`.

⚠️ **Dos implementaciones rivales:** `AndroidAppPreferences.kt` (SharedPreferences legacy) y `AndroidDataStoreAppPreferences.kt`. La selección no está clara en DI. Ver BUGS_AND_DEBT.md §2.

### NSUserDefaults (iOS)
`IosAppPreferences.kt` con bridge a `NSUserDefaults` + migración perezosa desde clave legacy. Implementación real, no stub.

### Firestore
Colecciones principales: `userParkings`, `spots`, `vehicles`, `zones`, `userProfiles`. Acceso vía `FirebaseDataSourceImpl` y repos. Listeners se reactivan en `Offline→Online` mediante `reconnectTick` en `HomeViewModel`.

---

## Errores y resultados

```kotlin
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val exception: Throwable) : AppResult<Nothing>()
}
```

UseCases retornan `Result<T>` (stdlib) o `AppResult<T>` (interno). Flows usan `.catch { e → … }` para aislar errores de red sin matar el flujo.

`PaparcarError` (sealed) describe errores de negocio (Auth, Permission, Location, Network, Storage, Detection).

---

## Convenciones obligatorias

(Resumen — la versión vinculante está en `CLAUDE.md`)

- **Strings**: NUNCA hardcoded. Todo a `composeResources/values/strings.xml` con keys en inglés `feature_component_description`. Mínimo EN+ES.
- **Magic numbers**: NUNCA inline. `private companion object` con UPPER_SNAKE_CASE.
- **Logs**: Napier con tag, nunca `println`.
- **Imports**: nunca wildcard.
- **Build artifacts**: nunca se commitean (`build/`, `.kotlin/metadata`, logs).

---

## Modularización (no urgente)

Análisis previo en `docs/archive/ARCH-002-modularization-review.md`. El proyecto es un monolito (~330 ficheros Kotlin). Los path A/B/C evaluados concluyeron que **no es momento de modularizar** — los costes de compilación + KMP + Compose superan los beneficios hasta >500 ficheros o builds >2 min.

Si el proyecto crece, candidato natural a primer split: extraer `detection/` (algoritmo + workers) a un módulo aparte con interfaces en `:core:detection-api`.
