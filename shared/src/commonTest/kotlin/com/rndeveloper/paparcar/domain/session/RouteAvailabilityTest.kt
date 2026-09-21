package com.rndeveloper.paparcar.domain.session

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [TRIP-A-ROUTE-THAT-ONLY-LIVES-HERE-SAYS-SO-001]
 *
 * Existe porque las cuatro situaciones del recorrido se dibujaban con el mismo lienzo en blanco, y
 * eso hizo leer un problema de RESPALDO como «las rutas no se pintan». Medido en 18 sesiones reales:
 * nueve con recorrido y nueve con la DISTANCIA sin un solo punto.
 */
class RouteAvailabilityTest {

    private fun session(
        polyline: String? = null,
        snapped: Boolean = false,
        distance: Float? = null,
    ) = UserParking(
        id = "s1",
        userId = "u1",
        location = GpsPoint(latitude = 40.0, longitude = -3.0, accuracy = 8f, timestamp = 1L, speed = 0f),
        routePolyline = polyline,
        routeSnapped = snapped,
        routeDistanceMeters = distance,
    )

    @Test
    fun should_drawTheRoute_when_theLineIsFinal() {
        assertEquals(
            RouteAvailability.Drawn,
            session(polyline = "abc", snapped = true, distance = 1200f).routeAvailability(),
        )
    }

    @Test
    fun should_sayItIsRecalculating_when_thereArePointsNotYetOnTheStreet() {
        assertEquals(
            RouteAvailability.Recalculating,
            session(polyline = "abc", snapped = false, distance = 1200f).routeAvailability(),
        )
    }

    @Test
    fun should_sayTheRouteIsGone_when_thereIsADistanceButNoLine() {
        // ⛔ EL testigo del ticket. La distancia se estampa en la MISMA escritura que la polilínea,
        // así que 11,8 km sin un punto es un recorrido que existió y ya no está — nunca «este viaje
        // no tuvo recorrido», que es como se dibujaba.
        assertEquals(
            RouteAvailability.LostOffDevice,
            session(polyline = null, snapped = false, distance = 11839.5f).routeAvailability(),
        )
    }

    @Test
    fun should_sayNothing_when_theTripNeverHadARoute() {
        // Pin manual, sin conducción medida: acusar de pérdida a un viaje que nunca tuvo trazado es
        // la mentira simétrica, y la que este veredicto también existe para evitar.
        assertEquals(RouteAvailability.None, session().routeAvailability())
        assertEquals(
            RouteAvailability.None,
            session(distance = 0f).routeAvailability(),
            "cero metros no prueba que hubiera recorrido",
        )
    }

    @Test
    fun should_preferRecalculating_over_lost_when_bothCouldApply() {
        // ⚠️ El orden de las ramas: un viaje recién aparcado tiene puntos y distancia a la vez y no
        // está perdido, está en cola. Si «perdido» se evaluara primero, cada aparcamiento nuevo
        // anunciaría una pérdida durante los minutos que tarda el snapping.
        assertEquals(
            RouteAvailability.Recalculating,
            session(polyline = "abc", snapped = false, distance = 8790f).routeAvailability(),
        )
    }

    @Test
    fun should_notDraw_when_theLineIsEmpty_evenIfMarkedSnapped() {
        // `routeSnapped = true` con polilínea vacía es la forma que escribe el filtro de privacidad
        // al salir hacia la nube en filas viejas: no puede leerse como «hay línea».
        assertEquals(
            RouteAvailability.LostOffDevice,
            session(polyline = "", snapped = true, distance = 500f).routeAvailability(),
        )
    }
}
