package com.rndeveloper.paparcar.domain.matching

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.places.RoadWay

/**
 * La línea que se DIBUJA de un viaje, a partir de lo medido y de las calles que tenemos.
 *
 * ## El invariante
 *
 * **La línea llega SIEMPRE hasta el último punto medido.** El casado puede *reemplazar* un tramo por
 * la geometría de su calle; no puede *borrar* el final. Donde hay calles la línea va por la calle;
 * donde no las hay, va por lo medido — pero llega. [ROUTE-A-DRAWN-LINE-MUST-REACH-THE-CAR-001]
 *
 * ## Por qué hace falta alguien que lo sostenga
 *
 * [TrailMapMatcher] no puede sostenerlo solo: sólo recibe `roads`, así que un fix **sin cobertura**
 * (no hemos pedido ese terreno) y un fix **fuera de la calzada** le llegan idénticos — ninguno
 * encuentra calle. Y son cosas distintas: el segundo es ruido que la doctrina manda no dibujar
 * crudo; el primero es terreno recorrido del que no tenemos el mapa.
 *
 * Quien sí sabe distinguirlos es quien eligió el bbox. Por eso la cobertura entra aquí como
 * [RoadCoverage] y el casado se pide **sólo del prefijo cubierto**: más allá no se descarta nada,
 * se dibuja lo medido.
 *
 * El defecto que lo trajo: cuando un re-fetch de Overpass fallaba a mitad de viaje (429/504/mirror
 * que contesta vacío), la caché de calles se quedaba con la zona vieja, se casaba la traza ENTERA
 * contra ella, los fixes nuevos se caían del match por «off-road», y la línea terminaba en el borde
 * de la cobertura. La vista prefiere la casada, así que la cruda —que sí llegaba al coche— no se
 * dibujaba nunca. Sólo se arreglaba reiniciando la app, que era lo único que vaciaba la caché.
 */
object TripLine {

    /**
     * @param drawn la polilínea a pintar; termina en el último fix de la traza.
     * @param fixesOutsideCoverage cuántos fixes finales quedaron fuera del bbox de calles.
     * @param offRoadTailMeters metros de cola que, teniendo calles alrededor, no casaron con ninguna.
     */
    data class Drawn(
        val drawn: List<GpsPoint>,
        val fixesOutsideCoverage: Int,
        val offRoadTailMeters: Double,
    ) {
        /** Si la línea salió entera por la calle: nada que explicar en un diagnóstico. */
        val whole: Boolean get() = fixesOutsideCoverage == 0 && offRoadTailMeters == 0.0
    }

    /**
     * Mínimo de fixes cubiertos que hace que casar valga la pena. Por debajo, lo medido es todo lo
     * que hay y es además lo único honesto: dos puntos no eligen una calle.
     */
    const val MIN_MATCH_POINTS = 3

    /**
     * @param trail los fixes medidos, en orden.
     * @param roads las calles que tenemos, válidas dentro de [coverage].
     * @param coverage el bbox con el que se pidieron [roads]; `null` = no tenemos ninguna.
     */
    fun drawn(trail: List<GpsPoint>, roads: List<RoadWay>, coverage: RoadCoverage?): Drawn {
        if (coverage == null || roads.isEmpty() || trail.size < 2) {
            return Drawn(trail, fixesOutsideCoverage = 0, offRoadTailMeters = 0.0)
        }
        // `takeWhile` y no `filter`: en cuanto la traza sale de la cobertura dejamos de poder
        // responder por ella, y volver a entrar no lo arregla — el tramo de fuera seguiría sin
        // calles. El prefijo es lo único de lo que el casado puede hablar.
        val covered = trail.takeWhile { it in coverage }
        val outside = trail.size - covered.size
        if (covered.size < MIN_MATCH_POINTS) {
            return Drawn(trail, fixesOutsideCoverage = outside, offRoadTailMeters = 0.0)
        }
        val matched = TrailMapMatcher.match(covered, roads)
        return Drawn(
            drawn = matched.drawnOver(covered) + trail.drop(covered.size),
            fixesOutsideCoverage = outside,
            offRoadTailMeters = matched.shortfallMeters(covered),
        )
    }
}
