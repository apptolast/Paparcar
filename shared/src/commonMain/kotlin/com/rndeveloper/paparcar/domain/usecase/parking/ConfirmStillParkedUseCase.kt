package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.detection.DepartureConfirmationListener
import com.rndeveloper.paparcar.domain.detection.StillParkedQuestion
import com.rndeveloper.paparcar.domain.detection.isCurrentFor
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.SpotRepository
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Ratifies an EXISTING parking. It never creates, moves, releases or restarts a trip. */
@OptIn(ExperimentalTime::class)
class ConfirmStillParkedUseCase(
    private val preferences: AppPreferences,
    private val parkingRepository: UserParkingRepository,
    private val spotRepository: SpotRepository,
    private val departureListener: DepartureConfirmationListener,
    private val notifications: AppNotificationManager,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val mutex = Mutex()

    /** False means that this question/session has already been superseded. */
    suspend operator fun invoke(question: StillParkedQuestion): Result<Boolean> = mutex.withLock {
        try {
            if (!isCurrent(question)) return@withLock Result.success(false)
            val parking = parkingRepository.getActiveSessionByGeofence(question.geofenceId)
            if (parking == null) {
                // A saved replacement has retired this question's session. Only retire its own
                // question, never the one that may have arrived while the repository was read.
                if (isCurrent(question)) {
                    preferences.setStillParkedQuestion(null)
                    notifications.dismiss(AppNotificationManager.STILL_PARKED_NOTIFICATION_ID)
                }
                return@withLock Result.success(false)
            }
            if (!isCurrent(question)) return@withLock Result.success(false)

            // A human refutes a provisional departure. Withdraw the public deduction before
            // acknowledging it, so a failed withdrawal remains retryable from the same card.
            if (parking.provisionalDepartureAtMs != null) {
                spotRepository.retractSpot(parking.id).getOrThrow()
                if (!isCurrent(question)) return@withLock Result.success(false)
                parkingRepository.markProvisionalDeparture(parking.id, null).getOrThrow()
            }

            // Repository/network suspension must not let an old answer clear a newer question.
            if (!isCurrent(question) ||
                parkingRepository.getActiveSessionByGeofence(question.geofenceId)?.id != parking.id
            ) return@withLock Result.success(false)
            if (!isCurrent(question)) return@withLock Result.success(false)

            departureListener.notifyDepartureDismissed(question.geofenceId)
            preferences.setStillParkedQuestion(question.copy(confirmedHereAtMs = nowMs()))
            notifications.dismiss(AppNotificationManager.STILL_PARKED_NOTIFICATION_ID)
            Result.success(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PaparcarLogger.w("ConfirmStillParked", "Could not ratify the saved parking", e)
            Result.failure(e)
        }
    }

    private fun isCurrent(question: StillParkedQuestion): Boolean =
        preferences.stillParkedQuestion()?.isCurrentFor(question.geofenceId, question.shownAtMs) == true
}
