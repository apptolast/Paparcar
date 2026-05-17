package io.apptolast.paparcar.data.mapper

import io.apptolast.paparcar.data.datasource.local.room.ZoneEntity
import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.domain.model.ZoneIcon

fun Zone.toEntity(): ZoneEntity = ZoneEntity(
    id = id,
    userId = userId,
    name = name,
    lat = lat,
    lon = lon,
    iconKey = iconKey,
    createdAt = createdAt,
)

fun ZoneEntity.toDomain(): Zone = Zone(
    id = id,
    userId = userId,
    name = name,
    lat = lat,
    lon = lon,
    iconKey = iconKey,
    createdAt = createdAt,
)

/**
 * Domain → Firestore map for `users/{uid}/zones/{id}`. Mirrors the
 * VehicleRepository convention: a flat Map<String, Any?> so the gitlive
 * SDK serialises it without needing a @Serializable DTO.
 */
fun Zone.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "userId" to userId,
    "name" to name,
    "lat" to lat,
    "lon" to lon,
    "iconKey" to iconKey,
    "createdAt" to createdAt,
)

/**
 * Firestore map → entity. Unknown iconKey values from future clients
 * fall back to [ZoneIcon.DEFAULT] instead of dropping the row.
 * Coordinates that fail to deserialise return null so a single corrupt
 * document does not break the whole sync.
 */
fun Map<String, Any?>.toZoneEntity(): ZoneEntity? {
    val id = this["id"] as? String ?: return null
    val userId = this["userId"] as? String ?: return null
    val name = this["name"] as? String ?: return null
    val lat = (this["lat"] as? Number)?.toDouble() ?: return null
    val lon = (this["lon"] as? Number)?.toDouble() ?: return null
    val iconKey = (this["iconKey"] as? String)?.takeIf { it in ZoneIcon.PRESETS }
        ?: ZoneIcon.DEFAULT
    val createdAt = (this["createdAt"] as? Number)?.toLong() ?: 0L
    return ZoneEntity(
        id = id,
        userId = userId,
        name = name,
        lat = lat,
        lon = lon,
        iconKey = iconKey,
        createdAt = createdAt,
    )
}
