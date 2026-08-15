package io.apptolast.paparcar.presentation.map

import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.model.RouteInferenceResolution
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ParkingLocationViewModel(
    observeAdaptiveLocation: ObserveAdaptiveLocationUseCase,
    private val userParkingRepository: UserParkingRepository,
    private val vehicleRepository: VehicleRepository,
) : BaseViewModel<ParkingLocationState, ParkingLocationIntent, ParkingLocationEffect>() {

    init {
        userParkingRepository.observeActiveSessions()
            .map { it.firstOrNull() }
            .onEach { session -> updateState { copy(userParking = session) } }
            .catch { e ->
                sendEffect(ParkingLocationEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
            }
            .launchIn(viewModelScope)

        // Whole history, most-recent → oldest. Re-sorted defensively so the prev/next stepper is
        // deterministic regardless of the source's ordering (Room already sorts; fakes may not).
        userParkingRepository.observeAllSessions()
            .map { sessions -> sessions.sortedByDescending { it.location.timestamp } }
            .onEach { sessions -> updateState { copy(orderedSessions = sessions) } }
            .catch { e -> PaparcarLogger.w(TAG, "observeAllSessions failed — prev/next unavailable", e) }
            .launchIn(viewModelScope)

        // Vehicles resolve the focused session's real body shape + paint colour for the icon.
        vehicleRepository.observeVehicles()
            .onEach { vehicles -> updateState { copy(vehicles = vehicles) } }
            .catch { e -> PaparcarLogger.w(TAG, "observeVehicles failed — icon falls back to size shape", e) }
            .launchIn(viewModelScope)

        observeAdaptiveLocation()
            .onEach { location ->
                updateState { copy(isLoading = false, userLocation = location) }
            }
            .catch { e ->
                updateState { copy(isLoading = false) }
                sendEffect(ParkingLocationEffect.ShowError(PaparcarError.Location.Unknown(e.message ?: "")))
            }
            .launchIn(viewModelScope)
    }

    override fun initState(): ParkingLocationState = ParkingLocationState()

    override fun handleIntent(intent: ParkingLocationIntent) {
        when (intent) {
            is ParkingLocationIntent.OnSpotSelected ->
                sendEffect(ParkingLocationEffect.NavigateToSpotDetails(intent.spotId))

            is ParkingLocationIntent.SetFocusedSession ->
                updateState { copy(focusedSessionId = intent.sessionId) }

            // [orderedSessions] is newest-first, so stepping OLDER walks down the list (+1) and
            // stepping NEWER walks up (-1). Chevrons read as a timeline: ‹ past, › toward today.
            ParkingLocationIntent.FocusOlder -> stepFocus(+1)
            ParkingLocationIntent.FocusNewer -> stepFocus(-1)

            // The user's verdict on a reconstructed stretch — persisted (and synced); the observed
            // sessions flow re-renders the map with the answer applied. [ROUTE-GAP-HONEST-001]
            is ParkingLocationIntent.ResolveInferredRoute -> viewModelScope.launch {
                userParkingRepository
                    .resolveInferredRoute(
                        id = intent.sessionId,
                        resolution = if (intent.confirmed) {
                            RouteInferenceResolution.CONFIRMED
                        } else {
                            RouteInferenceResolution.REJECTED
                        },
                    )
                    .onFailure { e ->
                        sendEffect(ParkingLocationEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
                    }
            }
        }
    }

    /** Moves the focus by [delta] within [ParkingLocationState.orderedSessions], clamped to the ends. */
    private fun stepFocus(delta: Int) {
        updateState {
            val index = orderedSessions.indexOfFirst { it.id == focusedSessionId }
            if (index < 0) {
                this
            } else {
                val target = (index + delta).coerceIn(0, orderedSessions.lastIndex)
                copy(focusedSessionId = orderedSessions[target].id)
            }
        }
    }

    private companion object {
        const val TAG = "ParkingLocationVM"
    }
}
