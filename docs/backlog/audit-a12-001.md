# AUDIT-A12-001 — Coordinator readability + doc drift

- **Rama:** `fix/AUDIT-A12-001-coordinator-extract-docs`
- **Origen:** auditoría 2026-07-04, hallazgo A12 ("extraer las fases del invoke() de ~400 líneas +
  regenerar docs").

## Hecho ✅

**Doc-drift de CLAUDE.md §Detección** reescrito al sistema actual. El texto anterior describía el
diseño PRE-rediseño y mentía al siguiente lector/agente:
- "HIGH (≥0.75) → auto-confirm" → falso: el scoring HIGH por sí solo ya NO auto-confirma; hace
  falta conducción medida + egress (pasos/cinemático); evidencia débil degrada a prompt.
- "BT connect → DetectDepartureUseCase" → el flujo cambió.
- No mencionaba la doctrina ("el evento nomina, solo el movimiento medido confirma";
  fallo asimétrico), ni AR-first, ni ancla lock/freeze, ni la red de seguridad, ni el intake único.

## Diferido conscientemente ⏸ — extracción del `.collect { location -> }` de `invoke()`

El bloque per-fix (~370 líneas, `CoordinatorParkingDetector.kt`) sigue siendo monolítico. Ya se
extrajeron `updateStopTracking`, `evaluateConfidence`, `evaluateCandidatePhase`, `runConfirm`,
`beginConfirm`, `advanceHigh/LowMedium`, etc. Lo que queda enhebra estado MUTABLE a través de las
iteraciones (`completed`, `activeVehicleId`, `loggedVehicleExit`) y usa `return@collect` — una
extracción exige convertirlos en campos/holder y `return@collect`→returns tempranos.

**Por qué NO ahora:** es el corazón de detección que llevamos estabilizando 8 días de field-test,
y sigue PENDIENTE de field-test (freeze/kinematic/gates BT). El valor de la extracción es cosmético
(legibilidad); el riesgo es un reorden sutil de precedencia que los 761 tests + replay podrían no
cazar. Hacerlo a ciegas, sin poder validar en device, es mal negocio ahora. La auditoría misma
condiciona "el replay hace el refactor seguro" — hagámoslo cuando el loop esté field-validado y
mergeado, con el replay como red.
