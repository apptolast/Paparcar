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

/**
 * ¿Caben estas DOS cápsulas la una al lado de la otra, o tienen que apilarse?
 * [UI-A-PILL-PAIR-SPLITS-THE-BUDGET-IN-TWO-001]
 *
 * La pregunta hermana de [ctaFitsInline], y la que faltaba: un par de CTAs no compite contra el
 * texto de su fila, compite **entre sí**, y cada uno se queda con la MITAD del ancho. Nadie se lo
 * cobraba — la rama de dos botones de `HomeDetectionSurface` ni miraba la colocación—, así que
 * `home_det_awaiting_cta_primary`, que este mismo paquete ya declaraba frase (15 chars en inglés,
 * 25 en francés), se pintaba a media anchura y se cortaba: «Marcar mi apa…» / «Voy conducie…»,
 * los dos ilegibles, en los nueve idiomas.
 *
 * ⛔ **No es [ctaFitsInline] aplicado dos veces**, aunque los números salgan cerca. Aquel gobierna
 * el hueco *trailing*, donde el vecino es copy acotado que PUEDE envolver a dos líneas; aquí el
 * vecino es un botón que no envuelve nada. Dos preguntas, dos anchuras, dos constantes — la misma
 * distinción que `HomeParkingRow` dejó escrita al negarse a pasar por el predicado equivocado.
 */
fun ctaPairFitsSideBySide(primary: String, secondary: String): Boolean =
    primary.length <= CTA_SIDE_BY_SIDE_MAX_CHARS && secondary.length <= CTA_SIDE_BY_SIDE_MAX_CHARS

/**
 * Lo que mide una cápsula cuando comparte fila con otra, en caracteres.
 *
 * La fila útil son ~300 dp; quitado el hueco de 8 dp quedan **~146 dp por cápsula**, y descontados
 * los 32 dp de padding de [PapCtaPill] quedan **~114 dp de texto**. El rol `cta` es 15 sp SemiBold
 * con 0,1 de tracking, o sea ~8,4 dp por carácter: **~13**.
 *
 * Es 12 y no 13 porque la maqueta que rompió —la card del coche— es la variante EMPOTRADA, que
 * paga además el padding de su huésped. Y porque el device dijo lo mismo que la cuenta: «Voy
 * conduciendo» (15) se pintó «Voy conducie…» el 25-09, 12 glifos más los puntos suspensivos.
 *
 * ⛔ El valor no arbitra nada hoy por casualidad: de los tres pares vivos, dos miden ≥ 15 en los
 * nueve locales y se apilan siempre. El único que roza el listón es el sí/no de la pregunta, y ahí
 * el idioma SÍ decide —11 en alemán, 20 en italiano—, que es exactamente lo que este presupuesto
 * existe para responder sin que nadie lo mire.
 */
const val CTA_SIDE_BY_SIDE_MAX_CHARS = 12
