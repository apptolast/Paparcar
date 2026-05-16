package io.apptolast.paparcar.presentation.addspot

import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.usecase.location.GetLocationInfoUseCase
import io.apptolast.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import io.apptolast.paparcar.presentation.base.BaseViewModel
import io.apptolast.paparcar.presentation.map.CameraTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class, kotlin.time.ExperimentalTime::class)
class AddFreeSpotViewModel(
    private val permissionManager: PermissionManager,
    private val locationDataSource: LocationDataSource,
    private val reportSpotReleased: ReportSpotReleasedUseCase,
    private val observeNearbySpots: ObserveNearbySpotsUseCase,
    private val getLocationInfo: GetLocationInfoUseCase,
) : BaseViewModel<AddFreeSpotState, AddFreeSpotIntent, AddFreeSpotEffect>() {

    // Tracks the map's camera position so we can debounce reverse-geocoding without
    // re-running it on every onCameraMove tick. emits (lat, lon) pairs.
    private val cameraFlow = MutableStateFlow<Pair<Double, Double>?>(null)

    // Anchor used to observe nearby spots — fixed to the user's first GPS fix so
    // panning the map around does not thrash the Firestore listener.
    private var spotsAnchor: GpsPoint? = null

    init {
        permissionManager.permissionState
            .flatMapLatest { perm ->
                if (perm.allPermissionsGranted) locationDataSource.observeBalancedLocation()
                else emptyFlow()
            }
            .onEach { gps ->
                if (spotsAnchor == null) {
                    spotsAnchor = gps
                    startObservingNearbySpots(gps)
                    updateState {
                        copy(
                            userGpsPoint = gps,
                            initialCameraTarget = CameraTarget(
                                lat = gps.latitude,
                                lon = gps.longitude,
                            ),
                        )
                    }
                } else {
                    updateState { copy(userGpsPoint = gps) }
                }
            }
            .catch { /* GPS chain best-effort — manual pin still works */ }
            .launchIn(viewModelScope)

        cameraFlow
            .filterNotNull()
            .debounce(REVERSE_GEOCODE_DEBOUNCE_MS)
            .distinctUntilChanged()
            .flatMapLatest { (lat, lon) -> getLocationInfo(lat, lon) }
            .onEach { info -> updateState { copy(pinLocation = info) } }
            .catch { /* reverse-geocoding is best-effort */ }
            .launchIn(viewModelScope)
    }

    override fun initState(): AddFreeSpotState = AddFreeSpotState()

    override fun handleIntent(intent: AddFreeSpotIntent) {
        when (intent) {
            is AddFreeSpotIntent.CameraPositionChanged -> {
                updateState { copy(cameraLat = intent.lat, cameraLon = intent.lon) }
                cameraFlow.value = intent.lat to intent.lon
            }
            is AddFreeSpotIntent.ConfirmReport -> reportSpot()
        }
    }

    private fun startObservingNearbySpots(anchor: GpsPoint) {
        observeNearbySpots(anchor, NEARBY_SPOTS_RADIUS_METERS)
            .onEach { spots -> updateState { copy(nearbySpots = spots) } }
            .catch { /* best-effort overlay; absence shouldn't block reporting */ }
            .launchIn(viewModelScope)
    }

    private fun reportSpot() {
        val current = state.value
        val lat = current.cameraLat ?: current.userGpsPoint?.latitude
        val lon = current.cameraLon ?: current.userGpsPoint?.longitude
        if (lat == null || lon == null) {
            sendEffect(AddFreeSpotEffect.ShowError(PaparcarError.Location.ProviderDisabled))
            return
        }
        if (current.isReporting) return
        updateState { copy(isReporting = true) }
        viewModelScope.launch {
            val spotId = "manual_${Clock.System.now().toEpochMilliseconds()}"
            reportSpotReleased(lat, lon, spotId, SpotType.MANUAL_REPORT, confidence = 1f)
            updateState { copy(isReporting = false) }
            sendEffect(AddFreeSpotEffect.SpotReported)
        }
    }

    private companion object {
        const val NEARBY_SPOTS_RADIUS_METERS = 1000.0
        const val REVERSE_GEOCODE_DEBOUNCE_MS = 400L
    }
}
