# UI-THE-ADD-ZONE-CHIP-IS-ONE-CHIP-IN-EVERY-STATE-001 · el "+" de zona no es un botón aparte: es el mismo chip, colapsado

**Estado:** ✅ Done — en master

## Problema

Reportado por el user (09-09-2026): *"el chip de zonas de home cuando lo cerramos queda un fab
button pequeño que no concuerda con el alto del chip y sin animación ni nada"*.

Al medirlo no es un defecto, son **tres del mismo origen**:

| Pieza | Altura | De dónde sale |
|---|---|---|
| `ZoneChip` | **34 dp** | EMERGENTE: `top 3` + caja de toque del lápiz `28` + `bottom 3`. Nadie la declara |
| `MapCircleFab` de "+" | **32 dp** | `ADD_ZONE_CHIP_SIZE_DP`, una constante **sin ninguna relación** con la de arriba |
| Invitación de 2 líneas | **~56 dp** | Un tercer componente, con sus propios paddings |

1. **El "+" mide 2 dp menos que los chips a los que acompaña.** No porque alguien eligiera 32: porque
   la altura del chip no está escrita en ningún sitio, así que no había con qué casarla.
2. **El cierre de la invitación es un corte seco**: `if (dismissed) { … return }` cambia un widget de
   56 dp por otro de 32 dp en el mismo frame. Ninguna transición.
3. **El mismo "+" se coloca de dos maneras**: suelto lleva `padding(start = 14, top = 6)`; dentro de
   la fila lo posiciona `contentPadding(horizontal = 14, vertical = 8)`. O sea que **salta de sitio
   en cuanto creas tu primera zona**.

## Doctrina violada

- `⛔ sistemas, no parches` — el invariante "todo lo que vive en la fila de zonas mide lo mismo" no
  existe en ningún sitio. Cuadrar el 32 a mano sería el parche; el arreglo es que la altura tenga
  **un** dueño y que las tres piezas sean **una**.
- `⛔ Extender composables con params por estado, no ficheros paralelos`
  ([[feedback_reuse_composables_across_states]]) — `HeaderAddZoneChip` es exactamente el fichero
  paralelo: un segundo componente para lo que es un ESTADO del primero.

## Señales / datos disponibles

Nada que medir en campo: las tres alturas se leen del código y la geometría doble también. El estado
`dismissed` ya existe (`rememberSaveable`, ámbito de sesión) y esa semántica se conserva — es una
invitación, que vuelva en un arranque nuevo está bien.

## Diseño

**Una fila, una altura, un chip de añadir con dos estados.**

1. **La altura pasa a estar DECLARADA y compartida**: `ZONE_CHIP_HEIGHT` en `HomeZoneChips.kt`, junto
   al chip que la define. `ZoneChip` la aplica con `Modifier.height(...)` en vez de dejar que la
   dicte por accidente la caja de toque del lápiz, y el chip de añadir la usa como diámetro. Que el
   "+" y el chip midan lo mismo deja de ser una coincidencia que mantener.
2. **`AddZoneChip(expanded: Boolean)` sustituye a los dos widgets**: colapsado es un círculo de
   `ZONE_CHIP_HEIGHT`; expandido es la píldora con título, pista y ×. La misma superficie glass, el
   mismo contrato de sombra que el resto de la fila [MAP-GLASS-001].
3. **El cierre se ANIMA**: `animateContentSize()` sobre el contenido y fade del bloque de texto, con
   `PapMotion.medium()`. La píldora se encoge hasta ser el círculo — que es lo que siempre fue.
4. **Una sola geometría**: el estado vacío deja de ser una rama con su propio padding y pasa por el
   MISMO `LazyRow` con cero zonas. La invitación y el "+" quedan donde estará el "+" de siempre, así
   que **no hay salto** al crear la primera zona. La divergencia de paddings desaparece por
   construcción, no por mantenerla en sync.

## Criterio de éxito

1. En la fila de zonas, chips y "+" tienen exactamente la misma altura (declarada, no ajustada).
2. Cerrar la invitación se ve como un colapso animado, no como un cambio de widget.
3. Crear la primera zona no mueve el "+" ni un píxel.
4. Galería mock con los tres estados (invitación, colapsado, con zonas) y `@Preview` en paridad.
5. `assembleMockDebug` + `compileProdDebugKotlin` + `:shared:testDebugUnitTest` verdes.

## Consumidores auditados

| Sitio | Qué era | Acción |
|---|---|---|
| `HeaderZoneChips` · item `add_zone` | `MapCircleFab(32dp)` | → `AddZoneChip(expanded = false)` |
| `HeaderAddZoneChip` | el widget paralelo entero (invitación + rama `if (dismissed)`) | 🗑 **borrado** |
| `ADD_ZONE_CHIP_SIZE_DP` · `CHIP_ICON_BOX_DP` · `CHIP_ICON_DP` · `HINT_ALPHA` · `DISMISS_HIT_DP` | constantes que solo servían a ese widget | 🗑 borradas |
| `ZoneChip` | altura emergente | ahora `Modifier.height(ZONE_CHIP_HEIGHT)` |
| `MapTypeToggle` · `MapControlButtons` | otros usuarios de `MapCircleFab` | ✅ exentos — flotan sobre el mapa a 48/56dp, **no comparten fila con los chips**, así que no tienen por qué compartir su altura |
| Galería mock + `HomeZoneChipsPreviews` | solo tenían los chips | ✅ ganan fila-con-"+", invitación, y un caso **interactivo** para el colapso |

**26 imports huérfanos** quedaron en `HomeHeaderSection.kt` al borrar el widget paralelo (Kotlin no
da error por ellos, así que se pudren en silencio). Barridos.
⚠️ El barrido automático se llevó también `getValue`/`setValue`, que el delegado `by` necesita
**aunque no aparezcan escritos en el cuerpo** — lo cazó el compilador; anotado por si se repite.

## Ejecutado

- `ZONE_CHIP_HEIGHT = 34.dp` en `HomeZoneChips.kt`, junto al chip que define el ritmo de la fila.
- `AddZoneChip(expanded)` sustituye a los dos widgets. Colapsado = círculo de `ZONE_CHIP_HEIGHT`
  (el "+" vive en un cuadrado tan ancho como alta es la fila, así que **no hay una segunda
  constante de tamaño que mantener en sync**). Expandido = píldora con título, pista y ×.
- `heightIn(min=)`, no `height`: expandido lleva dos líneas de 16sp (32dp) que una caja fija de 34
  **recortaría en cuanto el usuario suba la escala de fuente**. Y mientras la invitación se muestra
  es lo único en la fila, así que no hay nada al lado con lo que desalinearse.
- `animateContentSize(PapMotion.medium())` + `AnimatedVisibility` (fade + expand/shrink horizontal).
- El estado vacío pasa por el MISMO `LazyRow`: la divergencia de paddings —y con ella el salto del
  "+" al crear la primera zona— desaparece **por construcción**, no por mantenerla cuadrada.

### Verificación

- `:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin` + `:shared:testDebugUnitTest` ✅
- **En device** (Pixel 8 Pro, Dev Catalog, sha256 device↔local `b876304f82dbab67`):
  - fila `[Casa][Garaje][+]` — el "+" a la misma altura que los chips ✅
  - la invitación colapsa al pulsar la × ✅
  - **la × NO dispara además el `onClick` de la píldora**: si lo hiciera, intentar descartar la
    invitación abriría el modo "añadir zona" en la app real. Comprobado — solo corre `onDismiss`.
- ⚠️ No se capturó un frame intermedio de la animación (el `screencap` no cae dentro de los 240 ms);
  lo verificado son los dos extremos y que la transición ocurre.
- ⏳ Falta verlo en el Home real con una cuenta: la galería prueba el componente, no el header.
