package io.apptolast.paparcar.data.datasource.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AddressDto(
    val street: String? = null,
    val city: String? = null,
    val region: String? = null,
    val country: String? = null,
)

@Serializable
data class PlaceInfoDto(
    val name: String = "",
    val category: String = "",
)

@Serializable
data class SpotDto(
    // El ID se genera en el cliente y coincide con el ID del documento
    val id: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val reportedAt: Long = 0L,
    val reportedBy: String = "",
    val speed: Float = 0f,
    val address: AddressDto? = null,
    val placeInfo: PlaceInfoDto? = null,
    // Phase 4 — defaults preserve backward compat with existing Firestore documents
    val type: String = "AUTO_DETECTED",
    val confidence: Float = 1f,
    val sizeCategory: String? = null,
    val enRouteCount: Int = 0,
    val expiresAt: Long = 0L,
    // Phase 7 — community signals (default 0 for backward compat)
    val acceptCount: Int = 0,
    val rejectCount: Int = 0,
)
