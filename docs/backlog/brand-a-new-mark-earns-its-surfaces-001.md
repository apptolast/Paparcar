# BRAND-A-NEW-MARK-EARNS-ITS-SURFACES-001 · una marca nueva no entra por ser nueva: entra superficie a superficie

**Estado:** ✅ **Done** — mergeado a master el **29-09-2026** (squash, tras rebase sobre `caa54cc2d`)
· forma elegida revisada el **24-09** · la rama `chore/BRAND-A-NEW-MARK-EARNS-ITS-SURFACES-001-logo-review`
y su worktree `../Paparcar-brand-logo` se retiran en el cierre. Lo único que queda abierto son los PNG
de la ficha de Play, que siguen siendo el render de la marca de 2022 →
`docs/backlog/play-the-listing-art-still-shows-the-2022-mark-001.md`.

> 🔄 **El user decidió (24-09): LA MARCA ES LA COMPLETA, y es la única.** Una sola marca y ningún
> umbral: la rueda derecha va en **anillo abierto**, separada del cuerpo por un canal de 10 u igual
> por los dos lados, que es el hueco que sostiene la lectura de la **P**. ⚠️ Su precio se paga
> **abajo**, y está medido: ese detalle fino mide ~1 px a los 51 y a los 24 px de icono, así que por
> debajo se cierra solo — a 24 px el dibujo se desmiga en tres trozos (uno es una miga de 6 px) y a
> 20 px ya ha perdido una rueda. Donde muerde es el small icon de notificación y los chips. La
> compacta queda como ejemplo `09-compacta`, no cableada. Y **fuera el disco**: la marca de 2022 lo
> necesitaba para sostenerse, ésta no.
>
> 🕐 **Hubo dos reglas antes de ésta, y las dos están descartadas**: (a) dos dibujos con un umbral en
> 32 dp —completa arriba, compacta abajo—, descartada el 23-09 porque obliga a cada superficie a
> saber en qué lado está; (b) la compacta para **todo** (`66cc0a840`), descartada el 24-09 al ver las
> dos formas rasterizadas a sus píxeles reales: la P aflojada se nota también a 96 y a 210 px, que es
> donde la marca se mira de verdad. Lo que sobrevive de las dos vueltas es la parte buena: **una sola
> marca, sin umbral** — sólo que la única es la completa.
>
> Ya no hay 25 copias del path: hay `docs/branding/master/export_surfaces.py`, y los drawables son
> salida suya. Verificado: `grep -rl "M92.28,57.77"` devuelve **cero** ficheros.
>
> 🔄 **Rebasada sobre master `cc2b08566`** (21-09): 7 commits replicados, **sin un solo
> conflicto** — la rama tocaba 65 ficheros y master otros 95, con intersección vacía. La única
> parada fue mecánica: el commit que metía los `.pyc` no podía aplicarse porque el exportador
> había regenerado un `__pycache__` sin trackear en el worktree. Borrarlo y `--continue`.
>
> 🔄 **Rebasada otra vez sobre master `2119f3bc7`** (21-09): 9 commits replicados, **cero
> conflictos**. Los dos commits que master había ganado viven en Room, DTOs, mappers y strings, y
> esta rama sólo toca marca, drawables, web y un Kotlin de UI: intersección vacía otra vez.
>
> 🔄 **Rebasada sobre master `bb77971db`** (24-09): 17 commits replicados, **cero conflictos** —
> intersección de ficheros vacía otra vez (master tocaba 24, la rama 81, ni uno en común). Una sola
> parada, en el sitio ya conocido: el commit que metía los `.pyc` no se puede aplicar si el
> exportador ha vuelto a crear `docs/branding/master/__pycache__` sin trackear. Borrarlo y
> `--continue`.
> - 🟢 **Y este rebase trae el arreglo del ruido de CRLF**: master llega con `.gitattributes`
>   (`* text=auto eol=lf`, de `GIT-A-CLEAN-REPO-SHOWS-ITS-WORK-001`, que lo justifica citando este
>   ticket). Regenerar los tres generadores después del rebase da **cero diferencias y cero ficheros
>   tocados sólo en el fin de línea** — el ruido que obligaba a descartar 8 SVG a mano ya no existe,
>   y con él muere el atajo que costó `462eae58e`. `__pycache__` tampoco aparece: lo cubre el
>   `.gitignore` nuevo.

## Problema

Hay una propuesta de logo nueva (`paparcar_logo_remastered`, 21-09) y la pregunta que trae no es
"¿te gusta?" sino "¿puede sustituir al actual?". El actual no vive en un sitio: vive en **21 sitios**
— cinco drawables de `:app`, cinco de `composeResources`, diez mipmaps PNG, el generador de la web,
el favicon, el icono 512 de Play, la feature graphic y una regla de `CLAUDE.md`. Cambiarlo sin haber
medido antes es garantizar que la mitad de esas superficies se quedan con el anterior, y varias de
ellas (Play, la web) ya están publicadas.

## Doctrina violada

Ninguna todavía — la tarea es precisamente no violar ninguna. Las que están en juego:

- **Nivel 3 · Ilustración/marcadores** (CLAUDE.md § Iconos): el logo trae su color, no se tiña.
- **Compensación óptica del logo** (CLAUDE.md § Iconos): grosor de rueda 6 sobre oscuro / 7 sobre
  claro. ⚠️ **La propuesta nueva no tiene NINGÚN trazo** — sus ruedas son coronas rellenas. La regla
  no se edita: se queda sin objeto y hay que reescribirla.
- **Color**: `Color(0x…)` literal prohibido y un solo verde de marca. El export viene en `#24F885`,
  que no es `PapGreen` (`#25F48C`).
- **La web es un consumidor más de la verdad del producto**: `logoMark()` de `web/lib/icons.mjs` y
  `hosting/public/favicon.svg` llevan el path del logo **embebido**, no referenciado.

## Señales / datos disponibles

Medido sobre el propio asset (rasterizado a 4–8 px por unidad y analizado por componentes conexas,
transformada de distancia y círculo mínimo envolvente). Detalle completo en
[`docs/branding/new-logo-analysis.md`](../branding/new-logo-analysis.md).

Lo que decide la tarea, en tres cifras:

> ⚠️ **Rectificado dos veces.** La primera versión de este doc decía que el lóbulo *tocaba* el
> cuerpo en una tangencia; la segunda, que sus remates tenían **anchura cero**. Las dos eran
> falsas y las dos las medí mal. Lo que sigue es lo que sale de volver a medir, y es menos grave.

1. El **lóbulo inferior derecho es una isla suelta** (5.294 u², el 17 % de la tinta) que **nunca
   toca el cuerpo**: corre a **7,26 u** de él (mínimo real, ápice del lóbulo contra el cuerpo).
   Es el arco de la media luna, y es una decisión de dibujo.
2. Sus **cuatro remates son esquinas sin radio** —69,5° · 68,9° · 85,4° · 97,1°— en una marca que
   por lo demás es toda arcos; la de 97,1° se lee **a escuadra**. Defecto de acabado, no de
   estructura. **✅ corregido** en su día en `paparcar-mark-2026-refined.svg` (borrado luego, ver § 6.2 del análisis):
   esquina más cerrada 68,9° → **147,3°**, área de tinta −0,1 %, path del lóbulo 924 → **226 car**.
3. El **detalle fino se va en bloque**: el hueco contra el cuerpo (7,3 u) y la ranura (8 u) llegan a
   1 px a los **52 px** y **47 px** de icono. Por debajo de ~48 px el lóbulo se funde con el cuerpo.
   Éste es el único límite medible de verdad. ⏳ sin decidir.
4. El dibujo es **full bleed** (bbox = los 248 × 240 completos) y su centro de masa está a
   +12,6 / +10,1 u del centro de la caja.

## Diseño

La tarea **no** sustituye nada. Introduce el asset y deja escrito qué haría falta para sustituirlo:

- `docs/branding/reference/source/` — los dos ficheros **tal cual los entregó el diseño**, byte a byte
  (sha256 verificado contra el origen). Son la evidencia de qué se revisó.
- `docs/branding/reference/paparcar-mark-2026.svg` — copia **limpia**, misma geometría coordenada a coordenada
  (verificado por raster: Δα máx 13/255, sólo en el antialias del disco). Cambia tres cosas no
  geométricas: CSS `<style>` → atributo `fill`, `#24F885` → `#25F48C`, fuera los ids de Illustrator.
- `docs/branding/reference/ic_launcher_foreground_2026.xml` — VectorDrawable **no cableado**, con el
  encuadre resuelto: el círculo mínimo envolvente centrado en (54,54) con radio exactamente 33 dp,
  la safe zone de Android. Existe para poder verlo en device sin tocar el icono de producción.
- `docs/branding/master/` — **el maestro paramétrico**, que es lo que de verdad faltaba.
  `compas.py` reconstruye el Compás desde su geometría (seis circunferencias y dos rectas tangentes)
  con **224 px de diferencia sobre 238.080** — 0,09 %, todo antialias — y pasa de 3.402 caracteres
  de path a 499. `rebuild_lobe.py` hace lo propio con el lóbulo derecho. `build_marks.py` genera los
  SVG de `mark/` y los ejemplos de `explorations/`.
- `docs/branding/mark/paparcar-mark-compas-b.svg` — **Compás B+**, el Compás con la tangencia
  resuelta, menos masa y la S. Generado, no dibujado. **No cableado.**
- `docs/branding/reference/paparcar-mark-fluida.svg` — la **Fluida** vectorizada desde su único
  original (un ráster), por `master/vectorize_fluida.py`. **80 cúbicas**, desvío máximo 0,92 u
  contra el contorno real y solape **0,962**. Sustituye al calco de 185 puntos.
- `docs/branding/README.md` — los tres nombres (Trazo · Fluida · Compás) y la estructura
  `reference/` → `master/` → `mark/` + `explorations/`.
- `docs/branding/new-logo-analysis.md` — la revisión.

El invariante que esta tarea instala es **dónde vive el máster**: `docs/branding/reference/paparcar-mark-2026.svg`.
Hoy no hay ninguno — el path del logo actual está copiado a mano en once ficheros distintos, y por eso
la compensación óptica de las ruedas tuvo que documentarse en `CLAUDE.md` como pareja de números
(6 y 7) en vez de derivarse de una fuente.

## Criterio de éxito

- ✅ Los dos ficheros originales están en el repo sin tocar y con su hash verificado.
- ✅ Existe una copia limpia cuya geometría es demostrablemente la misma.
- ✅ Existe un candidato de icono adaptativo que no lo recorta ninguna máscara de OEM.
- ✅ Está escrito, superficie a superficie, qué haría falta para sustituir.
- ✅ **Cero referencias cambiadas**: el logo actual sigue siendo el logo.
- ⏳ Decisión del user sobre las correcciones imprescindibles (§ Correcciones del análisis).

## En mano: el encuadre no era una propiedad del fichero, y por eso se perdió

Visto en el Pixel 8 tras la sustitución: **el splash salía al doble de tamaño y recortado por la
máscara circular de Android 12+ hasta ser irreconocible.**

La causa es una sola, y no está en la marca: el maestro es *full-bleed*. Su círculo mínimo
envolvente mide **293,18** sobre un lienzo de 248 — el **118 %** de su propia caja. Los iconmarks
de 2022 llevaban el glifo al ~57 % de la suya. Sustituir un path por otro «a tamaño de caja» es, por
tanto, multiplicar por **2,07** en toda superficie que no reencuadre. Yo había aplicado encuadre por
MEC sólo a `ic_launcher_foreground`, que fue justamente la única que se veía bien.

El arreglo es el que pedía la doctrina: el invariante en UN sitio. `frame(fraction)` en
`export_surfaces.py` — cada superficie declara **qué fracción de su caja ocupa el MEC** y el script
deriva viewport y traslación. Ninguna superficie vuelve a elegir un número a ojo.

| Constante | Valor | Por qué |
|---|---|---|
| `F_IN_CIRCLE` | 0,70 | la marca metida en un círculo visible — ver abajo |
| `F_SPLASH` | `F_IN_CIRCLE × 192/288` | el splash sólo enseña los 2/3 interiores de su lienzo, y en círculo |
| `ADAPTIVE_R_DP` | `F_IN_CIRCLE × 72/2` = 25,2 dp | el círculo visible del icono adaptativo mide 72 dp |
| `F_MARK` | 1,00 | los iconmarks ya no llevan disco: la marca ocupa lo que ocupaba el disco |
| `F_NOTIF` | 0,92 | su contenedor no es un círculo visible sino la insignia del sistema |

**Segundo defecto, misma raíz, en Kotlin.** El crest de Plus multiplicaba por
`GLYPH_OVERSCAN = 1.22f`, y ese 1,22 era una compensación del arte viejo — su propio KDoc lo decía:
*«the artwork only spans about 54 % of its canvas»*. Con la marca nueva la premisa muere y el 1,22
empuja la cabeza y la rueda izquierda a través del `CircleShape`, que las recorta. Pasa a
`GLYPH_FRACTION`, que lee el mismo `F_IN_CIRCLE` que el launcher y el splash, porque la pregunta es
la misma — *la marca metida en un círculo* — y tener tres respuestas es cómo se vuelve a desalinear.

Lección para el próximo cambio de marca: *el tamaño al que se dibuja un logo no es una propiedad del
fichero, es una relación entre su MEC y el hueco que lo recibe.* Un path que no declara la suya
obliga a cada consumidor a recordarla, y el que la olvide no falla al compilar.

## Tercera vuelta: una safe zone no es un objetivo de diseño

Con el encuadre ya arreglado, el user siguió viendo la marca **pegada al margen**, y tenía razón: el
primer arreglo puso las fracciones en 0,92, que es la **safe zone** de Android. Y la safe zone es un
**límite de recorte** —lo que ninguna máscara de OEM corta— **no el tamaño al que la marca debe
dibujarse**. Es el tercer número del mismo ticket heredado de un contexto que ya no existe, después
del `1.22` del crest y del lienzo full-bleed del maestro.

Medido en el propio lanzador del Pixel 8 (radio envolvente del glifo ÷ radio de la baldosa):

| | frac |
|---|---|
| Paparcar, primer arreglo | 0,917 |
| Gmail | 0,843 |
| YouTube | 0,786 |
| Photos — redondo y denso, el comparable honesto | 0,740 |
| **Paparcar, ahora** | **0,70** |
| Paparcar 2022, escala óptica conservada en `play-listing/assets/icon.html` | 0,643 |

0,70 queda **por debajo** de Photos a propósito: su molinete tiene aire entre aspa y aspa y el
nuestro no, así que a igual envolvente el nuestro pesa más.

⚠️ **Trampa al medir un lanzador**: la baldosa de Paparcar salía de radio 72 y la de todos los demás
90. No es un defecto — el lanzador **encoge el icono recién instalado** y dibuja el aro claro en el
anillo que libera. Medir ahí da 0,727 en vez de 0,917, o sea te convence de que ya estaba bien.

**Verificado en device** (`prodRelease` firmado, sha256 device↔local `71e96ea4…`, Pixel 8): launcher
medido en **0,683** con el disco respirando, splash contenido y sin recorte, small icon entero y
legible a 24 dp, crest de Plus con aire por los cuatro lados. `testAll --rerun-tasks` verde y
`node web/build.mjs --check` OK.

De paso, una trampa del propio exportador: el `print` final llevaba un emoji y reventaba con
`UnicodeEncodeError` en la consola cp1252 de Windows **después** de haber escrito todos los ficheros
— la peor forma de fallar, porque parece que no generó nada y sí generó.

## Cuarta vuelta: la tangencia cobró en alto y no se había devuelto en ancho

Lo vio el user en el render, como la tangencia: **la marca quedaba alta y estrecha**. Y no era una
impresión — bajar los lóbulos de `y = 184` a `y = 192` le dio 8 u de alto y cero de ancho, con lo que
el lienzo pasó de `248 × 240` (1,033) a `248 × 248` (1,000). La marca no adelgazó: dejó de ser más
ancha que alta, que en un dibujo tan lleno se lee igual.

La devolución es simétrica y **no lleva ni una escala**: `DX = 8` a la derecha para la **cabeza** y
la **rueda derecha**, juntas. Juntas porque comparten el borde derecho (`164+84` = `192+56`), que es
el enrase que ya había descartado la vía del `R 76`; moverlas a la vez lo conserva. **La rueda
izquierda no se toca** — es la que apoya el lienzo en `x = 0` y la que lleva el acuerdo r 34, y su
subpath sale byte por byte idéntico. Lienzo **`256 × 248`** (1,032): la relación de antes de
tangenciar.

Lo que sobrevive intacto, y es por lo que una traslación vale donde una escala no valdría: la
tangencia (`y = 168` no tiene x dentro), el hueco de la media luna (7,39 u — su arco está centrado
en la cabeza, que se movió lo mismo), el acuerdo (`r = LC.y − under_y`) y los tres anillos. Lo que se
estira es la **pata** y el **bajo del cuenco**: justo donde una P se abre.

⚠️ **Una constante sí había que reseguir, y no avisa.** `TIP_X` —la punta de la gota— fija el borde
*inferior* de la pata por tangencia al `r 50` de la cabeza. Con la cabeza 8 u más lejos y la punta
quieta en 68, la pata adelgaza sola de **33,1 a 30,8** y los anillos dejan de ser monolineales.
`TIP_X = 71` la devuelve a 33,2. *Es el mismo patrón del `1.22` del crest y de la safe zone: una
medida tomada contra una pieza que después se mueve deja de medir lo que medía.* Cuarta vez en este
ticket.

**Y el mismo patrón, una capa más abajo, en el propio exportador**: `MEC_R` y `MEC_C` estaban
**escritos a mano** (`146,5917` en `(124 · 132,1435)`). Son la relación marca↔hueco que las once
superficies leen, o sea justo lo que no puede escribirse a mano: mover un lóbulo las deja encuadrando
el dibujo anterior, en once sitios a la vez, sin que nada falle al compilar. Ahora se **derivan** —
`Compas.mec()` resuelve el círculo mínimo envolvente de las tres circunferencias y `export_surfaces`
lo pide al maestro. Validado contra el valor medido a mano: coincide en `6·10⁻⁴`.

Tres agujeros más del mismo tipo, cerrados en la misma vuelta:
- `build_marks.py` escribía los SVG **al lado del script**, y alguien tenía que copiarlos a
  donde lee el exportador. Un paso manual entre un generador y su único
  consumidor es la forma más barata de volver a tener un dibujo que nadie sabe de dónde sale. Ahora
  escribe directo, y `export_surfaces` **compara el SVG con lo que `build_marks.build()` produce
  ahora mismo** antes de exportar nada.
- `logoMark()` de `web/lib/icons.mjs` tenía los tres paths dentro, con una nota que pedía a quien
  pasara por allí que recordara regenerarlos en `docs/branding/master/`. Ahora lee
  `web/lib/mark.generated.mjs`, que escribe el exportador: no hay dónde reescribirlo.
- `play-listing/assets/{icon,feature}.html` seguían llevando, además del path viejo, **una elipse
  con trazo del logo de 2022 y un `</g>` que no cerraba nada** — restos de la sustitución. Fuera, y
  su encuadre recalculado para que el envolvente ocupe lo mismo que antes: el icono de Play no
  cambia de tamaño óptico, sólo de dibujo.

⛔ **El lienzo ya no es cuadrado.** Las superficies de Android y el favicon no se enteran porque se
encuadran por el envolvente (`frame(fraction)`, que da caja cuadrada), pero cualquiera que meta la
marca en una caja cuadrada a pelo le corta la rueda derecha o la aplasta. Por eso
`mark.generated.mjs` publica `MARK_W` y `MARK_H` junto a los paths, y `logoMark({size})` trata
`size` como **altura** y deriva el ancho.

`testAll --rerun-tasks` verde · `node web/build.mjs --check` OK · `:app:process{Prod,Mock}DebugResources`
OK (AAPT parsea los VectorDrawable nuevos). ⏳ **Sin ver en device**: el cambio es de proporción, así
que pide mirarlo en el lanzador y en el splash.

## Quinta vuelta: el hueco de la rueda derecha no era uno, eran dos

Otra vez el user mirando el render: *«el hueco entre la rueda derecha y el cuerpo es más estrecho por
un lado que por el otro»*. Medido punto a punto sobre el contorno (distancia mínima de cada punto del
lóbulo al cuerpo): **7,393 u en la media luna y 10,000 u en la ranura de la repisa**. Un **35 %** de
diferencia entre un lado y el otro de la misma rueda.

No era un descuido: son **dos piezas con historias distintas** que el ojo lee como un solo canal. La
media luna es un arco concéntrico con la cabeza —hueco constante por construcción, heredado del
dibujo original en 7,393—; la ranura son dos horizontales paralelas —`shelf_y − under_y`, que este
ticket ya había subido de 8 a 10—. Cada una era coherente consigo misma y juntas no.

Se igualan **en 10**, no en 7,39, y por dos razones medidas: 10 u aguantan hasta iconos de **~37 px**
donde 7,39 se cerraba a los **~51**, y eso cierra de paso el pendiente que este mismo ticket dejó
abierto (*«el hueco de la media luna sigue en 7,39 u»*). Verificado: el mínimo del canal pasa de
`7,393` a `10,000` y el percentil 5 con él.

⛔ **Lección de la medida, no del arreglo**: *dos piezas que el ojo lee como una sola tienen que
compartir su número, aunque cada una lo derive de su propia geometría.* Ninguna de las dos estaba
mal por separado — por eso sobrevivió a cuatro vueltas de revisión.

## Los ejemplos dejan de vivir en el historial de git

Hasta aquí, cada vuelta del dibujo existía sólo como un commit: comparar dos era un `git show` y un
visor, así que las decisiones se discutían **de memoria**. Ahora una variante es **una fila de
`VARIANTS`** en `build_marks.py` —sus parámetros y por qué existe— y todas se regeneran a la vez, con
el mismo código:

- `explorations/NN-<slug>.svg` — los seis ejemplos, numerados en orden.
- `propuesta.html` — **una sola página** (la escribe `master/dossier.py`): la propuesta, dónde va,
  los ejemplos en orden y la comparación con la Fluida. Cada cifra se mide al generarla.

## Sexta vuelta: el último pico, y una auditoría para no volver a decirlo de memoria

Decidido el ejemplo 03, el user vio lo siguiente: *«el hueco de la P que hace el pin acaba en
pico»*. Y es literal — la contraforma en gota son una base plana y **dos rectas que se cortan a
52,6°**. Era el único ángulo vivo de una marca que es toda arcos y tangencias, y se lee dos veces:
en el hueco y, invertido, en la muesca que el verde forma a su alrededor. Ahora lleva `r 10`.

⛔ **El radio no se compensa moviendo `TIP_X`.** La punta *virtual* es lo que fija el borde
**inferior** de la pata (es su tangente al `r 50`), así que correrla para recuperar los 20,2 u que
la gota pierde adelgaza la pata ≈0,8 u por cada u de punta. Se acepta la gota más corta; la pata se
queda en 33,2.

**Y «no tiene ni un pico» deja de ser una impresión.** Era una frase que había que volver a
comprobar a ojo cada vez que se tocaba un parámetro. `build_marks.py` ahora **audita** al generar:
aplana cada contorno y busca dónde el recorrido gira de golpe (> 20° en un punto). Un arco aplanado
gira uno o dos grados por segmento, así que un pico destaca por dos órdenes de magnitud.

| | 01 | 02 | 03 | **04** | 05 | 06 | 07 |
|---|---|---|---|---|---|---|---|
| esquinas vivas | 1 · 125° | 1 · 127° | 1 · 127° | **0** | 0 | 1 · 52° | 0 |

🔴 **Y la auditoría cazó un fallo mío en la primera pasada**: la variante del remate recto daba **dos
giros de 179°** — el contorno se doblaba sobre sí mismo. Había construido ese redondeo con un atajo
(*el centro va al lado del material, que es la izquierda del recorrido*) y en esa esquina salía al
revés: los dos contactos caían **pasado** el vértice. Un acuerdo entre dos rectas corta **siempre**
el sector NO reflejo, así que el centro va en la bisectriz de ese sector y los contactos caen antes
del vértice — sin razonar de qué lado está el relleno. Ahora es una sola función, `fillet_corner()`,
que usan el remate recto y la punta de la gota.

⛔ **Lo que importa no es el fallo, es que no se veía**: 3,9 u de retroceso a tamaño de icono es
menos de un píxel. Lo enseñó la medida, no el ojo, y el ojo había dado por buena esa variante en la
vuelta anterior. *Una propiedad que se afirma en una revisión visual hay que convertirla en una
comprobación, o se vuelve a perder al siguiente parámetro.*

## El remate RECTO de la Fluida (ejemplo 05, sin elegir)

Petición del user: llevarse de la Fluida **lo único que el Compás no le había copiado**. En el
Compás la rueda de abajo se funde en curva con el bajo del cuenco (acuerdo `r 34`, la «S»); en la
Fluida el bajo **corta la rueda con una recta y gira en esquina**, y eso es lo que la hace leer como
la panza de una P.

Medido sobre el contorno vectorizado de la Fluida (índices 950–1130 del contorno exterior): un tramo
**recto de 52 u a ≈56°**, y luego la horizontal del bajo, plana hasta `x ≈ 180`. No es una curva de
poca curvatura: es una recta.

Aquí no se copia el ángulo, se copia la **regla**: la recta se pide **tangente al lóbulo** y con el
**mismo apoyo en el bajo** que tenía el acuerdo (`x = 145,443`). Con esas dos condiciones quedan
determinados la recta y su punto de contacto —no hay ángulo que elegir a ojo— y salen **52,2°**, a
cuatro grados de la referencia.

- ⛔ **La tangencia no es acabado, es topología**: una recta que corte el círculo sin ser tangente
  deja un pico donde la rueda se encuentra con ella, y ese pico no se ve hasta que se amplía.
- ⛔ **El apoyo se deja quieto a propósito**: cambiar de acuerdo a recta tiene que cambiar la *curva*,
  no *dónde apoya*. Moviendo las dos cosas la comparación ya no dice cuál mirabas.
- ⛔ **Y no es un remate, es un cambio de carácter**: la rueda izquierda pierde su cuadrante superior
  derecho. Por eso entra como ejemplo y no como corrección.

**Una trampa que casi me hace juzgar mal la compacta**: mi rasterizador metía los tres `<path>` en un
solo recorrido *nonzero* y la dibujaba rota. Un visor SVG aplica la regla **dentro** de un path, no
**entre** paths: dos paths que se solapan se pintan uno encima del otro, no se cancelan — y la
compacta se apoya justo en ese solape («se unen por pintura, sin booleanas»). *Antes de llamar
defecto a lo que enseña una herramienta propia, comprobar que la herramienta dibuja lo que dibujaría
el consumidor real.*

**Y los dos HTML de Play pasan a generarse.** Iban a mano, y a la tercera edición seguida quedó claro
qué eran: el mismo agujero que el ticket existe para tapar, en un fichero que nadie asocia a la
marca. Ahora `export_surfaces.py` reescribe **sólo el grupo del glifo** (`write_play()`), con el
encuadre derivado del maestro. ⛔ Las fracciones (0,429 / 0,515 / 0,529 del lienzo 108) **no son
nuevas**: son las que esos gráficos ya tenían, conservadas a propósito porque el icono de Play está
publicado y esto cambia el dibujo, no su tamaño óptico.

## Consumidores auditados

Barrido completo (`grep` de `paparcar_logo|paparcar_iconmark|ic_launcher|splash_glyph|ic_logo_color|ic_notification_logo|logoMark`).
Ninguno tocado en esta tarea — la tabla existe para que la sustitución, si llega, no se deje ninguno.

> ⚠️ **El dato que manda**: `grep -rl "M92.28,57.77"` devuelve **25 ficheros** con el path del logo
> actual literalmente escrito dentro. 15 son fuentes (6 VectorDrawable de `:app`, 5 de
> `composeResources`, `icon.html` y `feature.html` de Play, `favicon.svg`, `web/lib/icons.mjs` y
> `docs/assets/paparcar-logo.svg`); los otros 10 son páginas generadas en `hosting/public` y salen
> solas al reconstruir la web. **No hay máster.** Esa, y no el dibujo, es la deuda real.

| # | Fichero | Qué es | Estado |
|---|---|---|---|
| 1 | `app/…/drawable/ic_launcher_foreground.xml` | foreground adaptativo + capa `monochrome` | exento (sin tocar) |
| 2 | `app/…/drawable/ic_launcher_background.xml` | fondo adaptativo (degradado Ink) | no depende del glifo |
| 3 | `app/…/drawable/splash_glyph.xml` | splash día (disco horneado) | exento |
| 4 | `app/…/drawable-night/splash_glyph.xml` | splash noche (invertido) | exento |
| 5 | `app/…/drawable/ic_notification_logo.xml` | small icon monocromo, **knockout `evenOdd`** · 17 usos | exento |
| 6 | `app/…/drawable/ic_logo_color.xml` | large icon de notificación (disco azul 2022) | exento — ⚠️ ya está desalineado con la marca actual |
| 7 | `app/…/mipmap-{m,h,x,xx,xxx}dpi/ic_launcher.png` | 5 PNG legacy (48→192) | exento |
| 8 | `app/…/mipmap-{…}/ic_launcher_round.png` | 5 PNG legacy redondos | exento |
| 9 | `app/src/mock/res/drawable/ic_launcher_foreground.xml` | icono del Dev Catalog (violeta) | exento |
| 10 | `shared/…/composeResources/drawable/paparcar_logo.xml` | logo con disco Ink | exento |
| 11 | `…/paparcar_logo_dark.xml` | logo con disco neón | exento |
| 12 | `…/paparcar_iconmark_green.xml` | glifo suelto, tema oscuro | exento |
| 13 | `…/paparcar_iconmark_forest.xml` | glifo suelto, tema claro | exento |
| 14 | `…/paparcar_iconmark_white.xml` | glifo **tintable** (`PaparcarMark`, cresta Plus) | exento |
| 15 | `web/lib/icons.mjs` → `logoMark()` | SVG **embebido** + el par de trazos 6/7 | exento |
| 16 | `hosting/public/favicon.svg` | favicon (SVG embebido) | exento |
| 17 | `web/assets/img/apple-touch-icon.png` | + su copia con hash en `hosting/public` | exento |
| 18 | `web/assets/img/og-card{,-small}.jpg` | tarjetas de compartición | exento |
| 19 | `docs/release/play-listing/assets/play-icon-512.png` + `icon.html` + 2 alternativas | icono de Play | exento |
| 20 | `docs/release/play-listing/assets/*feature-graphic-1024x500.png` + `feature.html` | 11 feature graphics (9 locales + base + alternativa) | exento |
| 21 | `docs/assets/paparcar-logo.svg` | logo en la documentación | exento |
| 22 | `CLAUDE.md` § Iconos · *Compensación Óptica del Logo* | la regla 6/7 | ⚠️ **queda sin objeto** si entra la marca nueva: no hay trazos que compensar |
| 23 | `README.md` línea 59 | apunta a `paparcar_logo.xml` como fuente de verdad | ⚠️ pasaría a apuntar al máster de `docs/branding/` |

Fuera del barrido por no depender del glifo: `ic_pap_car.xml`, `ic_marker_my_vehicle.xml`,
`ic_widget_*.xml`, `ic_notification_debug.xml`.

## La vuelta a la completa (24-09)

La decisión se tomó **mirando**: las dos formas enfrentadas a 48 px sobre claro y sobre oscuro, y las
dos rasterizadas a 48 y a 24 píxeles reales, ampliadas sin suavizar.

🔑 **Sobre el vector esto no se puede decidir.** A 48 CSS px en una pantalla densa el navegador pinta
96 o 144 píxeles físicos, y eso favorece a la completa mucho más de lo que la favorece un icono de
48 dp, que el ojo ve a ~7,6 mm. La comparación honesta es la del píxel; el vector sirve para ver el
dibujo, no para juzgar si sobrevive.

Qué cambió — todo por **regeneración**, ni un path a mano:

| Pieza | Antes (compacta) | Ahora (completa) |
|---|---|---|
| `CHOSEN` de `build_marks.py` | `spec(lob_y=196, tip_x=77, r_cntr=46, r_hole=28, right='ring')` | `spec()`, los valores por defecto |
| `CHOSEN_SLUG` | `compacta` (ejemplo 09) | `punta` (ejemplo 04) |
| Rueda derecha | anillo entero fusionado | anillo abierto · canal 10 u |
| Anillos (cabeza / rueda / pata) | 38 / 28 / 34,2 | 34 / 32 / 33,2 |
| Agujeros · contraforma | r 28 · r 46 | r 24 · r 50 |
| Lienzo · envolvente | 256 × 252 · r 150,82 en (128 · 134,29) | 256 × 248 · r 149,44 en (128 · 132,44) |
| Esquinas vivas | 0 | 0 |

⛔ **Y al hacerlo salió un comentario que mentía en once ficheros.** `export_surfaces.py` escribía el
nombre de cada path —*el ojo del pin*, *la rueda derecha*, *el cuerpo*— desde una **tupla fija**,
pero el orden en que se pintan depende de la forma: la compacta pinta el cuerpo primero y el ojo al
final, porque las piezas se unen por pintura. Desde `66cc0a840` los once ficheros generados llamaban
«el ojo del pin» al cuerpo, y no falló nada: **un comentario no se ejecuta**. Arreglado donde vive el
invariante, no donde mordió — `build_marks.path_names()` devuelve los nombres en el orden en que
`paths_of()` devuelve los paths, y el consumidor ya no elige.

⚠️ **El ruido de CRLF volvió a aparecer, y es el mismo que ya costó `462eae58e`.** Regenerar en
Windows deja ~8 SVG de `explorations/` modificados **sólo** en el fin de línea. Se descartan uno a
uno tras comprobar con `git diff -w --ignore-cr-at-eol` que no llevan nada dentro — nunca por
carpeta, que es exactamente lo que aquella vez revirtió el favicon y las nueve páginas de la web sin
que nada fallara.

## Follow-ups deliberadamente fuera de alcance

- **La decisión de marca** (¿se cambia o no?) es del user. El análisis da argumentos, no un veredicto.
- **Si el afilado del anillo es intencionado**, los remates sobran: no serían un defecto, serían una
  firma. Eso lo decide quien dibujó el logo, no este ticket.
- **El hueco de la media luna sigue en 7,39 u** (1 px a los ~51 px de icono). Ensancharlo pide abrir
  la media luna, que adelgaza el lóbulo. Decisión pendiente.
- **Vectorizar la Fluida de verdad**, si interesa: lo que hay es un calco de su ráster, con 185
  puntos repartidos por el trazador. Redibujarla con regla es, literalmente, lo que ya hace el Compás.
- **La cabeza más pequeña** (R 76) queda descartada por el enrase, pero la idea de aligerar la P
  bajando `R_HEAD` sigue disponible si se acepta mover también el lóbulo derecho.
- ⏳ **Los PNG de Play siguen siendo los de la marca de 2022.** `icon.html` y `feature.html` ya
  llevan la marca nueva (su grupo del glifo lo reescribe `export_surfaces.py`), pero
  `play-icon-512.png`, los feature graphics —base, 9 locales y las alternativas— **no se han vuelto a
  renderizar en ninguna vuelta de este ticket**: siguen siendo el render de `PLAY-LISTING-001`. Se
  re-renderizan con Chrome headless (comando en `docs/release/play-listing/README.md`) y hay que
  hacerlo antes de volver a tocar la ficha de Play.
- Si el cambio se aprueba, abrir un ticket **por superficie** o, mejor, uno solo que genere las 21
  desde el máster con un script, que es lo único que evita volver a tener once copias del mismo path.

## Rebase del 25-09-2026

Rebaseada sobre `4b90ae506` (master del 25-09), **18 commits, behind:0**. Dos vueltas: la primera
sobre `c8ba40f51` y, al terminar, master ya había ganado `4b90ae506` (el mundo mock a Dublín, que no
toca marca ni web) — la segunda fue limpia.

- **Un solo conflicto en las dos vueltas**: `hosting/public/assets/manifest.json`, el mapa de hashes
  de contenido de la web. Es **salida del generador**, y ahí resolver a mano no significa nada: se
  tomó un lado para desatascar y se **regeneró después** con `node web/build.mjs` (`--check` OK).
  Cambian de paso los hashes de las dos hojas de estilo y las siete páginas que las referencian.
  ⛔ Es la otra cara de `462eae58e`: un fichero generado no se resuelve leyendo el conflicto, se
  resuelve ejecutando lo que lo genera.
- La intersección de ficheros dejó de ser vacía por primera vez (11 ficheros), y **todos menos el
  README son salida de la web**: master traía `COPY-THE-APP-NEVER-SAYS-WHAT-IT-IS-FOR-001`.
- ⚠️ Volvió a saltar la parada conocida: `__pycache__` de `docs/branding/master` bloqueando el
  replay. Se borra antes de `--continue`.
- Verificado **después** del rebase, que es cuando vale: `./gradlew testAll` verde,
  `:app:compileMockDebugKotlin` y `:app:compileProdDebugKotlin` OK, y regenerar los cuatro
  generadores no mueve un byte.

## Rebase del 29-09-2026 (el previo al merge)

Rebaseada sobre `caa54cc2d` (master del 28-09), **21 commits, behind:0, cero conflictos**. Los 12
commits que master había ganado desde `4b90ae506` no tocan **ni un byte** de `web/` ni de
`hosting/` — intersección vacía otra vez, así que no hubo que regenerar nada para desatascar
(`node web/build.mjs --check` OK después, sin diferencias).

- ⚠️ Tercera vez que la parada es la misma: `__pycache__` de `docs/branding/master` **sin trackear**
  bloqueando el replay de `23a124687` (el commit que los metió; `9b16ab448` los saca dos commits
  después). Se borran y `--continue`. No es un conflicto, es git negándose a sobrescribir ficheros
  sin trackear.
- 🟢 **El ruido de CRLF ya no engaña, pero sigue ahí**: los tres ficheros de
  `hosting/public/assets/` que `build.mjs` escribe con CRLF salen como `M` en `git status` con el
  diff **vacío**. Se comprobó byte a byte (`git show :f | tr -d '\r'` vs. el disco: hashes
  idénticos) antes de tocarlos — que es lo único que distingue este caso del de `b0a1f4c95`, donde
  el mismo atajo revirtió salida de verdad.
- Entra además, commiteado al empezar el rebase, el arreglo de la **sombra del logo en Auth**: sin
  disco, `Modifier.shadow(6.dp, CircleShape)` pintaba una baldosa redonda fantasma **y** recortaba
  la marca full-bleed contra ese círculo (`clip` va a `true` con `elevation > 0.dp`). Único call
  site de `PaparcarLogo` fuera del componente, así que no había más consumidores que barrer.
