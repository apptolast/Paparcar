package com.rndeveloper.paparcar.domain.matching

import com.rndeveloper.paparcar.domain.model.GpsPoint

/**
 * El rectángulo de mundo para el que TENEMOS calles: el bbox con el que se pidió la red de OSM.
 *
 * No es un detalle del fetch, es el **alcance de una respuesta**: fuera de él no sabemos si un fix
 * está en la calzada o fuera de ella, sencillamente porque no hemos preguntado por ese terreno. Esa
 * distinción es la que faltaba cuando la línea del viaje se dibujaba recortada
 * ([com.rndeveloper.paparcar.domain.matching.TripLine]), y por eso la cobertura viaja como un tipo
 * y no como cuatro `Double` sueltos. [ROUTE-A-DRAWN-LINE-MUST-REACH-THE-CAR-001]
 */
data class RoadCoverage(
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
) {
    /** Si [other] cae entero dentro — o sea, si lo que ya tenemos vale y no hay que re-pedir. */
    operator fun contains(other: RoadCoverage): Boolean =
        other.minLat >= minLat && other.minLon >= minLon &&
            other.maxLat <= maxLat && other.maxLon <= maxLon

    /** Si tenemos las calles de este fix — o sea, si casarlo significa algo. */
    operator fun contains(point: GpsPoint): Boolean =
        point.latitude in minLat..maxLat && point.longitude in minLon..maxLon

    companion object {
        /** El bbox de [points] ensanchado [marginDeg] grados por cada lado. */
        fun around(points: List<GpsPoint>, marginDeg: Double): RoadCoverage {
            val lats = points.map { it.latitude }
            val lons = points.map { it.longitude }
            return RoadCoverage(
                minLat = lats.min() - marginDeg,
                minLon = lons.min() - marginDeg,
                maxLat = lats.max() + marginDeg,
                maxLon = lons.max() + marginDeg,
            )
        }
    }
}
