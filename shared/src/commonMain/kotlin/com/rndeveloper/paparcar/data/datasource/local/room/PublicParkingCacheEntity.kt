package com.rndeveloper.paparcar.data.datasource.local.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A public car park kept on disk so the map is not empty on a cold start or without a radio.
 * [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
 *
 * ⛔ **One row per facility, keyed by its own id — never a response blob.** The in-memory
 * [com.rndeveloper.paparcar.data.cache.LocationKeyedCache] keys whole answers by rounded coordinates
 * plus radius, and that is right for memory and wrong for disk: pan the camera 200 m and it is a
 * different key, so a key-shaped disk cache would miss almost every time while still costing the
 * space. Stored per element and read by bounding box, one saved row serves any later query that
 * contains it, wherever the camera came from.
 *
 * [cachedAt] is when this row was last seen in a fresh answer. It is NOT what makes the row
 * trustworthy — that is [OsmCoverageEntity], which records the *area* we asked about and when.
 * A row's own stamp only drives retention.
 */
@Entity(
    tableName = "osm_public_parkings",
    indices = [Index(value = ["latitude", "longitude"])],
)
data class PublicParkingCacheEntity(
    /** Stable id WITHIN its source — an OSM element id today. */
    @PrimaryKey val id: String,
    val name: String?,
    val operator: String?,
    /** [com.rndeveloper.paparcar.domain.parkings.PublicParkingKind] by name. */
    val kind: String,
    val latitude: Double,
    val longitude: Double,
    val capacity: Int?,
    /** [com.rndeveloper.paparcar.domain.parkings.FeeInfo] by name — UNKNOWN is a real answer. */
    val fee: String,
    val maxHeightMeters: Double?,
    val openingHours: String?,
    val cachedAt: Long,
)
