# UI-STAT-ICON-CENTERS-ON-DIGITS-001 · El icono de una stat se centra en el line box, no en los dígitos

**Estado:** ✅ Done — visto en device (Redmi mock) junto al rediseño de stats; rebasada sobre
VEH-STATS-SAY-SOMETHING-USEFUL-001 sin conflictos (el fix y el rediseño tocaban hunks distintos
de `VehiclePageContent.kt`); tests + mock verdes.

## Problema
En la fila de stats de la hero card de Vehículos (`VehicleStatsRow` → `StatCell`,
`VehiclePageContent.kt`), el icono de cada métrica queda visiblemente ALTO respecto a su
número. El `Row` usa `Alignment.CenterVertically`, que centra el icono sobre el **line box**
del texto — y el line box de `statNumber` no es simétrico respecto a los dígitos.

Con los metrics reales del TTF (`barlow_condensed_bold.ttf`: upm 1000, ascent 1000,
descent −200, capHeight 700):

- A 25sp la línea natural mide 30sp (ascent 25 + descent 5). `statNumber` fuerza
  `lineHeight = 25sp` con `LineHeightStyle(Center, Trim.Both)`; como 25 < 30 no hay leading
  que recortar y el **shrink** de 5sp se reparte por igual → ascent′ 22.5sp / descent′ 2.5sp.
- Los dígitos (sin descendentes) ocupan la banda [baseline − capHeight, baseline]: su centro
  óptico queda a `ascent′ − capHeight/2` = 22.5 − 8.75 = **13.75sp** del borde superior.
- El centro del line box está a **12.5sp**. El icono, centrado en el box, queda
  **~1.25dp alto** respecto al centro óptico del número. De ahí el "flota alto" que se ve.

El intento anterior ([CARD-ONE-BADGE-001], comentario en `PaparcarType.statNumber`) recortó el
line box, pero el recorte con `Alignment.Center` reparte ascent/descent simétricamente sobre una
métrica que NO es simétrica (25/5), así que corrigió parte del error y dejó el resto.

## Doctrina violada
Ninguna regla escrita; es un bug visual puro. Sí aplica «sistemas, no parches»: la corrección no
puede ser un `padding(top = X.dp)` mágico que se rompa al cambiar `fontSize` o el font scale del
sistema.

## Señales / datos disponibles
Metrics medidos del TTF real (script sobre `head`/`hhea`/`OS/2`), no estimados.

## Diseño
El invariante correcto es: **el icono se centra en la banda de cap-height de los dígitos**, que
es donde vive la tinta de un dato numérico. Esa banda se define desde la BASELINE, que es el
único ancla estable ante cambios de `lineHeight`, trim, font scale o longitud del texto.

En `StatCell`, el par icono+número se alinea por línea de alineación de Row:
- `Text` → `Modifier.alignByBaseline()`.
- `Icon` → `Modifier.alignBy { it.measuredHeight / 2 + capHeightPx/2 }`: su "baseline" virtual
  queda a `iconCentro + capHeight/2`, de modo que al casarla con la baseline del texto el centro
  del icono cae exactamente en `baseline − capHeight/2` = centro de la banda de dígitos.
- `capHeightPx = statNumber.fontSize.toPx() × 0.70f`, con 0.70 = capHeight real del TTF
  (700/1000 upm), constante nombrada — métrica de la fuente, no un pad mágico. Escala con sp
  (incluye el font scale del usuario) y no depende del lineHeight.

## Criterio de éxito
El centro vertical del icono coincide con el centro de la banda de dígitos para cualquier
`fontScale` y cualquier valor ("3", "12 h", "—", "92%"). Verificación en device (galería mock /
pantalla real); compila prod y mock, tests verdes.

## Consumidores auditados
`grep PaparcarType.current.statNumber` → único consumidor: `VehiclePageContent.StatCell`.
- `counter` (PapSheet, sheet-header): dígito centrado en un Box tile, sin icono lateral → exento.
- `metadata` (SettingsScreen:1092): texto sin icono emparejado a dígitos condensados → exento.
- Peek meta-rows: Inter por [PEEK-META-INTER-001], line box casi simétrico → exento.
No hay más pares icono+número condensado en la app a día de hoy.
