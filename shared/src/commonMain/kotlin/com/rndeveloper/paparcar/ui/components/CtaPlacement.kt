package com.rndeveloper.paparcar.ui.components

/**
 * ¿Cabe este CTA al lado del texto, o tiene que irse debajo?
 * [UI-A-PILL-IS-FOR-A-LABEL-NOT-A-SENTENCE-002]
 *
 * La colocación la decide el LABEL, no un booleano que alguien tenga que acordarse de pasar — que es
 * la misma lección que [UI-A-PILL-IS-FOR-A-LABEL-NOT-A-SENTENCE-001] sacó de `RouteNoticePill`:
 * *«la condición ya distingue etiqueta de frase sin un parámetro»*.
 *
 * Existe porque el parámetro falló. [UI-DET-STOP-BELONGS-BESIDE-THE-STORY-001] decidió inline
 * midiendo los 9 locales —el CTA del viaje eran **9** chars, PL «Zatrzymaj»— y lo dejó escrito como
 * una decisión de fila. Después el copy se volvió una FRASE en
 * [WIDGET-A-TRIP-CAN-BE-ANSWERED-FROM-THE-LAUNCHER-001] («Non sono nella mia auto», **23**) y
 * ninguna de las maquetas que lo alojaban volvió a medirse: la decisión seguía ahí, con su premisa
 * muerta. Un cambio de copy no puede reventar una maqueta en silencio.
 *
 * [PapCtaPill] pinta `maxLines = 1` sin envolver, así que en una `Row` la cápsula se mide a ancho
 * intrínseco y **el que paga es el texto de al lado**, siempre.
 */
fun ctaFitsInline(label: String): Boolean = label.length <= CTA_INLINE_MAX_CHARS

/**
 * Lo que el hueco *trailing* de una fila puede gastarse en un botón sin comerse su propio texto.
 *
 * La fila útil mide ~300 dp (card de 360 menos márgenes y padding). Un CTA que pase de ~135 dp —el
 * 45 %— deja al título sin su segunda línea; descontados los 32 dp de padding de la cápsula quedan
 * ~100 dp de texto, que al tamaño del rol `cta` son unos 13 caracteres.
 *
 * ⛔ **Ningún label vivo cae entre 12 y 17**, así que el valor exacto no decide nada hoy: los que
 * caben miden ≤ 11 («Inschakelen», «Hinzufügen») y los que no, ≥ 18. Está escrito para que el
 * PRÓXIMO copy caiga del lado correcto solo, no para arbitrar los de ahora.
 */
const val CTA_INLINE_MAX_CHARS = 14
