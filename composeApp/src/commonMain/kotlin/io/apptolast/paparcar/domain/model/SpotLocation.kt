package io.apptolast.paparcar.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SpotLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long,
    val speed: Float,
)

//package io.apptolast.paparcar.domain.model
//
//import kotlin.math.*
//
///**
// * Representa una ubicación geográfica con metadatos de precisión.
// * Modelo del dominio para coordenadas GPS.
// */
//data class SpotLocation(
//    val latitude: Double,
//    val longitude: Double,
//    val accuracy: Float,
//    val timestamp: Long
//) {
//    /**
//     * Calcula la distancia en metros a otra coordenada usando fórmula de Haversine
//     */
//    fun distanceTo(latitude: Double, longitude: Double): Float {
//        val earthRadius = 6371000.0 // Radio de la Tierra en metros
//
//        val latDistance = Math.toRadians(latitude - this.latitude)
//        val lonDistance = Math.toRadians(longitude - this.longitude)
//
//        val a = sin(latDistance / 2) * sin(latDistance / 2) +
//                cos(Math.toRadians(this.latitude)) * cos(Math.toRadians(latitude)) *
//                sin(lonDistance / 2) * sin(lonDistance / 2)
//
//        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
//
//        return (earthRadius * c).toFloat()
//    }
//
//    /**
//     * Calcula la distancia en metros a otra SpotLocation
//     */
//    fun distanceTo(other: SpotLocation): Float {
//        return distanceTo(other.latitude, other.longitude)
//    }
//
//    /**
//     * Valida si la ubicación tiene precisión suficiente para ser considerada
//     */
//    fun hasValidAccuracy(): Boolean = accuracy in 0f..50f // 50 metros máximo
//
//    /**
//     * Verifica si las coordenadas son válidas (dentro de rangos normales)
//     */
//    fun isValidCoordinates(): Boolean {
//        return latitude in -90.0..90.0 && longitude in -180.0..180.0
//    }
//
//    companion object {
//        /**
//         * Umbral de precisión válida por defecto (50 metros)
//         */
//        const val VALID_ACCURACY_THRESHOLD_METERS = 50f
//    }
//}

