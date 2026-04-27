package io.apptolast.paparcar.data.datasource.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parking_sessions")
data class UserParkingEntity(
    @PrimaryKey val id: String,
    val userId: String = "",
    val vehicleId: String? = null,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long,
    val spotId: String? = null,
    val geofenceId: String? = null,
    val isActive: Boolean,
    // AddressInfo — populated asynchronously after the parking session is saved
    val addressStreet: String? = null,
    val addressCity: String? = null,
    val addressRegion: String? = null,
    val addressCountry: String? = null,
    // PlaceInfo — name + PlaceCategory enum name (e.g. "FUEL")
    val placeInfoName: String? = null,
    val placeInfoCategory: String? = null,
    // Detection reliability [0.0, 1.0]: 1.0=user confirmed, ~0.90=vehicle-exit, ~0.75=slow-path
    val detectionReliability: Float? = null,
)
