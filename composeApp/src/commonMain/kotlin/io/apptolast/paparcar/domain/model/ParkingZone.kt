package io.apptolast.paparcar.domain.model

import kotlinx.datetime.Instant

/**
 * Representa una zona de parking geográfica.
 * Este es el modelo de dominio puro y único.
 */
data class ParkingZone(
    val id: String,
    val name: String,
    val center: Pair<Double, Double>, // latitude, longitude
    val radius: Float, // en metros
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val restrictions: List<String> = emptyList(),
    val maxStayMinutes: Int? = null,
    val priceInfo: String? = null
)
