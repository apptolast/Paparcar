package io.apptolast.paparcar.domain.usecase.detection

import io.apptolast.paparcar.domain.detection.DetectionRuntimeState
import io.apptolast.paparcar.domain.detection.ParkingStrategyResolver
import io.apptolast.paparcar.domain.detection.PostDetectionLifecycle
import io.apptolast.paparcar.domain.detection.ServicePresence
import io.apptolast.paparcar.domain.detection.resolvePostDetectionLifecycle
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Emits `true` while there is a WATCH GAP: the departure watcher SHOULD be live (a Coordinator car is
 * parked and auto-detection is on) but the service is [ServicePresence.Dead]. [DET-WATCH-REACTIVATE-001]
 *
 * The "should be live" half is not re-derived here — it is [resolvePostDetectionLifecycle], the same
 * pure rule the service applies when deciding whether to stay resident after a trip. One rule, two
 * readers: the service closing a session and the app noticing the watcher is missing.
 *
 * Being a STREAM is the point. The predecessor self-heal read `observeActiveSessions().first()` once
 * at process start, so a clean install (Room empty until the Firestore sync lands) saw "nothing
 * parked", skipped the start, and left the watch dead until the next process launch — the exact
 * 2026-08-14 report. Here the gap simply closes when the session arrives, whenever that is.
 */
class ObserveDepartureWatchGapUseCase(
    private val userParkingRepository: UserParkingRepository,
    private val vehicleRepository: VehicleRepository,
    private val strategyResolver: ParkingStrategyResolver,
    private val appPreferences: AppPreferences,
    private val detectionRuntime: DetectionRuntimeState,
) {
    operator fun invoke(): Flow<Boolean> = combine(
        userParkingRepository.observeActiveSessions(),
        vehicleRepository.observeVehicles(),
        appPreferences.observeAutoDetectParking(),
        detectionRuntime.presence,
    ) { sessions, vehicles, autoDetectEnabled, presence ->
        // Only a DEAD service is a gap: Sentry and Active are both genuinely watching.
        presence == ServicePresence.Dead &&
            resolvePostDetectionLifecycle(
                autoDetectEnabled = autoDetectEnabled,
                hasParkedSession = sessions.isNotEmpty(),
                // Reads the BT adapter at call time, exactly like the service's epilogue: a car
                // connected over Bluetooth is covered by the ACL receiver and needs no resident
                // watcher, so that is NOT a gap. [DET-STRATEGY-GATE-001]
                strategy = strategyResolver.strategyFor(vehicles),
            ) == PostDetectionLifecycle.EnterSentry
    }.distinctUntilChanged()
}
