package io.apptolast.paparcar.presentation.addspot

import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class, kotlin.time.ExperimentalTime::class)
class AddFreeSpotViewModel(
    private val permissionManager: PermissionManager,
    private val locationDataSource: LocationDataSource,
    private val reportSpotReleased: ReportSpotReleasedUseCase,
) : BaseViewModel<AddFreeSpotState, AddFreeSpotIntent, AddFreeSpotEffect>() {

    init {
        permissionManager.permissionState
            .flatMapLatest { perm ->
                if (perm.allPermissionsGranted) locationDataSource.observeBalancedLocation() else emptyFlow()
            }
            .onEach { gps -> updateState { copy(userGpsPoint = gps) } }
            .catch { /* GPS chain best-effort — pin still usable via map drag */ }
            .launchIn(viewModelScope)
    }

    override fun initState(): AddFreeSpotState = AddFreeSpotState()

    override fun handleIntent(intent: AddFreeSpotIntent) {
        when (intent) {
            is AddFreeSpotIntent.CameraPositionChanged ->
                updateState { copy(cameraLat = intent.lat, cameraLon = intent.lon) }
            is AddFreeSpotIntent.ConfirmReport -> reportSpot()
        }
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
}
