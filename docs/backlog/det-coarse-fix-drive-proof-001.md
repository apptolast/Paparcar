# DET-COARSE-FIX-DRIVE-PROOF-001 · un móvil con accuracy crónicamente mala no puede probar NUNCA un viaje

**Estado:** ⚪ Abierto, sin código · hallazgo del análisis del field 16-08 · **NO es el bug que cerró `e9186a52`**

## Problema

Field 16-08, Samsung SM-A536B (cuenta cardomfer, uid `sUGo7EYl16XDtosI8Ei7LFeAo2E2`), sesión
`1786873042480`: **69,3 minutos de viaje real perdidos**.

```
09:37:22Z  SESSION_STARTED   ARM:SIGNIFICANT_MOTION (sentry-wake geof=0575e3e8) · self_observed
…          69,3 min · vmax 80 km/h · drive 44/602 fix · steps 62
10:20:20Z  DECISION PROMPT_SHOWN            low_medium(timeout=90032ms)
10:38:28Z  CANDIDATE DISCARDED              Candidate→Notified
10:43:35Z  CANDIDATE DISCARDED              Candidate→Notified
10:46:30Z  DECISION UNATTENDED_NO_DRIVE_NUDGE
10:46:39Z  SESSION_ENDED  outcome=aborted_unattended_no_drive
```

Una cabecera que dice **80 km/h** y un veredicto que dice **"sin conducción medida"**. Esa
contradicción es el bug.

## La causa: la puerta de accuracy, no el arm

Analizados **475 de los 602 fixes** (600 de ~646 eventos; el resto no se descargó — las cifras de
abajo son por tanto cotas inferiores, y la conclusión no depende de la cola que falta):

| | |
|---|---|
| Fixes a velocidad de conducción (≥ 5 m/s = 18 km/h) | **37** |
| …**creíbles** (`accuracy ≤ minGpsAccuracyForDriving` = 50 m) | **10** (máx 13,6 m/s a acc 49,5 m) |
| …**descartados** por accuracy > 50 m | **27** — accuracy min 60 / mediana 87 / **máx 180 m** |
| Velocidad más alta descartada | **22,3 m/s = 80 km/h** ← el "vmax" de la cabecera |
| Accuracy de todo el stream | mediana 20 m · p90 **76 m** · máx **299 m** |

El pico de 80 km/h que anuncia el resumen **llegó con accuracy > 50 m**, así que
`credibleSpeedFix` fue `false` y ese fix no alimentó nada. Con `driveProven` en `false`,
`maxSpeedMps` se queda en 0 y la rama `!measuredDriving` del timeout desatendido hace lo que debe:
nudge, sin pin. Cada guard hizo su trabajo; el problema es que en este dispositivo **el 17 % del
stream (80 de 475 fixes) supera los 50 m**, y precisamente los fixes rápidos son los peores.

### Por qué NO es DET-UNVERIFIED-ARM-DRIVE-PROOF-001 (`e9186a52`)

Aquel bug era *"el evento de armado vetaba un movimiento que el stream SÍ había medido"*. Aquí el
movimiento **nunca llegó a ser admisible**: tanto `EvaluateMeasuredDepartureUseCase` (ya borrado) como
`EvaluateShortHopDriveProofUseCase` pasan por `isCredibleDrivingSpeed`, que incluye la misma puerta de
50 m. Con este stream, ninguno de los dos habría cambiado el resultado — así que **no hace falta saber
qué build llevaba el Samsung** para descartar esa hipótesis. Ese ⏳ queda cerrado.

### ⚠️ Interacción con DET-SENTRY-ARM-PEDESTRIAN-CLOCK-001

Ese ticket (en master `6ae35526`, pendiente de validar en campo) añadió `isCredibleDrivingSpeed` a
`EvaluateShortHopDriveProofUseCase.qualifies`, o sea exige que la racha de 3 fixes esté **a velocidad
de conducción creíble**. Con sólo 10 fixes creíbles dispersos en 69 minutos, una racha de 3
consecutivos es improbable en este dispositivo. Es decir: **el fix del FP del Oppo endurece
justamente la población que ya sufre este FN.** Está documentado allí como "sólo puede producir falsos
negativos, nunca pines nuevos", que es la dirección que la doctrina prefiere — pero este móvil es el
caso concreto que lo paga. Hay que mirarlo con datos de campo antes de tocar umbrales.

## Doctrina implicada

- **Mejor falso negativo que falso positivo** se respeta. Pero *"parking perdido con datos = bug
  NUESTRO"* también aplica: hubo 69 minutos de stream vivo y un viaje real.
- El conflicto es real y no se resuelve bajando el umbral a ciegas: `minGpsAccuracyForDriving = 50 m`
  existe porque un fix degradado a 100 m reportó 21,6 km/h con el móvil quieto en una mesilla y publicó
  una plaza fantasma (DET-EXIT-TRUST-001, field 2026-07-08). Bajarlo resucita esa clase.

## Diseño candidato (sin decidir)

Un solo fix degradado no es evidencia; **una racha coherente de fixes degradados sí puede serlo**. La
información que hoy se tira es la *consistencia*: 27 fixes rápidos en la misma dirección durante
minutos no es ruido, y eso se puede exigir sin relajar el umbral para el fix individual.

- **Corroboración por desplazamiento entre fixes degradados**: si dos fixes con acc 90 m distan 600 m
  en 30 s, el desplazamiento supera holgadamente ambas envolventes; el par es admisible aunque
  ninguno lo sea por separado. Es exactamente el patrón que ya usa `isCorroboratedVehicleHop`
  (`d > prev.acc + curr.acc + margen`) — **ya existe el predicado, no se aplica a esta puerta.**
- Alternativa peor: un `minGpsAccuracyForDriving` por dispositivo/adaptativo. Introduce estado
  aprendido y hace el diagnóstico irreproducible. Descartada salvo evidencia de que la anterior falla.

## Sub-hallazgo accionable ya: **el resumen de sesión miente** — ✅ CERRADO (03-09)

> Salió a ticket propio y está hecho:
> [DET-A-SESSION-ROLLUP-MUST-USE-THE-NUMBERS-THE-VERDICT-USED-001](det-a-session-rollup-must-use-the-numbers-the-verdict-used-001.md).
> El resumen imprime ahora `vmax 80km/h (cred 49) · drive 44/602fix (cred 7)`, y la aritmética vive
> en una pieza pura con tests. **Lo que sigue abierto de ESTE ticket es su mitad grande**: admitir
> una racha coherente de fixes degradados como prueba de viaje (el diseño de abajo, sin decidir).
> El texto original se conserva tal cual porque es la evidencia de campo que lo abrió.

`FirestoreDetectionEventLogger` calcula el rollup (`vmax`, `drive N/M fix`) con su propio umbral
`DRIVING_SPEED_KMH` y **sin la puerta de accuracy**, mientras la decisión usa `maxSpeedMps`
(drive-proof gated). De ahí `vmax 80km/h · drive 44/602fix` junto a `aborted_unattended_no_drive`.

Eso no es cosmético: el resumen es lo primero que se lee en un diagnóstico (lo dice la skill
`field-test`), y esta contradicción cuesta una sesión entera de teorizar. El arreglo es pequeño y
contenido: que el rollup publique **los números admisibles** (o ambos, p. ej.
`vmax 80km/h (admisible 49) · drive 10/475 creibles de 37 rápidos`).

## Criterio de éxito

- El Samsung repite un viaje con este perfil de accuracy y **queda pin** (exacto o zona), o
  el diagnóstico dice con una sola línea por qué no.
- Ningún resumen de sesión vuelve a mostrar un `vmax` que la decisión no usó.
- No se resucita la clase DET-EXIT-TRUST-001: un fix aislado a 100 m sigue sin probar nada.

## Registro

- 2026-08-17 — abierto al cerrar el ⏳ "¿qué build lleva el Samsung de Carlos?": la pregunta era
  irrelevante, la causa es otra. Doc escrito en el worktree `../Paparcar-usecase-doctrine` por ser
  doc-only; si se implementa, worktree propio.
