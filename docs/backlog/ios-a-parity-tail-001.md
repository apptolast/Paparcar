# IOS-A-PARITY-TAIL-001 · La cola de paridad silenciosa — cada hueco se cierra o firma su porqué

**Estado:** ✅ Done · plegado en `feature/IOS-F0-001-fase0` (hash en MEMORY.md)
**Declarado ⏳:** veredicto CI (Native + simulador + xcodebuild) · en device: Home libera la
ubicación al backgroundear; una sesión iOS confirmada lleva `routePolyline`.
**Follow-ups abiertos (docs propios):** `ios-death-a-kill-that-signs-its-name-001` ·
`ios-diag-a-log-that-survives-to-be-shipped-001` · `ios-devcat-a-gallery-the-simulator-can-open-001` ·
`ios-route-a-snap-the-drainer-never-does-001`.
**Padre:** `ios-parity-audit-2026-09-03.md` (ticket 8/8, paraguas P2 "trocear al ejecutar")

## Problema
Ocho huecos del port iOS degradan vía `getOrNull()` o no-op documentado — nada crashea, todo
resta. La tabla del padre (filas 187-196) debe quedar a cero: cada fila **o se implementa o se
declara asimetría-por-diseño con su porqué**. Hoy ninguna de las ocho está ni cerrada ni firmada.

## Doctrina violada
Ninguna de detección. La regla que sí muerde es la del padre: una degradación silenciosa que nadie
declaró no es una decisión, es un olvido con buena suerte.

## Señales / datos disponibles
Barrido completo del 07-09 (este ticket). Los hallazgos que cambian el plan respecto a la tabla:
- `glassBlur` tiene **cero call sites** en todo el repo — el "hueco iOS" cuesta 0 en runtime; lo
  honesto es borrarlo, no implementarlo.
- `collectAsStateWithLifecycle` **ya corre en iOS** (`PermissionsScreen.ios.kt:20` lo usa en prod y
  la suite del simulador pasa) — la premisa de la costura `StateFlowExt` ("iOS no tiene lifecycle")
  es falsa. Además `App.kt:157-159` ya la bypasea en commonMain y
  `VehicleRegistrationScreen.kt:128` usa `collectAsState` crudo.
- `MapForegroundEffect.ios.kt` emite `true` una vez y **nunca `false`** → la petición de ubicación
  high-accuracy de Home no se libera jamás en iOS [UI-LOC-FOREGROUND-001]. Único hueco con coste
  de batería medible hoy.
- `RoadNetworkDataSource` tiene precedente iOS exacto: `IosOverpassPlacesDataSourceImpl` ya habla
  con Overpass por NSURLSession. El matcher (`TrailMapMatcher`) es común.
- `TripTrail`/`DrivingRouteStore`: la lógica interesante (`DrivingRoute.append`, testeada) es
  común; solo falta el shell NSUserDefaults (idioma ya establecido: `IosPendingArmRecords` y cía).
  `ConfirmParkingUseCase.encodeFreshRoute()` es común → con store + feeder, las sesiones iOS
  llevan `routePolyline` sin tocar nada más.
- `ProcessDeathAttributor` **no tiene puerto común** (clase androidMain inyectada por tipo
  concreto) — no es un port mecánico: exige promover un puerto y diseñar sobre MetricKit.
- `LocalDiagnosticsLog` depende de un sink de fichero que iosMain no tiene (`FileAntilog` es
  androidMain; iosMain no tiene paquete `logging/`) + gzip sin equivalente Foundation.
- Dev Catalog: el modelo (MockScenario + fakes + iosMockModule) YA es común/iOS; solo las 3
  pantallas Compose (~2.400 LOC) están varadas en `app/src/mock/`.

## Diseño
El sistema es el criterio de aceptación del padre aplicado fila a fila, sin heroísmos:

**Se implementa aquí (mecánico, valor inmediato):**
1. `MapForegroundEffect.ios` real — `NSNotificationCenter` (will-enter-foreground /
   did-enter-background) + `DisposableEffect`; el `false` por fin se emite.
2. `IosOverpassRoadNetworkDataSourceImpl` (NSURLSession, espejo del de places) + binding →
   el map-matching en vivo de `HomeTripController` se activa en iOS.
3. `IosTripTrail` (NSUserDefaults + JSON, ring 60/12h como Android) + binding.
4. `IosDrivingRouteStore` (ídem, `DrivingRoute.append` común) + binding + **feeder**: el stream de
   tracking de `IosDetectionController` alimenta el store, como hace el service Android.
5. Retirar la costura `StateFlowExt` (expect/actual con premisa falsa): todo call site pasa a
   `collectAsStateWithLifecycle` directo, incluido el `collectAsState` crudo de
   VehicleRegistration. Una abstracción cuyas dos ramas serían idénticas no es una abstracción.
6. Borrar `glassBlur` (3 ficheros, cero llamadores) — al quitar algo, borrar limpio.

**Se firma como asimetría-por-diseño (con el porqué en código y aquí):**
7. `observePassiveLocation()` → CoreLocation **no tiene** proveedor pasivo cross-app; no hay API
   contra la que escribir. Ya documentado en `IosLocationDataSourceImpl.kt:38-40`. CERRADA.

**Se declara con ticket propio (exigen diseño, no port):**
8. `ProcessDeathAttributor` → `ios-death-a-kill-that-signs-its-name-001.md` (promover puerto común
   + MetricKit `MXAppExitMetric`, entrega next-day — otra semántica, otro diseño).
9. `LocalDiagnosticsLog` → `ios-diag-a-log-that-survives-to-be-shipped-001.md` (antilog de fichero
   rotante iOS + gzip zlib/cinterop ANTES de poder snapshotear).
10. Dev Catalog iOS → `ios-devcat-a-gallery-the-simulator-can-open-001.md` (mover 3 pantallas a
    commonMain: coste real = ~2.400 LOC al binario prod + BackHandler/Configuration).
11. Snap de ruta + pin-a-pin en el carril de enrichment común →
    `ios-route-a-snap-the-drainer-never-does-001.md` (extraer el `snapRoute` del worker Android a
    pieza común consumida también por `SyncOutboxDrainer`).

## Criterio de éxito
- Tabla del padre a cero: 6 filas implementadas, 1 firmada, 4 con doc de ticket propio.
- CI del PR #3 verde: Native compila, suite en simulador pasa, xcodebuild construye.
- En simulador/device: Home libera la ubicación al pasar a background; una sesión confirmada
  lleva `routePolyline`.

## Consumidores auditados
- `RoadNetworkDataSource`: `PresentationModule:36` (getOrNull) → `HomeTripController:143-158`
  (único consumidor común; se activa con el binding). `EnrichParkingSessionWorker` es androidMain
  y no cambia. → CUBIERTO con el binding iOS.
- `TripTrail`: `DomainModule:82` → `GetOneLocationUseCase:54`. Ojo colisión de nombre: el
  `tripTrail` de `PaparcarMapView`/`HomeMapSection` es un param de UI, NO este puerto. → CUBIERTO.
- `DrivingRouteStore`: `PresentationModule:40` → `HomeTripController:307` ·
  `DetectionModule:130` → `ConfirmParkingUseCase:326,423`. Ambos se activan con binding + feeder.
  → CUBIERTO.
- `StateFlowExt`: 8 call sites vía costura + 1 crudo (VehicleRegistration) + 3 bypass (App.kt) +
  2 directos por diseño (PermissionsScreen.*) → barridos TODOS a la llamada directa.
- `glassBlur`: grep repo entero = solo las 3 declaraciones + el doc del padre. → BORRADO limpio.
- `MockKoinGraphVerifyTest` allowlist (app/src/testMock): su comentario justifica los 3 opcionales
  con "same degradation as prod iOS" — esa razón muere con este ticket → actualizar el comentario
  (el allowlist sigue: el flavor mock sigue sin binding a propósito).
- `IosMockModule` (espejo a mano): decidir si el mock iOS binda los stores nuevos → NO: mock queda
  sin binding en ambas plataformas, misma degradación, comentario actualizado.
