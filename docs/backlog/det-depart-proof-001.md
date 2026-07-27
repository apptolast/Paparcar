# DET-DEPART-PROOF-001 — la publicación de la plaza liberada exige un fix independiente del EXIT

**Estado:** ⏳ implementado en `bugfix/DET-DEPART-PROOF-001` (encima de DET-DRIVE-PROOF-001) · esperando go-ahead de commit
**Origen:** FP 2026-07-27 18:30 (Oppo, en casa, PARADO — segunda tarde consecutiva), sesión `1785169816161`.

## Forense (telemetría pap-26)

El pin fantasma de ayer (DET-DRIVE-PROOF-001, 14:56) seguía ACTIVO en el móvil, con su geocerca
`9d2cb23b` alrededor del salón. Hoy:

| hora | qué | dato |
|---|---|---|
| 18:30:16.1 | fix espejismo | 36.6031846,-6.2293295 — a **121 m** de casa, **4 m/s (14.4 km/h)**, acc 21.5 m |
| 18:30:16.1 | GEOFENCE_EXIT arma | `geof=9d2cb23b d=121m acc=21m dep=verified_speed` — la verificación se cree ese único Doppler (umbral 10 km/h, acc ≤ 50) |
| 18:30:16.267 | **plaza fantasma publicada** | `spots/9d2cb23b`, Avenida Sanlúcar 33, conf 0.85 (heredada del pin fantasma), TTL 2 h — el worker de salida muestreó el MISMO fix cacheado 140 ms después y lo dio por "velocidad de conducción confirmada" |
| 18:30:20 → 18:33 | stream real | todos los fixes en casa a 0–0.3 m/s — el espejismo fue UN fix |
| 18:33:45 | DECISION | `PROMPT_SHOWN low_medium 0.55` — **DET-DRIVE-PROOF-001 funcionó**: sin conducción corroborada no hubo pin, degradó a pregunta |
| 18:39:57 | CANDIDATE OPENED | el usuario abre la notificación |

Validación de campo del fix de ayer ✅ (el mismo tipo de espejismo ya no planta pin). El FP de hoy
es la **otra autoridad** que seguía confiando en un fix aislado: la publicación de la plaza liberada.

## Agujero

`DetectParkingDepartureUseCase` confirmaba la salida con `isCredibleDrivingSpeed` sobre UNA muestra
(`getOneLocation` con `freshFixMaxAgeMs=30 s`) — y esa muestra puede ser **el mismo fix que disparó
el EXIT** (eco de caché): un solo espejismo se cuenta a sí mismo dos veces (dispara + "confirma") y
publica una plaza a la comunidad. Es la violación de siempre: *el evento nomina, solo el movimiento
MEDIDO confirma* — un fix no es movimiento.

## Fix (el invariante en UN sitio: la decisión de salida)

`departureProofMinGapMs = 20_000L` (config): una muestra de velocidad solo **confirma** si su
timestamp posdata el EXIT en ≥ 20 s — es decir, si es una medición genuinamente nueva. Se aplica en
el cómputo único de `speedConfirmsMovement` (cubre la rama velocidad-sola Y la rama ENTER+velocidad).

- Conductor real: sigue a velocidad en los reintentos del worker (~15/45/105 s) → confirma en el
  primer intento cuyo fix pasa el gap (~45 s; con el Coordinator armado alimentando la caché, el
  fix de ese intento es reciente). Coste: la publicación se retrasa ~45 s. Asimetría respetada.
- Espejismo: las ráfagas observadas mueren en ≤ 10 s (27-07 14:56: Doppler credible aún a +9 s →
  el gap de 20 s da 2× margen); los fixes posteriores del móvil parado van a 0–0.3 m/s → los 4
  intentos quedan `Inconclusive` → `Dismissed`, nada publicado, sesión intacta.
- Telemetría: `Inconclusive.reason = "exit_echo"` cuando una velocidad credible se rechaza por no
  ser independiente → el evento `DEPARTURE_VERDICT` del worker registra `Inconclusive(exit_echo)`.

Cobertura por convergencia (sin tocar más sitios): la puerta vive en `DetectParkingDepartureUseCase`,
por donde pasan las TRES vías con autoridad de publicación no-preconfirmada — EXIT boundary, EXIT
stale y el `DispatchDeparture(preconfirmed=false)` del safety-net. Las vías `preconfirmed=true`
(reconcile con presupuesto de pasos) llevan su propia física y no cambian.

## Auditoría de consumidores de `isCredibleDrivingSpeed` (un-fix) — 27-07

| sitio | autoridad | veredicto |
|---|---|---|
| `DetectParkingDepartureUseCase` | **publica plaza + borra sesión** | ⛔ cerrado AQUÍ (gap de independencia) |
| `VerifyDepartureEvidenceUseCase` (pre-arm `verified_speed`) | solo arma/siembra flags; el pin lo guarda DET-DRIVE-PROOF | ✅ nominación — se queda (por doctrina) |
| `EvaluateSafetyNetCheckUseCase:249` | despacha `preconfirmed=false` → pasa por la puerta nueva | ✅ cubierto por convergencia |
| `EvaluateBtParkUseCase` | contexto de desconexión BT real (hardware, MAC propia) | ✅ clase de riesgo distinta — se queda |

## Ficheros tocados
- `ParkingDetectionConfig.kt` (+`departureProofMinGapMs` + validación)
- `DetectParkingDepartureUseCase.kt` (puerta de independencia, `Inconclusive.reason`, `REASON_EXIT_ECHO`)
- `RunDepartureCheckUseCase.kt` (label del verdict con reason)
- `DetectParkingDepartureUseCaseTest.kt` (+3 tests: eco, borde del gap, reason nulo; 3 confirmes → fix independiente)
- `RunDepartureCheckUseCaseTest.kt` (replay end-to-end del eco de 140 ms; reloj del fixture parametrizado)
- `docs/detection/PARKING-DETECTION.md` (changelog)

## Pendiente
- ⏳ Commit (esperando go-ahead), APK ambos móviles, field-test (vigilar: salida real debe publicar
  con `Confirmed` a ~45 s y ningún `Inconclusive(exit_echo)` debe aparecer en viajes reales de
  arranque rápido... si aparece, es el eco haciendo su trabajo en el intento 0 y confirmará después).
- La plaza fantasma `spots/9d2cb23b` fue borrada de Firestore a mano el 27-07 (~18:50).
