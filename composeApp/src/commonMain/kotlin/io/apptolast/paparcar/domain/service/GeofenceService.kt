package io.apptolast.paparcar.domain.service

import io.apptolast.paparcar.domain.model.ParkingZone
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * Servicio para gestionar geofences y zonas de parking
 */
interface GeofenceService {

    /**
     * Verifica si una ubicación está dentro de una zona de parking
     */
    suspend fun isInParkingZone(latitude: Double, longitude: Double): Boolean

    /**
     * Obtiene zonas de parking cercanas
     */
    suspend fun getNearbyParkingZones(
        latitude: Double,
        longitude: Double,
        radiusMeters: Float
    ): List<ParkingZone>

    /**
     * Configura geofences para zonas de parking
     */
    suspend fun setupParkingGeofences(zones: List<ParkingZone>): Result<Unit>

    /**
     * Obtiene flow de eventos de geofence
     */
    fun getGeofenceEvents(): Flow<GeofenceEvent>

    /**
     * Sincroniza zonas de parking desde el servidor
     */
    suspend fun syncParkingZones(): Int

    /**
     * Obtiene todas las zonas de parking cacheadas
     */
    suspend fun getAllParkingZones(): List<ParkingZone>

    /**
     * Verifica si los servicios de ubicación están disponibles para geofences
     */
    suspend fun isGeofenceServiceAvailable(): Boolean
}

/**
 * Eventos de geofence
 */
sealed class GeofenceEvent {
    data class Entered(
        val zoneId: String,
        val zoneName: String,
        val timestamp: Long
    ) : GeofenceEvent()

    data class Exited(
        val zoneId: String,
        val zoneName: String,
        val timestamp: Long
    ) : GeofenceEvent()

    data class Dwell(
        val zoneId: String,
        val zoneName: String,
        val dwellTimeMs: Long,
        val timestamp: Long
    ) : GeofenceEvent()

    data class Error(
        val error: String,
        val timestamp: Long
    ) : GeofenceEvent()
}
