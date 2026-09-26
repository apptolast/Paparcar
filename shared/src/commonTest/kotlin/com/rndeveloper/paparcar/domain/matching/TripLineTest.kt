package com.rndeveloper.paparcar.domain.matching

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.places.RoadWay
import com.rndeveloper.paparcar.domain.util.haversineMeters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El invariante de [TripLine]: **la línea dibujada llega SIEMPRE hasta el último punto medido.**
 * [ROUTE-A-DRAWN-LINE-MUST-REACH-THE-CAR-001]
 *
 * El caso de campo que lo trajo (25-09-2026): a mitad de viaje el re-fetch de calles falla, la caché
 * se queda con la zona del principio, y la línea terminaba en el borde de esa zona — «se acaba en un
 * punto y no sigue por donde voy». Sólo se arreglaba reiniciando la app, que era lo único que
 * vaciaba la caché.
 *
 * ⛔ **El fixture tiene que modelar eso de verdad**: las calles se acaban DONDE se acaba la
 * cobertura. Una primera versión de este test daba al matcher una calle que llegaba hasta el final
 * del viaje y sólo mentía en el bbox — y entonces pasaba igual de verde con el bug puesto, porque el
 * matcher encontraba calle para todos los fixes. Un test que no puede ponerse rojo no prueba nada.
 */
class TripLineTest {

    private fun gp(lat: Double, lon: Double, accuracy: Float = 5f) = GpsPoint(lat, lon, accuracy, 0L, 0f)

    /** El viaje: 18 fixes en línea recta hacia el este, ~5 m al norte de la calle, separados ~45 m. */
    private val trail = (0..17).map { gp(36.60005, -6.2395 + it * 0.0005) }

    /** Cobertura que sólo llega hasta media calle — la mitad del viaje se queda fuera.
     *  El borde va a propósito ENTRE dos fixes (-6.2350 y -6.2345) y no sobre uno: puesto justo
     *  encima, la suma en coma flotante de `-6.2395 + 9*0.0005` decide de qué lado cae. */
    private val halfCoverage = RoadCoverage(minLat = 36.599, minLon = -6.2400, maxLat = 36.601, maxLon = -6.2348)

    /** Las calles que trajo ESA petición: acaban con ella. Más allá no tenemos mapa — que es
     *  justo lo que el matcher no puede distinguir de «este fix está fuera de la calzada». */
    private val cachedRoad = RoadWay((0..10).map { gp(36.6000, -6.2400 + it * 0.0005) })

    private val fullCoverage = RoadCoverage(minLat = 36.599, minLon = -6.2400, maxLat = 36.601, maxLon = -6.2300)

    /** La calle entera, la que habría traído un fetch que no falló. */
    private val wholeRoad = RoadWay((0..20).map { gp(36.6000, -6.2400 + it * 0.0005) })

    private fun metersApart(a: GpsPoint, b: GpsPoint) =
        haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)

    @Test
    fun `should_reach_the_last_measured_fix_when_the_road_cache_covers_only_the_start`() {
        val line = TripLine.drawn(trail, listOf(cachedRoad), halfCoverage)

        // Este es EL defecto: antes se casaba la traza entera contra la caché vieja, los fixes de
        // más allá se caían por «off-road», la línea terminaba en el borde de la cobertura —a unos
        // 400 m del coche— y la cruda no se dibujaba nunca porque la vista prefiere la casada.
        val gap = metersApart(line.drawn.last(), trail.last())
        assertTrue(gap < 10.0, "la línea tiene que llegar al último fix medido; se quedó a ${gap.toInt()} m")
        assertFalse(line.whole, "una línea que no salió entera por la calle tiene que decirlo")
        assertEquals(8, line.fixesOutsideCoverage, "8 fixes caen fuera del bbox cacheado")
    }

    @Test
    fun `should_still_put_the_covered_stretch_on_the_street`() {
        val line = TripLine.drawn(trail, listOf(cachedRoad), halfCoverage)

        // El tramo con calles sigue callejeado: pegado a la calzada, no a los ~5 m de deriva del GPS.
        // Llegar al coche no se paga aflojando [ROUTE-LINE-ONROAD-001] donde sí hay datos.
        val onStreet = line.drawn.count { haversineMeters(it.latitude, it.longitude, 36.6000, it.longitude) < 1.0 }
        assertTrue(onStreet >= 2, "el tramo cubierto tiene que ir por la calle, salieron $onStreet puntos en ella")
    }

    @Test
    fun `should_report_a_whole_line_when_the_cache_covers_the_trip`() {
        val line = TripLine.drawn(trail, listOf(wholeRoad), fullCoverage)

        assertTrue(line.whole, "con cobertura completa no hay nada que explicar")
        assertEquals(0, line.fixesOutsideCoverage)
        assertTrue(metersApart(line.drawn.last(), trail.last()) < 10.0)
    }

    @Test
    fun `should_hand_back_the_measured_trail_when_there_are_no_roads_at_all`() {
        assertEquals(trail, TripLine.drawn(trail, emptyList(), fullCoverage).drawn)
        assertEquals(trail, TripLine.drawn(trail, listOf(wholeRoad), coverage = null).drawn)
    }

    @Test
    fun `should_hand_back_the_measured_trail_when_not_even_the_start_is_covered`() {
        // La cobertura está en otro sitio: no hay prefijo que casar, pero el viaje se dibuja igual.
        val elsewhere = RoadCoverage(minLat = 40.0, minLon = -3.0, maxLat = 40.1, maxLon = -2.9)
        val line = TripLine.drawn(trail, listOf(wholeRoad), elsewhere)

        assertEquals(trail, line.drawn)
        assertEquals(trail.size, line.fixesOutsideCoverage)
        assertFalse(line.whole)
    }
}
