package io.apptolast.paparcar.data.datasource.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists geocoder + POI results keyed by rounded lat/lon.
 * Key format: "${(lat * 10000).roundToInt()}_${(lon * 10000).roundToInt()}"
 * gives ~11 m precision at the equator — enough to cache a parking-spot area.
 */
@Entity(tableName = "geocoder_cache")
data class GeocoderCacheEntity(
    @PrimaryKey val locationKey: String,
    val addressStreet: String?,
    val addressCity: String?,
    val addressRegion: String?,
    val addressCountry: String?,
    val addressCountryCode: String?,
    val placeInfoName: String?,
    val placeInfoCategory: String?,
    val cachedAt: Long,
    /** True once Phase-2 (POI network fetch) has completed for this entry. */
    val poiChecked: Boolean = false,
)
