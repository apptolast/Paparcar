# DET-ADAPTIVE-SAMPLING-EARNS-ITS-FIXES-001 · El muestreo adaptativo gana un escalón de crucero (solo densificar)

**Estado:** ✅ Done (2026-09-14) — mergeado a master con squash; el hash vive en `MEMORY.md`
**Verificado:** suite `:shared:testDebugUnitTest` completa verde (`ObserveAdaptiveLocationUseCaseTest`
11/11: 3 escalones + histéresis 20–25 + boost cargando + burst intacto) · verify del grafo mock 2/2 ·
`compileMockDebugKotlin` + `compileProdDebugKotlin` OK · **`compileKotlinIosSimulatorArm64` OK**
(verifica también por compilador los cambios iOS de la fase 1).
**Abierto:** 2026-09-14 · desde master `b3d8168d` · fase 2 del pivote trayectos-como-producto
(plan en memoria `project_trip_product_pivot_and_route_store_001`)

## Problema

Con el trayecto como producto principal, la traza dibujada es un entregable — y en crucero urbano
sale pobre. **Paso 0 cumplido: el corpus de replay (8 trazas con banda de conducción, 421 pares de
fixes conduciendo) se midió ANTES de tocar nada**, y refuta parte del diagnóstico de partida:

| Hipótesis | Medición | Veredicto |
|---|---|---|
| "Pedimos fix cada 30 s conduciendo" | Cadencia post-burst med **13,9 s** (manda el `minUpdateInterval=15 s` de BALANCED, no el interval de 30) | ❌ mal formulada |
| Naps del OEM en el stream | **0/421** pares con hueco ≥60 s en ESTE corpus | ⚠️ existe (MIUI 7 min, campo 14-08, comentario en `CoordinatorDetectionService:1866`) pero no representado; lo mitiga el tap pasivo |
| Fixes descartables por el gate de 100 m | **8 %** de los fixes en conducción (38/475), concentrados en 3 trazas | ✅ causa real, secundaria |
| Espaciado entre vértices | med 35 m, **p90 132 m, max 534 m**; `CameliasGondola` p90 **265 m en urbano** | ✅ **causa dominante**: a ~15 s y 50 km/h son ~200 m por segmento — esquinas cortadas que el HMM debe adivinar |
| Burst post-arm | med 2,1 s los 3 primeros min | ✅ funciona, no tocar |

(Script del análisis en scratchpad `analyze_corpus.py`; parsea `fix(dtMs, lat, lon, acc, speed)`
de `coordinator/replay/Trace_*.kt`.)

## Doctrina violada

Ninguna — y el diseño la refuerza: *solo el movimiento MEDIDO confirma*, y esto añade medición.
**Regla dura del ticket (decisión del user, sesión 14-09): SOLO DENSIFICAR, nunca bajar del suelo
actual.** DET-BURST-001 ya midió que la inanición de fixes cambia veredictos
(`maxSpeedMps < 5` por hambre, no por coche lento); quitar densidad es lo peligroso, añadirla no.

## Diseño

`ObserveAdaptiveLocationUseCase` pasa de 2 a 3 escalones. La decisión sigue viva en el use case
puro de commonMain; el service no cambia.

| Modo | Stream | Cuándo | vs hoy |
|---|---|---|---|
| **HighAccuracy** | HIGH_ACCURACY 5 s/2 s | < 11 km/h (histéresis existente), burst 3 min, **o cargando** | igual |
| **Cruise** (nuevo) | HIGH_ACCURACY **10 s/5 s** (`observeCruiseLocation`) | 18–90 km/h | antes BALANCED 30/15 → ~3× más denso: ~70–140 m por segmento a 50 km/h |
| **Highway** | BALANCED 30 s/15 s (el `observeBalancedLocation` de siempre) | ≥ 90 km/h, con histéresis de reentrada a 72 km/h | igual que hoy (la autovía es el caso trivial del matcher — la geometría la restaura el grafo) |

- **Histéresis**: HA↔Cruise con los umbrales existentes (5/3 m/s); Cruise↔Highway 25/20 m/s (banda
  ancha para no aletear con la oscilación de velocidad en autovía).
- **Boost cargando** (decisión del user): `ChargingStateSource` (puerto nuevo, opcional —
  `null` = sin boost) → cargando fija HighAccuracy a cualquier velocidad. Impl Android
  (`BatteryManager.isCharging`); iOS queda null a propósito (batteryMonitoring de UIDevice tiene su
  propia ceremonia — follow-up si algún día importa).
- **Simplificación declarada**: el plan decía "≥90 km/h y rumbo estable"; `GpsPoint` no lleva
  bearing y la velocidad sola es un proxy suficiente (rotondas/salidas bajan de 90 → cae a Cruise).
  Si el campo enseña lo contrario, se añade el cómputo de rumbo por posiciones consecutivas.

## Criterio de éxito

- Tests del use case: Cruise a velocidad de conducción post-burst · Highway ≥25 m/s · histéresis en
  la banda 20–25 · cargando fija HighAccuracy · el burst sigue pinando HighAccuracy.
- Suite completa verde + compiles mock/prod + **iosSimulatorArm64 compila** (ahora se puede en
  Windows — memoria 14-09).
- En campo (cuando toque, gate no bloqueante de este merge): p90 de espaciado urbano < 150 m en un
  viaje real de crucero.

## Consumidores auditados

`grep ObserveAdaptiveLocationUseCase` — **todos reciben la densificación, monotónica**:

| Consumidor | Efecto | Clasificación |
|---|---|---|
| `CoordinatorDetectionService:103` (decisión + tap de ruta) | más evidencia medida, ruta más densa | cubierto (objetivo del ticket) |
| `IosDetectionController:102` | ídem en iOS | cubierto |
| ⚠️ `BluetoothParkingDetector:68` (caza post-disconnect) | opera parado/lento → HighAccuracy; Cruise apenas le aplica | exento con razón |
| ⚠️ `BluetoothDepartureDetector:66` (vigilancia de salida BT, `f2800a11`) | a velocidad de conducción ahora Cruise en vez de Balanced → mide la salida ANTES | cubierto (mejora en la dirección del ticket 4 del plan) |
| `ParkingHistoryViewModel:23` | pantalla abierta conduciendo → algo más de batería, medición mejor | cubierto |
| `DomainModule:117` (factory) | gana el puerto opcional `getOrNull()` | cerrado |
| Implementadores de `LocationDataSource` (android, ios, fakes commonMain/commonTest, mock de app) | método nuevo `observeCruiseLocation` SIN default — el compilador fuerza el barrido | cerrado |
| Replays de detección (`DetectionTraceReplayTest`) | inyectan fixes grabados, cadencia congelada | exento (no consumen el use case) |

## Fuera de alcance

- El nap del OEM: lo cubre el tap pasivo [ROUTE-PASSIVE-FILL-001] + (futuro) el tracker de fase 3.
- Displacement (`setMinUpdateDistanceMeters`): con 10 s/5 s el espaciado ya queda bajo el objetivo;
  añadirlo hoy sería un segundo mando sin medición que lo pida.
- Bajar el gate de accuracy o meter fixes >100 m: decidido en la sesión — no se mete basura.
