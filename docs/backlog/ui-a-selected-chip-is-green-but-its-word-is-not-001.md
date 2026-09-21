# UI-A-SELECTED-CHIP-IS-GREEN-BUT-ITS-WORD-IS-NOT-001 · El chip elegido es verde; su palabra no

**Estado:** 🔵 En revisión · rama `feature/UI-A-SELECTED-CHIP-IS-OUTLINED-NOT-FILLED-001` ·
worktree `../Paparcar-chip` · cierra la **issue #49**

> ⚠️ **La rama conserva el ID viejo** (`…-IS-OUTLINED-NOT-FILLED-001`) porque renombrarla dejaría
> huérfana la PR #55. El ID de dentro del código sí se renombró: ver *Cómo cambió el encargo*.

## Problema

Pedido en la issue #49: el verde de los chips es demasiado neón y hay que bajar su intensidad.

## Cómo cambió el encargo (y por qué el ticket se renombró)

La issue pedía literalmente *«seguir manteniendo el borde verde, pero dejando el fondo de container
igual, no meterle el fondo verde»*, y eso se implementó: fondo neutro siempre, borde verde, y el
texto en `onSurface` tras la primera review.

**En la segunda review el user lo revirtió a medias:** *«creo que lo que debemos hacer es mantener
el fondo verde que teníamos en cada chip pero dejando el texto normal»*. O sea que de las dos
señales que se habían quitado —relleno y palabra verde— **la que sobraba era la palabra, no el
relleno**.

Eso deja el ID original (`A-SELECTED-CHIP-IS-OUTLINED-NOT-FILLED`) describiendo lo contrario de lo
que hace el código, que es justo la clase de rot que el proyecto no tolera en un ID: se renombra a
**`UI-A-SELECTED-CHIP-IS-GREEN-BUT-ITS-WORD-IS-NOT-001`**, que sí se entiende sin abrir el doc.

## Diseño final

`PaparcarFilterChip`, el chip canónico de toda la app (17 call sites):

| | Sin seleccionar | Seleccionado |
|---|---|---|
| Fondo | `surfaceContainerHigh` | **`primaryContainer`** (como antes del ticket) |
| Borde | `outline` a `DEFAULT_OUTLINE_ALPHA` | `PapColor.selected` |
| Texto e icono | `onSurface` | **`onSurface`** ← lo único que cambia |

**El cambio neto contra master es una sola línea**: desaparece `selected -> PapColor.selected` del
`contentColor`. Todo lo demás vuelve a ser lo que ya había.

La regla que queda escrita: *lo verde es la superficie, no la palabra*. Con el texto también verde,
un chip encendido daba dos señales del mismo hecho y la etiqueta perdía legibilidad justo cuando más
se lee.

## Medida del contraste (los dos temas, antes de tocar nada)

`onSurface` sobre `primaryContainer`:

| Tema | Par | Contraste |
|---|---|---|
| Claro | `#0E1A2E` sobre `#A8F5D0` | **13,8:1** |
| Claro | *(par de diseño `onPrimaryContainer` `#002819`)* | 12,6:1 |
| Oscuro | `#EBF2EF` sobre `#133D28` | **10,7:1** |

O sea que usar `onSurface` en vez del par «oficial» **no cuesta contraste: lo mejora** en claro. No
hay nada que ceder aquí. [UI-COLOR-GREEN-TEXT-EARNS-ITS-CONTRAST-001]

## Criterio de éxito

- Un chip elegido se distingue a un metro por su superficie verde, y la etiqueta se lee igual de
  bien encendida que apagada.
- Ningún otro componente cambia.

## Consumidores auditados

- `PaparcarFilterChip` → único sitio tocado; sus 17 call sites heredan el cambio sin tocarse.
- `PapSegmentedChoice` (tipo/tamaño de vehículo, elecciones de Ajustes) → **exento a propósito**: es
  otro widget —uno de N, siempre hay uno activo— y ahí el relleno es lo que dice cuál manda.
- `ColorGuardrailTest` → no aplica: no se introduce ningún `Color(0x…)` literal ni `tertiary`.

## Pendiente

⏳ **Verlo en device.** Es un cambio visual y no hay móvil conectado, así que la PR va sin captura
del estado final como pide `PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001`.
