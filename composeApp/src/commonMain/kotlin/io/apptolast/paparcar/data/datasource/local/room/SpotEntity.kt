package io.apptolast.paparcar.data.datasource.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room cache entry for a community parking spot received from Firestore.
 *
 * Acts as the offline-first local source for [SpotRepositoryImpl].
 * All columns mirror [SpotDto] so the conversion is lossless.
 */
@Entity(tableName = "cached_spots")
data class SpotEntity(
    @PrimaryKey val id: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val reportedAt: Long,
    val reportedBy: String,
    val speed: Float = 0f,
    val addressStreet: String? = null,
    val addressCity: String? = null,
    val addressRegion: String? = null,
    val addressCountry: String? = null,
    val placeInfoName: String? = null,
    val placeInfoCategory: String? = null,
    val type: String = "AUTO_DETECTED",
    val confidence: Float = 1f,
    val sizeCategory: String? = null,
    val enRouteCount: Int = 0,
    val expiresAt: Long = 0L,
)
