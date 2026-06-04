package io.apptolast.paparcar.data.datasource.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "zones")
data class ZoneEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val iconKey: String,
    val createdAt: Long,
    val radiusMeters: Float = 250f,
    val isPrivate: Boolean = false,
)
