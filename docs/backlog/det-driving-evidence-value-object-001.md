# DET-DRIVING-EVIDENCE-VALUE-OBJECT-001 · «¿condujo?» se responde una vez, con una medición

**Estado:** ✅ Done · rama `feature/DET-DRIVING-EVIDENCE-VALUE-OBJECT-001-driving-evidence-vo`
· worktree `../Paparcar-drivingevidence-vo` · **apilada sobre
`DET-DRIVING-EVIDENCE-IS-THE-ONLY-GATE-001`** (aún sin mergear a master)

Pieza 1 del rediseño (`docs/detection/REDESIGN-DETECTION-SYSTEM.md` §8). Cierra §6.0.

## Problema

La pregunta *«¿condujo esta sesión?»* se responde hoy en cuatro sitios con cuatro cosas distintas:

| señal | qué es de verdad | quién la lee |
|---|---|---|
| `session.driveAuthorized` | la **nominación** — un solo fix en banda la enciende | 6 stages + el bucle |
| `session.hasEverMoved` | velocidad Y desplazamiento a la vez, una sola vez | sólo el abort de no-movimiento |
| `drive.proven` / `provenMaxSpeedMps` | la **prueba** corroborada por la traza | epílogo, supersede, ejecutor |
| `provenDrivingBandMs` → `sessionSawDriving` | tiempo sostenido en banda | `weakEvidenceOnly` y `hasKinematicProof` |

**Y el camino que más pines planta —`steps+egress`— no consulta ninguna de las dos últimas.** Se
apoya en que `driveAuthorized` ya dejó pasar el fix. `PreDriveSkipStage.kt:17-20` lo avisa en su
propio KDoc (*«passing this gate proves nothing about the trip»*) y tres etapas más abajo se trata
como si lo probara.

## Doctrina violada

*El evento NOMINA, solo el movimiento MEDIDO confirma.* La nominación está haciendo el trabajo de la
prueba en el camino principal.

## Señales / datos disponibles — medidas, no supuestas

Contadas sobre el `parkdiag` del Redmi de la noche del 29→30-08 (6.464 líneas). `credibleFixes` usa
la definición EXACTA de `DriveProof.onFix`: `speed >= minimumTripSpeedMps (5,0)` **y**
`acc <= minGpsAccuracyForDriving (50 m)`.

| sesión | fixes | `credibleFixes` | excursión máx desde el origen | banda sostenida | veredicto real |
|---|---|---|---|---|---|
| viaje real 21:47 | 324 | **86** | 3.098 m | 30.001 ms | ✅ pin correcto |
| ruido 23:30 | 22 | 0 | 16 m | 0 | abort |
| **FP parafarmacia 23:47** | 102 | **1** | **72 m** | **0** | 🔴 pin silencioso 0.9 |
| ruido 00:13 | 53 | 0 | 23 m | 0 | abort |
| casa 01:20 (viaje real) | 376 | **7** | 2.493 m | 45.021 ms | 🔴 pin a 142 m (ancla mala) |
| ruido 01:59 | 53 | 0 | 9 m | 0 | abort |

⚠️ **Corrección a §6.1 del rediseño.** Ese apartado atribuye **44** `drivingFixes` a la sesión de
casa. Son **7**. El 44 sale de contar `speed >= 5` **sin la puerta de precisión**, y `credibleFixCount`
sí la aplica: 37 de esos 44 fixes se descartan por accuracy > 50 m (era la noche del agujero de GPS).
Consecuencia real: el umbral de 5 **no es «conservador»**, es **centrado** — el ruido llega a 2 y el
viaje real más flojo tiene 7, así que hay margen 2 por ambos lados. Se documenta como lo que es.

## Diseño

Un value object en `domain/detection/physics/DrivingEvidence.kt`, construido en **un solo sitio**
(`DetectionSessionState.drivingEvidence(fix, config)`), con tres estados y un derecho cada uno:

| evidencia | derecho |
|---|---|
| `Measured` | confirmar en silencio |
| `Weak` | **preguntar**, jamás plantar |
| `None` | cerrar; ni pin ni pregunta de park |

`Measured` exige **las tres a la vez**, y cada una mata el FP por su cuenta:

| condición | bar | FP parafarmacia | 21:47 | 01:20 | Calle Gavia |
|---|---|---|---|---|---|
| `credibleFixes >= minDrivingFixesForConfirm` | **2** | **1** ✗ | 86 ✓ | 7 ✓ | 2 ✓ |
| excursión máx sobre fixes creíbles `>= minimumTripDistanceMeters` | 150 m | **72 m** ✗ | 3.098 ✓ | 2.493 ✓ | 543 ✓ |
| banda sostenida `>= sustainedDriveProofMs` | 30 s | **0** ✗ | 30,0 s ✓ | 45,0 s ✓ | 36 s ✓ |

### 🔴 El umbral del rediseño (5) estaba mal, y lo dijo un test

Con `minDrivingFixesForConfirm = 5` el replay **`calle_gavia_001_correct_detection_still_anchors_at_calle_gavia`** se puso ROJO: *«the correct park must save expected:1 but was:0»*.

Esa traza (04-07-2026) es el stream esquelético de MIUI que esta misma config cita **dos veces** como
el peor trayecto legítimo del histórico: **11 fixes en toda la sesión, de los cuales sólo 2** están a
velocidad de conducción con precisión creíble. Un bar de 5 convertía un aparcamiento correcto en un
falso negativo silencioso.

El bar honesto es **2**, y no es un número inventado: es **la regla LONE-SAMPLE que el proyecto ya
aplica**. `DET-LONE-SAMPLE-CANNOT-UNFREEZE-AN-ANCHOR-001` se niega a mover un ancla con un solo fix a
velocidad de viaje («run 1 of 2»); esto se niega a plantar un pin con uno solo. El FP tenía
exactamente **1**.

Y deja claro cuál es el reparto de trabajo: **el contador de fixes es la más débil de las tres
condiciones** — mide con qué densidad muestreó el OS, no si un coche se movió. Las que cargan el peso
son las dos físicas, y ahí el margen es amplio: el FP falla las dos (72 m contra 150; 0 ms contra
30 s) y Calle Gavia las pasa con holgura (543 m, 36 s).

**Excursión, no desplazamiento neto origen→final.** El rediseño dice «desplazamiento NETO», pero
tomado literalmente (origen → último fix) un viaje de ida y vuelta que aparca en su propia calle da
≈0 y se convertiría en falso negativo. Lo que hay que refutar es *«71 m de ida deshechos 64,8 m»*, y
eso lo refuta la **excursión máxima alcanzada con precisión creíble**: el FP llegó a 72 m, por debajo
de los 150 m que la config ya considera un viaje. Los dos viajes reales están a 2,5-3,1 km.

**Exención declarada:** un `SHORT_HOP` probado satisface la condición de excursión sin llegar a
150 m — es un re-aparcamiento corto, y su prueba (racha de fixes creíbles lejos del pin de origen) ya
es un desplazamiento corroborado. Sin esta exención, mover el coche 100 m calle abajo sería un FN.

### Lo que este ticket NO toca, y por qué

`driveAuthorized` (el latch de ciclo de vida) se queda como está. Gobierna *el derecho a seguir
viva*, no *el derecho a confirmar*, y son planos distintos — es el alcance recortado que sobrevive en
`DET-DISPLACEMENT-DRIVE-MUST-SURVIVE-ITS-NEXT-FIX-001` (§9.2). Fundirlo aquí mezclaría dos deltas de
conducta en un solo ticket.

## Criterio de éxito

- Replay del FP de la parafarmacia: `Measured` **no** se alcanza, y el desenlace es `Prompt`, no
  `Confirmed`. Verificado por **neutralización** de cada una de las tres condiciones por separado.
- Los dos viajes reales de esa noche siguen dando `Measured`.
- Ninguna regresión: `:shared:testDebugUnitTest` completo en verde.

## Consumidores auditados

`grep` de los cuatro señales y de `sustainedDriveWitnessed`:

| sitio | pregunta que hace | clasificación |
|---|---|---|
| `EvaluateParkingDecisionUseCase:234` (`sessionSawDriving`) | ¿puede confirmar en silencio? | **cerrado** — lee el veredicto |
| `StageInputs:86` → `assertionBlocksRelocation` | ¿esta sesión vio conducir, para poder mover un pin ASERTADO? | **cerrado** — lee el veredicto. Dirección segura: un veredicto más estricto bloquea MÁS relocalizaciones del pin del usuario |
| `DetectionEffectExecutor:191` → `ConfirmParkingUseCase` | igual, en el guard de aserción/repark | **cerrado** — lee el veredicto |
| `EvaluateUnattendedParkingSaveUseCase` (`maxSpeedMps`, `credibleDrivingFixes`) | ¿qué guarda el timeout desatendido? | **exento aquí** — es la decisión de la Pieza 4 (`DET-NO-CLOCK-PLANTS-A-PIN-001`); tocarla aquí mezclaría dos deltas |
| `PreDriveSkipStage`, `FalseEnterAbortStage`, `NoMovementBudgetStage`, `VehicleAttributionStage`, `StopTracking:215`, `EgressEvidence:127` (`driveAuthorized`) | ¿puede esta sesión seguir viva / contar pasos / atribuir vehículo? | **exento con razón** — plano de ciclo de vida, no de confirmación. Es `DET-DISPLACEMENT-DRIVE-MUST-SURVIVE-ITS-NEXT-FIX-001` |
| `SessionEpilogue:56`, `SessionSupersede:62`, `CoordinatorParkingDetector:624` (`provenMaxSpeedMps`) | estadística persistida / herencia de un supersede / veto de paso | **exento** — no deciden un pin; son telemetría y herencia |
| `hasEverMoved` | sólo alimenta el abort de no-movimiento | **exento** — no participa en ningún confirm |

### Nota de alcance: `None` no cierra la sesión todavía

`DrivingEvidence.None` expone `mayAskAboutAPark = false`, pero **nadie lo consume aún**. Cerrar una
sesión por `None` es una decisión de cierre, y los cierres son la Pieza 4. Se deja el derecho
declarado y sin cablear a propósito: cablearlo aquí sería un segundo delta de conducta
(sesiones que hoy preguntan dejarían de hacerlo) sin el replay que lo respalde.

## Verificación

- **1.812 tests en verde.**
- **Falsación** (método de `DET-2208-TRIPS-BECOME-REPLAYS-001`): al revertir el cableado del
  veredicto en `EvaluateParkingDecisionUseCase` se ponen rojos **exactamente los dos tests nuevos**
  de decisión, y ninguno más.
- El bar de 5 se descartó **porque un test lo puso rojo**, no por opinión (ver arriba).
