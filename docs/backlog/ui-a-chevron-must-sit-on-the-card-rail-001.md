# UI-A-CHEVRON-MUST-SIT-ON-THE-CARD-RAIL-001 · el chevron de una fila vive en el raíl de la card, y hay UN chevron

**Estado:** ✅ Done — en master. `./gradlew testAll :app:compileMockDebugKotlin
:app:compileProdDebugKotlin --rerun-tasks` verde (con `--rerun-tasks` a propósito: un cambio de
imports no mueve el bytecode y los guardarraíles Konsist salen UP-TO-DATE sin llegar a ejecutarse).
⏳ Sin ver en device.

## Problema

En la ficha de vehículo (pestaña Vehículos), el bloque de estado —«📍 Marx-Engels-Forum · Berlin /
🕐 Parked · 22 h ago»— es la puerta al mapa y lleva su chevron. Ese chevron **no está en ningún
raíl**: cae a mitad de aire entre el texto y el borde de la card.

Medido en el código, distancias al borde derecho INTERIOR de la card:

| afordancia | cómo se dibuja | eje del glifo |
|---|---|---|
| lápiz de editar (ficha) | `IconButton(size = 40)` tras `end = 16` | **36 dp** |
| chevron de *Share this car* | `Icon` 24 dp tras `contentPadding` 16 dp de `PapListItem` | **28 dp** |
| chevron del bloque de estado | `Icon` 20 dp al final de la columna `weight(1f)` | **74 dp** |

Las 74 dp salen de que el `statusLine` se pinta **dentro de la columna de texto** del
`VehicleIdentityHeader` (`VehicleIdentityHeader.kt:131-193`), o sea antes del `TRAILING_GAP_DP` (8)
y de la caja de 40 dp del lápiz: 16 + 40 + 8 = 64, más medio glifo. Está lo bastante cerca del raíl
para parecer que lo intenta y lo bastante lejos para parecer roto, y deja un hueco muerto tras
«Berlin» que no lee como aire sino como desalineación.

Y una vez ahí, la pregunta del user («¿y del mismo tamaño que el de Share?») destapa lo segundo: en
la app hay **cuatro chevrons de fila distintos**.

| sitio | glifo | tamaño | tinte |
|---|---|---|---|
| `PapNavChevron` — Ajustes, Licencias, Historial, cifras (**12 call sites**) | `KeyboardArrowRight` (AutoMirrored) | 20 | `onSurface @ .3` |
| `SharedCarSection.Chevron()` — *Share this car* | `ChevronRight` | 24 | `onSurfaceVariant` |
| `VehiclePageContent` — bloque de estado | `ChevronRight` | 20 | `onSurfaceVariant` |
| `HomeParkingRow` — card de coche de Home | `ChevronRight` | 22 | `onSurfaceVariant` |

El KDoc de `PapNavChevron` dice literalmente *«one size, one dimness, everywhere»*. No es verdad
desde hace tres copias. En la PROPIA pantalla de Vehículos conviven los cuatro: las filas de
Historial y las de cifras ya usan `PapNavChevron`, y las dos cards de arriba no.

## Doctrina violada

- **Sistemas, no parches** — «one size, one dimness, everywhere» es un invariante que vive en UN
  sitio (`PapNavChevron`) y que tres call sites re-implementaron a mano. No se arregla moviendo un
  icono: se arregla borrando las copias.
- **[UI-LIST-ITEM-001]** — el trailing de una fila va en el borde del contenedor. El del bloque de
  estado no lo está.
- **⛔ Magic numbers** — `STATE_CHEVRON_DP`, `CHEVRON_DP` y el 24 implícito de `Chevron()` son tres
  números para el mismo hecho.

## Señales / datos disponibles

Captura del user en device (Oppo, tema oscuro, 16-09) + los tres `contentPadding` citados arriba.
No hace falta telemetría: es geometría estática.

## Diseño

**El raíl de acciones de una card es el EJE DEL GLIFO, no el borde de su caja táctil**, y está a
`CARD_PADDING + tamañoDelChevron / 2` del borde interior. Con `PapNavChevron` a 20 dp: **26 dp**.

1. **El bloque de estado sale de la columna de texto.** `VehicleIdentityHeader` pasa a ser un
   `Column`: la fila de identidad (tile · textos · trailing) y, debajo, el `statusLine` a ancho
   completo con `start = TILE_DP + TILE_TEXT_GAP_DP` para conservar el sangrado actual (el pin
   sigue alineado con el nombre). Su chevron aterriza en el raíl, igual que el de *Share*.
   - ⛔ El sangrado izquierdo NO se toca. Lo que estaba mal era el extremo derecho; llevarse también
     el pin al margen de la card habría reflujado la lectura de la ficha entera sin que nadie lo
     pidiera.
2. **`alignsWithFirstLine` deja de leer `statusLine`.** Ese predicado decide si el tile se centra o
   se alinea con la primera línea, y sólo tiene sentido sobre lo que hay DENTRO de la columna. Pasa
   a `showSize || metaSecondLine != null`, que da exactamente el mismo valor en los dos call sites
   (ficha `true`, Home `false`) y ya no miente sobre por qué.
3. **Un solo chevron de fila.** `PapNavChevron()` en los tres outliers; se borran
   `SharedCarSection.Chevron()`, `STATE_CHEVRON_DP` y `CHEVRON_DP`. `PapNavChevron` gana un
   `contentDescription` opcional (el bloque de estado anunciaba *«ver en el mapa»* y eso no se
   pierde) y su tamaño se publica como `PapNavChevronSize`, por la misma razón por la que
   `PapListItemGap` es público: lo que hay que alinear FUERA de la fila no puede copiar el número.
4. **El lápiz baja al mismo raíl.** Su caja táctil son 40 dp y no se toca —bajarla rompería el
   mínimo táctil—, así que la caja **sobresale** hacia el borde lo justo para que su glifo caiga en
   el eje: `(EDIT_BOX_DP - PapNavChevronSize) / 2` = 10 dp, calculado, no escrito a mano. Le quedan
   6 dp de margen al borde de la card.

## Criterio de éxito

- Lápiz, chevron de estado y chevron de *Share this car* comparten eje vertical (26 dp del borde
  interior de la card) y el mismo glifo/tamaño/tinte que las filas de Historial de esa misma
  pantalla.
- No queda ningún `Icons.*.ChevronRight` de trailing de fila fuera de `PapNavChevron`.
- `./gradlew testAll` verde + `assembleMockDebug` para ver la ficha en el Dev Catalog.

## Consumidores auditados

`grep -rn "ChevronRight\|KeyboardArrowRight" shared/src/commonMain` → 6 sitios:

| sitio | veredicto |
|---|---|
| `PapSettingRows.PapNavChevron` | **la fuente** — gana `contentDescription` + `PapNavChevronSize` |
| `SharedCarSection:233` (`Chevron()` privado) | **cerrado** — borrado, usa `PapNavChevron()` |
| `VehiclePageContent:249` (bloque de estado) | **cerrado** — `PapNavChevron()` + raíl |
| `HomeParkingRow:339` (trailing de la card de Home) | **cerrado** — `PapNavChevron()`, `CHEVRON_DP` borrado |
| `PapSheet:270` (`PapStepperSlot` del header) | **exento** — no es trailing de fila: es el botón *siguiente* de un pager, hermano del `ChevronLeft` de al lado. Cambiarle el glifo rompería la pareja |
| `PeekShared:118` (fila meta del peek) | **exento** — su chevron se dimensiona (18 dp) y se tiñe con el ICONO LÍDER de su propia fila, no con el raíl de una card. Es parte de la meta-line, no navegación de card |
| `ParkingHistoryDetailScreen:29-30` | **cerrado** — `ChevronLeft` y `ChevronRight` importados y NO usados, restos de un refactor anterior. Borrados: dejarlos habría hecho falsa la afirmación de que no queda ninguno fuera de `PapNavChevron` |

Sin `PapNavChevron` en `iosMain`/`androidMain`: es `commonMain` puro, así que el lado iOS viaja
gratis con el mismo código. No hay hueco que abrir.

## Fuera de alcance (deliberado)

Nada. El barrido cubre los cuatro chevrons de fila; los dos exentos quedan justificados arriba.
