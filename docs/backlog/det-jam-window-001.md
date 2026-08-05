# DET-JAM-WINDOW-001 — El atasco al salir no mata la sesión: creep RECIENTE extiende el presupuesto de no-movimiento

**Estado:** ✅ IMPLEMENTADO 2026-07-30 en `feature/DET-JAM-WINDOW-001-creep-extended-guard`.
Suite prod completa verde (incluido replay `Trace_LateExitOnFoot001`). ⏳ field-test.
**Origen:** Pieza 3 del plan Driversnote (`project_det_driversnote_learnings_plan`): copiar el
ESPÍRITU de su `stopTimeout=10min` sin auto-confirmar jamás.

---

## El gap

`maxNoMovementMs` (4 min) aborta en silencio toda sesión que no alcanza velocidad de conducción
(18 km/h). Es el guard correcto contra arms espurios (AR fantasma en casa, EXIT zombi), pero
tenía un cohort inocente atrapado: **sales de la plaza y te quedas clavado en un atasco o
semáforo largo sin llegar nunca a 18 km/h** → a los 4 min la sesión muere → todo el viaje queda
sin cobertura (el siguiente parking depende de re-arm por AR/SigMotion/heartbeat).

La mitad "abort→prompt" de la pieza 3 original YA está construida para el caso post-conducción
(DET-NODRIVE-ZONE-001, DET-GAP-ANCHOR-001, unattended_*): este ticket cubre solo el hueco
PRE-conducción.

## La decisión: el creep RECIENTE es el discriminador

Tres perfiles llegan al check de 4 min sin velocidad de conducción, y la física los separa:

| Perfil | Desplazamiento desde origen | Desplazamiento RECIENTE (2 min) |
|---|---|---|
| Atasco / stop-go | crece | **≥30 m por ventana — sigue avanzando** |
| Peatón que se alejó y paró (`Trace_LateExitOnFoot001`) | 40+ m (cola del paseo) | ~0 m |
| Arm zombi en casa | ruido GPS <20 m | ~0 m |

Por eso el primer intento (creep desde `sessionOrigin`) FALLÓ el replay del peatón: su cola de
paseo desplazaba 42 m. El discriminador correcto es la **ventana rodante**: desplazamiento entre
el fix más viejo y el más nuevo de los últimos `jamCreepWindowMs` (2 min), solo con fixes de
accuracy ≤50 m (un teleport multipath no fabrica creep).

- Creep reciente ≥ `jamCreepMinMeters` (30 m) en el check → el presupuesto se extiende hasta
  `jamExtendedNoMovementMs` (10 min, el espíritu Driversnote).
- El creep cesa (atasco resuelto en parada, peatón, zombi) → fold ~una ventana después.
- Techo de 10 min incluso creeping → fold con outcome distinto **`aborted_no_movement_jam`** +
  evento `NO_MOVEMENT_JAM_FOLD` (creep + rawMax) — telemetría para dimensionar el cohort antes de
  decidir si merece nudge (decisión diferida a datos de campo, deliberadamente SIN prompt: nunca
  incordiar a un conductor en un atasco ni resucitar el nag de los arms nocturnos de 24/25-07).
- La lane stale/zombi (probe 75 s) NUNCA se extiende: el desplazamiento de un arm stale no es de
  fiar (el teléfono pudo viajar desde el evento).
- Doctrina intacta: la extensión solo VIGILA más tiempo; confirmar sigue exigiendo lo de siempre.

## Cambios

- `ParkingDetectionConfig`: `jamCreepMinMeters=30f` + `jamCreepWindowMs=2min` +
  `jamExtendedNoMovementMs=10min` (+ requires).
- `CoordinatorParkingDetector`: ventana rodante session-scoped + guard bifurcado + outcome/evento
  nuevos + `JAM_CREEP_MAX_ACCURACY_M=50f`.
- Tests: 4 nuevos (extensión por crawl, fold en techo con label jam, ruido estacionario NO
  extiende, stale lane nunca extiende); el replay del peatón pasa sin tocar.

## Validación pendiente (field)

- Atasco real al salir: sesión sobrevive y el viaje se detecta al arrancar.
- Noches en casa: los arms espurios siguen plegando a 4 min / 75 s (perfil OEM sin cambio).
- Dimensionar `aborted_no_movement_jam` en diagnostics → ¿nudge para el sub-cohort re-park?

## Relacionados

- Pieza 3 de `project_det_driversnote_learnings_plan` · DET-ZOMBIE-PROBE-001 (lane corta intacta)
  · DET-NODRIVE-ZONE-001 (la mitad prompt post-conducción, ya en master) · contrato
  `feedback_detection_contract` (telemetría del fold).
