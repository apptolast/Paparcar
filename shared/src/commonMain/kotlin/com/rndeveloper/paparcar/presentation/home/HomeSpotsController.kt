package com.rndeveloper.paparcar.presentation.home

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.permissions.PermissionManager
import com.rndeveloper.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import com.rndeveloper.paparcar.domain.util.haversineMeters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/** One emission of the nearby-spots subscription: fresh data, or a stream error (recovered to empty). */
sealed interface SpotsUpdate {
    data class Data(val spots: List<Spot>) : SpotsUpdate
    /** The spots stream failed — the VM surfaces it as a snackbar effect. A [Data] (empty) follows. */
    data class Error(val message: String) : SpotsUpdate

    /**
     * ⛔ **Nobody has asked yet** — there is no query centre: no fix, and no camera the user has
     * pointed anywhere. NOT the same thing as an empty answer, and the difference is the whole
     * reason this exists: «no free spots nearby» is a verdict about the world, and we have not
     * looked. Same rule the complement lanes already follow.
     * [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001] [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001]
     */
    data object Unasked : SpotsUpdate
}

/**
 * Self-contained owner of the nearby-spots subscription and the query centre that drives it.
 *
 * The subscription depends ONLY on (CORE permission, query centre) — deliberately NOT on the
 * connectivity reconnect tick: the Firestore listener inside [ObserveNearbySpotsUseCase] auto-reconnects
 * and the centre is fed by the GPS stream (which owns its own reconnect), so a connectivity flap never
 * tears this subscription down. When the centre is null (no permission) an empty list flows down the
 * SAME pipe instead of a side state write, so the VM's single sink owns every state update. [SPOT-FLICKER-001]
 *
 * Built by Koin with its own collaborators and exposed as a **cold** [updates] flow the ViewModel
 * collects (applying `applyNewSpots`, which also prunes the selection — that logic reads
 * `activeSessions`/`selection` so it stays in the VM). Commands ([updateQueryCenter], [recenter],
 * [maybeRecenterOnPan]) mutate the internal query centre.
 */
@OptIn(ExperimentalCoroutinesApi::class, kotlin.time.ExperimentalTime::class)
class HomeSpotsController(
    private val permissionManager: PermissionManager,
    private val observeNearbySpots: ObserveNearbySpotsUseCase,
) {

    // Centre used for spot queries. Seeded from GPS on first fix; updated when the
    // user pans the map past SPOT_CAMERA_PAN_THRESHOLD_METERS in Browse mode.
    private val spotQueryCenter = MutableStateFlow<GpsPoint?>(null)

    /**
     * Cold flow of nearby-spot updates for the current (deduped) query centre.
     *
     * ⛔ **Ya no lo bloquea el permiso de ubicación.** Lo hacía, y el efecto era que sin permiso el
     * carril de plazas decía «no hay plazas cerca» sin haber preguntado NUNCA — panearas donde
     * panearas, porque el centro solo se sembraba desde un fix. El centro de consulta no es la
     * posición del usuario: es el punto del mapa que está mirando, y preguntar por él no necesita
     * ningún permiso. Sin centro se emite [SpotsUpdate.Unasked], que no es una lista vacía.
     * [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001]
     */
    val updates: Flow<SpotsUpdate> =
        spotQueryCenter
            .distinctUntilChanged { old, new -> old.closeEnoughTo(new) }
            .flatMapLatest { center ->
                if (center == null) {
                    flowOf<SpotsUpdate>(SpotsUpdate.Unasked)
                } else {
                    observeNearbySpots(center, ObserveNearbySpotsUseCase.DEFAULT_SEARCH_RADIUS_METERS)
                        .map<List<Spot>, SpotsUpdate> { spots -> SpotsUpdate.Data(spots) }
                        .catch { e ->
                            emit(SpotsUpdate.Error(e.message ?: ""))
                            emit(SpotsUpdate.Data(emptyList()))
                        }
                }
            }

    /** Keeps the query centre glued to GPS while the user hasn't panned away. */
    fun updateQueryCenter(gps: GpsPoint) {
        spotQueryCenter.value = gps
    }

    /** Recentres the query on the given point (recenter FAB). */
    fun recenter(gps: GpsPoint) {
        spotQueryCenter.value = gps
    }

    /**
     * Browse-mode pan handler: if the user has panned more than [SPOT_CAMERA_PAN_THRESHOLD_METERS] from
     * the current spot query centre, move the centre to the new camera position so the nearby-spots
     * query rebuilds against where the user is actually looking.
     *
     * ⛔ **También SIEMBRA el centro cuando no hay ninguno.** Antes salía con `false` mientras el
     * centro fuese nulo, o sea: sin fix, la consulta no arrancaba jamás por mucho que el usuario
     * llevase el mapa a su calle. El fix ya no es la única forma de decirle a la app de qué sitio
     * estás hablando — buscar una dirección o arrastrar el mapa también lo dicen.
     * [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001]
     *
     * ⛔ (0, 0) no es un sitio, es el Golfo de Guinea: la coordenada que un mapa reporta antes de
     * tener cámara. Sembrar ahí pediría las plazas del Atlántico. Mismo filtro que
     * `ViewportRefetch.isWorthAsking` y `viewportCentreOrGps`.
     *
     * @return true when the centre was moved — the VM flips `isSpotQueryCenteredOnUser` off in that case.
     */
    fun maybeRecenterOnPan(lat: Double, lon: Double): Boolean {
        if (lat == 0.0 && lon == 0.0) return false
        val current = spotQueryCenter.value
            ?: return true.also { spotQueryCenter.value = cameraPoint(lat, lon) }
        if (haversineMeters(current.latitude, current.longitude, lat, lon) <= SPOT_CAMERA_PAN_THRESHOLD_METERS) return false
        spotQueryCenter.value = cameraPoint(lat, lon)
        return true
    }

    /** Un punto de CÁMARA disfrazado de fix: sin precisión ni velocidad, porque no es una medición
     *  del teléfono — es el sitio del que el usuario está preguntando. */
    private fun cameraPoint(lat: Double, lon: Double) = GpsPoint(
        latitude = lat,
        longitude = lon,
        accuracy = 0f,
        timestamp = Clock.System.now().toEpochMilliseconds(),
        speed = 0f,
    )

    // Two nullable GpsPoints are "close enough" if both are null, or both non-null
    // and within SPOT_RESUBSCRIBE_THRESHOLD_METERS of each other.
    private fun GpsPoint?.closeEnoughTo(other: GpsPoint?): Boolean {
        if (this == null && other == null) return true
        if (this == null || other == null) return false
        return haversineMeters(latitude, longitude, other.latitude, other.longitude) < SPOT_RESUBSCRIBE_THRESHOLD_METERS
    }

    private companion object {
        // Both at 300m so GPS drift alone never triggers a Firestore reconnect —
        // only a genuine camera pan or location jump does.
        const val SPOT_RESUBSCRIBE_THRESHOLD_METERS = 300.0
        const val SPOT_CAMERA_PAN_THRESHOLD_METERS = 300.0
    }
}
