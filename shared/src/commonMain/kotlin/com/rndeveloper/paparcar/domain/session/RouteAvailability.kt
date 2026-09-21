package com.rndeveloper.paparcar.domain.session

import com.rndeveloper.paparcar.domain.model.UserParking

/**
 * En qué estado está el RECORRIDO de un viaje — el veredicto que la pantalla de detalle necesita
 * para no contar cuatro situaciones distintas con el mismo lienzo en blanco.
 * [TRIP-A-ROUTE-THAT-ONLY-LIVES-HERE-SAYS-SO-001]
 *
 * ⛔ Nace de un reporte que parecía «las rutas no se pintan» y no lo era. Medido sobre 18 sesiones
 * reales: **nueve tenían recorrido y nueve tenían la DISTANCIA sin un solo punto**. Y la distancia
 * se estampa en la misma escritura que la polilínea, así que una fila que dice 11,8 km sin geometría
 * es una fila a la que se la quitaron *después* de medirla — la copia en la nube va sin trazado
 * [PRIV-A-TRACE-IS-NOT-A-STAT-001], y al reinstalar, el historial se rehidrata desde ahí.
 *
 * El defecto no era el filtro de privacidad, que hace lo que promete: era que **la pérdida es
 * silenciosa**. «Este viaje no tuvo recorrido» y «este viaje tuvo recorrido y ya no lo tenemos» son
 * dos frases distintas, y las dos se dibujaban igual: en blanco.
 */
enum class RouteAvailability {
    /** Trazado final sobre calles: se dibuja. */
    Drawn,

    /** Hay puntos medidos, pero todavía no se han ajustado a la calle. El detalle lo dice. */
    Recalculating,

    /**
     * Se midió un recorrido —hay distancia— y su trazado **ya no está**. Lo normal es que se grabara
     * en un móvil que perdió sus datos y que la copia de la cuenta fuera sin trazado.
     *
     * ⛔ No se puede recuperar: nunca salió del teléfono. Lo único honesto es decirlo.
     */
    LostOffDevice,

    /** Este viaje nunca tuvo recorrido: pin manual, sin conducción medida, hop trivial. */
    None,
}

/**
 * ⚠️ El orden de las ramas importa: `Recalculating` se decide **antes** que la distancia, porque un
 * viaje recién aparcado tiene puntos y distancia a la vez y no está perdido, está en cola.
 */
fun UserParking.routeAvailability(): RouteAvailability = when {
    routeSnapped && !routePolyline.isNullOrEmpty() -> RouteAvailability.Drawn
    !routePolyline.isNullOrEmpty() -> RouteAvailability.Recalculating
    // ⛔ `> 0`, no `!= null`: una distancia de cero metros no prueba que hubiera recorrido, y
    // acusar de pérdida a un viaje que nunca tuvo trazado es la mentira simétrica de la que este
    // veredicto existe para evitar.
    (routeDistanceMeters ?: 0f) > 0f -> RouteAvailability.LostOffDevice
    else -> RouteAvailability.None
}
