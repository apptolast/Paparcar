package io.apptolast.paparcar.domain.places

import io.apptolast.paparcar.domain.model.GpsPoint

/** A single OSM road (a `highway=*` way) as its ordered list of vertices. [ROUTE-SNAP-001] */
data class RoadWay(val points: List<GpsPoint>)

/**
 * Fetches the drivable road network (OSM `highway=*` ways) inside a bounding box, used to map-match
 * the live trip trail onto streets — for free, against OpenStreetMap data (no paid Roads API).
 * Implemented per platform (Overpass over HTTP on Android). Degrades gracefully: a failure returns
 * [Result.failure] and the caller falls back to the raw/smoothed GPS trail. [ROUTE-SNAP-001]
 */
interface RoadNetworkDataSource {
    suspend fun getRoads(
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double,
    ): Result<List<RoadWay>>
}
