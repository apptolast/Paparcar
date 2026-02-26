package io.apptolast.paparcar.data.datasource.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parking_sessions")
data class UserParkingSessionEntity(
    @PrimaryKey val id: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long,
    val spotId: String? = null,
    val geofenceId: String? = null,
    val isActive: Boolean,
)
