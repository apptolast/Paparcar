# Arquitectura de señales de detección — referencia

> El diseño de referencia del núcleo de Paparcar. Define **qué señal gobierna cada decisión** y por
> qué. Principio rector: **fallo asimétrico** — un falso negativo (no detecto plaza) cuesta ~0; un
> **falso positivo (plaza fantasma) rompe la confianza de la red**. Por eso cada decisión cuelga de
> la señal *más difícil de falsear en un no-evento*.

> **Relacionado:** este doc cubre las *señales* del algoritmo. La capa de *readiness / permisos /
> banner* (qué le decimos al usuario sobre la detección, el tiering CORE-vs-PRODUCER y el onboarding)
> está en [`DETECTION-READINESS.md`](./DETECTION-READINESS.md) (epic DET-READY-001).

## Calidad de señal — la regla

La señal **decisiva** (la que confirma/dispara) debe ser **físicamente imposible de producir en un
no-evento** (atasco, semáforo, drop-off, ir en bus). Todo lo demás solo puede *abrir candidato*,
*subir confianza* o *corroborar* — **nunca decide solo**.

| Señal | ¿Aparece en un no-evento? | Rol |
|---|---|---|
| `speed = 0` / parado | Sí (semáforo, cola) | no decisiva |
| Dwell (tiempo parado) | Sí (cola) | no decisiva (solo timing del prompt) |
| **STILL** (AR) | Sí (coche parado en atasco) | no decisiva, redundante con el egreso → **eliminada como señal** (DET-D-03) |
| AR `IN_VEHICLE_EXIT` | Sí (espurio a mitad de trayecto = FP de Praga) | no decisiva |
| Pasos solos | Sí (móvil en el bolsillo en stop-and-go) | no decisiva |
| AR `IN_VEHICLE_ENTER` | Sí (bus, coche ajeno) | **corroborador** de la liberación (no trigger, no obligatorio) |
| **Egreso** = pasos AND desplazamiento ≥18 m tras conducir | **No** (exige caminar lejos del coche parado) | **DECISIVA** (confirmar park) |
| **GEOFENCE_EXIT de MI coche** + velocidad | **No** (específico de MI plaza + conducir) | **DECISIVA** (armar + liberar) |

Las dos rutas de detección convergen independientemente en el egreso: la ruta BT confirma con
*"walk ≥30 m"* — eso **es** desplazamiento peatonal. Señal de que el principio es correcto.

## Las 3 etapas y su señal

```
                 GEOFENCE_EXIT(mi coche) → coordinator filtra por velocidad
   [PARKED] ───────────────────────────────────────────────▶ [DRIVING]   ← ARMAR detección
      ▲                                                          │            (CoordinatorDetectionService,
      │                                                          │             getForegroundService privilegiado)
      │  egreso: pasos ∧ desplazamiento ≥18 m                    │ stop (speed<umbral)
      │  (decisivo)                                              ▼
      └────────────────────────────────────────────────── [CANDIDATE]   ← DETECTAR park
                                                                 │
                              GEOFENCE_EXIT + velocidad          ▼
   [PARKED] ◀──────────────────────────────────────── publicar plaza   ← LIBERAR (departure)
```

El **mismo discriminador (velocidad)** gobierna armar y liberar. Armar y liberar cuelgan del MISMO
`GEOFENCE_EXIT`; la diferencia walk-vs-drive la decide la velocidad (en el coordinator para armar, en
`DetectParkingDepartureUseCase` para liberar). El IN_VEHICLE solo corrobora la liberación lenta de
garaje — no es obligatorio ni para armar ni para liberar.

### 1. ARMAR (empezar a detectar) — `DET-G-01`
- **Trigger: `GEOFENCE_EXIT` de la plaza del usuario**, entregado al servicio vía
  `getForegroundService` (Play Services lo entrega privilegiado, igual que AR — `BUG-FGS-001`).
- **Walk-out filtrado por velocidad (igual que la liberación):** la liberación distingue andar-vs-
  conducir por **velocidad** (`DetectParkingDepartureUseCase` confirma con `GEOFENCE_EXIT` + velocidad
  de salida; el IN_VEHICLE solo corrobora, no es obligatorio). El armado usa el **mismo** discriminador:
  como no se puede leer velocidad de forma síncrona dentro de la ventana de FGS, **el coordinator es
  el gate de velocidad** — un walk-out nunca alcanza `minimumTripSpeedMps` y se auto-aborta
  (`falseEnterAbortSteps` / `maxNoMovementMs`). Ningún spot fantasma puede salir: el egreso exige
  haber conducido Y caminado lejos.
- **AR `IN_VEHICLE_ENTER` ya NO arma** — disparaba con cualquier vehículo (bus, coche ajeno). Queda
  solo para registrar el timestamp de la ventana de departure.
- **Cold-start:** la geocerca solo existe si hay plaza. El primer park (o tras liberar sin nuevo
  park) se marca a mano (`HomeMode.AddingParking`). Tras marcarlo → al salir se arma el siguiente.
  *Al liberar se borra la geocerca, pero el armado ya se disparó en el exit, así que da igual.*

### 2. DETECTAR (confirmar park)
- **Decisivo: egreso** = `stepCount ≥ minStepsToConfirm` **AND** desplazamiento ≥
  `minEgressDisplacementMeters` (18 m) desde `bestStopLocation` (la posición del coche, fix de mejor
  precisión en la ventana inicial). Ver `PARKING-DETECTION.md` §DET-A/DET-C-01.
- **Pure function:** `EvaluateParkingDecisionUseCase` (DET-D-02) — egreso obligatorio para todo
  auto-confirm; STILL/dwell/AR-exit solo abren candidato / muestran prompt.
- **Prompt (no decisivo):** tras un dwell sin egreso (garaje, GPS frío) → "¿Has aparcado?" manual.

### 3. LIBERAR (publicar plaza) — ya correcto
- **Decisivo: `GEOFENCE_EXIT` + velocidad de salida sostenida** (`DetectParkingDepartureUseCase`),
  con `IN_VEHICLE` reciente como corroborador. Es el mismo `GEOFENCE_EXIT` que ahora también arma.

## Estado vs el diseño

| Pieza | Estado |
|---|---|
| Egreso decisivo (detectar) | ✅ DET-A / DET-C-01 / DET-D-02 |
| GEOFENCE_EXIT arma (no AR), walk filtrado por velocidad | ✅ DET-G-01 — **pendiente verificar `getForegroundService`-geofencing en device** |
| AR no decisivo | ✅ AR movido a broadcast (sin FGS, sin flash); nunca arma; solo registra el timestamp de entrada + alimenta EXIT/STILL al coordinator ya armado. `HandleVehicleTransitionUseCase` + `TransitionAction` borrados. |
| Pasos+egreso confirman sin AR EXIT | ✅ DET-D-03 — Path 8 ya no exige `vehicleExitConfirmed`; el egreso es el gate decisivo. Validado por field test (la traza esperaba ~16 s al AR EXIT con pasos+egreso ya listos). AR EXIT pasa a hint puro. |
| Hold post-confirm (errand re-anchor) | ✅ DET-C-02 — el egreso confirma **tentativo**; la sesión sigue viva `confirmHoldMs` (2 min). Si se reanuda la marcha → descarta y re-ancla en el sitio final (bug del tabaco: el park se quedaba en el kiosko). Si pasa la ventana parado → finaliza. Reloj inyectable + 2 tests. Tunear `confirmHoldMs` con datos de campo. |
| STILL eliminada como señal | ✅ DET-D-03 — sin registro AR (`requestActivityTransitionUpdates` STILL borrado), sin consumo en receiver/iOS/coordinator. El scaffolding del scorer (`ParkingSignals.activityStill`, `stillBonus`) queda **inerte** (siempre false) pendiente del rework D-03c. |
| Scorer probabilístico → metadato (D-03c) | ⏳ tras field test de garaje |
| Cold-start UI | ⏳ DET-G-02 |

## Riesgos abiertos (verificar en field test)
1. **`getForegroundService` para geofencing** arranca el FGS en Android 12+ — sin verificar (AR sí
   funciona; geofencing es la misma API de Play Services → probable). Fallback: revertir el
   PendingIntent a `getBroadcast` (1 línea) + el `GeofenceBroadcastReceiver` sigue en su sitio.
2. ~~Flash de FGS en cada `IN_VEHICLE_ENTER`~~ — **resuelto:** AR movido a `getBroadcast` (sin FGS).
3. **Muerte de proceso** entre conducir y salir: el armado depende de `GEOFENCE_EXIT` (no de estado
   en memoria), así que se arma igual; la liberación cae a velocidad si falta el timestamp de AR.
