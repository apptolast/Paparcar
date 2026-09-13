# IOS-DI-A-MOCK-GRAPH-ONLY-PROD-IS-VERIFIED-001 · el flavor mock crashea y ningún test lo mira

**Estado:** ✅ Done (03-09-2026) · plegado en `feature/IOS-F0-001-fase0` (**la rama iOS, NO
master** — la regresión la introdujo `87f08dbe` [IOS-F0-03] y master ni conoce
`DeviceCapabilities`; llegará a master cuando la rama mergee) · ⏳ declarados: device (`/run` del
flavor mock) y compile de `iosMain` en Mac/CI (`[VERIFICAR-MAC]`).

## Problema

`DeviceCapabilities` [IOS-F0-03] se bindea SOLO en los platform modules de producción
(`androidPlatformModule` true/true, `iosPlatformModule` false/false), pero
`DetectionModule.kt:276` lo exige con `get()` (`EvaluateDetectionReliabilityUseCase`) y los
grafos MOCK no cargan ningún platform module:

- Android mock (`MockPaparcarApp`): `loginPresentationModule + presentationModule + domainModule
  (includes detectionModule) + mockModule` → `NoDefinitionFoundException` al resolver
  `HomeViewModel` / `SettingsViewModel` / `PermissionsViewModel` (los tres consumen la fiabilidad).
- iOS mock (`MockMainViewController`): `presentationModule + domainModule + iosMockModule` → ídem,
  más ~11 bindings desfasados respecto al gemelo Android (sin `MockScenario`, sin
  `DetectionEventLogger`/`UiLocationLogger`, sin `DepartureWatchResumer`, sin `DeviceInfoProvider`,
  sin `DiagnosticsRepository`/`DiagnosticsReportUploader`, sin `AddressAndPlaceRepository` ni
  `geocoderCacheDao`/`LocalAddressAndPlaceDataSource`, sin config de login).

Compila y los 2.178 tests pasan porque `KoinModuleVerifyTest` solo verifica el grafo
**prod-Android**. Detectado en la auditoría de paridad
(`docs/backlog/ios-parity-audit-2026-09-03.md`).

## Doctrina violada

- *Prohibición sin testigo no es chequeo* [DET-KOIN-MODULE-VERIFY-001]: hay CUATRO grafos
  arrancables (prod/mock × Android/iOS) y solo uno tiene verificación. Los otros tres solo fallan
  en runtime, en la pantalla que primero pida el binding.
- *Dev Catalog en sync* [MOCKQA-001]: el propio historial de `MockModule.kt` es una lista de
  parches por este mismo agujero (`MOCK-SETTINGS-TAB-CRASHES-WITHOUT-DEVICE-INFO-001`,
  `ACCOUNT-DELETE-SWEEPS-DIAGNOSTICS-001`, `MOCK-AUTH-SCREENS-NEED-THEIR-VIEWMODELS-001`…). El
  invariante nunca se fijó en un test.

## Señales / datos disponibles

- `KoinModuleVerifyTest` (androidUnitTest de `:shared`) ya tiene el patrón: `verify()` +
  población-testigo. La lista blanca `Boolean::class` ya documenta el caso `DeviceCapabilities`.
- Los fakes viven en `shared/src/commonMain/fakes/` — visibles desde ambas plataformas; lo único
  que vive en `:app` es `mockModule` + el Dev Catalog.

## Diseño

**El invariante — «todo grafo arrancable resuelve» — se fija donde cada grafo se define:**

1. `DeviceCapabilities` en los dos mock modules: Android `(true, true)` (espeja el device donde
   corre el Dev Catalog), iOS `(false, false)` (espeja el contrato iOS).
2. `IosMockModule` se resincroniza con su gemelo Android binding a binding (misma lista de
   contratos, fakes compartidos; `RoadNetworkDataSource` queda fuera a propósito: su impl es
   androidMain y en iOS prod tampoco existe — paridad con prod, no con Android).
3. **Testigo nuevo**: verify del grafo mock-Android con el `mockModule` REAL (no una copia de su
   lista), en el source set de test del flavor mock de `:app`, espejando la lista de módulos de
   `MockPaparcarApp` igual que `KoinModuleVerifyTest` espeja `PaparcarApp`. Con población-testigo
   (la fiabilidad y el Home deben estar entre los sujetos verificados).
4. El grafo mock-iOS no puede verificarse hoy desde JVM (iosMain) — queda cubierto por espejo
   manual y lo hereda [TEST-A-KMP-SUITE-THAT-ONLY-RUNS-ON-JVM-IS-HALF-A-SUITE-001] cuando los
   tests iOS corran. Declarado aquí, no silenciado.

## Ejecutado (03-09, pendiente de commit)

- `MockModule.kt`: `DeviceCapabilities(true, true)` con comentario del porqué.
- `IosMockModule.kt`: reescrito como gemelo binding-a-binding (scenario/runtime cableados igual,
  `DeviceCapabilities(false, false)`, loggers no-op, diagnósticos, geocoder cache, login config
  con `googleWebClientId = null`). `[VERIFICAR-MAC]` — iosMain no compila en Windows; lo cubre el
  job `apple` cuando la rama pase por CI.
- `MockMainViewController.kt`: añade `loginPresentationModule`, espejo de `MockPaparcarApp`.
- `MockKoinGraphVerifyTest` nuevo en `app/src/testMock/` (+ deps de test en `:app`): verify del
  grafo mock REAL + testigo de población. Lista blanca con su porqué cada entrada: `Context`,
  `Function0/1`, `UserScopedRepository`, `Boolean`, y los TRES puertos opcionales que el grafo
  mock deja fuera a propósito (`DrivingRouteStore`, `LocalDiagnosticsLog`, `TripTrail` — los
  consumidores los toman nullable vía `getOrNull()`, misma degradación que iOS prod).
- `ci.yml`: `:app:testMockDebugUnitTest` añadido al paso de tests.
- **Prueba negativa hecha**: quitando el binding, caen los DOS carriles — el verify con la
  excepción exacta del crash de producción (`Missing definition … 'capabilities' …
  EvaluateDetectionReliabilityUseCase`) y el testigo de población.
- Verificado: `:app:testMockDebugUnitTest` (2/2) + `:shared:testDebugUnitTest` +
  `:app:compileMockDebugKotlin` + `:app:compileProdDebugKotlin` — BUILD SUCCESSFUL.
- ⏳ Falta: Home/Settings/Permissions abiertos en device (`/run` de `assembleMockDebug`) y el
  compile iosMain en un Mac/CI.

## Criterio de éxito

- `:app:testMockDebugUnitTest` verde con el verify nuevo; quitar cualquier binding obligatorio del
  mock lo pone rojo.
- El verify nuevo corre en CI (mismo job que el resto de tests).
- Home/Settings/Permissions abren en `assembleMockDebug` (verificación en device si hay `/run`).
- `:shared:testDebugUnitTest` + `compileMockDebugKotlin` + `compileProdDebugKotlin` verdes.

## Consumidores auditados

- `get<DeviceCapabilities>()`: solo `EvaluateDetectionReliabilityUseCase` (DetectionModule) →
  cubierto por los 4 grafos tras el fix.
- Consumidores de los bindings que le faltaban a `IosMockModule`: `HomeViewModel`
  (`UiLocationLogger`, `DepartureWatchResumer`), ~8 use cases (`DetectionEventLogger`),
  `GetAddressAndPlaceUseCase` (`AddressAndPlaceRepository`), `DeleteAccountUseCase`
  (`DiagnosticsRepository`), `SendDiagnosticsReportUseCase` (`DiagnosticsReportUploader`),
  Ajustes (`DeviceInfoProvider`) → todos bindeados en la resincronización.
- Pendiente de barrer al cerrar: ningún otro `single`/`factory` de `commonMain` con `get()`
  obligatorio fuera de los grafos mock (lo responde el verify nuevo, no un grep manual).
