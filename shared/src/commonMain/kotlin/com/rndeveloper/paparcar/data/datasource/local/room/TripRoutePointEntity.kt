package com.rndeveloper.paparcar.data.datasource.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.rndeveloper.paparcar.domain.model.GpsPoint

/**
 * One admitted fix of the CURRENT trip's driven route. [TRIP-A-LONG-DRIVE-KEEPS-ITS-ORIGIN-001]
 *
 * A row per fix is what removes the two structural limits of the old single-blob stores at once:
 * appending is O(1) instead of a full-list rewrite per tick, and the route needs no sliding cap —
 * a 7-hour drive keeps its origin. The table only ever holds ONE trip (single tracked trip → single
 * buffer): a new-trip gap wipes it before seeding the fresh origin, and confirming or aborting the
 * trip clears it. Rows are transient by design — at confirm the route is encoded onto the session
 * (`routePolyline`) and this table empties.
 *
 * Column set mirrors [GpsPoint] INCLUDING provenance (`provider`, `satelliteCount`): a fix that
 * says where it came from must still say so after a process death.
 * [DET-A-FIX-MUST-SAY-WHERE-IT-CAME-FROM-001]
 */
@Entity(tableName = "trip_route_points")
data class TripRoutePointEntity(
    /** Insertion order IS the route order — timestamps can repeat or be absent (synthetic fixes). */
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long,
    val speed: Float,
    val provider: String?,
    val satelliteCount: Int?,
) {
    fun toGpsPoint() = GpsPoint(
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        timestamp = timestamp,
        speed = speed,
        provider = provider,
        satelliteCount = satelliteCount,
    )

    companion object {
        fun fromGpsPoint(point: GpsPoint) = TripRoutePointEntity(
            latitude = point.latitude,
            longitude = point.longitude,
            accuracy = point.accuracy,
            timestamp = point.timestamp,
            speed = point.speed,
            provider = point.provider,
            satelliteCount = point.satelliteCount,
        )
    }
}
