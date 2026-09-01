# UI-EVERY-SKELETON-BREATHES-THE-SAME-001 · Los dos skeletons de lista respiran distinto por accidente

**Estado:** ✅ Done — implementado dentro de `UI-SEVEN-STRAYS-FROM-THE-CANON-001` (incidencia 4)
**Abierto:** 31-08-2026 · **Cerrado:** 01-09-2026

## Resolución

Opción **(a)**: `PapShimmerBox` sigue siendo el único primitivo y gana el ritmo de bloque grande
como constante compartida `PapShimmerBlockScale = 0.4f` (en `PapShimmer.kt`): 0.15→0.40 × 0.4 =
0.06→0.16, exactamente el ritmo que Home ya tenía. Duración unificada en `PapMotion.Breathe`
(600 ms) — el token de motion del sistema, que Historial ya usaba. Los dos skeletons de lista
(`SpotsSkeletonList`, `HistorySkeletonSection`) se portaron a `PapShimmerBox` conservando sus
factores secundarios (subtítulo ×0.7, chips ×0.85, header ×0.7) sobre la escala de bloque.

Lo que cambia a la vista: Home respira a 600 ms en vez de 900; Historial baja su alfa máxima de
0.18 a 0.16 y pierde su `LinearEasing` particular. ⏳ **Pendiente el visto bueno en device** (claro
y oscuro), como pedía el criterio de éxito — los otros dos criterios verificados por grep: cero
`rememberInfiniteTransition` en `presentation/`, ambas listas leen las mismas constantes del mismo
fichero.

## Problema

La app tiene **tres** ritmos de shimmer, y sólo uno está en un primitivo:

| dialecto | alfa · duración | quién lo usa |
|---|---|---|
| `PapShimmerBox` (`PapShimmer.kt:53-54`) | 0.15→0.40 · 600 ms (`PapMotion.Breathe`) | placeholders inline pequeños: `BrowsePeek.kt:237-255`, `PapSheet.kt:355` |
| a mano, lista | 0.06→**0.16** · **900 ms** | Home · `SpotsSkeletonList` (`HomeSheetContent.kt:350-407`, constantes en `404-408`) |
| a mano, lista | 0.06→**0.18** · **600 ms** | Historial · `HistorySkeletonSection` (`HistoryContent.kt:367-429`) |

Los dos skeletons de LISTA quieren claramente ser lo mismo (misma alfa mínima, mismos bloques
redondeados, misma intención) y difieren en 0.02 de alfa y 300 ms de periodo. Nadie decidió eso: se
escribieron por separado. No es un bug visible — es la clase de deriva que convierte "el sistema"
en "dos sitios que se parecen".

## Doctrina violada

`PapShimmer.kt:20-26` dice literalmente *"Reuse this instead of hand-rolling an `infiniteTransition`
per screen"* — y aun así los dos skeletons de lista de la app lo hacen a mano. Un primitivo que sólo
cubre el caso pequeño no es el primitivo único que su propio doc afirma ser.

## Diseño (a decidir)

⚠️ **Lo que NO hay que hacer** (medido el 31-08 al intentarlo dentro del ticket padre): portar los
skeletons de lista a `PapShimmerBox` tal cual. Su rampa 0.15→0.40 es ~2,5× la de un skeleton de
lista; dejaría el historial y el sheet de Home visiblemente más oscuros. La rampa fuerte está
calibrada para elementos pequeños (26 dp), no para bloques de 148 dp.

Opciones reales:
- **(a)** `PapShimmerBox` gana un ritmo de "bloque grande" — nótese que `alphaScale = 0.4` sobre la
  rampa actual da exactamente 0.06→0.16, que es el ritmo de Home. Faltaría unificar la duración.
- **(b)** Un segundo primitivo hermano (`PapSkeletonBlock`) con la rampa de lista, y `PapShimmerBox`
  se queda para lo inline.

Decidir cuál antes de tocar nada; ambos exigen elegir UNA duración (600 u 900 ms) y aplicarla a los
dos sitios, lo que cambia el aspecto de al menos uno.

## Criterio de éxito

- Ningún `rememberInfiniteTransition` de skeleton fuera de `ui/components/`.
- Home e Historial respiran con las mismas constantes, leídas del mismo sitio.
- Verificado en device en claro y oscuro: el cambio de ritmo del que pierda su valor actual es
  aceptable a la vista.
