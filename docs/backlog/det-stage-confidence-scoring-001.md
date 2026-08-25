# DET-STAGE-CONFIDENCE-SCORING-001 · P3.1 — la primera rama sale del método, y refuta el andamio

**Estado:** ✅ Done (2026-08-25) · rama `refactor/DET-STAGE-CONFIDENCE-SCORING-001-p3-1` ·
worktree `../Paparcar-stage-1`

Paso **P3.1**, la primera etapa real. Sigue a `5e52c641` (P3.0).

## Qué mueve

El scorer y la máquina de fases pasan a `stages/ConfidenceScoringStage.kt`: **pura**, devuelve un
veredicto con el estado que quiere, los efectos que pide y las líneas de traza que quiere escritas.
El coordinator la ejecuta, registra las notas en orden y corre los efectos en un ejecutor inline
hasta que P3.11 le dé fichero propio.

Es la **última** entrada de la precedencia, movida la **primera** a propósito: todo lo que está por
encima sigue viendo exactamente lo que espera.

## Lo importante: el primer consumidor refutó el andamio tres veces

| Corrección | Por qué P3.0 no lo cazó |
|---|---|
| `AskUser` necesita el fix sobre el que pregunta | el censo mapeaba métodos a ramas **por NOMBRE**, sin mirar una sola firma |
| una etapa necesita `stoppedDurationMs` | el bucle lo mide una vez por fix; que la etapa lo recalcule sería un segundo reloj |
| `Prompt` nunca fue **un** efecto | es una ACCIÓN más un MARCADOR, y en la vía HIGH hay un tercer evento en medio |

La tercera es la afilada. El orden de hoy es **notify → `Candidate(OPENED)` → `PROMPT_SHOWN`**. Un
solo efecto `Prompt` habría emitido los dos marcadores pegados y **habría intercambiado en silencio
un par de eventos que tienen orden definido**. `NotifyPrompt` y `RecordPromptShown` son ramas
separadas solo por eso.

Y es exactamente la lección que este refactor lleva encontrando en cada fase: **algo que se lee como
una comprobación y no lo es**. El censo de P3.0 certificaba un andamio que la primera etapa falsificó
en cuanto lo tocó.

## Y el ORDEN del plan resultó estar mal en un punto

Un tercio de lo que hacen estas ramas es **emitir diagnóstico**, y buena parte en caminos que **no
cambian estado**: «notif suppressed, timeout in ~4200ms» es una línea de `PARKDIAG` de una rama que
decide no hacer nada.

El plan programa el tap de diagnóstico el ÚLTIMO (P3.12), después de las diez mudanzas. **La primera
mudanza lo necesita**: perder esas líneas cambia `parkdiag`, y `parkdiag` es el instrumento de
field-test.

Así que `StageVerdict` gana su canal de notas **ahora**, en su forma mínima honesta —una lista de
strings que el orquestador registra en orden—. P3.12 sigue teniendo trabajo: cambiar el `String` por
una nota tipada y quedarse con los dedups.

## Un test cazó una regresión de verdad a mitad de la mudanza

Escribir de vuelta el sub-estado `confirmation` ENTERO del veredicto **borra un `pendingConfirm`
abierto por el fast-confirm en el MISMO fix** — la etapa razona sobre un snapshot tomado al principio
de la iteración.

`should_discard_held_confirm_when_position_outran_the_steps_at_settle` falló exactamente por eso.

La regla que sale de ahí: **el write-back de una etapa es tan ancho como lo que la etapa cambia de
verdad, y ni un campo más.** Desaparece cuando el bucle sea de un solo escritor (P3.13).

## Doctrina

Ninguna tocada. **Cero cambio de conducta.**

## Tests

Los tests del bloque mudado son los del coordinator y **no se ha editado ni un assert**. Los **6**
tests de precedencia de P0.1 siguen verdes, que es el criterio común de la fase.

`StageOrderTest`/`StageScaffoldTest` sí se tocan: son míos de P3.0 y el censo **se puso rojo** con
las tres correcciones, que es literalmente para lo que estaba puesto.

**1.629 tests**, 0 fallos. Coordinator **−102 líneas**. `assembleMockDebug` ✅.

Siguiente: **P3.2**, `FastConfirmStage` [DET-D-03] — y con ella empieza a pesar el adaptador
`parkingDecisionInput`, que hoy entra a la etapa como lambda y cuya casa es `stages/` en cuanto lo
compartan dos de las tres etapas que lo usan.
