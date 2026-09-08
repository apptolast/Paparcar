# UI-PLUS-A-BRAND-FILL-IS-NOT-A-METALLIC-FINISH-001 · El card de Plus grita el verde de marca y no tiene brillo

**Estado:** ✅ Done · rama `bugfix/UI-PLUS-A-BRAND-FILL-IS-NOT-A-METALLIC-FINISH-001-metallic-finish`

## Problema

Tercera revocación en device del acabado de Plus (07-09, Oppo, tema oscuro). Literal del user:

> «es demasiado cantoso el verde y apenas se le nota el brillo metálico»

Las **dos quejas son el mismo defecto**, y eso es lo que las tres iteraciones anteriores no vieron:
cada una trató una y empeoró la otra.

Medido sobre los tokens en master (`8af5c8db`):

| | valor | lectura |
|---|---|---|
| `PapPremiumDeep` `#16BC70` | L\*67.5 · C\*62.9 | |
| `PapPremiumSheen` = `PapGreen` `#25F48C` | L\*85.5 · C\*78.9 | el verde **neón** de marca |
| Recorrido L\* dentro del card | **18.0** | y todo él entre 67 y 85 |
| Croma media del barrido | **70.9** | casi el máximo de sRGB |
| `PapInkHigh` (el card normal de al lado) | L\*13.2 | |

1. **«Cantoso»** — `PapGreen` está calibrado como **acento**: un botón, un glifo, un borde de 2 dp.
   La croma que resulta enérgica en 200 px² resulta chillona en un card de ~20 000 px². El área
   amplifica la saturación. Estábamos poniendo una losa de L\*76 y croma 71 sobre una página de
   L\*13.
2. **«Apenas se nota el brillo»** — consecuencia directa de lo anterior. El lecho ya vive pegado al
   techo de luminosidad, así que **no queda headroom por encima para una banda especular**. Un
   reflejo es, por definición, más claro que lo que lo rodea; sobre un lecho a L\*85 no existe. Los
   18 L\* de recorrido, además, son un degradado suave, no un reflejo.

> 🔑 **No se puede tener un reflejo sobre una superficie que ya está en su máximo.**

## Doctrina violada

Ninguna regla escrita — y ese es el hallazgo. `UI-COLOR-DOCTRINE-001` gobierna **qué hue** significa
qué, y el guardarraíl `the premium sweep keeps the brand's hue in both themes` vigila justamente eso
(≤7° de la marca). El barrido lo cumplía a la perfección: 0,2° de desvío.

La doctrina de color **no dice nada sobre croma ni sobre área**, y por ahí se coló: un token puede
tener el hue correcto, pasar el guardarraíl y aun así ser el color equivocado para la superficie que
va a cubrir. Se añade esa fila al doc de color.

## Diseño

**La tinta oscura se queda, y eso es lo que hace posible todo lo demás.** Con contenido oscuro sobre
verde, una banda más clara solo puede **mejorar** el contraste — nunca pelearse con el texto. Por eso
el reflejo puede ser de verdad brillante. (Con tinta blanca, cada highlight sería una amenaza a la
legibilidad, que es la trampa en la que caería la solución "lecho oscuro".)

Dos ejes, medidos, con el hue de marca intacto:

- **Bajar la croma** 71 → 55: el lecho pasa de neón a verde marca *cepillado*. Ataca «cantoso»
  directamente, que es un problema de saturación mucho más que de luminosidad.
- **Bajar el lecho y abrir el recorrido** 18 → 28 L\*: deja sitio arriba para el reflejo.

Tres paradas por tema en vez de dos (sombra · lecho · reflejo), y el reflejo es una **banda
estrecha** en diagonal, no una parada más de un degradado suave — un degradado monótono lee como
"card con gradiente", un reflejo estrecho lee como metal.

### Reparto de responsabilidades

- `ui/theme/Color.kt` — **qué verdes** son (los tres tokens por tema) y su resolución por tema
  (`premiumSweep`). Es donde vive el color, como manda `UI-COLOR-THE-RAMP-HAS-ONE-RESOLVER-001`.
- `ui/components/PapPremiumSurface.kt` — **qué forma tiene el reflejo** (dónde cae la banda, cómo de
  estrecha, en qué diagonal). El acabado es propiedad de la superficie, no de la paleta.

### Valores (calculados en Lab, no a ojo)

Tema oscuro — tinta `onPrimary` oscura:

| parada | hex | L\* | C\* | contraste tinta | desvío hue |
|---|---|---|---|---|---|
| sombra | `#318F58` | 53.0 | 45.9 | 4.69:1 | 0.2° |
| lecho | `#3BA365` | 59.9 | 50.2 | 5.96:1 | 0.1° |
| reflejo | `#40E489` | 80.9 | 69.8 | 11.42:1 | 0.1° |

Tema claro — tinta blanca; aquí el **reflejo es la restricción** (es la parada más clara y la blanca
tiene que sobrevivir sobre ella):

| parada | hex | L\* | C\* | contraste blanco | desvío hue |
|---|---|---|---|---|---|
| sombra | `#005225` | 29.8 | 39.2 | 9.40:1 | 5.6° |
| lecho | `#006936` | 38.5 | 44.5 | 6.83:1 | 3.3° |
| reflejo | `#00864E` | 49.0 | 49.9 | 4.65:1 | 0.2° |

El desvío de hue en las paradas oscuras del tema claro viene de que la croma pedida se sale del
gamut sRGB a esa luminosidad y se recorta; sigue dentro del límite de 7°.

## Criterio de éxito

- Contraste de la tinta ≥4.5:1 en **la parada peor** de cada tema (no en la media).
- Croma media del barrido claramente por debajo de la del acento de marca.
- Recorrido L\* dentro del card ≥25 en oscuro, con la banda del reflejo estrecha.
- El guardarraíl de hue (≤7°) sigue verde, ahora también sobre las paradas nuevas.
- Verificado **en device**, no en preview: es donde han muerto las tres versiones anteriores.

## Consumidores auditados

`grep` de todo lo que nombra la superficie o sus tokens:

| Sitio | Estado |
|---|---|
| `presentation/settings/SettingsScreen.kt` | ✅ cubierto — pasa por `PapPremiumSurface`, se movió solo |
| `presentation/vehicles/HistoryContent.kt` | ✅ cubierto + **recompuesto** (era el que peor se veía) |
| `presentation/paywall/PaywallScreen.kt` | ✅ cubierto (hero) + arreglada la alineación de las filas |
| `architecture/ColorGuardrailTest.kt` | ✅ actualizado: paradas nuevas + guardarraíl nuevo |
| `ui/theme/Color.kt`, `ui/components/PapPremiumSurface.kt` | ✅ es donde vive el arreglo |

**Nadie más nombra los tokens premium.** Que los tres consumidores se arreglaran editando dos
ficheros es la receta única de `PapPremiumSurface` funcionando: la razón de que exista era
exactamente esta: que un cambio de acabado no fuese tres cambios.

## Resultado

Verificado en device (Oppo, tema oscuro, `assembleMockDebug`), que es donde murieron las tres
versiones anteriores:

- El card ya no grita: croma media 70,9 → 55,3.
- La banda especular se ve — recorrido 18 → 28 L\*, y estrecha en lugar de un fundido.
- Historial recompuesto en tres bandas (cabecera · explicación · acción) con el CTA a ancho
  completo, en vez del botón colgando de un párrafo.
- Paywall: «Gratis:» y «Plus:» comparten borde izquierdo y el ✓ cae en la primera línea.

Suite: 2.286 tests, 0 fallos. `prodDebug` y `mockDebug` compilan.

### Lo que el guardarraíl nuevo cazó de inmediato

Al primer intento el tema CLARO falló: recorrido de 19 L\* contra el mínimo de 22, porque la tinta
blanca topa el brillo en L\*49. Se arregló bajando la SOMBRA clara (L\*29,8 → 25,3), no relajando el
mínimo — al oscurecer, el contraste con blanco solo mejora. El guardarraíl hizo su trabajo el
mismo día en que se escribió.

**Estado:** ✅ Done

---

## Epílogo · UI-PLUS-THE-METAL-IS-AN-OBJECT-NOT-A-BACKGROUND-001 (mismo turno)

Cuarta revocación en device, y la última: **«en tema claro chirría bastante»**.

Medido: en oscuro el metal queda **+55 L\*** por encima de la página; en claro quedaba **−59 L\***
por debajo. En un tema es un objeto iluminado, en el otro un agujero en sombra. El metal es
brillante porque REFLEJA.

🔑 **La causa de raíz era mía y de una línea**: `PapPremiumSurface` tomaba su tinta de
`colorScheme.onPrimary`, que en tema claro es **blanca**. Con tinta blanca el lecho *tiene* que ser
oscuro. Doblé el lecho para encajar una tinta PRESTADA, en vez de darle a la superficie su tinta.

Arreglo, en tres piezas:

1. **Un solo metal para los dos temas.** Una pierna de tema existe para que un color siga siendo
   legible contra la superficie de detrás; esta superficie es opaca y trae su propia tinta, así que
   no tiene contra qué. Es un OBJETO, no un fondo. Se eliminaron los tres tokens claros.
2. **`premiumInk`** — rol nuevo, tinta oscura en ambos temas. Barridos los 18 sitios que leían
   `onPrimary` en superficie premium (4 ficheros).
3. **`premiumEdge` sí cambia con el tema**, y por física: blanco es la esquina iluminada sobre
   fondo oscuro, pero sobre papel blanco un filo blanco es invisible y deja el card sin contorno →
   allí es el tono de sombra del propio barrido.

Valores nuevos, calibrados contra el tono que pidió el user y **con el brillo a 0,1° del verde del
LOGO**: `#36AA67` → `#44C479` → `#63F49D` (L\*62 → 71 → 87, croma del lecho 58,8).

⚠️ Se cambió también el comparador del guardarraíl de croma: antes cada tema se medía contra su
propia pierna de marca, y la clara (`PapGreenLight`) es un verde profundo cuya croma está limitada
por el gamut a esa luminosidad. Medir un lecho de luminosidad media contra ella era comparar croma
a dos luminosidades distintas. Ahora ambos se miden contra el verde del logo, que es el que el
usuario reconoce y el que no se invierte con el tema.

Verificado en device en LOS DOS temas.

### Corolario · la quinta pasada, y la conclusión única

«No hace falta exagerar y meter tantos claro-oscuro». Cierto: perseguir metal cepillado *real*
(tres bandas + grano, 25 L\*) funciona en una chapa fotografiada y a tamaño de card se lee como
RAYAS. Fuera el grano, una sola banda suave, recorrido 13 L\*.

> 🔑 **Conclusión única, tras CUATRO revocaciones en device:** una superficie de verde de marca
> **calmada**, con **UN** brillo suave en diagonal, **tinta oscura**, e **idéntica en los dos temas**.
>
> Cada intento falló por exceso en un eje distinto, y el patrón solo se ve poniéndolos en fila:
> v1 se pasó de **hue**, v2 de **oscuridad**, v3 de **croma**, v4 de **textura**. Lo que queda al
> quitarle el exceso a las cuatro es una card verde tranquila con un reflejo.

⚠️ Se BAJÓ el mínimo de recorrido del guardarraíl (22 → 10 L\*) que yo mismo había escrito. Se deja
dicho por qué: el bug original nunca fue «poco contraste dentro del card», fue **cero headroom por
encima del lecho**. Un recorrido grande era UNA manera de tener headroom, y resultó ser la que se ve
mal a este tamaño. El guard defiende ahora que el brillo pueda EXISTIR (parada iluminada por encima
del lecho, comprobación nueva), no que sea dramático. La guarda de croma no se tocó.

### Sexta pasada · el hallazgo que cierra las cuatro revocaciones

«El efecto que metimos antes, pero con este color y sin tantos tonos oscuros y claros.»

> 🔑 **ESTRUCTURA y AMPLITUD son perillas distintas, y solo una fue nunca el problema.**

Las muchas bandas irregulares + el grano son lo que el ojo lee como superficie de metal trabajada.
Lo que hacía que la versión revocada pareciera exagerada **no era tenerlas**: era repartirlas sobre
**25 L\***. Comprimidas a **11 L\*** alrededor del lecho, la misma estructura se lee como acabado en
vez de como rayas.

Mi error intermedio fue confundir las dos perillas: al quitar las bandas (un único brillo suave)
quité las rayas **y el metal con ellas** — de ahí que hicieran falta dos pasadas más. Cuando algo
"se pasa", la pregunta correcta es *qué eje se pasó*, no *qué elemento quito*.

Valores finales: `#39B66E` → `#44C479` → `#64D48D` (L\*66 → 71 → 77), estructura completa
(bandas + grano + bisel), misma en los dos temas.

### Intento descartado · un solo brillo nítido y desaturado

La versión calmada quedó demasiado plana. El punto medio NO está en meter más claroscuro, sino en
la FORMA de la única banda — tres cambios, ninguno de ellos "más":

1. **Estrecha y nítida** (.44–.56 en vez de un fundido ancho). Un desvanecido amplio es un
   degradado; la luz rozando una superficie tiene borde.
2. **Asimétrica** (.30 → pico → .70): sube más rápido de lo que cae, como el filo especular real.
3. 🔑 **El pico DESATURA** — +15 L\* pero **13 de croma MENOS** que el lecho. Un reflejo tiende al
   color de la LUZ, así que un pico menos saturado se lee como luz cayendo sobre verde, mientras
   que uno más saturado solo se lee como «verde más verde». Yo lo tenía al revés, y ese solo
   cambio es la mayor parte de lo que faltaba.

`#32AA66` → `#44C479` → `#8EEBAD`. Sigue siendo UNA banda, sin grano.

---

## Séptima pasada · UI-PLUS-EMPHASIS-MATCHES-THE-MOMENT-001

«La card de abajo del historial es bastante cantosa: menos opaca, menos cantosa, menos grande y
mejor estructurada.»

> 🔑 **El eje que le faltaba al acabado no era otro color: era el PESO.**

Las revocaciones anteriores intentaron arreglar «demasiado» cambiando el COLOR, y el color no era el
problema — el mismo verde está bien en el paywall. Lo que estaba mal es que una card cuyo trabajo es
**darte un toque en el hombro mientras lees una lista de aparcamientos** llevaba el peso de una card
cuyo trabajo es **cerrar una venta**, y se comía un tercio de la pantalla haciéndolo.

`PapPremiumEmphasis`, dos pesos del MISMO material:

| | Dónde | Lecho | Tinta |
|---|---|---|---|
| `Filled` | Paywall: tier Plus, botón de compra, escudo | metal al 100 % | oscura (`premiumInk`) |
| `Subdued` | Pie del historial **y fila de Ajustes** | mismo barrido al **20 %**, como tinte sobre la página | la de la página (`onSurface`) |

⚠️ Los dos pesos necesitan tintas DISTINTAS y por eso la resuelve el componente: el lecho relleno es
verde vivo y pide tinta oscura; el atenuado es un tinte sobre la página y pide la `onSurface` de la
página, como cualquier otra card. Un call site no puede equivocarse porque no la elige.

El pie pasa de tres bandas a **una fila del tamaño de la lista**, y **toda la card es el toque** —
eso es lo que permite que el CTA baje de botón relleno a ancho completo a una línea de texto con
chevron: la card ya dice «esto se pulsa», el botón dentro lo decía dos veces. En tinte el medallón
vuelve a verde marca: aquí no hay lecho relleno contra el que desaparecer.

### La fila de Ajustes, al mismo peso

«En Settings tendría que ser igual de jerárquico que en Vehicles» / «el diseño de Settings es muy
llamativo». Correcto y por la misma razón: **es el mismo momento**. Una fila dentro de una lista que
estás recorriendo, no una pantalla a la que has venido a decidir. Un bloque relleno de metal de
marca entre la card de cuenta y los ajustes de detección se leía como un anuncio encajado.

Pasa a `Subdued` igual que el pie: medallón en verde marca (en tinte no hay lecho contra el que
desaparecer), título y subtítulo con la tinta de la página como cualquier otra fila de Ajustes, y
`PapNavChevron` en vez de un chevron a mano. El badge «ACTIVO» pasa a verde marca.

📌 Regla que queda: **`Filled` solo en el paywall**. Es la única pantalla cuyo trabajo ES el
producto; en cualquier lista, `Subdued`.

### Borrado limpio

`PapPremiumCta` quedó huérfano al desaparecer el botón sobre metal → retirado, junto con el string
`history_plus_footer_hint` en los 9 locales.

### Verificado en DOS dispositivos y DOS temas (08-09)

Oppo CPH2371 (ES, 1080×2400) y emulador Pixel 8 Pro (EN, 1344×2992), mismo APK — sha256 idéntico en
los dos. Ambos arrancan sin crash y la fila atenuada se comporta igual en oscuro y en claro.

El Pixel corre en **inglés**, así que de paso valida la base `values`: el copy corto de las cards
(«The last 30 days» / «Your full history») entra sin envolver en un móvil ancho, y la fila del pie
cabe en UNA línea de título («You have 63 earlier parkings»).
