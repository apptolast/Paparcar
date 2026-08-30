# DET-FAIL-CLOSED-BY-CONSTRUCTION-001 · lo que no se puede medir no puede valer como medido

**Estado:** ✅ Done · rama `feature/DET-FAIL-CLOSED-BY-CONSTRUCTION-001-fail-closed` ·
worktree `../Paparcar-fail-closed` · apilada sobre `DET-DETECTION-PATH-IS-A-TYPE-001`

Pieza 3 del rediseño (3a defaults permisivos + 3b política de nulos), más la **obligación 1** del
cruce §9.4.

## Problema

Tres decisiones distintas trataban la **ausencia de dato** como si fuera dato a favor.

## Lo que se cambia

### 3b · `null` en una pregunta de evidencia

| sitio | antes | ahora |
|---|---|---|
| `ParkingDetectionConfig.isCredibleDrivingSpeed` | `accuracyMeters == null` **leía como creíble** | precisión desconocida **no** es precisión creíble |
| `EvaluateGeofenceExitUseCase` | distancia no medible → `boundary`, la **máxima autoridad** (release instantáneo) | → `stale`, que **sigue disparando** el mismo worker con velocidad en vivo |

Sobre el segundo: `stale` **no descarta**. El servicio lo dice literalmente — *«the delivery position
only removes the right to an INSTANT release, never the duty to look»*. Por eso este cambio cabe bajo
el contrato de triggers: el EXIT sigue disparando siempre, sólo que tiene que enseñar velocidad en
vez de que se le crea.

### 3a · el default más fuerte deja de ser el que se hereda por olvido

`startParkingDetection(armEvidence: ArmEvidence = ArmEvidence.Manual)` → **parámetro obligatorio**.
Desde `DET-DRIVING-EVIDENCE-IS-THE-ONLY-GATE-001`, `manual` es una de las **tres** evidencias que
pueden guardar en silencio sin conducción medida, porque es la palabra del usuario. Una lane que se
olvidara de declarar su evidencia **heredaba la palabra del usuario**. Su propio KDoc ya lo avisaba
—*«only correct for the button — every other lane must state its own evidence»*— y una regla escrita
en prosa que el compilador no vigila es una regla esperando a su primer olvido.

Medido: de las 7 lanes, **una sola** dependía del default, y era el botón. Ahora lo dice en voz alta.

## Lo que NO se cambia, y por qué (esto es la mitad del ticket)

### `isAdmissibleEvidence(sessionStartMs = null) → true` — el §6.2 #8 se equivoca

Está en la lista de la Pieza 3b como default permisivo a cerrar. **No lo es**, y la distinción
importa más que la línea:

> *Fallar cerrado* gobierna una afirmación que **PLANTA** algo. Esto vigila una **SEÑAL** que sólo
> nomina. Y el contrato del proyecto sobre señales es la regla contraria: **todo trigger dispara
> siempre; un evento viejo pierde autoridad directa y pasa al evaluador, nunca se descarta.**

Devolver `false` ahí lo descarta. Se probó: dos tests de `VerifyDepartureEvidenceUseCase` —el caller
que el propio comentario del código nombra como dependiente— se pusieron rojos de inmediato.
Revertido, con la razón escrita en el código.

### `EvaluateBackfillDeferralUseCase` — semántica invertida

Aparece en la lista de 3b, pero su `null` significa *«no hay motivo para aplazar»*, no *«no hay
evidencia de aparcamiento»*: devolver `true` suprimiría **todos** los backfills sin sello de
resolución, que son la mayoría. Fallar cerrado aquí sería fallar al revés.

### `isEgressBornAtAnchor` → `true` sin ancla ni nacimiento de egress

Sin ancla, la pregunta *«¿el egress nació junto al ancla?»* es vacua, no dudosa: devolver `false`
haría preguntar a toda sesión sin ancla. Necesita separar los dos nulos, y eso es un delta de
conducta con su propio replay. **Fuera de alcance, anotado.**

### Obligación 1 del cruce · `sustainedDepartureFromAnchor`

Analizada. La función **falla cerrada en todas sus salidas** (devuelve `null` en cada condición), así
que la lista de 3b no le aplica tal cual. Pero el hallazgo del 28-08 sigue siendo real y ahora está
localizado con precisión:

```
⊘ ignoring driving-speed fix with poor accuracy (speed=11.81 acc=111.374 > 50)
⇢ SUSTAINED DEPARTURE — position ran 495 m from the anchor at 29.1 m/s
```

Su puerta de entrada es `fix.speed < movingBarMps` — **una afirmación de velocidad de un fix cuya
velocidad la vara de conducción acababa de suspender**. Y no se arregla exigiéndole precisión
creíble: su razón de existir es justamente funcionar *«when the OEM starves every individual fix of
credible accuracy»*. La salida honesta es que su autoridad venga del **desplazamiento sobre tiempo**,
que es lo que de verdad mide, y no del Doppler de la entrada.

Eso ensancha o estrecha la función según cómo se haga, y **sin un replay que lo respalde sería
justo la clase de suposición que este proyecto se ha prohibido**. Va a
`DET-DISPLACEMENT-DRIVE-MUST-SURVIVE-ITS-NEXT-FIX-001`, que es el ticket recortado que ya posee «el
latch y el ancla».

### #16 · `enterArmStepVetoMs = 0L`

Un veto apagado por defecto no es un veto — cierto. Pero encenderlo **aborta la sesión aguas arriba**
en vez de preguntar al final, con su propio riesgo de falso negativo. Ya estaba anotado en el barrido
de `DET-DRIVING-EVIDENCE-IS-THE-ONLY-GATE-001` como candidato de `DET-TWO-TIER-SENTRY-001`, que es
donde el problema es el ARMADO. Sigue allí.

### #15 · defaults de `ParkingDecisionInput`

Invertir los campos de duda (`egressBornAtAnchor = true` → `egressDoubt = true`) toca todos los
constructores del input. Mismo patrón, otro alcance.

## Consumidores auditados

| sitio | clasificación |
|---|---|
| `isCredibleDrivingSpeed` (6 llamantes) | **cerrado** — ninguno pasa accuracy nula desde un `GpsPoint` (es no-nulo); el camino nulo entra por `classifyDepartureSpeed` |
| `EvaluateGeofenceExitUseCase` → `staleDepartures` | **cerrado** — verificado que el servicio les dispara el mismo worker |
| `startParkingDetection` (7 lanes) | **cerrado** — obligatorio |
| `CoordinatorParkingDetector.invoke` | **exento con razón**: **ningún código de producción lo llama**. Todas las lanes entran por el servicio. Hacerlo obligatorio costaba 80 call sites de test restaurando un valor que sus escenarios nunca eligieron, y no lo puede alcanzar ninguna lane. *Un guardia donde no pasa nadie no es un guardia, es ruido* |
| `isAdmissibleEvidence` · `EvaluateBackfillDeferral` · `isEgressBornAtAnchor` | **exentos con razón** (arriba) |

## Criterio de éxito

- ✅ **1.824 tests en verde.**
- ✅ El test que consagraba `boundary` para una entrega no medible reescrito, con la razón medida.
- ✅ El caller del default explícito.
