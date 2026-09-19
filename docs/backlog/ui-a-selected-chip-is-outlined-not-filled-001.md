# UI-A-SELECTED-CHIP-IS-OUTLINED-NOT-FILLED-001 · Un chip elegido se marca con borde, no con relleno

**Estado:** 🔵 En revisión · rama `feature/UI-A-SELECTED-CHIP-IS-OUTLINED-NOT-FILLED-001` ·
worktree `../PaparcarKMP-issue49` · cierra la **issue #49**

## Problema

Pedido por el product owner (#49): el verde neón de los chips es demasiado intenso. El chip sin
seleccionar está bien; el seleccionado debe **mantener el borde verde y dejar el fondo como está**,
sin el relleno verde de hoy.

## Doctrina

Refuerza `UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001`: el verde es la tinta más escasa de la app
—lo que se puede HACER ahora y la identidad de un coche vigilado—. Un filtro no es ninguna de las
dos cosas, y una fila de píldoras con una rellena de verde convierte el filtro en lo más llamativo
de la pantalla.

## Diseño

`PaparcarFilterChip`, que es el chip canónico de toda la app (17 call sites):

- **Fondo**: `surfaceContainerHigh` esté elegido o no — `selected` deja de entrar en el `when`.
- **Borde**: sigue verde (`PapColor.selected`) cuando está elegido; contorno tenue cuando no.
- **Texto e icono**: pasan de `PapColor.selected` a `PapColor.actionText`. Es consecuencia directa
  de quitar el relleno: el verde de `primary` se apoyaba en `primaryContainer`, un par de color
  diseñado junto; sobre la superficie neutra es una palabra verde sobre gris claro, justo lo que
  `UI-COLOR-GREEN-TEXT-EARNS-ITS-CONTRAST-001` existe para impedir. El BORDE se queda en `selected`
  porque un trazo no se lee, se ve.

## Criterio de éxito

- Un chip elegido se distingue a un metro por borde + texto, sin mancha verde.
- En tema claro el texto del chip elegido pasa el contraste (es `actionText`, no `primary`).
- Ningún otro componente cambia: la selección de `PapSegmentedChoice` y las filas de Ajustes siguen
  como están — ver Preguntas.

## Preguntas (para la revisión)

1. **El control segmentado** (`PapSegmentedChoice`: tipo/tamaño de vehículo, elecciones de Ajustes)
   sigue con relleno `primaryContainer`. Lo he dejado fuera a propósito: es un widget distinto
   —uno de N, siempre hay uno activo— y ahí el relleno es lo que dice cuál manda. Si lo quieres
   también en borde, es un cambio de dos líneas en el mismo sitio.
