# Paparcar — Architecture

> **Doc vivo.** Describe el proyecto **en presente**: si algo aquí no es verdad hoy, es un bug del doc.
> Verificado contra master `46621e7f` el **2026-08-30** — versiones leídas de `gradle/libs.versions.toml`,
> rutas y cifras contadas sobre el árbol.
> Sustituye a `Paparcar_Arquitectura.md` (v4.0, abril 2026), **borrado** en este mismo barrido por
> estar reemplazado por completo — recuperable con `git show <commit>^:docs/archive/Paparcar_Arquitectura.md`.
> El análisis de modularización sigue vivo en [`archive/ARCH-002-modularization-review.md`](./archive/ARCH-002-modularization-review.md).

---

## Stack

| Capa | Tecnología |
|------|------------|
| Lenguaje | Kotlin 2.4.10 (KSP 2.3.11) · JVM 17 · Gradle 9.7.1 |
| Build | AGP 9.3.2 · compileSdk 37 · targetSdk 37 · minSdk 26 |
| UI | Compose Multiplatform 1.12.0 · Material3 (JB) 1.9.0 |
| Navegación | Navigation Compose KMP 2.9.2 |
| Arquitectura | Clean Architecture + MVI (State + Intent + Effect) |
| DI | Koin 4.2.2 (core + compose + viewmodel) |
| DB local | Room KMP 2.8.4 + sqlite-bundled 2.7.0 |
| Backend | Firebase vía GitLive 2.6.0 (Auth · Firestore · Crashlytics) · firebase-bom 34.18.0 |
| Maps | `io.github.rndevelo.kmpmaps:core:0.9.1-puck4` — **fork propio** en Maven Central (Google Maps en Android, Apple Maps en iOS) |
| Auth | BaseLogin 1.1.0 (librería propia, JitPack) — ⛔ no se toca desde este repo |
| Async | Coroutines 1.11.0 + Flow · Serialization 1.11.0 · Datetime 0.8.0 |
| Imágenes | Coil 3.6.0 + Ktor 3.5.2 (motor de red) |
| Logging | Napier 2.7.1 · Crashlytics |
| Background | WorkManager 2.11.2 (Android) — BGTaskScheduler pendiente en iOS |
| Tests | JUnit 4 · Turbine 1.2.1 · Robolectric 4.16.1 · **Konsist 0.17.3** (guardarraíles) |

**Targets:** Android `minSdk 26 / target 37 / compile 37` · iOS `arm64 + simulatorArm64`.

> El fork de kmp-maps es nuestro: la versión la manda este repo, no upstream (Software Mansion 0.9.1
> + PR #170 sin mergear, que da id estable al marker). [BUILD-KMPMAPS-CENTRAL-DEPENDENCY-001]

---

## Los dos módulos Gradle

Desde `b949efa1` [ARCH-HEALTH-001 F7] el proyecto está partido en dos, con el paquete
`com.rndeveloper.paparcar`:

```
:shared   KMP — TODA la lógica de producto (domain, data, presentation, ui, di, core)
:app      Shell Android — MainActivity, PaparcarApp, AppNotificationManagerImpl, manifest,
          res/, flavors prod|mock, firma. Aquí vive BuildConfig.
iosApp/   Shell SwiftUI (delega en Compose vía MainViewController)
```

`:app` es deliberadamente delgado: **4 ficheros Kotlin** en `src/main`. `:shared` no puede leer
`BuildConfig` (vive en `:app`), así que los *build facts* viajan por `AppBuildInfo` / `isDebugBuild`.

> ⛔ El plugin `com.android.kotlin.multiplatform.library` **vacía los composeResources del APK** — por
> eso `:shared` no lo usa. Trampa medida durante F7.

---

## Diagrama de capas

```
┌──────────────────────────────────────────────────────────────┐
│  UI (shared/commonMain/ui + presentation)                    │
│  HomeScreen · VehiclesScreen · SettingsScreen · Onboarding   │
│  PaparcarMapView · Pap* (design system) · GlassSurface       │
└────────────┬─────────────────────────────────────────────────┘
             │ State / Intent / Effect
┌────────────▼─────────────────────────────────────────────────┐
│  Presentation (commonMain/presentation) — MVI                │
│  HomeViewModel (+ controllers) · VehiclesViewModel · …       │
│  AppViewModel (auth + bootstrap) · SplashViewModel           │
└────────────┬─────────────────────────────────────────────────┘
             │ UseCases (Result<T> | Flow<T> | value object)
┌────────────▼─────────────────────────────────────────────────┐
│  Domain (commonMain/domain) — KOTLIN PURO                    │
│  usecase/{parking,detection,spot,location,user,zone,          │
│           vehicle,notification,diagnostics}                  │
│  detection/{stages,physics,state,fence,sentry,ports}         │
│  model/ · repository/ (interfaces) · error/PaparcarError     │
└────────────┬─────────────────────────────────────────────────┘
             │ Repository interfaces
┌────────────▼─────────────────────────────────────────────────┐
│  Data (commonMain/data)                                      │
│  repository/*Impl  →  Room DAO  ⇄  Firestore (GitLive)       │
│  mapper/ · geohash/ · geocoder/ · session/                   │
└────────────┬─────────────────────────────────────────────────┘
             │ expect/actual + bindings Koin
┌────────────▼─────────────────────────────────────────────────┐
│  Platform (shared/androidMain | shared/iosMain)              │
│  androidMain: CoordinatorDetectionService · BluetoothDetec-  │
│    tionService · 11 workers · 6 receivers · FusedLocation ·  │
│    ActivityRecognition · GeofencingClient · sensores         │
│  iosMain: CLLocation · CMMotion · CBCentralManager ·         │
│    UNUserNotificationCenter · NWPathMonitor                  │
└──────────────────────────────────────────────────────────────┘
```

---

## Flujo de datos canónico

**Lectura observable** (spots cercanos en el mapa):

```
HomeScreen
  ↓ collectAsStateLifecycleAware
HomeViewModel.state: StateFlow<HomeState>
  ↑ combine
ObserveNearbySpotsUseCase: Flow<List<Spot>>
  ↑
SpotRepository (offline-first)
  ↳ Room SpotDao.observeNearby(...): Flow<List<SpotEntity>>   ← Source of Truth
  ↳ Firestore listener → upsert en Room                        ← capa de sync
```

**Comando con efecto** (confirmar un aparcamiento):

```
BluetoothParkingDetector  |  CoordinatorParkingDetector
  ↓                                    ↓
        ConfirmParkingUseCase   ← punto único de convergencia
  ├→ UserParkingRepository.insertActive(...)   (Room, síncrono)
  ├→ GeofenceManager.register(...)             (Play Services / CLLocationManager)
  ├→ AppNotificationManager.notifyConfirmed()
  └→ ParkingSyncScheduler.enqueueSaveNewParkingSession(...)
         ↓
       SaveNewParkingSessionWorker (WorkManager / coroutine en iOS)
         ↓
       Firestore.set(...) + update({isActive:false}) sobre la sesión previa
```

---

## Estructura de paquetes

```
com.rndeveloper.paparcar                          (shared/src/commonMain)
├── domain/                          Kotlin puro — sin android.* ni platform.*
│   ├── model/                       Spot · UserParking · Vehicle · Zone · VehicleSize ·
│   │                                CarbodyType · SpotFit · ParkingDetectionConfig · …
│   ├── detection/                   el cerebro de detección, puro y testeable
│   │   ├── stages/                  12 stages del coordinator (Candidate, FastConfirm,
│   │   │                            HoldResolution, VehicleAttribution, UserConfirm, …)
│   │   ├── physics/                 12 predicados puros compartidos (CredibleMovement,
│   │   │                            WalkedVsRode, HonestZoneRadius, SessionOutcome, …)
│   │   ├── state/                   ConfirmationPhase + composición de sesión
│   │   ├── fence/ · sentry/         propiedad de geocerca · ciclo de vida del centinela
│   │   ├── ports/                   TripTrail · ManualParkingDetection (puertos)
│   │   └── CoordinatorParkingDetector.kt · ParkingStrategyResolver.kt · ArmEvidence.kt
│   ├── usecase/                     47 use cases en 9 áreas — uno por VEREDICTO
│   ├── repository/ service/ ports   interfaces
│   ├── permissions/ preferences/ session/ diagnostics/ …
│   └── error/PaparcarError.kt
│
├── data/
│   ├── datasource/local/room/       AppDatabase v1, 6 entidades, DAOs
│   ├── datasource/remote/           FirebaseDataSource + DTOs + logger de diagnóstico
│   ├── repository/                  *Impl offline-first + reconcile LWW (SyncReconcile.kt)
│   ├── mapper/ geohash/ geocoder/ session/
│
├── presentation/                    MVI: home · vehicles · settings · permissions ·
│                                    onboarding · bluetooth · vehicleregistration · map ·
│                                    app (Auth/Splash) · base · preview · util
├── ui/                              theme/ (Color · PapColor · PaparcarType · PapFontSet ·
│                                    VehicleIdentity · SpotStateColors) · components/ ·
│                                    icons/ · illustrations/ · auth/
├── core/crash/                      · di/ (DomainModule · DataModule · DetectionModule ·
│                                       PresentationModule)
└── fakes/                           fakes scenario-aware que comparten mock y tests

(shared/src/androidMain)             detection/{service,worker,receiver,sensor} ·
                                     bluetooth/ · location/ · notification/ · permissions/ ·
                                     preferences/ · diagnostics/ · logging/ · di/
(shared/src/iosMain)                 CL/CM/CB bridges · ios/stub/ · di/
(app/src/main)                       MainActivity · PaparcarApp · AppNotificationManagerImpl ·
                                     di/AppModule
(app/src/mock)                       Dev Catalog: DevMainActivity · DevRoot · DevCatalogScreen ·
                                     StateGalleryScreen
```

---

## Decisiones técnicas clave

### 1. Domain puro Kotlin
`domain/` no importa `android.*` ni `platform.*`. Lo verifica `ArchitectureTest` (Konsist), no la
buena voluntad. Las dependencias de plataforma se declaran como `interface` en domain y se
implementan vía `expect/actual` o bindings de Koin en `androidMain`/`iosMain`.

### 2. Offline-first con dual write
**Room es Source of Truth.** `ConfirmParkingUseCase` escribe a Room **sincrónicamente**; los workers
propagan a Firestore con reintentos. La lectura observa siempre Room. Rationale: la app tiene que
funcionar sin red — se aparca en sótanos.

Para vehículos, zonas y sesiones hay además **reconcile LWW** (`SyncReconcile.kt` + `VehicleReconcile`
/ `ZoneReconcile`): lo editado offline se marca pendiente y se drena al reconectar.

### 3. Estrategia dual de detección
Dos estrategias **independientes** que nunca se mezclan (regla vinculante en `CLAUDE.md`):

- **`BluetoothParkingDetector`** — determinista. BT disconnect del MAC emparejado → fix GPS →
  alejarse ≥30 m → confirma. Ligada a la MAC, no al modelo. Sin scoring ni Activity Recognition.
- **`CoordinatorParkingDetector`** — probabilístico. Arma con AR `IN_VEHICLE ENTER` (AR-first) o
  `GEOFENCE_EXIT`; confirma vía `EvaluateParkingDecisionUseCase`, que **siempre exige conducción
  medida**. El scoring por sí solo no auto-confirma.

`ParkingStrategyResolver` elige según `vehicle.bluetoothDeviceId != null && isBluetoothEnabled`.
Ambas convergen en `ConfirmParkingUseCase`.

**Spec canónica:** [`docs/detection/PARKING-DETECTION.md`](./detection/PARKING-DETECTION.md).
Antes de tocar nada: skill `det-change`.

### 4. La decisión es pura; el servicio solo hace I/O
`CoordinatorDetectionService` serializa todos los triggers en un **intake único** [DET-INTAKE-001] y
se limita a I/O y side-effects. **Qué se decide vive en use cases de `commonMain`**, testeables sin
device — incluidos los *replay tests* con trazas de campo reales.

Corolario de gobierno, enforced por `StagePurityGuardrailTest`: un caso de uso existe por
**veredicto** (algo citable en un diagnóstico), nunca por predicado. Los predicados compartidos son
funciones puras en `domain/detection/physics/`.

### 5. WorkManager para side-effects diferidos
Nada que pueda tardar o fallar por red se ejecuta inline. **11 workers** (Android):

| Worker | Rol |
|---|---|
| `SaveNewParkingSessionWorker` | nacimiento de sesión en Firestore (constraint CONNECTED) |
| `ClearActiveParkingSessionWorker` | cierre de la sesión liberada |
| `EnrichParkingSessionWorker` | geocoder + places, best-effort → encadena el update |
| `UpdateParkingSessionAddressAndPlaceWorker` | `update({address, placeInfo})` |
| `DepartureDetectionWorker` | valida la salida (geofence + AR + sesión) |
| `ReportSpotWorker` | publica la plaza liberada |
| `ParkingSafetyNetWorker` | red de seguridad 15 min + sensor de movimiento |
| `ParkingBackfillWorker` | reconcilia una salida que el OS no entregó |
| `GeofenceJanitorWorker` | restaura geocercas tras reboot / reinstall |
| `RegisterActivityTransitionsWorker` | re-registra las transiciones de AR |
| `FirstParkNudgeWorker` | nudge del primer aparcamiento |

En iOS estos son coroutine+retry sin persistencia tras process death — ver [`IOS_PLAN.md`](./IOS_PLAN.md).

### 6. MVI estricto
Cada pantalla tiene `<Name>State` / `<Name>Intent` / `<Name>Effect`. `BaseViewModel<S,I,E>` centraliza
`state: StateFlow`, `handleIntent(intent)` y `emitEffect(effect)`. Home, por tamaño, delega en
*controllers* (`HomeTripController`, `HomeSpotsController`, `HomeSearchController`,
`HomeGeocodingController`, `HomeUiController`), cada uno con su test.

### 7. Koin por capa y por plataforma
`commonMain/di/`: `DomainModule` · `DataModule` · `DetectionModule` · `PresentationModule`.
`androidMain/di/`: `AndroidPlatformModule` · `AndroidDetectionModule`. `iosMain/di/`: los dos `Ios*`.
`app/src/main/di/AppModule.kt` aporta lo que solo el shell puede construir.

> ⛔ Los *stages* del coordinator **no se inyectan**: se construyen dentro del detector. Meterlos en
> Koin fue evaluado y descartado. [DET-DI-DETECTION-MODULE-001]

### 8. Una sola Activity
`MainActivity` carga `App()` con el `NavHost`. `singleTask` + `configChanges` evita recrearla al rotar.
El flavor `mock` arranca por `DevMainActivity` en su lugar.

### 9. Guardarraíles ejecutables en vez de reglas escritas
Las convenciones que un humano olvida se comprueban con Konsist en `androidUnitTest/architecture/`:
`ArchitectureTest` · `TypographyGuardrailTest` · `ColorGuardrailTest` · `DividerGuardrailTest` ·
`LocaleParityGuardrailTest` · `StagePurityGuardrailTest` · `HoldLaneGuardrailTest` ·
`TriggerLaneGuardrailTest` · `PromptWindowGuardrailTest` · `HomeSliceGuardrailTest`.

---

## Modelos de datos clave

| Modelo | Resumen |
|--------|---------|
| `Spot` | Plaza comunitaria publicada: `location`, `type` (AUTO_DETECTED / MANUAL_REPORT), `status`, `confidence`, `sizeCategory`, `carbodyType`, `enRouteCount`, TTL |
| `UserParking` | Sesión propia: `vehicleId`, `location`, `geofenceId`, `isActive`, `detectionMethod`, **`detectionPath`**, **`armEvidence`**, `routePolyline`, `sizeCategory`, `carbodyType` |
| `Vehicle` | `brand`, `model`, `licensePlate?`, `bluetoothDeviceId?`, `isDefault`, `sizeCategory`, `carbodyType?`, `color?` |
| `UserProfile` | `userId`, `email`, `displayName`, `photoUrl` |
| `Zone` | Zona favorita/regulada: `location`, `radius`, `icon`, `name` |
| `ParkingConfidence` | High / Medium / Low con score y motivos |
| `ParkingDetectionConfig` | Todos los umbrales del coordinator, singleton inyectable |

**Invariantes críticas:**
- Toda `UserParking` tiene `vehicleId` no nulo. No existe histórico sin vehículo.
- Todo pin persiste su **procedencia** (`detectionPath` + `armEvidence`): en un diagnóstico siempre
  se puede decir qué trigger lo colocó.
- Un vehículo activo como máximo (`VehicleActiveStatePolicy`).

Talla × carrocería → `SpotFit`: ver [`architecture/VEHICLE-CATEGORIZATION.md`](./architecture/VEHICLE-CATEGORIZATION.md).

---

## Persistencia

### Room — `AppDatabase` **v1**
Entidades: `UserParkingEntity`, `UserProfileEntity`, `VehicleEntity`, `SpotEntity`, `ZoneEntity`,
`GeocoderCacheEntity`.

La cadena v2..v20 y sus 16 esquemas exportados se **borraron**: describían upgrades de bases que solo
existieron en nuestros propios móviles de test. v1 es la línea base y
`fallbackToDestructiveMigration(dropAllTables = true)` sigue activo mientras la app no esté publicada.
[DATA-ROOM-STARTS-AT-VERSION-ONE-001]

> ⚠️ **El primer release público cierra esa puerta**: desde ahí hay usuarios cuyos datos deben
> sobrevivir y todo cambio de esquema necesita su `Migration` y su schema exportado. El downgrade
> está medido, no supuesto: `AppDatabaseDowngradeTest`.

### DataStore Preferences (Android)
Tema, unidad de distancia y flags. `AndroidDataStoreAppPreferences` con snapshot in-memory (0
`runBlocking` por getter). La implementación legacy sobre SharedPreferences se borró en mayo; la
migración vive en el delegate `preferencesDataStore`.

### NSUserDefaults (iOS)
`IosAppPreferences` con migración perezosa desde la clave legacy. Implementación real, no stub.

### Firestore
`userParkings` · `spots` · `vehicles` · `zones` · `userProfiles` · `diagnostics/{uid}/sessions`.
Los listeners se reactivan al pasar Offline→Online (`reconnectTick` en `HomeViewModel`).

---

## Errores y resultados

One-shot → `kotlin.Result<T>` (stdlib) vía `runCatching`. Observables → `Flow<T>` con `.catch { }`
para no matar el stream (la UI sigue sirviendo cache). Los evaluadores puros y síncronos retornan un
value object de dominio.

> No existe wrapper `AppResult`: el estándar es `kotlin.Result<T>`.

Los errores de negocio visibles se modelan con `PaparcarError` (sealed: `Location`, `Network`,
`Database`, `Detection`, `Auth`, `Parking`, `Vehicle`), se emiten con `Effect.ShowError(...)`, se
mapean con un `when` en la pantalla y se muestran en un `SnackbarHost`. **Cero catch silenciosos.**

---

## Testing

| Source set | Ficheros | Para qué |
|---|---|---|
| `shared/src/commonTest` | 191 | el grueso: use cases, evaluadores, ViewModels, mappers, reconcile, **replay de trazas de campo** |
| `shared/src/androidUnitTest` | 16 | lo que necesita JVM/Android: guardarraíles Konsist, Robolectric, workers, paridad de deserializadores Firestore |

```bash
./gradlew :shared:testDebugUnitTest --console=plain
```

Regla: **fakes sobre mocks** (`FakeAuthRepository`, `FakePermissionManager`, …), naming
`should_expectedBehavior_when_condition`, y toda UseCase nueva lleva test.

---

## CI

`.github/workflows/`: `ci.yml` (build + tests; incluye un job `macos-latest` que compila `iosMain` —
antes de `02a29f62` **nadie compilaba iOS nunca**), `distribute-alpha.yml`, `distribute-beta.yml`.

---

## Convenciones obligatorias

(Resumen — la versión vinculante está en [`CLAUDE.md`](../CLAUDE.md))

- **Strings**: nunca hardcoded → `composeResources/values/strings.xml`, keys EN, y **los 9 locales en
  la misma tarea**. Faltar en uno no crashea: sale en inglés en silencio.
- **Tipografía y color**: se elige **rol**, no fuente/tamaño/peso; el estado se escribe, no se tiñe.
- **Magic numbers**: `private companion object` con UPPER_SNAKE_CASE.
- **Logs**: Napier con tag, nunca `println`. **Imports**: nunca wildcard.
- **Build artifacts**: nunca se commitean.

---

## Modularización (no urgente)

Análisis previo en [`archive/ARCH-002-modularization-review.md`](./archive/ARCH-002-modularization-review.md).
El split `:app` + `:shared` (F7) ya cubrió lo que dolía: aislar el shell Android de la lógica de
producto. `:shared` son ~800 ficheros Kotlin; el siguiente candidato natural sería extraer
`detection/` con interfaces en `:core:detection-api`, pero **no hay motivo hoy** — el coste de
compilación KMP + Compose sigue superando al beneficio. Plan por fases en
[`backlog/arch-health-001.md`](./backlog/arch-health-001.md).
