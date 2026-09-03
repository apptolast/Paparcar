# IOS-PARITY-AUDIT — qué le falta a iOS para ser simétrico con Android

**Estado:** 🔵 Auditoría completada 2026-09-03 · 8 tickets desglosados abajo, **0 empezados** ·
los cerrados previos que esta auditoría confirma se citan, no se reabren.

**Base auditada:** rama `feature/IOS-F0-001-fase0` @ `f301914b` (worktree `../Paparcar-ios-f0`),
en sync con `origin`, 2 commits detrás de master `6fd0b7c9` (solo los de onboarding).
**Método:** inventario fichero a fichero `androidMain` (96 .kt, 21 son `*Previews.kt`) vs
`iosMain` (38 .kt, 2.583 LOC) + auditoría del contrato `expect/actual` + grafo Koin completo +
build/deps + shell `:app` vs `iosApp/`.

---

## Lo que YA está bien (no generar tickets de esto)

- **Contrato `expect/actual` cerrado al 100 %**: 12 `expect` en commonMain, todos con `actual`
  iOS (el de Room lo genera KSP — `kspIosArm64`/`kspIosSimulatorArm64` registrados).
- **El grafo Koin de PRODUCCIÓN iOS resuelve entero**: los cinco puertos sin binding iOS
  (`RoadNetworkDataSource`, `LocalDiagnosticsLog`, `TripTrail`, `DrivingRouteStore`,
  `StepCounterSource`) están todos detrás de `getOrNull()` o solo tienen consumidores androidMain.
- **Periferia real y honesta**: CLLocation (con `allowsBackgroundLocationUpdates`), CLGeocoder,
  Overpass places, permisos (CLLocation+CoreMotion+UN+CB), notificaciones con categorías y
  acciones + delegate retenido, Room con la MISMA `ALL_MIGRATIONS`, geofence por CLCircularRegion
  (exit-only, suelo 100 m), CMMotionActivity que sintetiza ENTER/EXIT, CMPedometer live,
  NSUserDefaults prefs (213 LOC), side-records F0 (NSUserDefaults), connectivity por
  `nw_path_monitor`. Casi todos los no-ops llevan KDoc con su guard — cero `TODO()` vacíos.
- **Deps del build correctas para iOS**: Ktor darwin, Coil común, GitLive Firebase,
  kmp-maps fork (`core-iosarm64` publicado), BaseLogin 2.0.0 (`baselogin-iosarm64` resuelve).
- **CI**: el job `apple` (`macos-latest`) linka `linkDebugFrameworkIosSimulatorArm64`
  [CI-IOS-COMPILES-ON-A-MAC-NOT-ON-A-PROMISE-001].

## Asimetrías POR DISEÑO (no son deuda; no portar)

Estrategia Bluetooth entera (CoreBluetooth no expone ACL ni bondedDevices —
`DeviceCapabilities(false, false)` ya lo contrata y el techo iOS es ASSISTED [IOS-F0-03]),
SENTRY residente, `ExactHeartbeatScheduler`, `BootCompletedReceiver` (las CLRegion sobreviven al
reboot), exención de batería/OEM, `SignificantMotionMonitor` (lo sustituyen SLC/CLVisit en F2),
los 13 workers de WorkManager como FORMA (su función sí se cubre, ver tickets F2/colas) y los 21
`*Previews.kt`. Fuente: `docs/IOS-IMPLEMENTATION-PLAN.md` §2.

---

## Tickets

### IOS-DI-A-MOCK-GRAPH-ONLY-PROD-IS-VERIFIED-001 — el flavor mock crashea y nadie lo verifica
**Prioridad:** 🔴 P0 (rompe el Dev Catalog **también en Android**) · **Esfuerzo:** S ·
**Estado:** ⚪ Pendiente · **Dónde:** la rama iOS (regresión de `87f08dbe`), no master.

`DeviceCapabilities` [IOS-F0-03] solo se bindea en `androidPlatformModule` e `iosPlatformModule`,
pero `DetectionModule.kt:276` lo pide con `get()` obligatorio
(`EvaluateDetectionReliabilityUseCase`) y el mock arranca con
`loginPresentationModule + presentationModule + domainModule + mockModule` — ninguno lo bindea.
`domainModule` incluye `detectionModule`, y `HomeViewModel`/`SettingsViewModel`/
`PermissionsViewModel` consumen la fiabilidad ⇒ `NoDefinitionFoundException` al abrir Home mock,
en ambas plataformas. Compila y los 2.178 tests pasan porque `KoinModuleVerifyTest` solo verifica
el grafo **prod-Android**.

Además `IosMockModule.kt` está desfasado ~11 bindings respecto a `app/src/mock/.../MockModule.kt`
(le faltan, todos con `get()` obligatorio: `DeviceCapabilities`, `DetectionEventLogger`,
`UiLocationLogger`, `DepartureWatchResumer`, `DeviceInfoProvider`, `AddressAndPlaceRepository`,
`DiagnosticsRepository`, `DiagnosticsReportUploader`, `geocoderCacheDao` +
`LocalAddressAndPlaceDataSource`, config de BaseLogin, `MockScenario`). Hoy es inarrancable — y
además inalcanzable: `ContentView.swift` llama a `MainViewController()`, nadie invoca
`MockMainViewController`.

- Bindear `DeviceCapabilities` en los DOS mock modules.
- Resincronizar `IosMockModule` con su gemelo Android.
- **Sistemas, no parches**: el invariante es «todo grafo arrancable se verifica» — extender
  `KoinModuleVerifyTest` (o test hermano) a los grafos mock (Android e iOS son el mismo
  commonMain: verificar el mock común ya caza esto).
- **Aceptación:** Home/Settings/Permissions abren en `assembleMockDebug`; un test falla si un
  binding obligatorio falta en cualquier grafo arrancable.

### IOS-XCODE-A-PLIST-THAT-NEVER-ENTERS-THE-BUNDLE-001 — el proyecto Xcode no puede ni arrancar
**Prioridad:** 🔴 P0 (bloquea CUALQUIER validación en device) · **Esfuerzo:** S (pero necesita
el Mac del compañero para verificar) · **Estado:** ⚪ Pendiente.

- La `PBXResourcesBuildPhase` del `project.pbxproj` está **vacía** (`files = ( );`): aunque el
  compañero copie `GoogleService-Info.plist` (gitignored a propósito, repo público), no entra en
  el bundle y `FirebaseApp.configure()` (`iOSApp.swift`) crashea al arrancar.
- `TEAM_ID` vacío en `iosApp/Configuration/Config.xcconfig` → sin firma de device.
- Sin `.xcscheme` compartido en `xcshareddata/` → `xcodebuild` no puede correr en CI (es el
  escalón que [CI-IOS-COMPILES-ON-A-MAC-NOT-ON-A-PROMISE-001] dejó fuera a la espera de esto).
- **Aceptación:** el plist copiado localmente entra en el bundle; la app firma y arranca en un
  iPhone; el scheme compartido commiteado deja a CI listo para añadir el escalón `xcodebuild`.

### IOS-CRASH-A-BRIDGE-NOBODY-INSTALLS-001 — Crashlytics es un no-op silencioso en iOS
**Prioridad:** 🟠 P1 · **Esfuerzo:** S · **Estado:** ⚪ Pendiente.

`core/crash/CrashReporter.kt` (iosMain) es un bridge cuyo `bridge` nadie asigna: el propio KDoc
documenta el wiring Swift necesario y `iOSApp.swift` no lo hace. `FirebaseCrashlytics` **ni
siquiera está entre los productos SPM linkados** (solo Core, Auth, Firestore, AppCheck). Todo
`recordNonFatal`/`setUserId` se pierde en silencio.

- Añadir `FirebaseCrashlytics` a los productos SPM + implementar e instalar el bridge en
  `iOSApp.swift`.
- **Aceptación:** un non-fatal de prueba aparece en la consola de Crashlytics desde device.

### IOS-CI-A-BRANCH-THAT-NEVER-MEETS-THE-MAC-001 — la rama iOS nunca ha pasado por el job apple
**Prioridad:** 🟠 P1 · **Esfuerzo:** XS · **Estado:** ⚪ Pendiente.

`ci.yml` solo se dispara en push a `master`/`alpha`/`beta` y en PRs hacia ellas. La rama
`feature/IOS-F0-001-fase0` está pusheada pero **sin PR**: sus ediciones de `iosMain` (el módulo
de DI se editó a mano en el recompute) no las ha compilado NADIE — exactamente el agujero que el
ticket de CI describía. Los minutos macOS son gratis (repo público).

- Abrir PR **draft** de la rama (sin intención de merge — el merge sigue bloqueado por la
  decisión de campo del 14-08) o añadir `feature/IOS-*` a los triggers del workflow.
- **Aceptación:** cada push de la rama corre el job `apple` y el estado de `iosMain` es visible.

### IOS-TEST — la suite común nunca compiló en iOS (ticket YA existente, citado aquí)
**Estado:** ⚪ abierto sin rama como
[TEST-A-KMP-SUITE-THAT-ONLY-RUNS-ON-JVM-IS-HALF-A-SUITE-001] — no se duplica aquí.
29 errores mecánicos en 12 ficheros (nombres con `,`/`()`, `System.currentTimeMillis()`,
`kotlin.assert`); tras arreglar, correr `:shared:iosSimulatorArm64Test` en macOS y meterlo en el
job `apple`. Toda la doctrina de detección (2.044+ tests, 16 replays de campo) está verificada
solo en JVM.

### IOS-F1-A-CONTROLLER-FOR-THE-HAPPY-PATH-001 — el orquestador: iOS aparca de verdad
**Prioridad:** 🔴 P0 del port (el gap estructural) · **Esfuerzo:** L ·
**Estado:** ⚪ Pendiente · **Plan:** `docs/IOS-IMPLEMENTATION-PLAN.md` §8-F1.

No existe análogo a `CoordinatorDetectionService`: **nadie alimenta
`CoordinatorParkingDetector` con un stream GPS en iOS**. Por eso hoy son no-ops declarados
`IosManualParkingDetectionImpl` (4 métodos → `Unit`), `IosArrivalHandoffDetectionImpl` y
`IosDepartureWatchResumerImpl` (`= false`), y `IosDetectionSideRecords` es persistencia real
**sin un solo consumidor** («SKELETON» en su KDoc).

- `IosDetectionController` (iosMain puro): armado MANUAL + sesión
  `startUpdatingLocation` trip-scoped + coordinator en vivo + `ConfirmParkingUseCase`
  (el pipeline común ya funciona) + notificaciones. Sustituye los 3 no-ops.
- Registro de región al confirmar + consumo del `GeofenceEventBus` (en iOS el bus SÍ es el canal
  real y hoy nadie lo lee) → `EvaluateGeofenceExitUseCase` → re-armado.
- Reconcile `monitoredRegions` ↔ sesiones Room en app-start (presupuesto 20 regiones explícito).
- **Aceptación:** ciclo park→exit→re-park completo en device con app en foreground/background
  reciente; los side-records F0 tienen consumidor; skill `det-change` obligatoria.

### IOS-F2-A-WAKE-MUST-QUERY-THE-PAST-001 — wake-and-query: el corazón del tier asistido
**Prioridad:** 🟠 P1 del port · **Esfuerzo:** L · **Estado:** ⚪ Pendiente · **Dep:** F1 ·
**Plan:** §4 y §8-F2 del plan.

- Protocolo de reconstrucción: side-records + `CMMotionActivityManager.queryActivityStarting` +
  `CMPedometer.queryPedometerData` por rango → `List<TraceEvent>` → `DetectionTraceIngestion`
  (el puerto YA promovido en F0 — esta es su razón de existir).
- `IosDetectionStepAnchors.stepsSinceSeal()` deja de devolver `null` (su `TODO(F2)`): query por
  rango en vez de contador acumulativo. Hasta entonces el honest close es mudo en iOS.
- Departure inline con la escalera de retries + safety-net como mesh de wakes (ENTER cure,
  SLC/CLVisit, app-start, BGAppRefresh tick) corriendo `EvaluateSafetyNetCheckUseCase`.
- **Aceptación:** kill mid-trip (swipe) → el siguiente wake reconstruye y libera/pregunta;
  tabla S1–S12 de DET-SOLID en device; skill `det-change`.

### IOS-SYNC-A-QUEUE-THAT-DIES-WITH-THE-PROCESS-001 — colas durables + BGTaskScheduler
**Prioridad:** 🟠 P1 · **Esfuerzo:** M · **Estado:** ⚪ Pendiente · **Dep:** F1 (para tener algo
que encolar de verdad).

Los 3 schedulers de corrutina (`IosParkingSyncScheduler`, `IosParkingEnrichmentScheduler`,
`IosReportSpotScheduler`) son reales pero **no sobreviven a la muerte de proceso**. Caso de
corrupción ya documentado en `IosParkingSyncScheduler.kt` (~L96, `TODO [IOS-SYNC-001]`): un
delete remoto interrumpido no deja fila donde colgar el flag → el coche **resucita** en el
siguiente sync.

- Cola persistida (Room/NSUserDefaults) drenada en cada wake y app-open; `BGProcessingTask`
  (`requiresNetworkConnectivity`) como backstop, `BGAppRefreshTask` como tick.
- `BGTaskSchedulerPermittedIdentifiers` en `Info.plist` (hoy los `UIBackgroundModes`
  `fetch`/`processing` declarados no sirven de nada: no hay NI UNA llamada a `BGTaskScheduler`).
- El gate TTL de `ReportSpot` se conserva tal cual (oro en iOS, donde la cola puede tardar horas).
- **Aceptación:** matar la app con un write pendiente → el write llega igual; el delete
  interrumpido no resucita el vehículo.

### IOS-NOTIF-A-SURFACE-THAT-SPEAKS-ONE-LANGUAGE-001 — strings hardcoded y un toggle ignorado
**Prioridad:** 🟠 P1 (visible al usuario) · **Esfuerzo:** S · **Estado:** ⚪ Pendiente.

- `IosAppNotificationManagerImpl` lleva la copy **hardcodeada en inglés** mientras Android sirve
  los 9 locales — mover a `composeResources/strings.xml` (deuda ya anotada en
  `ios-stubs-2026-06-10.md` y nunca cerrada).
- **Gap de paridad real**: `showParkingSaved()` en iOS no consulta
  `AppPreferences.notifyParkingDetected`; Android sí lo cablea desde
  [SETTINGS-AUDIT-REMEDIATION-001]. El ajuste existe en la UI común y en iOS se ignora.
- **Aceptación:** notificaciones iOS en el locale del device; el toggle silencia en ambas
  plataformas; keys compartidas, cero copy nueva.

### IOS-A-PARITY-TAIL-001 — la cola de paridad silenciosa (paraguas, trocear al ejecutar)
**Prioridad:** 🟡 P2 · **Esfuerzo:** M en total · **Estado:** ⚪ Pendiente.

Todo degrada vía `getOrNull()` o no-op documentado — nada crashea, todo resta:

| Hueco | Consecuencia en iOS |
|---|---|
| `RoadNetworkDataSource` sin impl (existía stub en 2026-06, hoy ni binding) | sin map-matching / snap de ruta |
| `TripTrail` sin binding | sin breadcrumbs de trayecto |
| `DrivingRouteStore` sin binding | sin snapshot de ruta [DET-ROUTE-TRACK-001]; la ruta pin-a-pin (`inferPinToPinRoute`) tampoco |
| `LocalDiagnosticsLog` sin binding | «Report a problem» sube solo cabeceras, sin parkdiag |
| `observePassiveLocation()` → `emptyFlow()` | sin carril pasivo |
| `ProcessDeathAttributor` sin equivalente | las muertes de proceso iOS no se estampan |
| `glassBlur` → passthrough · `MapForegroundEffect` siempre `true` · `collectAsState` sin lifecycle | pulido visual/energético |
| Dev Catalog sin `DevCatalogScreen`/`StateGalleryScreen` en iOS | la galería mock no existe en device iOS |

- **Aceptación:** cada fila o se implementa o se declara asimetría-por-diseño en el plan (con su
  porqué), y esta tabla queda a cero.

---

## Orden recomendado

```
1. IOS-DI-A-MOCK-GRAPH…          (S, urgente: rompe Android mock hoy; va EN la rama iOS)
2. IOS-CI-A-BRANCH…              (XS, destraba la verificación de todo lo demás)
   + TEST-A-KMP-SUITE… (29 errores mecánicos, ticket ya abierto)
3. IOS-XCODE-A-PLIST… + IOS-CRASH-A-BRIDGE…   (con el compañero del Mac; en paralelo con 4)
4. IOS-F1-A-CONTROLLER…          (el port aparca de verdad)
5. IOS-F2-A-WAKE… ──► IOS-SYNC-A-QUEUE…
6. IOS-NOTIF… / IOS-A-PARITY-TAIL… (huecos, tamaño S/M)
```

**Contexto de merge:** la rama sigue con merge BLOQUEADO hasta APK de la rama a campo (decisión
user 14-08); el login social iOS tiene su propio ticket (`ios-social-login-001`:
`googleWebClientId = null` + GoogleSignIn declarado pero no linkado). La trampa conocida del
rebase sigue viva: cada traza que master añade resuelve contra el harness viejo y rompe SIN
conflicto — verificar siempre con `:shared:testDebugUnitTest` desde su worktree.
