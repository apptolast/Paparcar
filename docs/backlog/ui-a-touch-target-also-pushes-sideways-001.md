# UI-A-TOUCH-TARGET-ALSO-PUSHES-SIDEWAYS-001 · El mínimo táctil también empuja a los lados, y dentro de un chip eso se ve

**Estado:** ✅ Done · en master

## Problema

El user, mirando el chip de zona en Home (20-10):

> «también quería ver porque tenemos tanto margen en lapicito del chip de zona hacia los lados»

El lápiz del `ZoneChip` se dibuja con un glifo de **16 dp** dentro de un `Box` que pide
`minimumInteractiveComponentSize()`. Ese modificador impone **48 × 48 de LAYOUT**; el alto lo capa
el chip (34 dp, declarado), así que **el mínimo sólo gana a lo ancho**: quedan
`(48 − 16) / 2 = ` **16 dp de aire invisible a cada lado** del lápiz, que nadie escribió y que no
aparece en ninguna constante.

Medido sobre captura de device (Pixel_8, 420 dpi, 1 dp = 2,625 px): del glifo al borde derecho del
chip hay **52 px = 19,8 dp**, que es exactamente `16 (overhang) + 4 (padding end)`. El modelo
predice el píxel.

Y el propio `HomeZoneChips` lleva escrita la cuenta vieja:

> *«Tighter end inset than start: the pencil's **28dp** touch box already carries its own optical
> margin around the 16dp glyph»*

La compensación (`padding(start = 12.dp, end = 4.dp)`) se calculó contra una caja de 28, cuando el
mínimo táctil ya la había convertido en 48. **Un comentario fosilizó una cuenta que Material
cambió por debajo.**

## Doctrina violada

- `UI-THE-SPACING-SWEEP-IS-SCREEN-BY-SCREEN-001` — *«una constante de hueco junto a un control no
  dice lo que se ve»*. Es exactamente esa ley, **girada 90°**: la app aprendió a descontar el
  overhang vertical — `PapGap.betweenControls`, **13 usos y los 13 verticales** — y a lo ancho no lo
  descuenta nadie.
- `UI-A-SCALE-NOBODY-USES-IS-NOT-A-SYSTEM-001` — el área táctil es un rol con dueño
  (`PapControlHeight.touchTarget`), pero **se pide llamando a Material directamente** en 9 sitios,
  así que nada obliga a decir qué variante se quiere.

⛔ **El área táctil NO se toca**: 48 dp son accesibilidad. *Lo que se corrige es la CUENTA, no el
área* — la misma frase que preside `PapGap.betweenControls`.

## Señales / datos disponibles · los 9 call sites, medidos

Medido en master `b6e289010`. La pregunta que los separa no es el tamaño: es **si el control pinta
su caja**. Uno con fondo o borde se ve a su tamaño declarado y su overhang es aire entre superficies;
uno desnudo sólo enseña el glifo, y entonces el aire se mide contra él.

| call site | lo que se VE | overhang/lado | ¿molesta? |
|---|---|---|---|
| `PaparcarFilterChip` · trailing | glifo 12 dp (sin `.size()`) | **18 dp** | sí — dentro de un chip |
| `HomeZoneChips` · lapicito | glifo 16 dp | **16 dp** | sí — es el caso que el user ve |
| `PapClearIconButton` | círculo 22 dp | 13 dp | a medias — pinta fondo, pero es pequeño |
| `PapStepperButton` · activo / gastado | glifo en 32 dp | 8 dp | no |
| `PapSheet` · botón de icono | círculo 38 dp (borde) | 5 dp | no |
| `PapSheet` · dismiss × | círculo 34 dp (fondo) | 7 dp | no |
| `AddingZonePeek` · icono de zona | círculo 40 dp (fondo) | 4 dp | no |
| `VehicleColorSelector` · swatch | círculo 40 dp (borde) | 4 dp | no |

**No son siete problemas: son dos y medio.** El resto es aire normal entre superficies que se ven.

## 🔑 El hallazgo que cambia la pregunta

Antes de tocar nada: **el mínimo táctil de ese lápiz ya estaba incumplido**. El chip declara 34 dp
de alto, así que su caja real era **48 × 34** — el mínimo cumplido en UN eje, y justo el eje que
produce el aire. Esos 16 dp por lado no compraban accesibilidad: el dedo tenía 34 dp en vertical con
el modificador puesto y los sigue teniendo sin él.

**Un mínimo táctil que sólo se cumple en un eje no es un mínimo, es aire.**

Eso convierte el ticket en una decisión de diseño, no en un fix, y la tomó el user el 20-10 con las
dos salidas delante:

| salida | área táctil | coste |
|---|---|---|
| **recortar el aire** ← elegida | 28×34, la que el control tuvo siempre | por debajo de 48 en los dos ejes, dicho en voz alta |
| subir el chip a 48 de alto | 48×48, el mínimo de verdad | engorda los chips de zona de Home y arrastra al de añadir, que debe igualarlos |

⛔ La segunda sigue siendo la correcta el día que el diseño la admita. No se descartó por mala, se
descartó por cara.

## Diseño

1. El `Box` del lápiz deja de pedir `minimumInteractiveComponentSize()` y se queda con su
   `.size(ZONE_CHIP_EDIT_TAP_DP)` de 28. El `padding(end = 4.dp)` ya no se toca: con la caja de 28
   y el glifo de 16 quedan 6 dp de aire interno, así que se ven **10 dp** a la derecha y
   `6 (chipRow) + 6 = 12` a la izquierda.
2. **El guard existente no se desactiva, aprende.** `TouchTargetGuardrailTest` exige que todo
   `.size(…)` + `.clickable(` pida el mínimo, así que este cambio lo ponía rojo — y hace bien. Gana
   una exención **nombrada con su medición**, `CAPPED_BY_ITS_CONTAINER`, que dice qué la autoriza
   (el contenedor capa el alto) y qué NO (querer verse más compacto).

## Medido en device · antes y después

Pixel_8, 1080×2400 @420 dpi, mismo escenario del Dev Catalog (zona privada sembrada por
`FakeZoneRepository`):

| | antes | después |
|---|---|---|
| ancho del chip entero | **198,9 dp** | **179,0 dp** |
| diferencia | — | **19,8 dp más corto** |

Y 19,8 es exactamente lo que predice `48 − 28`: el chip deja de reservar ancho que nadie veía. La
medición del glifo al borde da 19,4 dp antes; después no se puede leer con el mismo umbral porque
las capturas salieron en temas distintos — el ancho del chip es la cifra que compara manzanas con
manzanas, y es la que prueba el cambio.

## Criterio de éxito

1. El chip se encoge ≈ 20 dp y el lápiz deja de flotar. ✅ medido.
2. El área táctil queda en 28×34 **dicho en el código**, no por accidente. ✅
3. `TouchTargetGuardrailTest` verde con la exención y **falsificado**: al vaciar
   `CAPPED_BY_ITS_CONTAINER` canta `a hand-drawn clickable declares its touch target`. ✅
4. `./gradlew testAll` verde.
5. Captura del chip antes/después [PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001].

## Consumidores auditados

Los 9 call sites de `minimumInteractiveComponentSize()`, y **no eran siete problemas**:

| call site | overhang/lado | resultado |
|---|---|---|
| `HomeZoneChips` · lapicito | 16 dp | **cerrado** — es el que el user ve |
| `PaparcarFilterChip` · trailing | 18 dp | **no se toca: está MUERTO.** `grep onTrailingClick` fuera del propio componente devuelve **cero**. Arreglar aire que no se renderiza es trabajo que nadie puede ver — y la deuda real ahí es el parámetro sin uso, no el padding |
| `PapClearIconButton` | 13 dp | **exento**: pinta un círculo de 22 dp con fondo, así que su overhang es aire entre superficies visibles, y vive suelto en un campo de texto, no apretado dentro de un chip |
| `PapStepperButton` ×2 | 8 dp | exentos, aire normal |
| `PapSheet` ×2 · `AddingZonePeek` · `VehicleColorSelector` | 4–7 dp | exentos: todos pintan su caja (fondo o borde) a 34–40 dp |

La regla que los separa, y que vale para el próximo: **si el control pinta su caja, el overhang es
aire entre superficies y está bien; si va desnudo dentro de un contenedor apretado, el aire se mide
contra el borde y se ve.**
