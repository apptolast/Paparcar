# BRAND-THE-APPLE-SURFACES-ARE-OUTSIDE-THE-MASTER-001 · el icono de la app iOS lleva la marca DESCARTADA

**Estado:** ✅ **Done** — sin commitear, a la espera de permiso. Rama
`chore/BRAND-THE-APPLE-SURFACES-ARE-OUTSIDE-THE-MASTER-001-apple-icons` · worktree
`../Paparcar-apple-icons`

Sale de una pregunta del user (30-09-2026) tras cerrar
[WEB-THE-MARK-WEARS-ITS-DISC-ON-THE-SITE-001](web-the-mark-wears-its-disc-on-the-site-001.md):
*«¿tenemos el icono nuevo para Apple también?»*.

## Problema

**No, y no es que esté «viejo»: es la marca DESCARTADA.**

`iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png` dibuja un disco verde con
ranura y un pie — comparado píxel a píxel contra `logos/paparcar_disc_mark.svg`, **es el mismo
dibujo**. Esa es la propuesta que el user rechazó al verla en el montaje del Shipaton (*«un logo
que no tiene nada que ver»*) y que la rama `2f26f1a16` retira del repo. Lo mismo en
`iosApp/iosApp/Preview Content/…/loco_paparcar.png`.

No es una marca vieja que se quedó atrás: **nunca fue la marca**. Y es el icono que iría a
TestFlight y a la App Store.

Viene de `c28c850a8` (init de iOS), pasó por `6970b4065` (carril de release) sin tocarse, y
[BRAND-A-NEW-MARK-EARNS-ITS-SURFACES-001] nunca llegó allí.

### Por qué se escapó

`export_surfaces.py` **no escribe ni una superficie de Apple**. Escribe las 5 de `:app`, las 3 de
`composeResources`, el favicon, la doc, la web, el emblema y los dos HTML de Play. `iosApp/` está
entero fuera del sistema de *«el logo se genera»*, así que ningún cambio de marca lo alcanza y
ningún `grep` lo delata — el dibujo vive como **píxeles**.

⛔ Esto matiza el barrido de `2f26f1a16`, que concluyó *«0 consumidores en el ÁRBOL»* para
`logos/paparcar_disc_mark.*`. Cierto **del fichero**, falso **del dibujo**: sigue vivo en dos PNG.
Es el mismo punto ciego que tuvo la `og-card`. Borrar esos SVG no resucita nada, pero tampoco
arregla esto.

### Y el cabo suelto del ticket anterior

`prepare_touch_icon()` dice que el `apple-touch-icon` **es** `play-icon-512.png` redimensionado. El
commiteado no es eso: 31.832 de 32.400 píxeles distintos, con la marca bastante más grande. O sea
que **correr `prepare-assets.py` por cualquier motivo lo encoge** sin que nadie lo pida. Decisión
del user (30-09): manda **el grande**, el que está publicado, y se arregla la función.

## Doctrina violada

- ***El logo se GENERA, no se copia*** (CLAUDE.md § Iconos). Cuatro superficies de Apple — tres de
  iOS y la de la web — fuera del generador.
- ***Sistemas, no parches***: sustituir los PNG a mano dejaría el mismo agujero abierto para el
  próximo cambio de marca. El invariante es *toda superficie de marca sale del maestro*.
- **Un artefacto generado tiene que corresponder a su generador** — la mitad viva de
  [`web-a-derived-asset-must-match-its-generator-001.md`](web-a-derived-asset-must-match-its-generator-001.md),
  que este ticket cierra para el touch icon.

## Señales / datos disponibles

Todo medido, cero números inventados:

| Dato | Medido | Cómo |
|---|---|---|
| Fracción del `apple-touch-icon` **aprobado** | **0,7595** | bbox del verde 110×109 px sobre 180 |
| Sesgo del método | **0,2 %** | sobre `play-icon-512`, cuya fracción declarada (`0.51458`) se conoce: mide `0.51342` |
| Fondo del touch icon aprobado | `#0D1117` **plano** | esquinas y bordes, los cuatro |
| Fondo del icono de Play | degradado `#141918 → #0B0F0E` | `bgB` en `icon.html` |
| Fracción del icono de Play vivo | `0.51458` | celda B de `PLAY_FRACTIONS` |
| Consumidores de `loco_paparcar` | **cero** | `grep` en TODO el repo, no sólo en `iosApp/` |
| Variantes declaradas en `AppIcon` | **3** (clara, oscura, teñida) | `Contents.json`; sólo la clara tiene `filename` |

## Diseño

**Las superficies de Apple son baldosas, no emblemas.** El disco no entra aquí: una baldosa es lo
que el sistema exige opaco y luego enmascara, que es exactamente el caso que CLAUDE.md ya exceptúa
(*«no son el disco, son la baldosa»*). Lo que se comparte con el resto es el **encuadre**, no el
soporte.

`export_surfaces.py` gana una sección de Apple con un helper `tile_svg()` — una baldosa con su
fondo, su fracción y su tinta — y escribe cuatro SVG en `docs/branding/apple/`. `render_graphics.sh`
gana el target `apple`, que los rasteriza a su destino. La rasterización sigue siendo de Chrome, que
es el rasterizador del repo.

| Superficie | Fondo | Marca | Fracción | Destino |
|---|---|---|---|---|
| iOS · clara | degradado `#141918 → #0B0F0E` | `PapGreen` | `0.51458` | `AppIcon.appiconset/app-icon-1024.png` |
| iOS · oscura | **transparente** | `PapGreen` | `0.51458` | `…/app-icon-1024-dark.png` |
| iOS · teñida | **transparente** | gris claro | `0.51458` | `…/app-icon-1024-tinted.png` |
| Web · touch icon | `#0D1117` plano | `PapGreen` | `0.7595` | `web/assets/img/apple-touch-icon.png` (180) |

Decisiones y por qué:

1. **El icono de iOS es el mismo dibujo que el de Play** — mismo fondo y misma fracción. Es la misma
   app en dos tiendas; no hay razón para dos tamaños ópticos, y así no se inventa un número.
2. **Las variantes de iOS 18 van sin fondo.** El sistema pone su propio telón detrás de la oscura y
   tiñe la teñida; una baldosa propia competiría con él. Por eso ahí la marca va sola.
3. **El touch icon conserva su fracción medida (0,7595) y su fondo plano**, no los de Play: es lo
   que está publicado y lo que el user aprobó. Deja de derivarse de `play-icon-512.png`.
4. **`prepare_touch_icon()` desaparece** de `prepare-assets.py`: su trabajo se muda al generador.
   Con eso `play-icon-512.png` se queda **sin ningún lector** en `web/`, y el chequeo 6 del guard
   pasa de «exactamente uno» a «ninguno».
5. **`loco_paparcar` se borra**, no se regenera: cero referencias en todo el repo y dibuja la marca
   descartada. Un asset muerto que además miente no merece mantenerse.

## Criterio de éxito

- Los tres PNG del `AppIcon` y el `apple-touch-icon` salen del maestro, y `Contents.json` declara
  los tres ficheros (hoy dos entradas están vacías).
- El `apple-touch-icon` regenerado es **equivalente al aprobado**, no una versión encogida.
- `grep -rl "paparcar_disc_mark\|loco_paparcar"` → cero, y ningún PNG del repo dibuja ya la marca
  descartada.
- Un testigo que falsifique el defecto: hoy nada comprueba que una superficie de Apple venga del
  maestro.
- `./gradlew testAll --rerun-tasks` verde.

⚠️ **No hay Xcode aquí**, así que el icono de iOS **no se puede ver montado en un simulador** desde
esta máquina — igual que [IOS-THE-RELEASE-LANE-LANDS-UNVERIFIED-001]. Se verifica por píxeles sobre
los PNG y por la declaración del catálogo; el «se ve bien en el home screen» queda para el user.

## Consumidores auditados

| Superficie | Antes | Después |
|---|---|---|
| `AppIcon/app-icon-1024.png` | **marca descartada** | generada (clara) |
| `AppIcon` oscura y teñida | entradas vacías | generadas |
| `Preview Assets/loco_paparcar.png` | **marca descartada**, 0 referencias | **borrada** |
| `web/assets/img/apple-touch-icon.png` | marca 2026, fuera de su generador | generada, misma fracción |
| `play-icon-512.png` | 1 lector en `web/` | **0 lectores**; sólo lo usa la ficha de Play |
| `:app`, `composeResources`, favicon, emblema, Play | ya generadas | **sin cambio** |


---

## Lo que cambió sobre la marcha

**El icono que el user aprobó estaba DEFORMADO, y sólo se vio al regenerarlo.** La decisión fue
«manda el grande, el publicado», así que se midió su fracción para reproducirla: bbox del verde
110×109 px sobre 180. Al comparar el generado contra él, el ancho salió **idéntico** (110 px) pero
el alto no: 107 el generado, 109 el publicado. La geometría del maestro (244×236) exige 106,4 para
ese ancho — o sea que **el publicado está estirado ~2,4 % en vertical**, que es exactamente lo que
CLAUDE.md advierte de meter una marca full-bleed en caja cuadrada.

Se conserva por tanto el **tamaño** que el user aprobó (el ancho coincide al píxel) y se corrige la
deformación. Eso explica también por qué su área de marca medía 18,4 % y la generada mide 17,0 %:
la diferencia era el estiramiento, no el tamaño.

## Verificado

- **El icono de iOS es el mismo dibujo que el de Play, y se puede comprobar de dos maneras**: el
  `transform` generado sale idéntico al que `icon.html` tenía escrito a mano
  (`translate(29.3210 30.5126) scale(0.18369)`), y la cobertura de marca mide **7,9 %** en los dos.
- **Las variantes oscura y teñida llevan transparencia real**: 92 % del lienzo sin alfa, medido, y
  visto sobre un fondo gris a propósito.
- **Refactor probado**: `render_graphics.sh` factoriza `svg_shot()` y el emblema del ticket anterior
  sale **byte-idéntico** después. ⚠️ En el camino se coló un `REPO` dos niveles arriba en vez de
  tres, y **lo cazó el chequeo de contenido del propio target** — no el exit code, que fue 0.
- **`BrandSurfaceGuardrailTest`, falsificado en sus CUATRO casos**: desfase con el maestro, variante
  de iOS 18 sin declarar, el dibujo descartado de vuelta (24,5 % donde van 7,9 %) y la web tomando
  prestado el icono de Play.
- **Barrido del árbol por PÍXELES, no por nombre**: ningún PNG publicable queda en la banda de la
  marca descartada. Los tres aciertos son `docs/` (dos bocetos de backlog y un diagrama de medidas
  de la marca de 2026), ninguno una superficie.
- `./gradlew testAll --rerun-tasks` verde · `node web/build.mjs --check` limpio · la `og-card` sigue
  dando `b1c51d9a…`, o sea que mover el touch icon fuera de `prepare-assets.py` no la tocó.

## Lo que este ticket NO puede probar

⚠️ **No hay Xcode en esta máquina**, así que nadie ha visto el icono montado en un home screen de
iOS — ni el enmascarado a squircle, ni el comportamiento real de las variantes oscura y teñida. Se
verifica por píxeles y por la declaración del catálogo. Mismo hueco que
[IOS-THE-RELEASE-LANE-LANDS-UNVERIFIED-001], y se hereda, no se resuelve.

⚠️ Y el guard **no puede saber si alguien re-rasterizó** tras regenerar un SVG: un PNG no dice
cuándo se hizo y git no guarda fechas. La banda de cobertura sólo lo nota si el dibujo cambió de
tamaño. Es el mismo hueco que tuvieron los gráficos de Play; queda escrito, no tapado.

