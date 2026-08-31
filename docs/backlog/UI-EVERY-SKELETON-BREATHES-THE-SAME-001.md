# UI-EVERY-SKELETON-BREATHES-THE-SAME-001 · Los dos skeletons de lista respiran distinto por accidente

**Estado:** 🟡 Abierto, sin rama · follow-up de `UI-HISTORY-A-LOADING-LIST-MUST-NOT-CLAIM-TO-BE-EMPTY-001`
**Abierto:** 31-08-2026

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
