# DET-STATE-DRIVE-PROOF-001 · P2.4 — tres tiempos de vida que parecían un acumulador

**Estado:** ✅ Done (2026-08-25) · rama `refactor/DET-STATE-DRIVE-PROOF-001-p2-4` ·
worktree `../Paparcar-state-4`

Paso **P2.4** de la Fase 2, `L`. Sigue a `13cbb148` (P2.3).

## Qué mueve

Las dos pruebas independientes de conducción, el pico que promocionan, el anillo de look-back y los
dos relojes de banda pasan a `domain/detection/state/DriveProof.kt`.

Y `EvaluateShortHopDriveProofUseCase` **se absorbe** dentro como perfil: era un **predicado** que
alimentaba a un solo veredicto, y `DET-VERDICT-NOT-PREDICATE-001` dice que eso vive DENTRO de ese
veredicto, no como clase inyectada aparte. Queda `internal` — directamente testeable, sin ceremonia.

## El hallazgo: el plan esperaba otra frontera

El plan decía que aquí la frontera sería «anillos contra relojes» [10 P2.4]. **No lo es.** Lo que
separa estos valores es **cuánto vive cada uno**, y hay tres:

| | Tiempo de vida |
|---|---|
| la prueba y los relojes de banda | **LATCH** — nada en la sesión los borra jamás |
| la carrera de short-hop | **RUN** — cualquier fix que falle la geometría la rompe a cero |
| el anillo de look-back | **VENTANA** — caduca por tiempo |

Leídos como once campos planos actualizados en un `copy`, los tres se leen como un solo acumulador.
Y la diferencia es justo lo que hace que una prueba sea una prueba: un latch que un fix lento pudiera
resetear perdería la plaza de todo el que para en un semáforo después de probar su conducción; una
carrera que latchease dejaría que un solo teletransporte de caché acabe acumulando una prueba.

## Verificado discriminante — y las dos respuestas son opuestas

| Neutralización | Resultado |
|---|---|
| convertir el latch de la prueba en propiedad del fix actual | 🔴 **10+ tests YA existentes** (CPDTest + replays) |
| hacer que la carrera de short-hop latchee en vez de resetear | 🔴 **2, los dos nuevos** |

Esa asimetría es el resultado útil: **el latch ya estaba bien cubierto; la CARRERA no lo estaba en
absoluto.** Correr el experimento sirve para saber cuál es cuál, no para acumular rojos.

## Dos renombres con motivo

- `driveProven: Boolean` → **`proven: DriveProofSource?`** (`TRACK_WINDOW` / `SHORT_HOP`), latcheado
  con el que lo probó PRIMERO. El «cómo» ya salía en la línea de log; ahora es un dato — que además
  es lo que permite que el log siga en el mismo instante con las mismas palabras **sin recalcular la
  prueba de short-hop una segunda vez** (el código viejo llamaba a `qualifies` dos veces por fix).
- `maxSpeedMps` / `pendingMaxSpeedMps` → **`provenMaxSpeedMps` / `peakMps`**. Ese par es exactamente
  sobre el que se escribió `DET-ASSERTION-OUTRANKS-INFERENCE-001` —un pico leído donde se quería la
  cifra sostenida— y los nombres viejos no daban ninguna pista de cuál era cuál.

## Doctrina

Ninguna tocada. **Cero cambio de conducta.**

## Tests

- `DriveProofShortHopTest` (12) — el test del use case absorbido, **movido con sus asserts intactos**.
  12 por 12; uno solo cambia de NOMBRE porque describía la API vieja y no la conducta
  (`…so the caller can keep the consecutive run` → `…so the run can be kept one fix at a time`).
  Anotado en la cabecera de `P0.4-baseline-tests.txt`, como se hizo con el renombrado de P1.8.
- `DriveProofTest` (14) — la promoción retroactiva y los tres tiempos de vida.

**1.603 tests**, 0 fallos. Coordinator **−134 líneas**, una clase inyectada menos.
`assembleMockDebug` ✅.

## Red de P0.4

```
tests 1603 - desaparecidos: 17 (5 renombrados de P1.8 + 12 del fichero movido, verificados uno a uno)
           - nuevos: 166
```

Siguiente: **P2.5**, `state/AnchorTrust.kt` — el más grande de la fase, y donde vive **el sellado
×5 → 1**: hoy la condición de rebind está copiada en cinco sitios y un campo nuevo tiene que
*acordarse* de copiarse una sexta vez. Va acompañado de **P2.5-bis** (los dos sabores del
egress-birth), que es paso PROPIO porque **puede cambiar conducta**: su criterio de aceptación es
literal — `Trace_Enamorados001` y `Trace_CameliasOppo001` sin cambio de desenlace, y cualquier delta
observable detiene el paso y vuelve para aprobación.
