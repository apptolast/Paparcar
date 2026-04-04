package io.apptolast.paparcar.presentation.map

import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@OptIn(ExperimentalCoroutinesApi::class)
class ParkingLocationViewModel(
    private val locationDataSource: LocationDataSource,
    observeNearbySpots: ObserveNearbySpotsUseCase,
    private val userParkingRepository: UserParkingRepository,
) : BaseViewModel<ParkingLocationState, ParkingLocationIntent, ParkingLocationEffect>() {

    init {
        userParkingRepository.observeActiveSession()
            .onEach { session -> updateState { copy(userParking = session) } }
            .catch { e ->
                sendEffect(ParkingLocationEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
            }
            .launchIn(viewModelScope)

        locationDataSource.observeBalancedLocation()
            .onEach { location ->
                updateState { copy(isLoading = false, userLocation = location) }
            }
            .catch { e ->
                // GPS / location chain error
                updateState { copy(isLoading = false) }
                sendEffect(ParkingLocationEffect.ShowError(PaparcarError.Location.Unknown(e.message ?: "")))
            }
            .launchIn(viewModelScope)
    }

    override fun initState(): ParkingLocationState = ParkingLocationState()

    override fun handleIntent(intent: ParkingLocationIntent) {
        when (intent) {
            is ParkingLocationIntent.OnSpotSelected -> sendEffect(ParkingLocationEffect.NavigateToSpotDetails(intent.spotId))
        }
    }
}
