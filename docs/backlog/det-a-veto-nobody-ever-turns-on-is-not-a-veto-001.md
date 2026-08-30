# DET-A-VETO-NOBODY-EVER-TURNS-ON-IS-NOT-A-VETO-001 · el veto de step tras un arm ENTER se borra, no se calibra

**Estado:** ✅ Done · **1.977 tests, 0 fallos** · `:app:compileProdDebugKotlin` +
`:app:compileMockDebugKotlin` verdes · delta de conducta en producción: **ninguna** (la rama borrada
nunca se ejecutaba)
**Origen:** #16 de la Pieza 3 del rediseño (`docs/detection/REDESIGN-DETECTION-SYSTEM.md:614`), el
único de los cuatro defectos de "defaults permisivos" que la Pieza 3 **no** cerró y dejó marcado
como *refutado/diferido*.

## Problema

`ParkingDetectionConfig.enterArmStepVetoMs` vale `0L` — apagado — desde que se escribió, y su propio
KDoc dice por qué: *"enable only after validating against replay traces that real short-hops don't
produce a first step this early"*. Esa validación no se hizo nunca.

Lo que cuelga de ese cero:

| sitio | qué es |
|---|---|
| `ParkingDetectionConfig:318` | el knob, `0L` |
| `ParkingDetectionConfig:1214` | su `require(>= 0)`, que sólo protege del valor negativo |
| `CoordinatorParkingDetector:636-649` | la rama de 4 condiciones dentro del `stepJob` |
| `SessionTelemetry:223-232` | la transición `enterArmStepVeto()` (degrada evidencia + un-seed) |
| `CoordinatorParkingDetectorTest:1835` | **el único** ejecutor: se pone `15_000L` él mismo |

En producción esa rama no se ejecuta jamás. La primera condición (`config.enterArmStepVetoMs > 0`)
es falsa en todos los call sites reales, así que el resto —incluida la comparación
`session.armEvidence == ArmEvidence.LABEL_VERIFIED_ENTER`, una de las membresías por string que
quedan vivas— no llega a evaluarse nunca.

## Doctrina violada

Ninguna, y ése es exactamente el argumento. El veto no está mal escrito: está **muerto**, y un
mecanismo muerto miente dos veces —

1. **Promete una protección que no da.** `PARKING-DETECTION.md:3292` lo lista como parte de la
   política de evidencia («*The B4 step-cadence veto exists behind `enterArmStepVetoMs`*»), y
   `det-driving-evidence-is-the-only-gate-001.md:175` lo describe como *"el mecanismo que habría
   cazado esto, atado a la otra etiqueta y apagado"* — el FP de la parafarmacia del 29-08.
2. **Invita a encenderlo sin datos.** Es lo que hace un knob documentado como *"enable only
   after…"*: el siguiente que lo lea pondrá 15 s creyendo que endurece.

Y es la lección ya pagada en `UI-TYPE-SYSTEM-HYGIENE-001`: *una excepción sobre código que no se
renderiza no es una excepción, es un agujero*. Aquí es una política sobre código que no se ejecuta.

## Señales / datos disponibles

Lo decisivo es que **el trabajo del veto ya lo hacen dos cosas que llegaron después de él**:

- **El desenlace que buscaba ya no depende de él.** El veto degradaba `verified_enter` a
  `self_observed` para que la sesión no confirmase en silencio. Desde
  `DET-DRIVING-EVIDENCE-IS-THE-ONLY-GATE-001`, `VerifiedByVehicleEnter.confirmsSilentlyWithoutMeasuredDrive`
  es **`false`**: ese arm ya no confirma en silencio, con veto o sin él.
- **El un-seed ya tiene un camino vivo y general.** `DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001`
  hizo el seed `OnTrust` **retractable** (`seededOnArmTrust` + `authorizedOnArmTrustOnly`), y
  `departureDismissed()` lo retracta restaurando todos los guards anti-caminata. El veto es un
  segundo camino ad-hoc al mismo estado, y apagado.

Lo único que se pierde al borrarlo: el veto abortaba la sesión **en silencio** (sin save y sin
prompt). Sin él, la misma caminata acaba en **pregunta**. Es el lado correcto del fallo asimétrico
—*ante la duda se pregunta*— y, desde `DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001`, esa pregunta
enseña dónde pregunta y se retira sola.

## Diseño

Borrar el knob, su `require`, la rama del `stepJob` y la transición `enterArmStepVeto()`. Nada lo
sustituye: el desenlace vivo ya está cubierto y **ya tiene test**.

`should_prompt_instead_of_saving_when_enter_only_arm_never_sees_driving`
(`CoordinatorParkingDetectorTest:1801`) es exactamente el escenario del veto —arm
`VerifiedByVehicleEnter`, ninguna conducción medida, ráfaga de pasos— corrido con la configuración
**de producción**, y afirma save=0 + prompt=1. Ese test pasa a ser la definición del
comportamiento, no un vecino del veto.

Si algún día hay traza que justifique refutar un ENTER por sus pasos, entra por la retractación que
ya existe (`departureDismissed`-like, un veredicto), no por un knob apagado. Queda escrito aquí para
que no se reinvente a ciegas.

## Criterio de éxito

- No queda ni una referencia a `enterArmStepVeto` / `enterArmStepVetoMs` en `shared/src`.
- `should_prompt_instead_of_saving_when_enter_only_arm_never_sees_driving` sigue verde **sin
  tocarlo**: es la prueba de que el desenlace no dependía del veto.
- Suite completa verde y sin variación de conducta en producción (la rama borrada nunca corría).

## Consumidores auditados

| sitio | clasificación |
|---|---|
| `ParkingDetectionConfig:318` (campo) | **cerrado** — borrado |
| `ParkingDetectionConfig:1214` (`require`) | **cerrado** — borrado con el campo |
| `CoordinatorParkingDetector:636-649` (rama del `stepJob`) | **cerrado** — borrada |
| `SessionTelemetry:223-232` (`enterArmStepVeto()`) | **cerrado** — borrada |
| `SessionTelemetryTest:98-105` | **cerrado** — borrado con la transición |
| `CoordinatorParkingDetectorTest:1835` (B4, único ejecutor) | **cerrado** — borrado |
| `CoordinatorParkingDetectorTest:1801` (B3, prompt) | **cubierto** — se queda intacto: es el criterio de éxito |
| `PARKING-DETECTION.md:3292` (§ política de evidencia) | **cerrado** — la frase deja de prometer el veto |
| `PARKING-DETECTION.md:1886` (transiciones atómicas) | **cerrado** — se retira de la enumeración |
| `REDESIGN-DETECTION-SYSTEM.md:469,614` (#16) | **cerrado** — #16 pasa de "diferido" a resuelto por borrado |
| `det-fail-closed-by-construction-001.md:87` (#16) | **cerrado** — apunta aquí |
| `det-driving-evidence-is-the-only-gate-001.md:175` | **exento** — arqueología del 29-08; describe el estado de entonces |
| `det-physics-evidence-admissibility-001.md:64` | **exento** — arqueología, cita `CPD:793` de un fichero ya reordenado |
| `det-state-session-telemetry-001.md:23` · `09-arquitectura-objetivo.md:422` | **exentos** — docs de plan cerrados, no doctrina viva |
