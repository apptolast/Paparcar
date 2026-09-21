package com.rndeveloper.paparcar.domain.session

import com.rndeveloper.paparcar.domain.matching.InferredRoute
import com.rndeveloper.paparcar.domain.model.RouteInferenceResolution
import com.rndeveloper.paparcar.domain.matching.RouteSegment
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.util.PolylineCodec

/**
 * En qué estado está el RECORRIDO de un viaje — el veredicto que la pantalla de detalle necesita
 * para no contar CINCO situaciones distintas con el mismo lienzo en blanco.
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

    /**
     * La ruta estaba **reconstruida entera** sobre las calles y el usuario contestó que no fue por
     * ahí, así que no queda nada dibujable. [TRIP-A-REFUSED-ROUTE-IS-NOT-A-BLANK-MAP-001]
     *
     * ⛔ No es «no hay ruta»: hay, y somos nosotros quienes no la dibujamos, **a petición suya**.
     * Quien acaba de contestar que no es quien MÁS derecho tiene a una frase: su respuesta hizo
     * desaparecer la línea, y sin explicación lo que ve es que la app se rompió al contestarle.
     */
    RejectedByUser,

    /** Este viaje nunca tuvo recorrido: pin manual, sin conducción medida, hop trivial. */
    None,
}

/**
 * Los tramos que de verdad se van a dibujar, aplicando ya el veredicto del usuario sobre los tramos
 * inferidos. **Es exactamente lo que pinta la pantalla**, con la misma función, para que el
 * diagnóstico y el dibujo no puedan discrepar: si esto viene vacío, el mapa está en blanco.
 * [TRIP-A-REFUSED-ROUTE-IS-NOT-A-BLANK-MAP-001] [ROUTE-GAP-HONEST-001]
 */
fun UserParking.drawableRouteSegments(): List<RouteSegment> =
    if (routeSnapped && !routePolyline.isNullOrEmpty()) {
        InferredRoute.split(
            points = PolylineCodec.decode(routePolyline),
            encoded = routeInferredSpans,
            resolution = routeInferredResolution,
        )
    } else {
        emptyList()
    }

/**
 * ⚠️ El orden de las ramas importa: `Recalculating` se decide **antes** que la distancia, porque un
 * viaje recién aparcado tiene puntos y distancia a la vez y no está perdido, está en cola.
 *
 * ⛔ Y `RejectedByUser` se decide **antes** que `Drawn`, con la pregunta correcta: no «¿rechazó?»
 * sino «¿queda algo que dibujar después de rechazar?». Una ruta con tramos MEDIDOS y tramos
 * inferidos rechazados sigue dibujando los medidos, y ésa no está en blanco: no hay nada que
 * explicar. [TRIP-A-REFUSED-ROUTE-IS-NOT-A-BLANK-MAP-001]
 */
fun UserParking.routeAvailability(): RouteAvailability = when {
    routeSnapped && !routePolyline.isNullOrEmpty() ->
        if (drawableRouteSegments().isNotEmpty()) {
            RouteAvailability.Drawn
        } else if (routeInferredResolution == RouteInferenceResolution.REJECTED) {
            RouteAvailability.RejectedByUser
        } else {
            // Polilínea degenerada (menos de dos puntos útiles): no es una pérdida ni un rechazo.
            // Cae a las ramas de abajo, que deciden por la distancia.
            fallbackWithoutLine()
        }
    !routePolyline.isNullOrEmpty() -> RouteAvailability.Recalculating
    // ⛔ `> 0`, no `!= null`: una distancia de cero metros no prueba que hubiera recorrido, y
    // acusar de pérdida a un viaje que nunca tuvo trazado es la mentira simétrica de la que este
    // veredicto existe para evitar.
    (routeDistanceMeters ?: 0f) > 0f -> RouteAvailability.LostOffDevice
    else -> RouteAvailability.None
}

/** Lo que queda cuando hay `routeSnapped` pero ninguna línea utilizable: la misma pregunta que se
 *  le hace a un viaje sin polilínea, para que una fila degenerada no invente ni pérdida ni rechazo. */
private fun UserParking.fallbackWithoutLine(): RouteAvailability =
    if ((routeDistanceMeters ?: 0f) > 0f) RouteAvailability.LostOffDevice else RouteAvailability.None
