package com.rndeveloper.paparcar.data.datasource.remote.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class RouteGeometryPrivacyTest {

    @Test
    fun `should strip the trace and keep the figures`() {
        // [PRIV-A-TRACE-IS-NOT-A-STAT-001] A trace is not a stat: the numbers travel, the WHERE
        // does not.
        val dto = ParkingHistoryDto(
            id = "p1",
            latitude = 36.6,
            longitude = -6.2,
            accuracy = 8f,
            timestamp = 1L,
            isActive = false,
            routePolyline = "encoded",
            routeSnapped = true,
            routeInferredSpans = "spans",
            routeInferredResolution = "ACCEPTED",
            routeDistanceMeters = 3_400f,
            searchTimeMs = 180_000L,
        )

        val stripped = dto.withoutRouteGeometry()

        assertNull(stripped.routePolyline)
        assertFalse(stripped.routeSnapped, "null polyline + snapped=false is the established no-route shape")
        assertNull(stripped.routeInferredSpans)
        assertNull(stripped.routeInferredResolution)
        assertEquals(3_400f, stripped.routeDistanceMeters, "the distance is a figure - it travels")
        assertEquals(180_000L, stripped.searchTimeMs, "the search time is a figure - it travels")
        assertEquals(dto.id, stripped.id)
    }

    @Test
    fun `should freeze the cloud geometry and refuse the local one when plus lapsed`() {
        // [PRIV-A-LAPSED-PLUS-STILL-UPLOADS-ROUTES-001] Con Plus caducado la geometria no se borra
        // (se subio pagando) y tampoco se refresca (eso seria una subida nueva): se CONGELA la que
        // la nube ya tiene. Por eso se copia la de `remote` y no se conserva la de `this`.
        val local = sessionWith(polyline = "line-NUEVA", snapped = true, spans = "spans-nuevos")
        val remote = sessionWith(polyline = "line-VIEJA", snapped = false, spans = "spans-viejos")

        val outgoing = local.withRouteGeometryOf(remote)

        assertEquals("line-VIEJA", outgoing.routePolyline, "sube la de la NUBE, no la del movil")
        assertFalse(outgoing.routeSnapped, "tambien el snapped viene de la nube")
        assertEquals("spans-viejos", outgoing.routeInferredSpans)
        assertEquals(3_400f, outgoing.routeDistanceMeters, "las CIFRAS siguen siendo las de hoy")
        assertEquals(180_000L, outgoing.searchTimeMs)
    }

    @Test
    fun `should keep a gap session without geometry when the cloud never had any`() {
        // Una sesion grabada DURANTE el hueco: la nube no tiene nada que congelar, asi que no sube
        // nada. Es la opcion «reanudar»: lo del hueco se queda en el movil.
        val local = sessionWith(polyline = "line-del-hueco", snapped = true, spans = "spans")

        val outgoing = local.withRouteGeometryOf(remote = null)

        assertNull(outgoing.routePolyline, "la del hueco NO sube por la puerta de atras")
        assertFalse(outgoing.routeSnapped)
        assertNull(outgoing.routeInferredSpans)
        assertNull(outgoing.routeInferredResolution)
        assertEquals(3_400f, outgoing.routeDistanceMeters, "y las cifras siguen viajando")
    }

    @Test
    fun `should leave nothing of the local geometry in the frozen document`() {
        // El testigo de que la lista de los cuatro campos esta COMPLETA: si manana nace un quinto
        // campo de geometria y nadie lo anade a `withRouteGeometryOf`, la geometria local se
        // colaria por ahi. Comparar contra el escurrido lo caza sin repetir la lista.
        val local = sessionWith(polyline = "line", snapped = true, spans = "spans")

        assertEquals(
            local.withoutRouteGeometry(),
            local.withRouteGeometryOf(remote = null),
            "congelar contra una nube vacia tiene que dar EXACTAMENTE el documento escurrido: si " +
                "difieren, un campo de geometria se escapa de una de las dos funciones",
        )
    }

    private fun sessionWith(polyline: String?, snapped: Boolean, spans: String?) = ParkingHistoryDto(
        id = "p1",
        latitude = 36.6,
        longitude = -6.2,
        accuracy = 8f,
        timestamp = 1L,
        isActive = false,
        routePolyline = polyline,
        routeSnapped = snapped,
        routeInferredSpans = spans,
        routeInferredResolution = "ACCEPTED",
        routeDistanceMeters = 3_400f,
        searchTimeMs = 180_000L,
    )
}
