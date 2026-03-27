package io.apptolast.paparcar.data.datasource.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parking_sessions")
data class UserParkingEntity(
    @PrimaryKey val id: String,
    val userId: String = "",
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
)
