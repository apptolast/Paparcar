package com.rndeveloper.paparcar.presentation.util

/**
 * La GRAMÁTICA de dos líneas con la que Home nombra cualquier sujeto — plaza, parking, gasolinera o
 * coche. [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001]
 *
 * Es del user, y nació de un defecto medido en los peeks: *«actualmente mostramos 3 líneas y las 2
 * últimas muestran dirección y a veces es repetitivo»*. La forma es:
 *
 * | Línea | Qué responde |
 * |---|---|
 * | 1 | **QUÉ es** — su nombre, y el calificador que lo distingue (el producto, la talla, el tipo) |
 * | 2 | **DÓNDE está · A CUÁNTO** |
 *
 * Y su cifra propia (precio, antigüedad) va al **trailing**, nunca a una tercera línea.
 *
 * ⛔ **La línea 2 NUNCA repite la 1, y ésa es la regla que el defecto violaba.** Un sujeto que no
 * puede nombrarse se nombra por su sitio —así lo hacen la gasolinera sin nombre de Francia y 1 de
 * cada 25 de OSM—, y entonces ese sitio **ya está** en la línea 1: volver a escribirlo abajo es la
 * repetición. La alternativa no es dejar la línea 2 vacía, es **bajar un nivel**: si el nombre ya es
 * la calle, abajo va la población.
 *
 * ⛔ **Y no es una función de composición.** Decidir qué se escribe es una regla de producto con
 * cuatro ramas y es lo que se puede equivocar; traducir «a 450 m» es otra cosa y ya tiene su sitio
 * (`distanceString`). Quien llama trae los textos ya resueltos.
 *
 * Esta función es la ÚNICA definición de la regla. Vivía copiada en privado dentro de la fila de
 * gasolinera —la única superficie que la cumplía— y no existía en los tres peeks de detalle, que
 * deduplicaban sólo contra el título y además daban a la distancia una fila entera para ella.
 */
fun subjectWhereLine(
    /** La línea 1 tal como se va a imprimir: es contra esto contra lo que se deduplica. */
    name: String,
    /** El sitio preferente — nombre de POI, o la calle cuando no hay POI. */
    placeLine: String?,
    /** El nivel de abajo — la calle cuando arriba hay un POI, o población/región cuando no. */
    secondaryLine: String?,
    /** «a 450 m», ya formateado, o null cuando no hay fix desde el que medir.
     *  ⛔ Null NO es cero: sin ubicación la línea se queda con el sitio y no miente con una cifra.
     *  [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001] */
    farText: String?,
): String? {
    val where = listOfNotNull(placeLine, secondaryLine)
        .firstOrNull { it.isNotBlank() && it != name }
    return listOfNotNull(where, farText)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(SUBJECT_LINE_SEPARATOR)
}

/** El mismo separador con el que el resto de la hoja une dos datos en una línea. */
private const val SUBJECT_LINE_SEPARATOR = " · "
