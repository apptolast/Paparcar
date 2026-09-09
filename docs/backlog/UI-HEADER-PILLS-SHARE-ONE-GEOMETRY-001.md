# UI-HEADER-PILLS-SHARE-ONE-GEOMETRY-001 · La cápsula de GPS ocupa la misma banda que las píldoras de encima, y el chip de añadir zona deja de rozar su borde

**Estado:** ✅ Done · en master

## Problema

La cabecera flotante de Home apila tres cosas: la barra de búsqueda, la fila de chips de zona y —
de vez en cuando — la cápsula de precisión de GPS. Las dos primeras están dimensionadas por el mismo
sistema (`ZONE_CHIP_HEIGHT` = 34 dp, radio de estadio, sombra de FAB de 6 dp, sangrado lateral de
14 dp). La tercera no:

| | alto | radio | sombra | sangrado interno |
|---|---|---|---|---|
| chip de zona | 34 dp declarado | 999 (estadio) | `MAP_FLOATING_SHADOW_DP` = 6 dp | 12 dp / 4 dp |
| cápsula de GPS | emergente (~26 dp) | 20 dp | 2 dp | 10 dp / 10 dp |

Resultado: cuando aparece —y aparece sola, sin que el usuario haga nada— no lee como un cuarto
control de la misma familia, sino como algo pegado debajo. Además entraba deslizando en VERTICAL
(`slideInVertically`) desde una banda de altura CERO, así que su llegada estiraba la columna de la
cabecera: la aparición era un empujón, no una llegada.

Segundo hallazgo, reportado en la misma pasada: en el chip de **añadir zona** desplegado, el `+` de
cabeza y la `×` de cierre van pegados al borde de la píldora. Medido en el código:

- la `×` vive en una caja táctil de 28 dp con un glifo de 16 dp (6 dp de margen propio) y la fila
  **no tiene padding final** → 6 dp de aire hasta el borde. El chip de zona de al lado sí lo tiene
  (`end = 4.dp`), y su lápiz respira 10 dp.
- el `+` va centrado en una caja de 34 × 34 → su centro cae a 17 dp del borde, justo en el centro
  del casquete redondo. El icono de cabeza de un chip de zona cae a 21 dp (12 de sangrado + 9 de
  medio icono). Por eso el `+` se ve más metido en la esquina que cualquier otro icono de la fila.

## Doctrina violada

- **Sistemas, no parches**: la geometría de la fila ya está declarada en un sitio
  (`ZONE_CHIP_HEIGHT`, cuyo KDoc dice literalmente que es *THE* altura de todo lo que va en esa
  banda). La cápsula la ignoraba y se inventaba la suya.
- **[MAP-GLASS-001]** — todo lo que flota sobre el mapa comparte sombra de FAB y forma de píldora.
  La cápsula llevaba 2 dp de sombra, de otra capa.
- **[UI-ACTIVITY-HEADER-RESERVES-ITS-HEIGHT-001]** — la altura se RESERVA por el caso peor; no se
  deduce de si el contenido está o no. Una banda que existe solo cuando hay contenido es una banda
  que mueve lo que tiene alrededor.

## Señales / datos disponibles

`HomeHeaderSlice.gpsAccuracy` (metros del último fix) ya alimenta a la cápsula. No hace falta
telemetría nueva: los umbrales (20 m / 50 m) y los dos niveles (ámbar / rojo) se quedan como están.

## Diseño

**Una sola geometría para la cabecera, declarada una vez.**

1. **El sangrado lateral es un número, no tres.** `HEADER_SIDE_INSET_DP` = 14 en
   `HomeHeaderSection`, consumido por la fila de búsqueda, el `contentPadding` de los chips y la
   banda de la cápsula. Antes eran tres literales que coincidían por costumbre.
2. **La cápsula tiene banda propia y RESERVADA** (`Box` de `ZONE_CHIP_HEIGHT`), a la misma
   distancia de los chips (8 dp, el `CHIP_SHADOW_ROOM_DP` que ya separa la búsqueda de los chips)
   que los chips de la búsqueda. Aparecer deja de cambiar la altura de la cabecera.
3. **La cápsula adopta la píldora de la fila**: alto `ZONE_CHIP_HEIGHT`, radio de estadio, sombra
   `MAP_FLOATING_SHADOW_DP`, icono de 18 dp, sangrados 12/14.
   **Excepción explícita y razonada:** NO pasa a `GlassSurface`. Su relleno ámbar/rojo *es* el
   mensaje; vaciarlo a `surfaceContainer` borraría lo único que la cápsula dice.
4. **Movimiento**: `fadeIn + slideInHorizontally` desde la izquierda y salida en la misma dirección,
   en la banda `PapMotion.medium()`. Como la banda ya está reservada, el desplazamiento es una
   deriva corta (un cuarto de su ancho), no un empujón.
5. **Chip de añadir zona**: desplegado, la fila de texto gana `end = 4.dp` (la `×` respira los
   mismos 10 dp que el lápiz del chip de zona) y el `+` gana `start = 4.dp` — animado con
   `animateDpAsState`, porque **colapsado no puede llevarlo**: la píldora tiene que seguir siendo un
   círculo exacto de 34 dp. La inserción se anima para que el `+` no salte al desplegarse.

## Criterio de éxito

- La cápsula, cuando aparece, se lee como un chip más de la cabecera: misma altura, mismo borde
  izquierdo, misma sombra.
- La cabecera NO cambia de altura al aparecer/desaparecer la cápsula.
- Entra derivando desde la izquierda y sale hacia la izquierda.
- En el chip de añadir zona desplegado, `+` y `×` respiran lo mismo que los glifos del chip de zona.
- Verificable **sin campo**: la cápsula entra en la galería de estados del Dev Catalog con sus dos
  niveles (era invisible fuera de una posición mala de verdad, que es justo por qué se pasó tanto
  tiempo sin revisar).

## Verificación

- `:app:compileMockDebugKotlin` + `:app:compileProdDebugKotlin` ✅
- `:shared:testDebugUnitTest` ✅ (incluye los guardarraíles Konsist de tipografía, color, divisores
  e imports; el cambio no introduce `fontSize`/`fontWeight`/`Color(0x…)`/wildcards).
- ⏳ **En mano**: la cápsula en la galería del Dev Catalog (dos niveles + alternar para ver la
  entrada) y la invitación de zona en Home.

## Consumidores auditados

- `HomeGpsAccuracyBanner` — **un único call site**: `HomeHeaderSection:107`. Cerrado.
- `ZONE_CHIP_HEIGHT` — `ZoneChip`, `AddZoneChip` y ahora la cápsula. Los tres leen la misma
  constante; ninguno declara la suya.
- `AddZoneChip` — call sites: `HomeHeaderSection` (fila real) y `StateGalleryScreen:621,628`
  (colapsado + invitación viva). Ambos cubiertos por el cambio, ninguno pasa geometría propia.
- Strings: **ninguno nuevo** (`home_gps_accuracy_banner` ya existe en los 9 locales). Sin trabajo
  de i18n.
