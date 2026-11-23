@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.analytics.AnalyticsEvent
import com.rndeveloper.paparcar.domain.analytics.AnalyticsTracker
import com.rndeveloper.paparcar.domain.analytics.SessionEndReason
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.service.GeofenceManager
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import kotlin.time.Clock

/**
 * Withdraws a false parking without publishing a free spot.
 * Only an active session may be retracted; stale notification actions are harmless.
 * Persistence failures are returned so callers never resume a trip after a failed correction.
 */
class RevertParkingUseCase(
    private val userParkingRepository: UserParkingRepository,
    private val geofenceService: GeofenceManager,
    private val notificationPort: AppNotificationManager,
    private val detectionEventLogger: DetectionEventLogger? = null,
    private val analyticsTracker: AnalyticsTracker? = null,
) {
    suspend operator fun invoke(parkingId: String): Result<Unit> = revertActive(parkingId).map { }

    /** Returns the corrected session, or null if the action referred to an inactive session. */
    suspend fun revertActive(parkingId: String): Result<UserParking?> {
        val now = Clock.System.now().toEpochMilliseconds()
        val session = try {
            userParkingRepository.observeActiveSessions().firstOrNull()
                ?.firstOrNull { it.id == parkingId }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (session == null) {
            detectionEventLogger?.log(
                DetectionEvent.Decision(
                    sessionId = parkingId,
                    timestampMs = now,
                    outcome = "REVERT_STALE",
                    pathLabel = null,
                    reason = "not_an_active_session",
                ),
            )
            notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
            return Result.success(null)
        }

        // Retract first: if closing fails, the active row remains resolvable for a safe retry.
        // Neither operation publishes a spot or declares a different active vehicle.
        userParkingRepository.retractParkingSession(parkingId, now)
            .onFailure { return Result.failure(it) }
        userParkingRepository.clearActiveParkingSession(
            parkingId,
            endedAtMs = now,
            publishedSpot = false,
            // [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001] A park the user has just said never
            // happened leaves no question behind: the stop it would ask about is the one being
            // withdrawn. The row is retracted in the line above, so nothing will read it anyway.
            refuelStop = null,
        ).onFailure { return Result.failure(it) }

        geofenceService.removeGeofence(parkingId).onFailure {
            // A leftover fence cannot release this now-inactive session.
            PaparcarLogger.w("PARKDIAG/Revert", "Could not remove inactive parking fence", it)
        }
        notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
        detectionEventLogger?.log(
            DetectionEvent.Reverted(
                sessionId = parkingId,
                timestampMs = now,
                sessionAgeMs = now - session.location.timestamp,
                location = session.location,
            ),
        )
        analyticsTracker?.track(
            AnalyticsEvent.ParkingSessionEnded(
                location = session.location,
                reason = SessionEndReason.REVERTED,
            ),
        )
        return Result.success(session)
    }
}
