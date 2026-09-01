# DET-A-USER-YES-DOES-NOT-SHRINK-A-WALK-ENTERED-DOUBT-001

> **Estado:** 🔴 abierto, **con evidencia y sin implementar** · descubierto 2026-09-01 sobre master `f58e9d64`
> **Origen:** la auditoría de `TEST-A-TRACE-WHOSE-GROUND-TRUTH-IS-NEVER-ASSERTED-001`. Salió al leer,
> por primera vez, el ground-truth de `TraceCameliasOppo001` — que llevaba desde julio sin que
> ningún test lo tocara.
> ⛔ **Es un cambio de detección**: antes de tocar nada, skill `det-change`.

---

## 1. El defecto, medido sobre el mismo stream

Trace `TraceCameliasOppo001` (campo 2026-07-15, Oppo). Ancla **walk-entered** (contaminada: la
congeló el peatón, no el coche), en `36.5976591,-6.2506682` — **37 m del coche real**. Dos puertas de
salida de la MISMA sesión, con la MISMA duda:

| quién decide | forma | radio | outcome |
|---|---|---|---|
| nadie contesta → timeout desatendido | **ZONA** | 60 m | `confirmed_unattended_zone_walk_entered_anchor` |
| el usuario toca **"Sí"** | **PIN EXACTO** | — | `confirmed_user` |

El pin exacto cae **exactamente en la coordenada del FP de campo** (`< 1 m` del `FIELD_PIN` del
fixture). La zona de 60 m **cubre** los 37 m de error real; el pin exacto, no.

## 2. Por qué pasa

`UserConfirmStage.shapeFor` acota la duda **de UNA sola fuente**: el agujero de GPS
(`state.anchorTrust.capture.gapMs`), que es la duda para la que se escribió
`DET-USER-YES-IS-NOT-A-COORDINATE-001`.

```kotlin
val doubtMeters = walkableInsideGapMeters(state.anchorTrust.capture.gapMs, config.maxPedestrianSpeedMps)
val worthDrawing = maxOf(where.accuracy, doubtMeters.toFloat()) > config.honestCloseMinZoneRadiusMeters
if (!worthDrawing) return SavedParkingShape.ExactPin(where, config.reliabilityUserConfirmed)
```

Un ancla walk-entered **no tiene agujero**: `gapMs = 0` → `doubtMeters = 0`, y la accuracy del fix
(≈3,3 m) está muy por debajo del suelo de 60 m → `ExactPin`. La duda del walk-in **no es que sea
pequeña: es que no se consulta**. `EvaluateUnattendedParkingSaveUseCase` sí la tiene, con nombre
propio (`WALK_ENTERED_ANCHOR`, `doubt = max(steppedBound, anchorWalkInSpanMeters)`).

⚠️ **Y la discrepancia es sobre SI dibujar, no sobre cuánto.** Forzando abierta la puerta de
`shapeFor` en la falsación, el guardado sale con **60,0 m** — el mismo radio, porque ambos caminos
caen en el mismo **suelo** (`honestCloseMinZoneRadiusMeters = 60f`). En este trace el offset medido
del walk-in está POR DEBAJO del suelo, así que su papel es **licenciar** el área, no dimensionarla.
⛔ No escribir que «la zona dibuja el offset del walk-in»: no es cierto aquí.

## 3. Por qué no es la excepción que ya está razonada

El KDoc de `UserConfirmStage` defiende una decisión deliberada, y **no es ésta**: la que está
razonada es `whereTheCarIs` (qué COORDENADA elegir; `NOT_RECORDED` conserva el ancla a propósito,
[[project_det_piece3b_null_policy_2026_08_31]]). Esto es `shapeFor` — la **FORMA**. Y la tesis del
propio fichero (*«the answer settles WHETHER, never WHERE»*) apunta justo al revés de lo que hace el
código: un toque prueba que hubo aparcamiento, no mide dónde.

## 4. Alcance propuesto (SIN decidir)

- `shapeFor` debe preguntar por la duda del **ancla**, no sólo por la del agujero. La duda ya está
  calculada en el camino desatendido: el arreglo es que **haya UN sitio** que la responda y los dos
  caminos lo lean — no un segundo cálculo en el stage.
- ⛔ Ojo con el barrido: la tercera puerta (`EvaluateHonestCloseUseCase`) usa la misma `honestZoneRadius`.
  Comprobar si tiene el mismo agujero antes de tocar una sola.
- ⛔ **Coste a medir antes de aceptar**: pasar de pin exacto a zona de 60 m en un "Sí" degrada la
  precisión de un aparcamiento que el usuario acaba de confirmar. Hay que decidir si el ancla
  walk-entered es motivo suficiente, o si sólo lo es cuando el offset medido supera el suelo.

## 5. Qué ya existe de este ticket

**Nada de código.** Lo que existe es el testigo: el par de tests
`camelias_oppo_001_an_unanswered_prompt_draws_the_walk_in_doubt_as_a_zone` y
`camelias_oppo_001_a_user_yes_drops_that_same_doubt_and_pins_exactly_today` en
`DetectionTraceReplayTest`. El segundo es **CARACTERIZACIÓN**: afirma el defecto de hoy y su mensaje
nombra este ticket. Al implementar, ese test **se voltea a exigir una zona** — no se borra.
