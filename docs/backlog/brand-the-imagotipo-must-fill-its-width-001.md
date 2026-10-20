# BRAND-THE-IMAGOTIPO-MUST-FILL-ITS-WIDTH-001 · el imagotipo no se alineaba, por dos razones encadenadas

**Estado:** ✅ Done

## Problema

Lo ve el user en la pantalla de login: *«el imagotipo se ve muy pequeño y no está alineado con el
subtítulo»*. **Son el mismo bug**, y se mide:

| | dónde empieza |
|---|---|
| El imagotipo | **56 dp** |
| El subtítulo «Find your spot, share your space» | **21 dp** |
| Diferencia | **35 dp** |

Y 35 dp es exactamente `(240 − 170) / 2`: el ancho pedido menos el ancho **intrínseco** del
drawable, partido por dos. El imagotipo no se estaba escalando a 240 dp — se dibujaba a su tamaño
nativo de 170 dp y se **centraba** dentro de la caja. Por eso se ve pequeño *y* por eso aparece
indentado respecto a todo lo demás.

## Causa

```kotlin
Image(painter = painterResource(resource), modifier = modifier.width(width))
```

`Modifier.width(240.dp)` fija el ancho, pero deja el **alto** sin restringir, así que el nodo
conserva el alto intrínseco del VectorDrawable: 31 dp. El `ContentScale.Fit` por defecto escala al
**mínimo** de las dos razones — `min(240/170, 31/31) = 1,0` — o sea, no escala. Y `Alignment.Center`,
también por defecto, reparte el sobrante a los dos lados.

Fijar un solo lado de una caja NO define la caja. Es la misma trampa que la doctrina ya nombra para
el otro lado —*«pedirle un `size` cuadrado es como se aplasta un logo»*— y que yo escribí en
`PaparcarImagotipo` pensando que fijar el ancho bastaba.

## Doctrina violada

[BRAND-THE-MARK-GAINS-ITS-WORDMARK-001], su propio punto: *«su lienzo es 5,484:1 y el alto SALE de
la relación»*. El componente lo documentaba y no lo hacía.

Y de rebote [UI-AUTH-HEADER-ALIGNS-WITH-ITS-FIELDS-001]: la cabecera de auth existe alineada al
borde izquierdo de sus campos, y el imagotipo era el único hijo que no lo respetaba.

## Diseño

La relación **no se escribe a mano** — sería un cuarto sitio donde vive un número del maestro, y
quedaría desfasado en silencio en cuanto el dibujo cambiara. Se lee del propio painter:

```kotlin
val painter = painterResource(resource)
val ratio = painter.intrinsicSize.width / painter.intrinsicSize.height
Image(..., modifier = modifier.width(width).aspectRatio(ratio))
```

Con el alto ya restringido por la relación, `Fit` escala a la caja entera y `Center` no tiene
sobrante que repartir: la alineación sale **gratis**, no se arregla aparte.

⛔ **No vale `ContentScale.FillWidth` a secas**: escalaría el dibujo pero el nodo seguiría midiendo
31 dp de alto, así que el logo se saldría de su propia caja y se recortaría.

## Criterio de éxito

- El borde izquierdo del imagotipo coincide con el del subtítulo, **medido en la captura**.
- El ancho dibujado es el ancho pedido, no el intrínseco.
- `./gradlew testAll` verde.
- Captura en claro y en oscuro [PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001].

## Consumidores auditados

`PaparcarImagotipo` tiene **un solo call site** hoy (la cabecera de auth), así que el arreglo en el
componente los cubre todos — que es justo el motivo de que el lockup sea un componente y no una
composición repetida.

---

## Segunda mitad: el margen horneado

Con el escalado arreglado el desfase bajó de **38,9 dp a 5,0 dp**, y el user volvió a señalarlo:
*«el isotipo no se alinea justo justo a la izquierda, hay un breve espacio en blanco»*. Tenía razón,
y no era layout: **el fichero entregado trae 9 u de margen por los cuatro lados** (2,65 % del ancho).

Y eso **no** es la zona de respeto de la marca —ésa se mide contra la altura del símbolo y es mucho
mayor—: es el margen con el que se exportó el SVG. La diferencia importa porque un margen horneado
se lo come **toda** superficie que coloque el logo, y ninguna lo pidió:

| Superficie | Lo que le metía |
|---|---|
| Login | 5 dp a la derecha del borde al que se alinea todo lo demás |
| Banner de Play | 12 px |
| Cabecera de la web, tarjeta social | otro tanto |

**El aire es COMPOSICIÓN y lo pone cada superficie.** Así que el lienzo del lockup pasa a ser su
caja de TINTA (`LOCKUP_PAD` desaparece, no se pone a cero y se deja ahí).

### Lo que arrastró, y cómo se cazó

- **La relación cambia de `5,484:1` a `7,320:1`**, así que el par de dp enteros del VectorDrawable ya
  no valía: `170 × 31` daba **0,97 %** de deformación. **Lo cazó el assert que el propio ticket
  anterior había puesto** — el que exige ≤ 0,2 %. Se recalculó: **183 × 25 dp, 0,0014 %**.
- **El alto del raster estaba CABLEADO a 187** en `render_graphics.sh`, y el `<img>` lleva los dos
  lados forzados: un alto viejo no recorta ni avisa, **ESTIRA** el logo y el PNG sigue midiendo lo
  que se pidió. Ahora se **deriva del `viewBox`** del SVG.
- **El umbral de transparencia del raster** (≥ 60 %) estaba medido sobre el lockup con margen; sin él
  la tinta ocupa más y daba 59 %. Re-medido y bajado a 45 %, con su razón escrita: lo que caza es un
  PNG con fondo opaco, que daría ~0.
- **Las dos cifras de cobertura de `BrandSurfaceGuardrailTest`** (4,5 % / 23,6 %) pasan a 6,7 % / 34,6 %.

## Verificación — medida en device, no a ojo

Emulador, densidad 420 (2,625 px/dp), cabecera de auth real:

| | borde del logo | borde del subtítulo | desfase |
|---|---|---|---|
| **antes** | 64,0 dp | 25,1 dp | **38,9 dp** |
| sólo el ratio | 30,1 dp | 25,1 dp | 5,0 dp |
| **+ recorte** | **24,0 dp** | 25,1 dp | **−1,1 dp** |

Los −1,1 dp finales son el *side bearing* de la «F» de *Find*: el logo queda pegado al borde de su
caja y la letra arranca 1 dp dentro de la suya. Es lo correcto ópticamente, y es el suelo al que se
puede llegar sin falsear una de las dos.

Ancho de tinta dibujado con el ratio ya arreglado: **227,4 dp** contra los **227,3 dp** que predice
la geometría para una caja de 240 — cuadra con 0,1 dp.
