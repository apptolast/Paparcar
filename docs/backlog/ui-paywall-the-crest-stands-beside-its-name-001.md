# UI-PAYWALL-THE-CREST-STANDS-BESIDE-ITS-NAME-001 · La cabecera del paywall se lee como la del login, y el metal sube por ESTRUCTURA

**Estado:** ✅ Done · mergeado a master por squash


## Problema
Dos cosas, vistas en device sobre `3ec0088a`:

1. **La cabecera se come la pantalla.** Crest de 96 dp centrado + `heroTitle` centrado + tagline
   centrada a ancho completo, todo bajo un espaciador de 64 dp: ≈ **260 dp** antes de que empiece
   la comparativa, que es el pitch de verdad. En un móvil de 360×800 eso es un tercio de la altura
   útil gastado en ceremonia.
2. **El acabado metálico se quedó corto.** Tras las cuatro revocaciones del 08-09 la superficie
   quedó calibrada por lo bajo, y en mano lee más «verde con degradado» que «metal».

## Doctrina violada
Ninguna se rompe — pero la cabecera actual argumentaba en su propio comentario que centrarse ERA
la ceremonia («ceremony is mostly space»), y la app ya tenía resuelta esa misma composición en
`PaparcarAuthHeader`, alineada a la izquierda **con el argumento contrario y mejor**
[UI-AUTH-HEADER-ALIGNS-WITH-ITS-FIELDS-001]: el contenedor dispone a sus hijos a ancho completo, así
que una cabecera centrada flota en un eje propio mientras las tarjetas, los planes y el CTA arrancan
todos en el mismo borde izquierdo. El paywall tenía ese mismo contenedor y la excepción no se
sostenía.

Y sobre el metal, la conclusión del 08-09 se aplica tal cual:
> **ESTRUCTURA ≠ AMPLITUD.** Las bandas y el grano son lo que se lee como metal; lo exagerado era
> repartirlos sobre 25 L\*.

Por tanto «más metálico pero no exagerado» **no** se toca en `Color.kt`: los tres tonos
(L\*62,0 → 70,9 → 86,1) se quedan **exactamente donde están**. Sube la estructura.

## Señales / datos disponibles
- `PaparcarAuthSlots.kt:273` ya tiene la composición pedida: logo 72 dp + `screenTitle` + `body` en
  una `Row` con `CenterVertically`.
- El tamaño del logo al lado del nombre vivía como `private val LOGO_BADGE_SIZE = 72.dp` dentro del
  fichero de auth — invisible para cualquier otra pantalla, que es justamente cómo el crest de Plus
  se inventó en su día un 60 dp propio y quedó minúsculo [UI-PLUS-WEARS-THE-BRANDS-OWN-MARK-001].
- `PapPremiumSurface` es la ÚNICA receta del metal (tarjeta, botón y crest salen de ella), así que
  subir el acabado es editar un fichero, no tres pantallas.

## Diseño

### 1 · La cabecera es la misma composición que la del login
`Row(CenterVertically)` = `PapPremiumMark(72 dp)` + `Column(Start)` con título y tagline. Alineada
al mismo borde izquierdo que todo lo demás de la pantalla.

- El rol del título pasa de `heroTitle` (28 sp) a **`screenTitle`** (24 sp): el rol lo manda la
  COMPOSICIÓN, y esta composición —marca al lado del nombre del producto— ya tiene su rol elegido en
  auth. Sigue siendo MARCA; no se elige por tamaño.
- El espaciador superior deja de ser «sitio para la ceremonia» y pasa a ser **exactamente la huella
  del botón de cerrar** (48 dp de `IconButton` + 4 dp de padding = 52). Así la cabecera no puede
  colisionar con él por construcción, sin reservar padding lateral frágil que se rompería en el
  locale que más ensancha.
- ⛔ El tamaño del logo se PROMUEVE a `PAPARCAR_LOGO_BESIDE_NAME_SIZE` en `PaparcarLogo.kt`, junto a
  `PAPARCAR_LOGO_SIZE`. Dos pantallas con la misma composición no pueden tener dos números privados:
  eso es exactamente lo que produjo el crest de 60 dp.

Coste medido en 360 dp de ancho: ≈ 260 dp → ≈ 148 dp. **~112 dp devueltos a la comparativa.**

### 2 · Más metal, misma amplitud
Todo dentro de `PapPremiumSurface.kt`. Los tres tonos no se tocan, luego el rango sigue siendo el
mismo y el contraste de `premiumInk` no puede empeorar (los stops nuevos sólo componen HACIA el
sheen, que es más claro).

- **`metallicSweep`** — se estrecha la subida a la banda especular (rampa 0,05 → 0,03; antes de ella
  una meseta plana de bed) y se le añade **una caída inmediata a bed y luego a shadow**: el contraste
  local (banda de Mach) es lo que hace que un reflejo lea como reflejo y no como degradado. Una
  tercera banda tenue completa el reparto irregular. Ni un stop fuera de `shadow/bed/sheen`.
- **`grain`** — los surcos suben de 0,05/0,035 a 0,075/0,05 de alfa. Textura, que es estructura.
- **`bevel`** — se estrechan los hombros (0,18 → 0,13 y 0,82 → 0,87) para que el filo superior
  atrape la luz en una banda más definida, en vez de subir mucho el alfa.
- **`edge`** — el filo iluminado pasa de 0,55 a 0,72 y gana una **esquina contraria tenue** (0,14):
  el metal atrapa luz en dos cantos, y ese segundo canto es lo que separa «objeto» de «tarjeta con
  borde».
- ⛔ **`SUBDUED_ALPHA` se queda en 0,20.** El peso `Subdued` (pie del historial, fila de Ajustes)
  interrumpe otra cosa; subirlo ahí es cómo se vuelve a un anuncio en medio de una lista.

### 3 · Higiene: un comentario que ya mentía
`Color.kt` decía junto a `PapPremiumSheen` que el acabado es «UNA sola luz, no las tres bandas más
grano que se revocaron». El fichero que dibuja lleva desde `3ec0088a` con las bandas Y el grano — lo
revocado fue la AMPLITUD, no la estructura. Se corrige la frase para que el siguiente lector no
retroceda por creerle.

### 4 · El escalón de la comparativa (visto en device, 09-09)
Con la cabecera ya compacta, en pantalla se ve un **escalón** entre las dos columnas. No es
estético: la tabla **emparejaba mal**.

`TierCardBody` no tiene columna de etiquetas compartida, así que **el icono de la fila N es lo ÚNICO
que dice que esas dos celdas hablan de lo mismo**. Y las listas no coincidían:

- Plus abría con una fila extra, `paywall_plus_everything` («Todo lo de Gratis», con ✓).
- Plus **no nombraba el widget**, que Free sí tiene.

Los dos errores se cancelaban en el RECUENTO (6 filas y 6 filas), que es justo por lo que nadie lo
vio: las tarjetas cuadraban de alto mientras el contenido iba desplazado. En pantalla, «Tu coche en
la pantalla de inicio» quedaba enfrente de «Zonas sin límite».

Arreglo, en dos mitades:

- **La frase sale de la tabla.** «Plus incluye todo lo de Gratis» describe la RELACIÓN entre las dos
  columnas: es la nota de la tabla, no una fila de una de ellas. Pasa a una línea `caption` justo
  bajo el `PapSectionHeader`, donde no cuesta una fila a ninguna de las dos y sigue dicha (⛔ la
  doctrina era que Plus ⊇ Free se DICE, no se infiere — se sigue diciendo).
- **Entra la fila del widget en Plus** (`paywall_plus_widget`, 9 locales). Y tiene contenido propio,
  no relleno: tener más de un coche ya ES Plus, así que el widget que puede listarlos es el de Plus
  [WIDGET-A-TRIP-IS-NOT-AN-EMPTY-WIDGET-001]. EN «All your cars on the home screen».

Queda un **6×6 exacto**: 🕐 · 🚗 · 🗺 · ▦ · 🔔 · ❤️, cada icono con su par a la misma altura.

### 5 · La frase sobraba, y la tabla pasa a emparejar por CONSTRUCCIÓN
Primero moví «Plus incluye todo lo de Gratis» a una nota bajo el encabezado. El user: *«yo creo que
se sobreentiende que Plus tiene lo de Free, no?»* — y con la tabla ya emparejada 1:1 tiene razón: la
frase existía por una doctrina («Plus ⊇ Free se DICE, no se infiere») escrita para una tabla que NO
cuadraba. Cuando cada celda de Plus está al lado de la celda de Gratis que sustituye, la tabla lo
dice mejor que la frase. `paywall_plus_everything` **borrada de los 9 locales**.

Y el riesgo residual que había anotado (una celda envuelve a 2 líneas y su pareja a 3 → el escalón
vuelve) **se cierra en vez de dejarse escrito**, porque era el mismo defecto por otra vía:

- Las dos listas paralelas (`FREE_LINES` / `PLUS_LINES`) se funden en **`TIER_ROWS`**, una lista de
  `TierRow(icon, free, plus)`. Añadir una fila a un lado y no al otro deja de ser representable.
- El layout deja de ser dos `Column` dentro de dos tarjetas: los dos lechos se pintan
  `matchParentSize` **detrás de UNA rejilla**, y cada fila es un `Row` con las dos celdas. Comparten
  altura por construcción, así que ningún envolvimiento desigual puede volver a desplazar lo de
  abajo. ⛔ Consecuencia: el contenido ya no está DENTRO de `PapPremiumSurface`, así que no hereda
  `premiumInk` — las celdas Plus lo declaran.

### 6 · Reparto vertical: la holgura va a UN sitio
En un móvil alto la pantalla se acababa y dejaba un campo muerto sobre el footer, que se lee como
algo que no ha cargado. `BoxWithConstraints` + `heightIn(min = maxHeight)` + `SpaceBetween` sobre
**dos** hijos: bloque de marca + comparativa arriba, planes abajo pegados al CTA. Sin holgura no hay
nada que repartir y la columna simplemente scrollea — una regla, los dos casos, sin rama.

⛔ **Dos hijos y no tres.** Repartir entre los tres bloques fue el primer intento y alejaba el
encabezado de sección un tercio de pantalla del crest: el bloque de marca ES el principio de la
pantalla y tiene que quedarse pegado a él. Lo que puede flotar es el precio, y flotarlo hacia su
botón dice algo cierto.

🟡 Sin tienda (`canPurchase = false`) no hay segundo bloque, así que la comparativa se queda arriba y
la holgura queda debajo. Es un estado de borde con su explicación fijada en el footer; se deja así a
propósito — inventar relleno para una pantalla que no puede vender sería peor.

### 7 · Las ruedas del logo, más anchas — en las DIEZ variantes
`strokeWidth` de las dos elipses. ⛔ No se toca un drawable: el logo es UN objeto y sus variantes no
pueden divergir.

Al barrerlas aparece que **el +2 entre familias es compensación óptica deliberada**, no deriva: el
glifo claro sobre lecho oscuro iba a 6 y el glifo oscuro sobre lecho claro a 8 (un trazo claro
"florece" sobre oscuro y parece más grueso). Se respeta subiendo las dos patas por igual:

| Familia | Antes | Ahora | Ficheros |
|---|---|---|---|
| Glifo claro | 6 | **7** | `paparcar_iconmark_white`, `_green`, `paparcar_logo`, `ic_launcher_foreground` (main y mock), `ic_logo_color`, `splash_glyph` (día) |
| Glifo oscuro | 8 | **9** | `paparcar_iconmark_forest`, `paparcar_logo_dark`, `splash_glyph` (noche) |

El primer intento fue 8/10 y en device se leyó gordo: el hueco de la elipse mide 12,3 unidades en su
eje corto, así que cada punto de trazo se le come DOS y a 8 la rueda perdía el aro. 7/9 deja el hueco
en 5,3 (frente a 6,3 del original) — se nota más ancha y sigue siendo un aro. ⚠️ El techo real está
en 10, donde el hueco se cierra: no es una escala lineal, es una resta.

⚠️ **`drawable-night/splash_glyph.xml` estaba en 6 con glifo OSCURO** — deriva real, iba con la
familia equivocada. Al pasar a 10 se cierra de paso.
⚠️ Esto cambia también el **icono del launcher y el splash**, no solo el logo dentro de la app. Es
intencionado: es el mismo logo.

### 8 · Las superficies Plus de Vehículos y Ajustes llevan el mismo metal
`SUBDUED_ALPHA` **0,20 → 0,34**. A un quinto, las bandas y el grano —lo único que hace que la
superficie lea como metal trabajado y no como un tinte verde— caían por debajo del umbral en que el
ojo los distingue: el paywall era metal y la fila de Ajustes, el pie del historial y la fila de
cifras eran un lavado plano. **Dos materiales para un producto.**

🔑 *Menos opaco no es menos metálico*, y 0,20 confundía las dos cosas. A 0,34 el barrido se lee como
acabado y la superficie sigue siendo claramente un TINTE sobre la página: tinta `onSurface`, filo
tenue, y sigue siendo un toque en el hombro dentro de una lista, no una losa de verde de marca.
Comprobado en device en claro y en oscuro sobre las tres superficies.

## Criterio de éxito
- La comparativa Free/Plus empieza visiblemente más arriba en un 360×800, y el bloque de marca lee
  como el del login.
- El metal se nota más trabajado en mano **sin** volver a las rayas de la v4.
- `:shared:testDebugUnitTest` verde (guardarraíles de tipografía y color incluidos) y
  `assembleMockDebug` + `compileProdDebugKotlin` compilan.

## Consumidores auditados
`grep` de `PapPremiumSurface(` / `PapPremiumMark` — 4 call sites, todos revisados:

| Call site | Peso | Efecto del cambio |
|---|---|---|
| `PaywallScreen.kt:333` tarjeta Plus | `Filled` | ✅ metal más marcado — es el objetivo |
| `PaywallScreen.kt` crest (`PapPremiumMark`) | `Filled` | ✅ además baja 96 → 72 dp en la composición nueva |
| `PapPremiumButton` (botón de compra) | `Filled` | ✅ mismo material, mismo cambio |
| `SettingsScreen.kt:425` · `HistoryContent.kt:440` · `VehicleStatsBlock.kt` | `Subdued` | ✅ intacto: `SUBDUED_ALPHA` no se toca, el alfa 0,20 amortigua las bandas nuevas igual que las viejas |

`PaparcarAuthSlots.kt` — único consumidor de `LOGO_BADGE_SIZE`; pasa a leer la constante compartida
con el MISMO valor (72 dp), luego el login no cambia ni un píxel.

Dev Catalog: el paywall ya tiene su `ScreenGroup` con 7 variantes en `StateGalleryScreen.kt`. Esto
es un cambio de LAYOUT y de acabado, no un estado nuevo → nada que añadir; las variantes existentes
son justamente el testigo.
