@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.map

import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.domain.model.RouteInferenceResolution
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock

class ParkingHistoryViewModel(
    observeAdaptiveLocation: ObserveAdaptiveLocationUseCase,
    private val userParkingRepository: UserParkingRepository,
    private val vehicleRepository: VehicleRepository,
) : BaseViewModel<ParkingHistoryState, ParkingHistoryIntent, ParkingHistoryEffect>() {

    init {
        // The ACTIVE session is deliberately NOT observed here. It only ever fed a fallback that
        // painted today's parking on the map while a HISTORIC one was still resolving — another
        // session's data dressed as this one. [UI-HISTORY-DETAIL-MUST-NOT-SPEAK-BEFORE-IT-KNOWS-001]

        // Whole history, most-recent → oldest. Re-sorted defensively so the prev/next stepper is
        // deterministic regardless of the source's ordering (Room already sorts; fakes may not).
        // Observed unfiltered on purpose: the stepper's per-vehicle scope is DERIVED from the focused
        // session in the state, so changing focus never re-subscribes. [HISTORY-DETAIL-VEHICLE-SCOPE-001]
        userParkingRepository.observeAllSessions()
            .map { sessions -> sessions.sortedByDescending { it.location.timestamp } }
            .onEach { sessions -> updateState { copy(allSessions = sessions) } }
            .catch { e -> PaparcarLogger.w(TAG, "observeAllSessions failed — prev/next unavailable", e) }
            .launchIn(viewModelScope)

        // Vehicles resolve the focused session's real body shape + paint colour for the icon.
        vehicleRepository.observeVehicles()
            .onEach { vehicles -> updateState { copy(vehicles = vehicles) } }
            .catch { e -> PaparcarLogger.w(TAG, "observeVehicles failed — icon falls back to size shape", e) }
            .launchIn(viewModelScope)

        // No loading flag rides on this stream: what it measures is a GPS fix, which says nothing
        // about whether the history has arrived — and the old `isLoading` it maintained was read by
        // nobody. [UI-HISTORY-DETAIL-MUST-NOT-SPEAK-BEFORE-IT-KNOWS-001]
        observeAdaptiveLocation()
            .onEach { location -> updateState { copy(userLocation = location) } }
            .catch { e ->
                sendEffect(ParkingHistoryEffect.ShowError(PaparcarError.Location.Unknown(e.message ?: "")))
            }
            .launchIn(viewModelScope)
    }

    // `allSessions = null`: nothing has been read yet. The screen shows a skeleton until Room speaks.
    override fun initState(): ParkingHistoryState = ParkingHistoryState(allSessions = null)

    override fun handleIntent(intent: ParkingHistoryIntent) {
        when (intent) {
            is ParkingHistoryIntent.SetFocusedSession ->
                updateState { copy(focusedSessionId = intent.sessionId) }

            // [orderedSessions] is newest-first, so stepping OLDER walks down the list (+1) and
            // stepping NEWER walks up (-1). Chevrons read as a timeline: ‹ past, › toward today.
            ParkingHistoryIntent.FocusOlder -> stepFocus(+1)
            ParkingHistoryIntent.FocusNewer -> stepFocus(-1)

            // A retraction, straight to the repository — there is no decision to make here, exactly
            // like ResolveInferredRoute below. The row leaves the history and survives for
            // diagnostics. [PARK-A-HISTORIC-PARKING-CAN-BE-WITHDRAWN-001]
            is ParkingHistoryIntent.WithdrawParking -> viewModelScope.launch {
                userParkingRepository
                    .retractParkingSession(intent.sessionId, Clock.System.now().toEpochMilliseconds())
                    .onSuccess { sendEffect(ParkingHistoryEffect.Withdrawn) }
                    .onFailure { e ->
                        sendEffect(ParkingHistoryEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
                    }
            }

            // The user's verdict on a reconstructed stretch — persisted (and synced); the observed
            // sessions flow re-renders the map with the answer applied. [ROUTE-GAP-HONEST-001]
            is ParkingHistoryIntent.ResolveInferredRoute -> viewModelScope.launch {
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
                        sendEffect(ParkingHistoryEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
                    }
            }
        }
    }

    /**
     * Moves the focus by [delta] within [ParkingHistoryState.orderedSessions], clamped to the ends.
     * That list is already scoped to the focused session's vehicle, so the step cannot leave the car.
     */
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
