# UI-DET-STOP-BELONGS-BESIDE-THE-STORY-001 · El «Stop» de la fila de viaje cabe a la derecha, no debajo

**Estado:** ✅ Done · rama `refactor/UI-DET-STOP-BELONGS-BESIDE-THE-STORY-001-stoptrailing` · worktree `../Paparcar-uipolish`
**Cerrado:** 01-09-2026 — ver «Resolución» abajo
**Origen:** revisión visual del sweep `UI-SEVEN-STRAYS-FROM-THE-CANON-001` (captura de la fila
«Driving your Mi Seat» con el botón apilado a ancho completo)

## Problema

La fila de detección en viaje (`DetectionStory.Driving` → `ActionRow` con
`primaryStacksBelow = true`) apila el CTA «Stop» a ancho completo debajo del contenido. En pantalla
el botón ocupa una franja entera para una palabra de cuatro letras, y la tarjeta crece en vertical
robándole altura al sheet. El user propone: **botón compacto a la derecha del contenido** (como el
badge/CTA trailing de otras filas), y si el título no cabe con el botón al lado, **que envuelva a
más líneas** en vez de prohibir el layout inline.

## La decisión que esto revierte — y por qué puede revertirse

El apilado NO fue un accidente. `HomeDetectionSurface.kt` (rama Driving) lo documenta:

> *"Stacked full-width, not inline: this row's copy carries the CAR NAME, so an inline pill steals
> the width the title needs and wraps 'Conduciendo tu Toyota Corolla' onto two lines (longer names
> then truncate). Same reason the alert watch rows stack. [DET-WATCH-HONEST-001]"*

Es decir: se apiló porque el inline provocaba (a) título a dos líneas y (b) truncado con nombres
largos. La propuesta del user ataca la premisa, no la conclusión: **(a) no es un defecto** — dos
líneas de título son aceptables y más baratas que una franja de botón entera — y **(b) sólo era
inevitable porque el título llevaba `maxLines` restrictivo**. Si el título envuelve libremente, el
inline no trunca nada.

## Diseño (a decidir al implementar)

- `ActionRow` gana un modo de CTA **trailing compacto** (pill pequeña, estilo del botón «Arreglar»
  de la fila de salud de Ajustes) para filas de UN solo CTA corto.
- El título/subtítulo de esas filas relajan `maxLines` para envolver cuando el nombre del coche lo
  pida — el texto cede altura, no anchura truncada.
- **Barrer los demás usuarios de `primaryStacksBelow`** y decidir fila a fila
  [SISTEMA, no parche]: `Driving` («Stop»), `WATCHING_FRAGILE` («fortify»), `WATCH_INTERRUPTED`
  («reactivar»), `AwaitingAnswer` (dos CTAs — probablemente se queda como está: un par sí/no no es
  un CTA trailing). Los labels largos multi-palabra («Solicitar exención de batería») puede que NO
  quepan como trailing ni con wrap — medirlos en los 9 locales antes de decidir; el layout lo manda
  el label más largo de los 9, no el inglés.
- Verificar en device con el nombre de coche más largo real del catálogo + locale más verboso
  (DE/PL suelen ganar).

## Resolución (01-09-2026)

El trabajo resultó MENOR que el diseño propuesto: **`ActionRow` ya tenía el modo trailing inline**
(un solo CTA sin `primaryStacksBelow` pinta la `CtaPill` a la derecha, con el subtítulo envolviendo
a 2 líneas) — lo usan otras filas desde `DET-READY-001h`. No hizo falta componente nuevo: el ticket
se redujo a decidir QUÉ filas vuelven a inline, y la decisión se tomó **midiendo los strings en los
9 locales** (el layout lo manda el más largo, no el inglés):

| Fila | CTA máx | Subtítulo máx | Decisión |
|---|---|---|---|
| `Driving` («Stop») | 9 chars (PL «Zatrzymaj») | 35 | **→ INLINE** (el cambio de este ticket) |
| `WATCHING_FRAGILE` («Activar») | 10 | **78** | se queda APILADA — el subtítulo no sobrevive al recorte de anchura |
| `WATCH_INTERRUPTED` («Reactivar») | 16 (PL, 2 palabras) | **126** | se queda APILADA — ídem, y es la fila de alerta |
| `AwaitingAnswer` (sí/no) | — | — | intacta: un par de respuestas no es un CTA trailing |

- El título ya envuelve (`maxLines = 2` en `ActionRow`) — la premisa del apilado original
  («inline = truncado») dejó de ser cierta cuando el título ganó su segunda línea.
- Área táctil: `CtaPill` mide 40dp visuales pero es `Surface(onClick)` de M3, que impone
  `minimumInteractiveComponentSize` (48dp) — el criterio se cumple sin engordar la pill.
- Galería/previews: las variantes Driving/Driving·BT/Candidate ya renderizan esta misma fila — sin
  cambios estructurales que espejar.

## Criterio de éxito

- La fila «Driving your X» presenta el Stop a la derecha sin truncar ningún título: los nombres
  largos envuelven a 2+ líneas.
- Ningún CTA queda con área táctil < 48dp.
- La decisión por fila queda escrita aquí (cuál pasa a trailing, cuál se queda apilada y por qué).
- Galería mock: las variantes Driving/fragile/interrupted reflejan el layout nuevo en la MISMA
  tarea.
