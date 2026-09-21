package com.rndeveloper.paparcar.domain.session

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.RouteInferenceResolution
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.util.PolylineCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        spans: String? = null,
        resolution: RouteInferenceResolution? = null,
    ) = UserParking(
        id = "s1",
        userId = "u1",
        location = GpsPoint(latitude = 40.0, longitude = -3.0, accuracy = 8f, timestamp = 1L, speed = 0f),
        routePolyline = polyline,
        routeSnapped = snapped,
        routeDistanceMeters = distance,
        routeInferredSpans = spans,
        routeInferredResolution = resolution,
    )

    /** Una polilínea real de 4 puntos, para poder hablar de TRAMOS y no sólo de «hay línea». */
    private val fourPoints = PolylineCodec.encode(
        listOf(
            GpsPoint(40.0000, -3.0000, 8f, 1L, 0f),
            GpsPoint(40.0010, -3.0000, 8f, 2L, 0f),
            GpsPoint(40.0020, -3.0000, 8f, 3L, 0f),
            GpsPoint(40.0030, -3.0000, 8f, 4L, 0f),
        ),
    )

    @Test
    fun should_drawTheRoute_when_theLineIsFinal() {
        // ⚠️ Puntos de verdad, no una cadena cualquiera: desde que el veredicto aplica los tramos
        // ([TRIP-A-REFUSED-ROUTE-IS-NOT-A-BLANK-MAP-001]) exige una línea UTILIZABLE, y eso es más
        // correcto que antes — una polilínea que no decodifica nunca se dibujó.
        assertEquals(
            RouteAvailability.Drawn,
            session(polyline = fourPoints, snapped = true, distance = 1200f).routeAvailability(),
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

    // ── El usuario dijo que no fue por ahí [TRIP-A-REFUSED-ROUTE-IS-NOT-A-BLANK-MAP-001] ──

    @Test
    fun should_sayTheUserRefusedIt_when_theWholeRouteWasReconstructed_andRejected() {
        // El caso del pin reconstruido de punta a punta: todo el recorrido es un tramo inferido, y
        // rechazarlo no deja nada que dibujar. Sin esta rama el veredicto decía `Drawn` y la
        // pantalla no pintaba nada — un lienzo en blanco justo después de contestar.
        val state = session(
            polyline = fourPoints,
            snapped = true,
            distance = 900f,
            spans = "0:3",
            resolution = RouteInferenceResolution.REJECTED,
        )

        assertEquals(emptyList(), state.drawableRouteSegments())
        assertEquals(RouteAvailability.RejectedByUser, state.routeAvailability())
    }

    @Test
    fun should_stillDraw_when_someStretchesAreMeasured_andOnlyTheInferredOnesWereRejected() {
        // ⛔ EL testigo que impide el arreglo perezoso (`resolution == REJECTED` a secas): aquí hay
        // tramos MEDIDOS, se dibujan, y el mapa no está en blanco. No hay nada que explicar, así
        // que no se explica nada.
        val state = session(
            polyline = fourPoints,
            snapped = true,
            distance = 900f,
            spans = "0:1",
            resolution = RouteInferenceResolution.REJECTED,
        )

        assertTrue(state.drawableRouteSegments().isNotEmpty(), "los tramos medidos siguen ahí")
        assertEquals(RouteAvailability.Drawn, state.routeAvailability())
    }

    @Test
    fun should_keepDrawing_when_theInferredStretchesWereConfirmed() {
        val state = session(
            polyline = fourPoints,
            snapped = true,
            distance = 900f,
            spans = "0:3",
            resolution = RouteInferenceResolution.CONFIRMED,
        )

        assertEquals(RouteAvailability.Drawn, state.routeAvailability())
    }

    @Test
    fun should_notAccuseTheUser_when_thereIsNoResolutionYet() {
        // Pendiente de contestar: la pantalla pregunta, no sentencia.
        val state = session(polyline = fourPoints, snapped = true, distance = 900f, spans = "0:3")

        assertEquals(RouteAvailability.Drawn, state.routeAvailability())
    }
}
