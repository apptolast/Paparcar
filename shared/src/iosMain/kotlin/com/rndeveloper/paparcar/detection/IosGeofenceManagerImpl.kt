@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rndeveloper.paparcar.detection

import com.rndeveloper.paparcar.domain.detection.fence.GeofenceRegistrationFailure
import com.rndeveloper.paparcar.domain.service.GeofenceEvent
import com.rndeveloper.paparcar.domain.service.GeofenceEventBus
import com.rndeveloper.paparcar.domain.service.GeofenceManager
import com.rndeveloper.paparcar.domain.service.GeofenceRegistrationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import platform.CoreLocation.CLCircularRegion
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.CLRegion
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject

/**
 * iOS implementation of [GeofenceManager] backed by [CLLocationManager] region monitoring.
 *
 * Differences vs. the Android (`GeofencingClient`) implementation:
 * - iOS limits an app to **20 monitored regions** total. Paparcar registers one
 *   geofence per active parking session, so this is comfortably within the cap.
 * - iOS has no equivalent of `NEVER_EXPIRE` — registered regions persist until
 *   explicitly stopped via [removeGeofence] or until the app is uninstalled.
 * - There is no `loiteringDelay` knob. We only monitor exit transitions, matching
 *   the Android `GEOFENCE_TRANSITION_EXIT` setup.
 * - Authorisation must be "Always" (background) for transitions to fire while the
 *   app is suspended; the [IosPermissionRequester] flow already prompts for this.
 *
 * Registrations are routed through `Dispatchers.Main` because [CLLocationManager]
 * must be called on a thread with an active runloop.
 */
class IosGeofenceManagerImpl(
    private val geofenceEventBus: GeofenceEventBus,
) : GeofenceManager {

    /** [IOS-F0-04, decisión 6] Apple's practical CLCircularRegion minimum: below ~100 m exits
     *  are delivered late or not at all (GPS noise floor). Contract-visible so callers sizing
     *  fences (`geofenceRadiusFor`, 60–200 m) can read the effective floor — this used to be a
     *  silent private clamp. */
    override val minRadiusMeters: Float = 100f

    private val manager = CLLocationManager()

    private val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(manager: CLLocationManager, didExitRegion: CLRegion) {
            geofenceEventBus.emit(
                GeofenceEvent.Exited(
                    geofenceId = didExitRegion.identifier,
                    timestamp = nowMillis(),
                ),
            )
        }

        override fun locationManager(
            manager: CLLocationManager,
            monitoringDidFailForRegion: CLRegion?,
            withError: NSError,
        ) {
            val regionId = monitoringDidFailForRegion?.identifier
            // El id y la razón viajan en CAMPOS, no dentro de la frase: quien escucha tiene que
            // poder decir de qué valla habla y archivar el porqué sin partir un string.
            // [IOS-A-DETECTION-THAT-FAILS-MUST-LEAVE-A-TRACE-001]
            geofenceEventBus.emit(
                GeofenceEvent.Error(
                    error = "${regionId ?: "unknown"}: ${withError.localizedDescription}",
                    timestamp = nowMillis(),
                    geofenceId = regionId,
                    failure = GeofenceRegistrationFailure.fromCLErrorCode(withError.code),
                ),
            )
        }
    }

    init {
        manager.delegate = delegate
    }

    override suspend fun createGeofence(
        geofenceId: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
    ): Result<Unit> = withContext(Dispatchers.Main) {
        // ⛔ **`startMonitoringForRegion` NO lanza.** Si la autorización no es «Siempre», si la clase
        // no se puede monitorizar o si se pasó el tope de 20 regiones, falla ASÍNCRONAMENTE por el
        // delegate — o no hace nada. Así que el `runCatching` que envolvía esta llamada devolvía
        // `success` SIEMPRE, y la app daba por vigilado un coche que no tenía ninguna región puesta.
        // Sin valla no hay EXIT, y sin EXIT el carril de salida no se dispara jamás: es el
        // «no funcionó ni una sola vez» de la issue #125, sin una sola línea que lo dijera.
        //
        // Se pregunta ANTES lo que iOS sí sabe contestar de inmediato. No cubre todos los fallos
        // posibles —los que sólo aparecen después siguen llegando por el delegate, ahora con su
        // razón—, pero convierte los tres frecuentes en un `Result.failure` con nombre, que es lo
        // que `ConfirmParkingUseCase` ya sabe registrar y escalar al janitor.
        // [IOS-A-DETECTION-THAT-FAILS-MUST-LEAVE-A-TRACE-001]
        preflight(geofenceId)?.let { reason ->
            return@withContext Result.failure(
                GeofenceRegistrationException(reason, "region monitoring unavailable for $geofenceId"),
            )
        }
        runCatching {
            val effectiveRadius = radiusMeters.coerceAtLeast(minRadiusMeters).toDouble()
            val region = CLCircularRegion(
                center = CLLocationCoordinate2DMake(latitude, longitude),
                radius = effectiveRadius,
                identifier = geofenceId,
            ).apply {
                setNotifyOnEntry(false)
                setNotifyOnExit(true)
            }
            manager.startMonitoringForRegion(region)
        }
    }

    /**
     * Lo que iOS puede decir sin esperar al delegate, en el orden en que importa. Null = adelante.
     * [IOS-A-DETECTION-THAT-FAILS-MUST-LEAVE-A-TRACE-001]
     *
     * El tope se mide **descontando la valla que se va a re-registrar**: volver a pedir una región
     * que ya está monitorizada es idempotente y no consume un hueco nuevo, así que contarla haría
     * fallar precisamente al janitor, que existe para eso.
     */
    private fun preflight(geofenceId: String): GeofenceRegistrationFailure? {
        if (manager.authorizationStatus != kCLAuthorizationStatusAuthorizedAlways) {
            return GeofenceRegistrationFailure.PERMISSION_DENIED
        }
        if (!CLLocationManager.isMonitoringAvailableForClass(CLCircularRegion)) {
            return GeofenceRegistrationFailure.NOT_AVAILABLE
        }
        val already = manager.monitoredRegions.filterIsInstance<CLRegion>().any { it.identifier == geofenceId }
        if (!already && manager.monitoredRegions.size >= MAX_MONITORED_REGIONS) {
            return GeofenceRegistrationFailure.TOO_MANY_GEOFENCES
        }
        return null
    }

    override suspend fun removeGeofence(geofenceId: String): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            val region = manager.monitoredRegions
                .filterIsInstance<CLRegion>()
                .firstOrNull { it.identifier == geofenceId }
            if (region != null) manager.stopMonitoringForRegion(region)
        }
    }

    override suspend fun removeAllGeofences(): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            manager.monitoredRegions
                .filterIsInstance<CLRegion>()
                .forEach { manager.stopMonitoringForRegion(it) }
        }
    }

    override fun getGeofenceEvents(): Flow<GeofenceEvent> = geofenceEventBus.events

    private fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * MILLIS_PER_SECOND).toLong()

    private companion object {
        const val MILLIS_PER_SECOND = 1_000.0

        /** El tope que iOS impone por app. Paparcar registra una valla por sesión aparcada, así que
         *  llegar aquí significa una fuga de vallas sin quitar, no un uso normal. */
        const val MAX_MONITORED_REGIONS = 20
    }
}
