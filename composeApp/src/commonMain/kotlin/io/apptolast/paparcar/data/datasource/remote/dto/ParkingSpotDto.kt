package io.apptolast.paparcar.data.datasource.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ParkingSpotDto(
    @SerialName("id") val id: String = "",
    @SerialName("lat") val latitude: Double,
    @SerialName("lon") val longitude: Double,
    @SerialName("geohash") val geohash: String = "",
    @SerialName("ts") val timestampEpochMs: Long,
    @SerialName("userId") val userId: String = "",
    @SerialName("confidence") val confidence: Float = 1.0f,
    @SerialName("accuracy") val accuracy: Float = 0f,
    @SerialName("verified") val isVerified: Boolean = false
)
