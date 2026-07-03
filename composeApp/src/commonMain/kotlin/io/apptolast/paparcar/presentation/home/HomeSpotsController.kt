package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import io.apptolast.paparcar.domain.util.haversineMeters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.time.Clock

/**
 * Owns the nearby-spots subscription and the query centre that drives it.
 *
 * The subscription depends ONLY on (CORE permission, query centre) — deliberately NOT on the
 * connectivity reconnect tick: the Firestore listener inside [ObserveNearbySpotsUseCase] auto-reconnects
 * and the centre is fed by the GPS stream (which owns its own reconnect), so a connectivity flap never
 * tears this subscription down. When the centre is null (no permission) an empty list flows down the
 * SAME pipe instead of a side state write, so the VM's single sink owns every state update. [SPOT-FLICKER-001]
 *
 * Like [HomeGeocodingController] the controller is presentation-layer-agnostic (no [HomeState], no
 * [HomeViewModel]): the freshly-fetched spots flow out through [onSpots] and the VM applies them (and
 * prunes the selection) in one place; errors flow out through [onError].
 */
@OptIn(ExperimentalCoroutinesApi::class, kotlin.time.ExperimentalTime::class)
class HomeSpotsController(
    private val scope: CoroutineScope,
    private val permissionManager: PermissionManager,
    private val observeNearbySpots: ObserveNearbySpotsUseCase,
    private val onSpots: (List<Spot>) -> Unit,
    private val onError: (message: String) -> Unit,
) {

    // Centre used for spot queries. Seeded from GPS on first fix; updated when the
    // user pans the map past SPOT_CAMERA_PAN_THRESHOLD_METERS in Browse mode.
    private val spotQueryCenter = MutableStateFlow<GpsPoint?>(null)

    /** Launches the nearby-spots subscription on [scope]. Call once from the VM's init. */
    fun start() {
        // Depends ONLY on (CORE permission, query centre). No reconnectTick: the
        // Firestore listener inside observeNearbySpots auto-reconnects, and the
        // centre is already fed by the GPS stream (which owns its own reconnect),
        // so a connectivity flap never tears this subscription down. When the
        // centre is null (no permission), we emit an empty list down the SAME
        // pipe instead of mutating state inside the transform — one sink,
        // applyNewSpots, owns every state write. [SPOT-FLICKER-001]
        combine(permissionManager.permissionState, spotQueryCenter) { perm, center ->
            if (perm.hasCorePermissions) center else null
        }
            .distinctUntilChanged { old, new -> old.closeEnoughTo(new) }
            .flatMapLatest { center ->
                if (center == null) {
                    flowOf(emptyList())
                } else {
                    observeNearbySpots(center, ObserveNearbySpotsUseCase.DEFAULT_SEARCH_RADIUS_METERS)
                        .catch { e ->
                            onError(e.message ?: "")
                            emit(emptyList())
                        }
                }
            }
            .onEach { spots -> onSpots(spots) }
            .launchIn(scope)
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
     * query rebuilds against where the user is actually looking. Only relevant once the centre has been
     * seeded by the first GPS fix.
     *
     * @return true when the centre was moved — the VM flips `isSpotQueryCenteredOnUser` off in that case.
     */
    fun maybeRecenterOnPan(lat: Double, lon: Double): Boolean {
        val current = spotQueryCenter.value ?: return false
        if (haversineMeters(current.latitude, current.longitude, lat, lon) <= SPOT_CAMERA_PAN_THRESHOLD_METERS) return false
        spotQueryCenter.value = GpsPoint(
            latitude = lat,
            longitude = lon,
            accuracy = 0f,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            speed = 0f,
        )
        return true
    }

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
