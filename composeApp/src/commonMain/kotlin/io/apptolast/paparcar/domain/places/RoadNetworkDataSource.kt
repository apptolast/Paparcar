package io.apptolast.paparcar.domain.places

import io.apptolast.paparcar.domain.model.GpsPoint

/** A single OSM road (a `highway=*` way) as its ordered list of vertices. [ROUTE-SNAP-001]
 *
 *  [isMinor] marks ways a through-drive rarely uses — `highway=service` (parking aisles, driveways,
 *  school drop-off loops, fuel-station forecourts). They stay in the graph so a trip that genuinely
 *  starts/ends on one still matches, but the matcher handicaps them so a service way running
 *  parallel to the real road never steals the line on a straight stretch. [ROUTE-QUALITY-001] */
data class RoadWay(val points: List<GpsPoint>, val isMinor: Boolean = false)

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
