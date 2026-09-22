package com.rndeveloper.paparcar.domain.service

import com.rndeveloper.paparcar.domain.detection.fence.GeofenceRegistrationFailure
import kotlinx.coroutines.flow.Flow

/**
 * Platform geofence registration port. [IOS-F0-04] The CONTRACT is command + observe; how the OS
 * delivers a transition into the process is platform INFRASTRUCTURE, deliberately outside it:
 *
 *  - **Android**: the OS fires a `PendingIntent.getForegroundService` that REVIVES a dead process
 *    straight into `CoordinatorDetectionService` (RC 9100 EXIT / 9101 enter-cure / 9102 witness —
 *    load-bearing, see the manifest and [DET-G-01]). The service acts on the intent decisively
 *    and republishes the event on [GeofenceEventBus] for in-process observers.
 *  - **iOS**: the OS relaunches the app (even after reboot) and calls the CoreLocation delegate,
 *    which publishes on the bus — there the bus IS the primary consumption path.
 *
 * **Re-registration semantics** (why no common re-register API exists): Play Services can lose
 * registrations (boot, GMS wipe) — Android re-registers via its boot receiver / janitor / cure
 * paths. CoreLocation's `monitoredRegions` persist across reboots and app kills on their own —
 * an iOS impl must RECONCILE OS regions against Room sessions on start, never blind-re-register.
 */
interface GeofenceManager {

    /**
     * The platform's radius floor: [createGeofence] clamps any smaller request UP to this value.
     * Default 0 = no platform floor (Android: sizing lives in `geofenceRadiusFor`, 60–200 m).
     * iOS declares 100 m (Apple's practical CLCircularRegion minimum) — callers sizing fences
     * must read the floor here instead of assuming the registered radius equals the requested
     * one. [IOS-F0-04, decisión 6]
     */
    val minRadiusMeters: Float get() = 0f

    /** Registers a fence at the given center. [radiusMeters] below [minRadiusMeters] is clamped
     *  up by the implementation. */
    suspend fun createGeofence(
        geofenceId: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
    ): Result<Unit>

    suspend fun removeGeofence(geofenceId: String): Result<Unit>

    /**
     * Deregisters every geofence this app has registered with the OS, regardless of id.
     *
     * Geofences live in Play Services / CoreLocation, not in Room, so wiping local storage
     * (sign-out, account switch) does not remove them — they would keep monitoring and could
     * fire an exit transition under the next user's session. This is the single teardown hook
     * that guarantees session isolation at the OS level. [SESSION-ISOLATION-001]
     */
    suspend fun removeAllGeofences(): Result<Unit>

    /** In-process observation stream of geofence transitions — the [GeofenceEventBus] surface.
     *  See the bus contract for its delivery guarantees (hot, broadcast, non-durable). */
    fun getGeofenceEvents(): Flow<GeofenceEvent>
}

sealed class GeofenceEvent {
    data class Exited(val geofenceId: String, val timestamp: Long) : GeofenceEvent()

    /**
     * La plataforma dice que una valla no se pudo vigilar.
     *
     * [IOS-A-DETECTION-THAT-FAILS-MUST-LEAVE-A-TRACE-001] Los dos campos nuevos existen porque este
     * evento tenía la información **dentro de una frase**: iOS metía `"$regionId: $descripción"` en
     * [error], así que el consumidor no podía decir DE QUÉ VALLA hablaba sin partir un string, ni
     * archivar el porqué. Y un fallo de vigilancia no es una línea de log: es la diferencia entre un
     * coche vigilado y uno que no lo está. Ambos opcionales — Android sigue emitiéndolo como
     * siempre, con su código de Play Services en la frase.
     */
    data class Error(
        val error: String,
        val timestamp: Long,
        /** La valla afectada (= el id de sesión), cuando la plataforma la nombra. */
        val geofenceId: String? = null,
        /** Por qué, en el vocabulario que ya usan el janitor y la red de seguridad. */
        val failure: GeofenceRegistrationFailure? = null,
    ) : GeofenceEvent()
}

/**
 * Un registro de valla que falló **por una razón que sabemos nombrar**, para que el
 * `Result.failure` que sube hasta `ConfirmParkingUseCase` no llegue como un `Throwable` anónimo.
 * [IOS-A-DETECTION-THAT-FAILS-MUST-LEAVE-A-TRACE-001]
 *
 * ⛔ Android NO lo usa: allí el fallo llega como la excepción de Play Services y se traduce en el
 * propio call site con `Throwable.toGeofenceRegistrationFailure()`, que necesita los tipos de GMS y
 * por tanto no puede vivir en commonMain. Las dos vías acaban en el MISMO
 * [GeofenceRegistrationFailure]; lo que esta excepción añade es poder cruzar la frontera de
 * plataforma sin perder el porqué.
 */
class GeofenceRegistrationException(
    val reason: GeofenceRegistrationFailure,
    override val message: String,
) : Exception(message)
