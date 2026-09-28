# ZONE-THERE-IS-NO-WORKER-AND-FIVE-KDOCS-SAY-THERE-IS-001 · barrer el worker que no existe

**Estado:** ✅ Done (28-09-2026) · rama
`chore/ZONE-THERE-IS-NO-WORKER-AND-FIVE-KDOCS-SAY-THERE-IS-001-sweep` · worktree `../Paparcar-no-worker`

## Problema

`ZONE-A-PAID-ALERT-MUST-NOT-BE-DOCUMENTED-AS-FREE-001` descubrió que **no existe ningún worker** de
vigilancia de zona: la mirada periódica de `ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001` se sustituyó
por el fanout del servidor (`ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001`) y los KDoc se
quedaron describiéndolo. Aquel ticket arregló **sólo las dos menciones del fichero que estaba
editando**, y dejó cinco en otros dos:

- `ZoneSpotAlertPolicy.kt` ×4 — *«the live stream while the app is open, and the worker while it is
  not»*, *«without the clock the worker would notify…»*, *«a worker that starts from nothing every
  fifteen minutes»*, *«a worker cannot inherit»*.
- `ZoneWatchRepository.kt` ×1 — *«Both lanes — the live stream and the background worker»*.

Resultado: el repo afirmaba **las dos cosas a la vez** — un fichero diciendo «no hay worker» y dos
describiendo el suyo. Eso es peor que el estado anterior: antes la mentira era consistente y se podía
cazar de una pasada; ahora contradictoria, y quien lea sólo uno de los tres se queda con la falsa.

## Doctrina violada

**Barrido de consumidores.** No es sólo para señales y flags: *un texto que miente tiene consumidores
igual que una función* — los lee el siguiente que toque el área. Cerrar la mención del fichero que
tienes abierto y dejar las otras cinco es exactamente el patrón que `CLAUDE.md` describe para el
código (cerrar la vía donde mordió y no barrer) aplicado a los comentarios.

## Qué se cambió

Los dos carriles reales quedan nombrados en los cinco sitios: **el stream vivo** y **el push del
servidor** (`PushMessageHandler`). Cada mención corregida deja dicho qué decía antes y hasta cuándo,
porque el «por qué» de esos párrafos sigue siendo válido y sólo el sujeto era falso:

- La memoria tuvo que salir del `var baselineTaken` porque **el otro carril no puede heredarla** — eso
  es verdad del push igual que lo era del worker, y es lo que justifica `ZoneWatchRepository`.
- Los quince minutos **fueron reales** en su día; lo que no existe es el worker que los corría.

⛔ No se toca ninguna decisión ni ningún veto: es un barrido de texto. Los vetos de `PROVISIONAL` y
`RELEASING` siguen justificándose en parte por una cadencia que ya no existe, y eso **sigue abierto**
como pregunta de producto en
[`zone-a-paid-alert-must-not-be-documented-as-free-001.md`](zone-a-paid-alert-must-not-be-documented-as-free-001.md).

## Criterio de éxito

`grep -rn -i worker` en `domain/zone/`, `domain/usecase/zone/` y `ZoneWatchRepository.kt` no devuelve
ninguna afirmación de que exista uno — sólo las notas de que no existe.

## Consumidores auditados

| Fichero | Estado |
|---|---|
| `ZoneSpotAlertPolicy.kt` | ✅ 4 menciones |
| `ZoneWatchRepository.kt` | ✅ 1 mención |
| `ObserveZoneSpotAlertsUseCase.kt` | ✅ ya barrido en el ticket anterior (2) |
| `ObserveZoneSpotAlertsUseCaseTest.kt` | ✅ ya barrido en el ticket anterior (1) |
| `docs/detection/PARKING-DETECTION.md` · `docs/backlog/zone-an-alert-nobody-is-awake-to-receive-001.md` | ✅ exentos: registros fechados de cuando el worker se planeó o existió. No se reescribe la historia |

## Verificación

Cambio de sólo comentarios: `compileDebugKotlinAndroid` + la suite. **Sin resultado visual** y sin
comportamiento que probar — un test no puede vigilar esto, lo vigila el barrido.
