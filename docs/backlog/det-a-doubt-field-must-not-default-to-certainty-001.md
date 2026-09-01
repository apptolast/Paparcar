# DET-A-DOUBT-FIELD-MUST-NOT-DEFAULT-TO-CERTAINTY-001

> **Estado:** ✅ **Done** — mergeado a master el 31-08-2026 (squash, `c604a058`). Rama y worktree
> borrados.
> **Origen:** fallo **#15** del inventario §6.2 del rediseño y último punto de la **Pieza 3**
> (fallar cerrado por construcción).
> **Delta de comportamiento:** cero. Ningún valor cambia; cambia **quién tiene que decirlo**.

---

## 1. El bug

`ParkingDecisionInput` llevaba **12 defaults sobre 19 campos**. Su hermano `UnattendedSaveInput`,
en el mismo carril, llevaba **2 sobre 18**. Cada uno justificado en su propio KDoc como *"for legacy
callers"*.

⛔ **No había legacy callers.** Cada clase tiene **UN** call site de producción (`StageInputs.kt`) y
**UN** helper de tests, y el de producción siempre pasó todos los campos por nombre. La población
para la que se escribió la excepción está vacía.

Lo que los defaults compraban de verdad era otra cosa: **poder AÑADIR una señal sin que nadie tenga
que responderla**. Un campo nuevo llega con una respuesta permanente y silenciosa a una pregunta
sobre evidencia.

### 1.1 Tres respondían hacia PERMITIR

| campo | default | qué significaba omitirlo |
|---|---|---|
| `egressBornAtAnchor` | `true` | *no hay duda sobre el ancla* |
| `lastSpeedMps` | `0f` | *no está rodando* → el gate de velocidad nunca dispara |
| `humanPoweredRide` | `false` | *hay motor* → se permite auto-confirmar |

Los otros nueve caen del lado conservador, pero eso es una propiedad de los valores que hay hoy, no
una garantía: nada obligaba a que el siguiente lo hiciera.

### 1.2 ⚠️ Y el barrido encontró el que sí importaba

`UnattendedSaveInput.humanPoweredRide = false` es el **PRIMER** guard que ejecuta
`EvaluateUnattendedParkingSaveUseCase` — el que faltaba cuando un viaje en bici de 59 minutos acabó
siendo un aparcamiento a 4,8 km del coche (campo 2026-08-16, `DET-BIKE-NOT-A-CAR-001`). Y su helper
de tests, que nombra los otros **diecisiete** campos uno a uno, **jamás lo mencionaba**. Todos los
escenarios del unattended corrían ese guard en su posición permisiva sin decirlo, y nada podía
notarlo.

Misma forma en `ParkingDecisionInput.restCertified`: el helper de decisión tampoco lo nombraba.
Es el patrón: *el campo que un helper no nombra es exactamente el que nadie está probando.*

---

## 2. El arreglo

**Cero defaults en los dos inputs.** Los 14 se borran; los dos helpers de test declaran su propio
default (asunto suyo) y lo pasan explícitamente.

⛔ **La otra vía del plan se descarta, y el porqué es la tesis del ticket.** §7 proponía *"o se
**invierten** para que el default sea el valor dudoso"* (`egressBornAtAnchor = true` →
`egressDoubt = true`). Una vez que **nada se puede omitir**, la polaridad del campo no compra
seguridad: sólo renombraría el predicado (`AnchorPredicates.isEgressBornAtAnchor`), el input hermano
que lee el mismo nombre y las trazas que lo escriben. Cero ganancia, churn en tres sitios.

⚠️ **Nullable no es un default.** `drivingEvidence: DrivingEvidence?` y `evidenceLabel: ArmLabel?`
siguen siendo nullable: *"este input no lleva veredicto"* es un estado real en el que un replay
puede estar. Lo que cambia es que quien está en ese estado tiene que **decir `null` en voz alta**.

---

## 3. Barrido de consumidores (todos los sitios auditados)

| # | fichero | qué había | qué hay | delta |
|---|---|---|---|---|
| 1 | `EvaluateParkingDecisionUseCase.kt` `ParkingDecisionInput` | 12 defaults / 19 campos | 0 defaults + KDoc de clase explicando la regla | — |
| 2 | idem, 8 KDoc de campo | *"Defaults to X for legacy callers"* | frase retirada; en los 3 permisivos se dice **cuál era la respuesta permisiva** | — |
| 3 | `EvaluateUnattendedParkingSaveUseCase.kt` `UnattendedSaveInput` | 2 defaults (`witnessedRestFix = null`, `humanPoweredRide = false`) | 0 defaults + KDoc con el hallazgo del helper | — |
| 4 | `domain/detection/stages/StageInputs.kt` | pasaba **todos** los campos ya | **sin tocar** — es la prueba de que los defaults no servían a nadie | ninguno |
| 5 | `EvaluateParkingDecisionUseCaseTest.kt` helper `input()` | 18/19 campos; `restCertified` heredado | +`restCertified: Boolean = false` declarado y pasado | ninguno |
| 6 | idem `confirmableInput()` | delega en `input()` | **sin tocar** | ninguno |
| 7 | `EvaluateUnattendedParkingSaveUseCaseTest.kt` helper `input()` | 17/18 campos; `humanPoweredRide` heredado | +`humanPoweredRide: Boolean = false` declarado y pasado | ninguno |
| 8 | los `.copy(restCertified = …)` / `.copy(humanPoweredRide = …)` de ambos tests | ya explícitos | **sin tocar** — `copy` no usa defaults | ninguno |

No hay más constructores de ninguno de los dos inputs en el repo (verificado por grep sobre `shared`
y `app`).

---

## 4. Tests

**Nuevo**: `androidUnitTest/architecture/EvaluatorInputGuardrailTest.kt` (2)
- `no evaluator input parameter carries a default`
- `both decision inputs are found and are the size they claim` — **el testigo**: la regla anterior
  filtra clases por NOMBRE, así que un rename la dejaría pasando sobre el conjunto vacío. Exige
  encontrar las dos y que cada una tenga ≥ 9 parámetros (miden 19 y 18; suelo a la mitad, doctrina
  de `GuardrailScope`). [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]

**Falsación (⛔ un test de prohibición sin verlo fallar siempre pasa):**
- reintroducido `egressBornAtAnchor: Boolean = true` → `no evaluator input parameter carries a
  default` **FAILED**;
- renombrado `ParkingDecisionInput` → `ParkingDecisionInputRenamed` en el filtro → `both decision
  inputs are found…` **FAILED**.

Restaurados los dos, verdes.

**Suite completa:** `:shared:testDebugUnitTest` → **1999 tests, 0 fallos** (1997 + 2).
`:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin` OK.

---

## 5. Lo que este ticket NO hace

- **No toca los predicados que producen esos valores** (`isEgressBornAtAnchor`,
  `isAnchorWalkEntered`, `egressExceedsWalkReach`…). Sus propios defaults permisivos — el fallo #10
  de §6.2, `isEgressBornAtAnchor` devolviendo `true` sin ancla — son otro ticket: ahí el default no
  es un parámetro omitido, es una **rama de retorno**.
- **No invierte ninguna polaridad.** Ver §2.
- **No añade campos ni cambia un solo valor.** Es un cambio de quién responde.

---

## 6. Doctrina que aplica

- *Fallar cerrado por construcción* (Pieza 3): la evidencia más fuerte no puede ser el valor omitido,
  y ninguna duda puede resolverse sola por no escribirse.
- *Sistemas, no parches*: el invariante es «un input de evaluador no declara defaults», así que se
  barre también el hermano — que es donde estaba el default que de verdad mordía.
- *Una prohibición sin testigo de población no es un chequeo*: el guardarraíl filtra por nombre, así
  que trae su propio testigo de que el filtro sigue encontrando algo.
