# Checklist de lectura del código — plan por fases

> Objetivo: leer el proyecto entero **por orden de dependencia** (domain → data → detección → presentation → bordes),
> no por orden alfabético. Cada fichero que abres ya conoce los tipos que usa.
>
> **Técnica de dos velocidades** por fichero:
> - **⚡ Escaneo**: solo firmas públicas + KDoc de cabecera + `companion object` de constantes. 2-5 min.
> - **🐢 Lectura lenta**: línea a línea, idealmente con su test al lado. Solo donde saltó una alarma en el escaneo.
> - Los ficheros marcados 🐢 abajo son los que *a priori* merecen pasada lenta; el resto empieza en ⚡ y solo
>   se profundiza si algo sorprende.
>
> Rutas relativas a `composeApp/src/commonMain/kotlin/io/apptolast/paparcar/` salvo que se indique otro source set.
>
> Al cerrar cada fase: **examen E2E** — narrar un flujo de punta a punta sin mirar el código.

---

## Fase 0 — El mapa antes del territorio (1 sesión corta)

- [ ] `CLAUDE.md` (raíz del repo)
- [ ] `docs/architecture/` (los docs que existan, p. ej. VEHICLE-CATEGORIZATION.md)
- [ ] `gradle/libs.versions.toml`
- [ ] `composeApp/build.gradle.kts` — flavors mock/prod, KSP, Room
- [ ] 🐢 `di/DataModule.kt` — el mejor índice del proyecto
- [ ] 🐢 `di/DomainModule.kt`
- [ ] 🐢 `di/PresentationModule.kt`
- [ ] `androidMain/.../di/AndroidPlatformModule.kt`
- [ ] `androidMain/.../di/AndroidDetectionModule.kt`
- [ ] `git log --oneline -50` — dónde ha estado la acción reciente

---

## Fase 1 — Domain: el vocabulario (2-3 sesiones)

### 1a. Modelos núcleo (🐢 los cinco primeros)
- [ ] 🐢 `domain/model/Spot.kt`
- [ ] 🐢 `domain/model/UserParking.kt`
- [ ] 🐢 `domain/model/Vehicle.kt`
- [ ] 🐢 `domain/model/VehicleSize.kt` + `domain/model/CarbodyType.kt` (test: `CarbodyTypeRulesTest`)
- [ ] 🐢 `domain/model/SpotFit.kt` (test: `SpotFitTest`)
- [ ] `domain/model/UserProfile.kt`
- [ ] `domain/model/Zone.kt` + `ZoneIcon.kt`
- [ ] `domain/model/SpotType.kt` · `ParkingConfidence.kt` · `ParkingSignals.kt` · `ParkingDetectionConfig.kt`
- [ ] `domain/model/DetectionReadiness.kt` + `DetectionReliability.kt`
- [ ] `domain/model/GpsPoint.kt` · `AddressInfo.kt` · `AddressAndPlace.kt` · `PlaceInfo.kt` · `PlaceCategory.kt` · `SearchResult.kt`
- [ ] `domain/model/VehicleType.kt` · `VehicleColor.kt` · `VehicleExtensions.kt` · `VehicleParkingRules.kt` · `VehicleWithStats.kt` · `VehicleMonitoringStatus.kt` · `ParkedVehicleSummary.kt` · `DrivingPuck.kt`
- [ ] `domain/model/bluetooth/BluetoothDeviceInfo.kt` + `BluetoothDeviceType.kt`

### 1b. Contratos (solo firmas)
- [ ] `domain/repository/` completo — `SpotRepository` · `UserParkingRepository` · `VehicleRepository` · `UserProfileRepository` · `ZoneRepository` · `AddressAndPlaceRepository` · `RemoteSyncable` · `UserScopedRepository`
- [ ] 🐢 `domain/error/PaparcarError.kt` — contrato de errores hacia UI
- [ ] Interfaces de plataforma (⚡ rápido, son puertos): `domain/location/LocationDataSource.kt` · `domain/geocoder/*` · `domain/places/*` · `domain/sensor/*` · `domain/bluetooth/BluetoothScanner.kt` · `domain/connectivity/*` · `domain/notification/AppNotificationManager.kt` · `domain/preferences/AppPreferences.kt` · `domain/session/LocalSessionCache.kt` · `domain/diagnostics/*`
- [ ] `domain/permissions/` — `PermissionManager.kt` · `AppPermissionState.kt` (test: `AppPermissionStateTest`) · `RequiredPermission.kt` · `OemBackgroundReliabilityManager.kt`
- [ ] `domain/service/` — `GeofenceManager.kt` · `GeofenceEventBus.kt` · `DepartureEventBus.kt` · schedulers (`ParkingEnrichment`/`ParkingSync`/`ReportSpot`)
- [ ] `domain/event/MapFocusEventBus.kt` + `StartAddParkingEventBus.kt`
- [ ] `domain/util/GeoUtils.kt` (test: `GeoUtilsTest`) + `PaparcarLogger.kt`
- [ ] 🐢 `domain/vehicle/VehicleActiveStatePolicy.kt` — invariante 1-activo (test: `VehicleActiveStatePolicyTest`)

### 1c. UseCases (escanear nombres; 🐢 solo los de negocio real)
- [ ] 🐢 `domain/usecase/parking/ConfirmParkingUseCase.kt` (test ✓) — punto de convergencia de TODO
- [ ] 🐢 `domain/usecase/parking/CalculateParkingConfidenceUseCase.kt` (test ✓) — umbrales 0.75/0.55
- [ ] 🐢 `domain/usecase/parking/EvaluateParkingDecisionUseCase.kt` (test ✓)
- [ ] 🐢 `domain/usecase/parking/ProcessConfirmedDepartureUseCase.kt` (test ✓) — publica la plaza
- [ ] `domain/usecase/parking/` resto: `DetectParkingDeparture` · `VerifyDepartureEvidence` · `RunDepartureCheck` · `EvaluateSafetyNetCheck` · `ReleaseActiveParkingSession` · `RevertParking` · `UpdateParkingLocation` · `ObserveParkedVehicles`
- [ ] `domain/usecase/detection/` — `EvaluateArEnterArm` · `EvaluateBtPark` · `EvaluateDetectionReliability` · `EvaluateFirstParkNudge` · `ObserveDetectionReadiness` · `ObserveDetectionReliability` (todos con test)
- [ ] `domain/usecase/spot/` — `ObserveNearbySpots` · `ReportSpotReleased` · `SendSpotSignal`
- [ ] `domain/usecase/location/` — `ObserveAdaptiveLocation` · `GetOneLocation` · `GetLastKnownLocation` · `GetAddressAndPlace` · `SearchAddress`
- [ ] `domain/usecase/user/` — `BootstrapUserData` · `GetOrCreateUserProfile` · `DeleteAccount`
- [ ] `domain/usecase/notification/NotifyParkingConfirmationUseCase.kt` · `domain/usecase/zone/SaveZoneUseCase.kt`

**Examen F1**: explicar qué es un `Spot` vs un `UserParking`, y qué camino recorre una confirmación de parking (qué UseCase toca qué repos/servicios).

---

## Fase 2 — Data: cómo se cumplen las promesas (2 sesiones)

### 2a. Room
- [ ] `data/datasource/local/room/AppDatabase.kt`
- [ ] 🐢 `data/datasource/local/room/Migrations.kt` — historia condensada del proyecto (vas por v12)
- [ ] Entidades + DAOs (⚡ en parejas): `SpotEntity`/`SpotDao` · `UserParkingEntity`/`UserParkingDao` · `VehicleEntity`/`VehicleDao` · `UserProfileEntity`/`UserProfileDao` · `ZoneEntity`/`ZoneDao` · `GeocoderCacheEntity`/`GeocoderCacheDao`
- [ ] `data/session/RoomLocalSessionCache.kt` · `data/geocoder/RoomGeocoderCacheDataSource.kt`

### 2b. Remote (Firestore vía GitLive)
- [ ] `data/datasource/remote/FirebaseDataSource.kt` (interfaz) → 🐢 `FirebaseDataSourceImpl.kt`
- [ ] `data/datasource/remote/dto/` — `SpotDto` · `VehicleDto` · `UserProfileDto` · `ZoneDto` · `ParkingHistoryDto` · `DetectionEventDto`
- [ ] `data/datasource/remote/RemoteUserProfileDataSource(-Impl).kt` · `FirestoreDetectionEventLogger.kt`
- [ ] `data/mapper/` — `SpotDtoMapper` (test ✓) · `VehicleMapper` · `UserProfileMapper` (test ✓) · `ParkingSessionMapper` (test ✓) · `ZoneMapper` · `EnumMapping`
- [ ] Test guardarraíl: `androidUnitTest/.../FirestoreDeserializerParityTest.kt` — por qué existe

### 2c. Repositorios (la complejidad real: offline-first + reconcile)
- [ ] 🐢 `data/repository/SyncReconcile.kt` — el patrón genérico `reconcilePending<T>`
- [ ] 🐢 `data/repository/VehicleRepositoryImpl.kt` + `VehicleReconcile.kt` (test: `VehicleReconcileTest`)
- [ ] 🐢 `data/repository/SpotRepositoryImpl.kt` (test ✓) — geohash, TTL, flicker fix
- [ ] 🐢 `data/repository/UserParkingRepositoryImpl.kt` — sesión activa, workers
- [ ] `data/repository/ZoneRepositoryImpl.kt` + `ZoneReconcile.kt` (test ✓)
- [ ] `data/repository/UserProfileRepositoryImpl.kt` · `AddressAndPlaceRepositoryImpl.kt` (test ✓)
- [ ] `data/geohash/Geohash.kt` — autocontenido, lectura rápida

**Examen F2**: narrar qué pasa al editar un vehículo estando offline y reconectar después (pendingSync → drenador → reconcile).

---

## Fase 3 — Detección: el corazón del producto (3-4 sesiones)

> La parte más densa y con más historia (todo el git log reciente es esto).
> Regla de oro aquí: **test primero** — los replay tests con trazas de campo reales son la mejor spec.

### 3a. El cerebro puro (commonMain)
- [ ] `domain/detection/DetectionTrigger.kt` · `DetectionRuntimeState.kt` · `ManualParkingDetection.kt` · `DepartureConfirmationListener.kt`
- [ ] 🐢 `domain/detection/ArmEvidence.kt` — el modelo de evidencia de armado
- [ ] 🐢 `domain/detection/ParkingStrategyResolver.kt` (test ✓) — BT vs Coordinator
- [ ] `domain/detection/TripTrail.kt` + `domain/matching/TrailMapMatcher.kt` (test ✓)
- [ ] 🐢 `domain/coordinator/ConfirmationPhase.kt` (test: `ConfirmationPhaseMappingTest`)
- [ ] 🐢🐢 `domain/coordinator/CoordinatorParkingDetector.kt` (~1.400 líneas) — leer ANTES sus tests:
  - [ ] `commonTest/.../coordinator/CoordinatorParkingDetectorTest.kt`
  - [ ] `commonTest/.../coordinator/replay/DetectionTraceReplayer.kt` + `DetectionTraceReplayTest.kt`
  - [ ] Trazas de campo fijadas: `Trace_CalleGavia001` · `Trace_Supermarket001` · `Trace_BugReparkWalk001`

### 3b. El lado Android sucio (`androidMain/.../`)
- [ ] 🐢 `detection/service/CoordinatorDetectionService.kt` (test: `CoordinatorDetectionServiceTest`)
- [ ] 🐢 `detection/service/ForegroundServiceController.kt` — FGS, ventanas exentas
- [ ] `detection/ActivityRecognitionManagerImpl.kt` + `ActivityRecognitionLabels.kt`
- [ ] Receivers: `receiver/ActivityTransitionReceiver.kt` · `GeofenceEnterReceiver.kt` · `GeofenceExitWitnessReceiver.kt` · `ParkingConfirmationReceiver.kt` · `BootCompletedReceiver.kt`
- [ ] 🐢 `detection/GeofenceManagerImpl.kt` + `GeofenceEventBusImpl.kt` — se borran en force-stop → re-registro
- [ ] `detection/DepartureEventBusImpl.kt` · `ManualParkingDetectionImpl.kt` · `TripTrailImpl.kt` · `SignificantMotionMonitor.kt`
- [ ] Sensores: `detection/sensor/AndroidStepCounterSource.kt` + `AndroidStepDetectorSource.kt`

### 3c. Workers (el ciclo de vida diferido)
- [ ] 🐢 `detection/worker/SaveNewParkingSessionWorker.kt` — nacimiento de sesión
- [ ] 🐢 `detection/worker/DepartureDetectionWorker.kt` + `ClearActiveParkingSessionWorker.kt` — muerte de sesión
- [ ] `detection/worker/ParkingBackfillWorker.kt` · `ParkingSafetyNetWorker.kt` · `GeofenceJanitorWorker.kt`
- [ ] `detection/worker/EnrichParkingSessionWorker.kt` · `UpdateParkingSessionAddressAndPlaceWorker.kt` · `ReportSpotWorker.kt` (test: `ParkingSyncWorkerTest`)
- [ ] `detection/worker/RegisterActivityTransitionsWorker.kt` · `FirstParkNudgeWorker.kt`
- [ ] Schedulers WorkManager: `detection/WorkManagerParkingEnrichmentScheduler.kt` · `WorkManagerParkingSyncScheduler.kt` · `WorkManagerReportSpotScheduler.kt`

### 3d. Estrategia Bluetooth (`androidMain/.../bluetooth/`)
- [ ] 🐢 `bluetooth/BluetoothParkingDetector.kt` — la estrategia determinista
- [ ] `bluetooth/BluetoothDetectionService.kt` · `BluetoothConnectionReceiver.kt` · `AndroidBluetoothScanner.kt`

### 3e. Localización y plataforma de apoyo
- [ ] `location/AndroidLocationDataSourceImpl.kt` — el stream GPS adaptativo
- [ ] `location/AndroidGeocoderDataSourceImpl.kt` · `OverpassPlacesDataSourceImpl.kt` · `OverpassRoadNetworkDataSourceImpl.kt`
- [ ] `notification/AppNotificationManagerImpl.kt` + `ForegroundNotificationProvider.kt`
- [ ] `permissions/PermissionManagerImpl.kt` + `OemBackgroundReliabilityManagerImpl.kt`
- [ ] `MainActivity.kt` + `PaparcarApp.kt` — arranque, deep-links, re-registro de geocercas
- [ ] `logging/FileAntilog.kt` — captura forense en device

**Examen F3** (el importante): narrar de punta a punta —
BT disconnect (o AR EXIT∧ENTER) → evaluador → `ConfirmParkingUseCase` → Room + Firestore + geofence +
notificación + worker de geocoding. Y el inverso: geofence EXIT → departure check → publicación de la plaza.

---

## Fase 4 — Presentation + UI (2-3 sesiones)

### 4a. Esqueleto MVI y routing
- [ ] `App.kt` + `Platform.kt` (raíz commonMain)
- [ ] 🐢 `presentation/base/BaseViewModel.kt` — el patrón que repiten todas las pantallas
- [ ] 🐢 `presentation/app/` — `AppViewModel` (test ✓) · `AppState` · `AppIntent` · `AppEffect` · `SplashViewModel` (test ✓) — routing Splash → Auth → … → Home

### 4b. Home (la pantalla grande, con controllers extraídos)
- [ ] `presentation/home/HomeState.kt` + `HomeIntent.kt` + `HomeEffect.kt` — primero el contrato
- [ ] 🐢 `presentation/home/HomeViewModel.kt` (test ✓) + `HomeStateTransitions.kt`
- [ ] Controllers: `HomeTripController` (test ✓) · `HomeSpotsController` (test ✓) · `HomeSearchController` (test ✓) · `HomeGeocodingController` · `HomeUiController`
- [ ] `presentation/home/model/DetectionUiState.kt` (test ✓) · `MapTrail.kt` (test ✓)
- [ ] `presentation/home/HomeScreen.kt` → secciones: `sections/header/` · `sections/map/` · `sections/sheet/` (⚡ los componentes; son layout)
- [ ] `ui/components/PaparcarMapView.kt` + `PaparcarMapMarkers.kt` — el mapa y sus marcadores custom

### 4c. Segunda pantalla en diagonal (confirmar el patrón) + resto en escaneo
- [ ] `presentation/vehicles/` — `VehiclesViewModel` (test ✓) + State/Intent/Effect; `HistoryContent`/chart en ⚡
- [ ] ⚡ `presentation/settings/` · `presentation/permissions/` (ViewModel test ✓) · `presentation/bluetooth/` (test ✓) · `presentation/vehicleregistration/` (tests ✓, incl. 🐢 `data/VehicleCatalog.kt` — inferencia brand+model→CarbodyType) · `presentation/map/` (test ✓) · `presentation/onboarding/`
- [ ] ⚡ `presentation/util/` — `SpotUiUtils` · `TimeStringComposables` · `ZoneIconMap` · etc.

### 4d. Sistema de diseño
- [ ] 🐢 `ui/theme/PaparcarType.kt` — los 18 roles tipográficos
- [ ] `ui/theme/` resto: `Theme` · `Color` · `SpotStateColors` · `Spacing` · `Shapes` · `Borders` · `PapMotion` · `Typography`
- [ ] ⚡ `ui/components/` primitivos `Pap*`: `PapListItem` · `PapIconTile` · `PapCard` · `PapButton` · `PapSectionHeader` · `PapDivider` · resto
- [ ] ⚡ `ui/components/Vehicle*` — la familia de iconografía de vehículo por geometría
- [ ] `ui/icons/PaparcarIcons.kt` + `ui/illustrations/`

**Examen F4**: narrar el camino de un tap — "Marcar aparcamiento" → Intent → ViewModel → UseCase → Effect → snackbar/navegación.

---

## Fase 5 — Los bordes (1 sesión)

### 5a. Guardarraíles (codifican reglas que no están en el código de producción)
- [ ] 🐢 `androidUnitTest/.../architecture/ArchitectureTest.kt`
- [ ] `androidUnitTest/.../architecture/TypographyGuardrailTest.kt` + `DividerGuardrailTest.kt`

### 5b. Dev Catalog (flavor mock)
- [ ] `mock/kotlin/.../DevMainActivity.kt` + `MockPaparcarApp.kt` + `di/MockModule.kt`
- [ ] `mock/kotlin/.../dev/DevRoot.kt` + `DevCatalogScreen.kt` + `StateGalleryScreen.kt`
- [ ] `fakes/MockScenario.kt` + `fakes/` (commonMain) — los fakes scenario-aware
- [ ] `commonTest/.../fakes/` — vistazo: qué fakes existen para tests

### 5c. iOS (vistazo de 10 min: qué expect/actual existen)
- [ ] ⚡ `iosMain/` — `MainViewController` · `di/Ios*Module` · stubs de detection/location/permissions

### 5d. Recursos y manifiestos
- [ ] `androidMain/AndroidManifest.xml` — permisos, services, receivers declarados
- [ ] ⚡ `composeResources/values/strings.xml` — vistazo a la convención de keys
- [ ] ⚡ `androidMain/.../presentation/**/‌*Previews.kt` — espejo de la galería mock (paridad)

**Examen final**: la lista de "esto me chirría" acumulada durante las 5 fases → convertir cada punto en
ticket de backlog (`docs/backlog/`) o descartarlo con motivo.

---

## Registro de hallazgos

Apunta aquí (o en fichero aparte) lo que chirríe SIN pararte a arreglarlo — al final tendrás candidatos
a refactor con contexto completo:

| Fase | Fichero | Qué chirría | ¿Ticket? |
|------|---------|-------------|----------|
|      |         |             |          |
