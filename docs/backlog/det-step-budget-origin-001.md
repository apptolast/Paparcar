# DET-STEP-BUDGET-ORIGIN-001 — Presupuesto de pasos y desplazamiento desde el MISMO origen

> **Estado**: ✅ Done · en master como `3ad8f648` — *"step budget and displacement measured from ONE
> origin — the seal point"*. La rama (apilada sobre `bugfix/DET-ENDED-VETO-RACE-001`) ya no existe;
> el marcador vive en **14 ficheros** de producción, y el ⏳ de campo quedó cubierto por la frontera
> de validado del 23-08.
> *(Corregido el 2026-08-30 por [DOCS-LIVING-DOCS-MUST-MATCH-MASTER-001].)*
> Implementado 2026-07-23; 935 tests verdes (8 del evaluador honest-close, con regresión Glorieta).
> ⏳ device + field-test. Origen: FP de campo 2026-07-22 01:47 (Redmi, pin dentro de casa).

## El falso positivo de campo (Glorieta, 22-jul 01:47)

1. 01:11 — park real en **Calle la Angelita 6** (`steps+egress`, fiab 0.9). Correcto.
2. El usuario camina ~243 m del coche a su casa (Glorieta de Juan de Austria).
3. 01:43 — trigger falso arma una sesión en casa; no hay movimiento → `aborted_no_movement`.
4. 01:47 — el honest-close pregunta "¿el coche se alejó de su pin?": distancia pin→casa 243 m,
   pasos desde el sello **110** < los 129 que exigía el gate (40 % de 243/0,75) → "condujo" →
   **planta un pin 0.5 DENTRO de casa y depone el park real 0.9**.

## Causa raíz — dos orígenes mezclados

El presupuesto compara **pasos desde el punto de sellado** contra **distancia desde el PIN** —
pero el sello ocurre en el confirm, **a mitad del egress** (~160 m del pin en el caso de campo).
La caminata del egress queda FUERA del contador pero DENTRO de la distancia: el presupuesto sale
corto por construcción y una caminata normal se lee como conducción. Es exactamente el
`BUG-WALK-DEPART-001` que el rung 3 del ladder dice evitar ("nunca afirmar que el coche está donde
está el PEATÓN"). El podómetro flojo del Redmi lo agrava, pero **caería igual con un contador
perfecto**.

## Fix — el invariante en UNA pieza

*Un baseline de pasos es un triple (contador, posición, momento); solo es comparable contra un
desplazamiento medido desde ESA posición.*

- **`DetectionStepAnchors.seal(geofenceId, sealPoint)`** persiste DÓNDE estaba el cuerpo al leer el
  contador (`anchor_seal_pos_<id>` = "lat,lon", misma edit atómica que el baseline).
  `stepsSinceSeal` devuelve `StepsSinceSeal(steps, sealPoint)`.
- **`EvaluateHonestCloseUseCase`** compara el presupuesto contra `distancia(sealPoint → abortFix)`
  (mismo origen). Sin `sealPoint` (sello legacy) → `KeepSilent` (conservador; el safety-net sigue
  de red). El guard "demasiado cerca para ser viaje" sigue siendo pin-based a propósito (pregunta
  por el COCHE).
- **`ConfirmParkingUseCase`** recibe `sealPoint` **sin default** — el compilador obliga a cada
  call site a decidir dónde está el cuerpo:
  - Coordinator (`runConfirm`) → `previousFix` (el último fix procesado — la posición real del
    egress), fallback el ancla.
  - Honest-close → `abortFix`. · Manual → el pin (el usuario está a su lado).
  - BT → `walkSettled` (el fix que confirmó el walk-away). · Backfill → `null` (el cuerpo puede
    estar en cualquier sitio; el cure del safety-net re-sella en el coche).
- **`ParkingSafetyNetWorker`** (cure re-anchor): escribe la posición junto al zero-point (está EN
  el coche → fix = origen honesto) y la poda con el resto de claves.

El safety-net evaluator NO cambia: su auto-release ya exige `anchoredToCar` (sello del cure, EN el
coche → mismo origen de facto) + tope absoluto `maxBoardingSteps`; sus demás usos del presupuesto
solo regulan PREGUNTAS (dirección segura de la asimetría).

## Aritmética de la regresión (test `should_stay_silent_when_the_seal_happened_mid_egress…`)

| | Antes (pin-origen) | Ahora (sello-origen) |
|---|---|---|
| distancia | 243 m (pin→casa) | 85 m (sello→casa) |
| pasos exigidos (40 %) | 129 | 45 |
| pasos contados | 110 | 110 |
| veredicto | 110<129 → "condujo" → **FP** | 110≥45 → caminata → **silencio** ✓ |

## Validación

- 935 tests verdes; 8 en `EvaluateHonestCloseUseCaseTest` (2 nuevos: regresión Glorieta +
  sello-sin-origen); `RunHonestCloseUseCaseTest` actualizado al nuevo contrato.
- Los sellos existentes en device (sin posición) degradan a silencio del honest-close hasta el
  próximo confirm/cure — pérdida aceptada por la asimetría (FN barato, FP caro).
- Field-test: aparcar → caminar a casa → provocar un abort (`aborted_no_movement` al relanzar la
  app lejos del coche vale) → el pin real DEBE sobrevivir y no aparecer ninguno aproximado.
