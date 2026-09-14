# TRIP-A-LONG-DRIVE-KEEPS-ITS-ORIGIN-001 · La ruta del viaje en curso vive en Room y deja de perder su origen

**Estado:** ✅ Done (2026-09-14) — mergeado a master con squash; el hash vive en `MEMORY.md`
**Abierto:** 2026-09-14 · desde master `f2800a11`
**Verificado:** suite `:shared:testDebugUnitTest` completa verde (RoomDrivingRouteStoreTest 6/6 ·
DrivingRouteTest 16/16 · AppDatabaseV1BaselineTest 4/4 con el testigo 7→8) ·
`:app:testMockDebugUnitTest` (verify del grafo mock) verde · `compileMockDebugKotlin` +
`compileProdDebugKotlin` OK · DDL de `MIGRATION_7_8` comparado carácter a carácter con el
`8.json` exportado por KSP — idéntico.

## Problema

El giro de producto convierte el trayecto —ruta, km, gasto— en producto principal, y el almacén de la
ruta en curso fue diseñado para otra cosa: dibujar "el viaje de ahora" en Home. Dos límites
estructurales, ambos medibles en el código:

1. **El tope deslizante de 500 puntos** (`DrivingRoute.MAX_POINTS`, `takeLast` en
   `DrivingRoute.append`): un viaje largo tira sus puntos MÁS VIEJOS — es decir, **pierde su
   origen**. En urbano denso (spacing ≈ 12 m por la decimación) el tope garantiza apenas ~6 km
   retenidos. Un viaje de 7 h no puede contarse con un almacén que olvida por dónde empezó.
2. **La escritura es O(n²) sobre el viaje**: `DrivingRouteStoreImpl` (SharedPrefs, Android) y
   `IosDrivingRouteStore` (NSUserDefaults, iOS) releen y reserializan **la lista JSON entera en cada
   fix admitido**. Con 500 puntos es unos KB por tick; sin tope sería creciente y sin límite — el
   tope y el formato blob se sostienen mutuamente, y hay que quitar los dos a la vez.

El tope, además, es el motivo del blob y viceversa; parchear uno solo (subir MAX_POINTS) dispararía
el coste de cada tick. Sistema, no parche: cambia la unidad de escritura.

## Doctrina violada

Ninguna, hoy: el almacén cumple lo que [DET-ROUTE-TRACK-001] le pidió. Lo que hay es un **techo
estructural** incompatible con el pivote de producto (trayectos como servicio) decidido el
2026-09-14: la fase 1 del plan es exactamente quitar este techo, porque todas las fases siguientes
(tracker, carril BT medido, viajes largos) escriben sobre este almacén.

## Señales / datos disponibles

- La regla de acumulación es pura y está testeada (`DrivingRoute`: gate de accuracy 100 m,
  decimación 12 m, auto-segmentación por gap de 20 min, `endAtAnchor`). La regla NO cambia; cambia
  quién la aplica y dónde se persiste.
- Room KMP ya corre en ambas plataformas (`AppDatabase` v7; iOS construye la base real en
  `IosPlatformModule:40-42`). Los DAOs se atan en `DataModule` común.
- Consumidores del puerto `DrivingRouteStore` (mapa completo abajo): todos llaman desde contexto de
  corrutina → el puerto puede pasar a `suspend` sin fricción.

## Diseño

**Una impl común Room sustituye a los dos gemelos de plataforma.** El invariante ("la ruta del viaje
en curso sobrevive a background, muerte de proceso y cold-start, sin perder el origen") pasa a vivir
en UN sitio: `RoomDrivingRouteStore` en `commonMain`, sobre una tabla nueva.

1. **`DrivingRoute` expone la regla por-punto**: `admit(last, point)` → `REJECT` (accuracy) ·
   `DECIMATE` (< 12 m) · `RESET` (gap > 20 min → viaje nuevo) · `APPEND`. La versión de lista
   `append(current, point)` se re-expresa sobre `admit` para que las dos vías no puedan divergir.
   **`MAX_POINTS` desaparece** — testigo nuevo: 600+ puntos admitidos, el origen sigue siendo el
   primero.
2. **Tabla `trip_route_points`** (v7→v8, `MIGRATION_7_8` + testigo en `AppDatabaseV1BaselineTest`
   siguiendo el patrón 6→7): `seq` PK autoincrement + los campos de `GpsPoint` (lat, lon, accuracy,
   timestamp, speed, provider?, satelliteCount? — la provenance viaja entera, DET-A-FIX-MUST-SAY-…).
   Nota: `CURRENT_IDENTITY_HASH` del baseline es el hash de **v1** — no cambia con una entidad nueva.
3. **`RoomDrivingRouteStore`** (commonMain): Mutex + caché en memoria del último punto admitido
   (para decidir decimación sin SELECT por fix; se rehidrata del DAO al primer uso tras
   renacer). `admit`→ un INSERT de UNA fila (O(1) por tick, durabilidad por punto — sin lotes: a
   ritmo de decimación es ≤1 insert/pocos segundos, batching sería sobre-ingeniería). `RESET` →
   DELETE + INSERT. `clear()` → DELETE.
4. **El puerto pasa a `suspend`** (`append`/`points`/`clear`).
5. **DI**: DAO + binding del store en `DataModule` (común, incondicional); se retiran los bindings de
   `AndroidDetectionModule` e `IosDetectionModule` y se borran las dos impls de plataforma.

**Fuera de alcance, a propósito:**
- Retención de fixes crudos post-snap para diagnóstico: los crudos son transitorios (viaje en curso);
  al confirmar, el polyline va a la sesión y `clear()` vacía la tabla — exactamente la semántica
  actual. `parkdiag.log` ya conserva el rastro forense.
- Migrar el contenido de los prefs viejos (`driving_route` / NSUserDefaults): un update a mitad de
  viaje pierde, como mucho, el dibujo de ESE viaje (el origen retrodatado de `HomeTripController` lo
  cubre). Los ficheros huérfanos (~KB) se quedan; no vale un ticket.
- Cambios de muestreo/perfiles: fase 2 del plan (DET-ADAPTIVE-SAMPLING-…), con `det-change`.

## Criterio de éxito

- Testigo del no-tope: una ruta de 600+ puntos admitidos conserva su primer punto (unit, puro).
- `RoomDrivingRouteStoreTest` (Robolectric): persiste y devuelve en orden; decimación no escribe;
  gap resetea la tabla; `clear()` vacía; una instancia NUEVA sobre la misma base continúa decimando
  contra el último punto persistido (renacer de proceso).
- Testigo de `MIGRATION_7_8` en `AppDatabaseV1BaselineTest`: un punto escrito y releído a través del
  fichero migrado.
- Suite completa verde (`:shared:testDebugUnitTest`) + `:app:compileMockDebugKotlin` +
  `:app:compileProdDebugKotlin`.

## Consumidores auditados

`grep DrivingRouteStore` (2026-09-14, worktree):

| Consumidor | Contexto | Acción |
|---|---|---|
| `CoordinatorDetectionService:1874` (tap pasivo) | `collect {}` (suspend) | sin cambio |
| `CoordinatorDetectionService:1910` (tap tracking) | `onEach {}` (suspend) | sin cambio |
| `IosDetectionController:332` (tap tracking) | `onEach {}` (suspend) | sin cambio |
| `ConfirmParkingUseCase:358` (`clear`) · `:455` (`points`) | invoke suspend | `encodeFreshRoute` pasa a suspend |
| `HomeTripController:307` (`points`) | lambda de flow | `freshRecordedRoute` pasa a suspend |
| `AndroidDetectionModule:83` / `IosDetectionModule:32` | binding | retirado → `DataModule` |
| `PresentationModule:42` · `DetectionModule:130` (`getOrNull`) | binding opcional | resuelve siempre (binding común) |
| `FakeDrivingRouteStore` (commonTest) | fake | overrides suspend, misma lógica |
| `DrivingRouteStoreImpl` (Android) · `IosDrivingRouteStore` (iOS) | impls | **borradas** |
| ⚠️ `MockModule.kt:141` (`app/src/mock`) | binding del Dev Catalog | **no lo vio el grep inicial** (acotado a `shared/src`) — lo cazó `compileMockDebugKotlin`. Pasa a `RoomDrivingRouteStore` sobre la base in-memory del mock + su DAO. Lección: el barrido de un símbolo va sobre el REPO, no sobre el módulo |

Nota iOS: en Windows el target iOS no compila — `IosDetectionModule` (binding retirado) e
`IosDetectionController` (tap `onEach`, ya suspend) quedan verificados por lectura, no por
compilador. El binding común de `DataModule` resuelve en iOS porque su Koin ya carga `dataModule`
(repos) y `AppDatabase` real existe (`IosPlatformModule:40-42`).
