# DET-MOTOR-PROOF-001 · "Conducción medida" = movimiento MOTORIZADO (sostenido + cadencia de pedaleo)

**Estado:** ✅ Done · master `08b53548` (squash 19-08-2026) · rama y worktree borrados · ⏳ validación de campo

## Problema
Field 2026-08-18 20:32 local, Oppo, sesión `1787077943062`: un paseo en bici de 6,1 min plantó el
pin `aa0d7b1d` en silencio (`steps+egress`, reliability 0.9) con el Kamiq real a ~540 m. Los cuatro
guards existentes fallaron por centímetros:

- `isHumanPoweredRide` (DET-BIKE-NOT-A-CAR-001) necesita AR `ON_BICYCLE` y AR emitió **cero
  eventos** en los 316 de la sesión — AR no clasifica en trayectos cortos (~6 min).
- `sessionSawDriving` (DET-UNVERIFIED-CONFIRM-001) se calcula sobre el **PICO**
  (`maxSpeedKmh ≥ 18.0`); la bici tocó 18,1 km/h en 2/124 fixes (~6 s en banda) y pasó por 0,1 km/h.
- El mismatch guard exige ≥8 min de sesión; duró 6,1.
- Los taints de ancla estaban limpios (el ancla se congeló honestamente donde paró la bici).

El agujero sistémico: **nada pregunta "¿esto era un MOTOR?"**. El drive-proof demuestra
desplazamiento real ≥18 km/h — cosa que una bici produce sin esfuerzo (la del 16-08 picó 38 km/h).

## Doctrina violada
*Fallo asimétrico: mejor falso negativo que falso positivo.* Un pin silencioso donde nunca hubo
coche es el peor resultado posible. Ante la duda (¿motor o músculo?) se PREGUNTA.

## Señales / datos disponibles (ambas ya se recogen, ninguna se consulta)
1. **Cadencia durante el movimiento**: la bici acumuló ~16-20 STEPs concurrentes con fixes a
   3,3-4,1 m/s (t=7-32s). Andar a 4 m/s es imposible; en coche el contador calla mientras rueda
   (fantasmas = ráfagas de 1-3, o parado tipo Calle Abeto). Firma del pedaleo: funciona en 30 s,
   sin AR.
2. **Tiempo sostenido en banda de conducción**: la bici acumuló ~6 s ≥18 km/h en 6 min; el peor
   coche legítimo del archivo (Calle Gavia, MIUI esquelético) hizo un hop único de 36 s. La vmax
   NO separa; el tiempo acumulado sí.

## Diseño — el SISTEMA
Dos cambios, cada uno en el sitio donde ya vive su invariante:

### (a) `sessionSawDriving`: de PICO a SOSTENIDO
- `CoordinatorParkingDetector` acumula `drivingBandMs`: huecos entre fixes **sucesivos** en banda
  de conducción creíble (speed ≥ `minimumTripSpeedMps`, acc ≤ `minGpsAccuracyForDriving`),
  acreditados solo si el hueco cabe en `driveProofWindowMaxMs` (60 s) — la MISMA ventana que el
  drive-proof ya confía, sin constante nueva. Un hueco mayor no prueba nada y acredita 0 (dos picos
  aislados nunca suman); un pico solitario no tiene pareja en banda y acredita 0. Medido sobre
  `GpsPoint.timestamp` (el replayer conduce el reloj desde esos mismos stamps → replayable).
  > Recalibrado durante la implementación: la 1ª versión ("ambos extremos consecutivos, tope
  > 10 s") suspendía a los dos coches reales del archivo — Gavia es UN hop de 36 s entre dos fixes
  > en banda, y en Enamorados los agujeros de accuracy urbana parten la racha en fragmentos
  > (~24 s). Los tests de replay cazaron ambos antes que el campo.
- La estadística se publica con la misma promoción que `maxSpeedMps`
  (DET-DRIVE-PROOF-001): **cero hasta que `driveProven`** — `provenDrivingBandMs`.
- `EvaluateParkingDecisionUseCase` recibe `sustainedDrivingMs` y calcula
  `sessionSawDriving = sustainedDrivingMs ≥ sustainedDriveProofMs` (30 s). Gatea, como hoy, la
  weak-evidence policy y el confirm cinemático.
- La bici del 18-08: ~4-6 s en banda → `sessionSawDriving=false` → arm `self_observed` →
  `weakEvidenceOnly` → **Prompt**, nunca pin. Gavia (36 s en un hop): sigue pasando.

### (b) Cadencia de pedaleo como 2ª fuente de `isHumanPoweredRide`
- `CoordinatorParkingDetector` cuenta `fastMotionStepEvents` (steps concurrentes con el último fix
  creíble a speed ≥ `egressStepMaxSpeedMps` = 3,0 m/s — el mismo techo peatonal que ya gatea los
  steps de egress — y frescura ≤ `pedalCadenceFixFreshnessMs`) y `fastMotionStepFixes` (fixes
  distintos acreditados).
- `isHumanPoweredRide` (domain/detection/HumanPoweredRide.kt) gana la fuente cinemática:
  `fastMotionStepEvents ≥ pedalCadenceMinStepEvents (12) && fastMotionStepFixes ≥
  pedalCadenceMinFixes (2)` → veto. Entra ANTES que la memoria AR: medir gana a recordar.
- El veto ya está enchufado a los DOS veredictos (confirm + unattended save) — resultado siempre
  Prompt/nudge, nunca descarte ni pin.

### Config nuevo (`ParkingDetectionConfig`, bloque MOTOR PROOF)
`sustainedDriveProofMs=30_000` (require ≤ `driveProofWindowMaxMs`: un solo hop confiable debe
poder satisfacerlo — Gavia) · `pedalCadenceMinStepEvents=12` · `pedalCadenceMinFixes=2` ·
`pedalCadenceFixFreshnessMs=10_000`. El hueco máximo acreditable reutiliza `driveProofWindowMaxMs`.

### Deliberadamente NO
- NO subir `minimumTripSpeedMps` (rompe jam-creep y GPS que sublee).
- NO apoyarse más en AR (el fallo raíz es que AR no llega en trayectos cortos).
- NO tocar el carril BT (una bici no tiene MAC — intocado por construcción).

## Criterio de éxito
- ✅ Test: forma de la bici del 18-08 (arm `self_observed`, 6 s en banda, pico 18,1) → Prompt;
  cadencia (16 steps / 6 fixes, AR mudo) → veto. Gavia-shape (hop de 36 s, arm débil) → Confirmed.
- ✅ Replays reales intactos: Gavia, Enamorados ×2, + toda la suite (1231 tests, 0 fallos;
  master tenía 1223).
- ⏳ Campo: un paseo en bici corto con el Oppo termina en pregunta, nunca en pin; un viaje real en
  coche sigue confirmando en silencio.

## Consumidores auditados (grep `sessionSawDriving|maxSpeed|humanPoweredRide`)
| Consumidor | Clasificación |
|---|---|
| `EvaluateParkingDecisionUseCase.sessionSawDriving` (weak-evidence + kinematic gate) | **cerrado** (el cambio (a)) |
| `EvaluateParkingDecisionUseCase.humanPowered` / `EvaluateUnattendedParkingSaveUseCase.HUMAN_POWERED` | **cerrado** (el cambio (b) — ambos ya consumen el predicado) |
| `EvaluateUnattendedParkingSaveUseCase.measuredDriving` (maxSpeedMps≥min) | **cubierto por convergencia**: una bici lo satisface, pero el veto de cadencia corre PRIMERO en ese veredicto. No se cambia a sostenido: un stream esquelético real con <30 s en banda perdería precisión de zona (regresión DET-GAP-ANCHOR-ZONE) |
| Mismatch guard (`maxSpeedKmh ≤ mismatchMaxSpeedKmh`) | **exento**: usa el pico como TECHO (moped lento), no como prueba de motor |
| Honest-close (`lastSessionMaxSpeedMps`, `trip_proven`) | **exento con razón**: solo corre en `aborted_false_enter`/`aborted_no_movement`; una bici que alcanza banda no termina ahí. Su confianza en desplazamiento no presenciado es el ticket propuesto DET-UNWITNESSED-DISPLACEMENT-001 |
| `PendingDetectionStore.sawDriving` → `shouldNudgeForStalePending` | **exento**: gatea un NUDGE (preguntar siempre es seguro), nunca un pin |
| `tripMaxSpeedMps` persistido + resumen Firestore (`vmax`, `drivingFixes`) | **exento**: telemetría, no veredicto |
| `EvaluateShortHopDriveProofUseCase` → `driveProven` | **exento**: sigue siendo la promoción de la estadística (una bici también cubre suelo real; lo que la frena ahora es (a)+(b)) |
| Safety-net / backfill (step-budget) | **vigilar en campo**: bici desde el propio coche engaña al step-budget; probablemente exento por reliability 0.5 + revert card |

## Vigilar en campo
- Motos: vibración → cadencia fantasma → Prompt (dirección permitida; validar).
- Viaje mixto bici→coche en UNA sesión: la cadencia del tramo bici degrada el pin final a Prompt
  (la fuente AR sí se supera con el embarque posterior; la cinemática no tiene concepto de
  embarque). Coste: un toque.
- Reposicionamiento muy corto (<30 s en banda) con arm débil: pasa de silencio a Prompt — es la
  dirección que manda la doctrina.
