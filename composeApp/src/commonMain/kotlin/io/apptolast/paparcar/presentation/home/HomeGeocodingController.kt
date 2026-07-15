package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.domain.model.AddressAndPlace
import io.apptolast.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** One geocoding result/flag for the home map — user address, camera address, or the shimmer flag. */
sealed interface GeocodeUpdate {
    data class UserAddress(val info: AddressAndPlace) : GeocodeUpdate
    data class CameraAddress(val info: AddressAndPlace) : GeocodeUpdate
    /** True while the camera geocoder is in flight — drives the address skeleton/shimmer. */
    data class CameraGeocoding(val active: Boolean) : GeocodeUpdate
}

/**
 * Encapsulates the two geocoding flows that decorate the home map:
 *
 * - **User location geocoding**: fired from every GPS emission, drives
 *   `HomeState.userAddressAndPlace`. A single in-flight job; latest wins.
 *
 * - **Camera location geocoding**: fired whenever the user pans the map
 *   centre. Two-stage cancellation:
 *     * `cameraDebounceJob` is cancelled on every move — drops the spam
 *       so the actual geocoder only fires when the pan settles.
 *     * `cameraGeocoderJob` is only cancelled when the debounce times
 *       out for a NEW location — Phase 2 (slow Overpass POI lookup)
 *       survives brief camera animations.
 *   `[BUG-GEOCODE-STUCK-001]` — the `isCameraGeocoding` flag is set inside
 *   the actual geocoder coroutine (not at pan time) and cleared in a
 *   `finally` block so the shimmer never sticks even when the latest job
 *   is torn down mid-collect by a newer pan. (`tryEmit` never suspends, so
 *   the old `withContext(NonCancellable)` wrapper is no longer needed for
 *   the clear to run inside a cancelled coroutine.)
 *
 * Built by Koin with its own use case and exposes the [updates] stream the
 * ViewModel collects into state — no `HomeState` type, no `HomeViewModel`
 * coupling, trivial to unit-test in isolation. The commands need a lifecycle
 * to launch their jobs in: the VM hands it over once via [attach].
 */
class HomeGeocodingController(
    private val getAddressAndPlace: GetAddressAndPlaceUseCase,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    private val tag: String = TAG,
) {

    // Buffered so tryEmit from a finally block (possibly cancelled coroutine) never drops; the VM's
    // Main.immediate collector drains it far faster than geocodes arrive.
    private val _updates = MutableSharedFlow<GeocodeUpdate>(extraBufferCapacity = UPDATES_BUFFER)

    /** Geocoding results + shimmer flag, in emission order. Collected by the VM into state. */
    val updates: SharedFlow<GeocodeUpdate> = _updates.asSharedFlow()

    private var scope: CoroutineScope? = null

    private var userGeocoderJob: Job? = null
    private var userGeocoderLat = Double.NaN
    private var userGeocoderLon = Double.NaN
    private var cameraDebounceJob: Job? = null
    private var cameraGeocoderJob: Job? = null
    private var cameraRequestLat = Double.NaN
    private var cameraRequestLon = Double.NaN

    /** Hands over the lifecycle the geocode jobs run in (the VM's scope). Call once before use. */
    fun attach(scope: CoroutineScope) {
        this.scope = scope
    }

    /**
     * Cancel any in-flight user-location geocode and start a fresh one — unless
     * the in-flight one is already answering (effectively) the same point.
     * GPS jitter re-asks every fix; without the dedup, a geocode slower than
     * the fix interval restarts forever and never emits. [GEOCODE-DEADLINE-001]
     * Updates flow to [updates] as Phase 1 / Phase 2 emissions arrive.
     */
    fun geocodeUserLocation(lat: Double, lon: Double) {
        val scope = scope ?: return
        if (userGeocoderJob?.isActive == true && isSamePoint(lat, lon, userGeocoderLat, userGeocoderLon)) return
        userGeocoderJob?.cancel()
        userGeocoderLat = lat
        userGeocoderLon = lon
        userGeocoderJob = scope.launch {
            getAddressAndPlace(lat, lon)
                .catch { e -> PaparcarLogger.w(tag, "geocodeUserLocation error", e) }
                .collect { info -> _updates.tryEmit(GeocodeUpdate.UserAddress(info)) }
        }
    }

    /**
     * Debounce + Phase-2-survival camera geocoding. See class kdoc for the
     * exact cancellation policy and shimmer-flag invariants. Re-asking for the
     * same point while a request is pending or in flight is a no-op — the
     * first ask's answer is the answer. [GEOCODE-DEADLINE-001]
     */
    fun geocodeCameraLocation(lat: Double, lon: Double) {
        val scope = scope ?: return
        val requestPendingOrActive = cameraDebounceJob?.isActive == true || cameraGeocoderJob?.isActive == true
        if (requestPendingOrActive && isSamePoint(lat, lon, cameraRequestLat, cameraRequestLon)) return
        cameraRequestLat = lat
        cameraRequestLon = lon
        cameraDebounceJob?.cancel()
        cameraDebounceJob = scope.launch {
            delay(debounceMs)
            cameraGeocoderJob?.cancel()
            cameraGeocoderJob = scope.launch {
                _updates.tryEmit(GeocodeUpdate.CameraGeocoding(true))
                try {
                    var addressReceived = false
                    getAddressAndPlace(lat, lon)
                        .catch { e -> PaparcarLogger.w(tag, "geocodeCameraLocation error", e) }
                        .collect { info ->
                            _updates.tryEmit(GeocodeUpdate.CameraAddress(info))
                            if (!addressReceived) {
                                addressReceived = true
                                _updates.tryEmit(GeocodeUpdate.CameraGeocoding(false))
                            }
                        }
                } finally {
                    _updates.tryEmit(GeocodeUpdate.CameraGeocoding(false))
                }
            }
        }
    }

    private companion object {
        const val TAG = "HomeGeocoding"
        const val DEFAULT_DEBOUNCE_MS = 600L
        const val UPDATES_BUFFER = 64

        /** ~11 m — mirrors the geocoder cache's cell size, so "same point" here
         *  means "same answer" there. */
        const val SAME_POINT_EPSILON_DEG = 0.0001

        fun isSamePoint(lat: Double, lon: Double, refLat: Double, refLon: Double): Boolean =
            kotlin.math.abs(lat - refLat) < SAME_POINT_EPSILON_DEG &&
                kotlin.math.abs(lon - refLon) < SAME_POINT_EPSILON_DEG
    }
}
