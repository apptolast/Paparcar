package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.domain.model.AddressAndPlace
import io.apptolast.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 *   `finally { withContext(NonCancellable) { ... } }` block so the shimmer
 *   never sticks even when the latest job is torn down mid-collect by a
 *   newer pan.
 *
 * State writes are delegated through callbacks rather than touching the
 * `HomeState` directly so the controller stays presentation-layer-agnostic
 * (no [HomeState] type, no [HomeViewModel] coupling) and remains trivial
 * to unit-test in isolation.
 */
class HomeGeocodingController(
    private val scope: CoroutineScope,
    private val getAddressAndPlace: GetAddressAndPlaceUseCase,
    private val onUserAddress: (AddressAndPlace) -> Unit,
    private val onCameraAddress: (AddressAndPlace) -> Unit,
    private val onCameraGeocodingChange: (Boolean) -> Unit,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    private val tag: String = TAG,
) {

    private var userGeocoderJob: Job? = null
    private var cameraDebounceJob: Job? = null
    private var cameraGeocoderJob: Job? = null

    /**
     * Cancel any in-flight user-location geocode and start a fresh one.
     * Updates flow to [onUserAddress] as Phase 1 / Phase 2 emissions arrive.
     */
    fun geocodeUserLocation(lat: Double, lon: Double) {
        userGeocoderJob?.cancel()
        userGeocoderJob = scope.launch {
            getAddressAndPlace(lat, lon)
                .catch { e -> PaparcarLogger.w(tag, "geocodeUserLocation error", e) }
                .collect { info -> onUserAddress(info) }
        }
    }

    /**
     * Debounce + Phase-2-survival camera geocoding. See class kdoc for the
     * exact cancellation policy and shimmer-flag invariants.
     */
    fun geocodeCameraLocation(lat: Double, lon: Double) {
        cameraDebounceJob?.cancel()
        cameraDebounceJob = scope.launch {
            delay(debounceMs)
            cameraGeocoderJob?.cancel()
            cameraGeocoderJob = scope.launch {
                onCameraGeocodingChange(true)
                try {
                    var addressReceived = false
                    getAddressAndPlace(lat, lon)
                        .catch { e -> PaparcarLogger.w(tag, "geocodeCameraLocation error", e) }
                        .collect { info ->
                            onCameraAddress(info)
                            if (!addressReceived) {
                                addressReceived = true
                                onCameraGeocodingChange(false)
                            }
                        }
                } finally {
                    withContext(NonCancellable) {
                        onCameraGeocodingChange(false)
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "HomeGeocoding"
        const val DEFAULT_DEBOUNCE_MS = 600L
    }
}
