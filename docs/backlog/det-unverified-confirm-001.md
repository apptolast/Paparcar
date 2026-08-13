# DET-UNVERIFIED-CONFIRM-001 — un arm sin testigo externo y sin conducción probada nunca confirma en silencio

**Estado:** 🔧 implementado en rama `bugfix/DET-WALKOUT-FP-001-walkout-false-positive`
(worktree `Paparcar-walkout-fp`), pendiente de merge + field-test.
**Origen:** FP de campo 2026-08-13 20:56 local (Oppo, uid `fiyp…`, sesión `1786647238401`):
el usuario salió de casa ANDANDO y la detección confirmó un aparcamiento nuevo a ~7 m del
anterior (18:19, misma Calle Góndola), desactivándolo.

## Forense 13-08 (telemetría pap-26, fix a fix)

| t | evento |
|---|---|
| 20:53:58 | Arm `ARM:SIGNIFICANT_MOTION (sentry-wake geof=1e0a2f6d)` → `ArmEvidence.Unverified` → label `self_observed`. Tercer wake del paseo; los dos anteriores abortaron `false_enter` |
| t+4,0 s | **PRIMER fix de la sesión (GPS frío): 24,8 km/h con acc 2,9 m** — espejismo Doppler con el teléfono a pie junto al coche. Único "driving fix" de la sesión (`drive 1/76fix`) |
| t+6 s → fin | 62 fixes restantes, todos 1,7–5 km/h (paseo puro) |
| 20:56:44 | `DECISION CONFIRMED steps+egress` (270 pasos, egress ≥18 m), rel 0.9, ancla junto al coche viejo. `routePolyline` = muñón de 4 puntos |

## Por qué los guards no lo pararon

- El espejismo pasó `credibleSpeedFix` (acc 2,9 ≤ 50 m) y ≥5 m/s → `hasEverReachedDrivingSpeed=true`
  con UN fix. Ese flag quedó **deliberadamente fuera** de DET-DRIVE-PROOF-001 ("arm seeding…
  deliberately untouched") — y al encenderse desarma los abortos anti-walking (`false_enter`).
- DET-DRIVE-PROOF-001 sí funcionó: `driveProven=false` → `maxSpeedKmh=0` → `sessionSawDriving=false`.
- **El agujero:** la política de evidencia débil del evaluador (`weakLabels`) solo contenía
  `verified_enter` y `verified_late`. `self_observed` — el label de un arm SIN verificación
  externa — no estaba, así que `steps+egress` confirmó EN SILENCIO sin conducción probada.
  Doctrina violada: *el evento nomina, solo el movimiento MEDIDO confirma*.
- El guard de repark no aplicaba (ventana 10 min; el parking anterior tenía 2h37m).

## Invariante implementado

`self_observed` entra en `weakLabels` (`EvaluateParkingDecisionUseCase`): una sesión cuyo arm no
tiene testigo externo **y** cuyo stream nunca probó conducción (`sessionSawDriving=false`,
estadística gated por drive-proof) degrada TODO auto-confirm a **Prompt** ("¿has aparcado aquí?").
Con un viaje real, `driveProven` se enciende en segundos y el confirm silencioso queda igual.
Fallo asimétrico correcto: el caso raro legítimo (viaje corto sin GPS que pruebe la conducción)
cuesta un toque; el paseo con espejismo ya no planta pines.

**Nota de observación:** `enter_at_car` (BoardingAtCar) queda fuera a propósito — su escalera de
armado propia (`EvaluateArEnterArmUseCase`) ya ata el embarque al coche propio. Si el campo
enseña el mismo agujero por esa vía, la misma jugada (añadirlo a `weakLabels`) lo cierra.

## Tests

- `EvaluateParkingDecisionUseCaseTest.should_prompt_when_self_observed_arm_and_session_never_drove`
  (réplica del FP: 270 pasos + egress + maxSpeed 0 + self_observed → Prompt).
- `…should_confirm_when_self_observed_arm_but_session_measured_driving` (con drive probado el
  confirm silencioso se conserva).

Relacionado: [DET-SENTRY-COOLDOWN-001](det-sentry-cooldown-001.md) (la otra mitad del mismo FP:
la tormenta de wakes que compraba lotería), [DET-DRIVE-PROOF-001](det-drive-proof-001.md),
[DET-GAP-ANCHOR-001](det-gap-anchor-001.md) (misma familia: pruebas que aguantan, testigo que no).
