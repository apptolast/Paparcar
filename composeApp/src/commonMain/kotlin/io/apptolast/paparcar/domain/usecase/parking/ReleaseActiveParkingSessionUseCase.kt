@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import kotlin.time.Clock

/**
 * Releases the user's active parking session:
 * 1. Schedules a durable spot-released report (WorkManager) so it survives process death.
 * 2. Clears the active session from the local database and Firestore.
 *
 * Returns [Result.failure] if clearing the session fails. The spot report is
 * always enqueued first so the community spot is never lost even if the clear fails.
 */
class ReleaseActiveParkingSessionUseCase(
    private val reportSpotReleased: ReportSpotReleasedUseCase,
    private val userParkingRepository: UserParkingRepository,
) {
    suspend operator fun invoke(
        lat: Double,
        lon: Double,
        currentSession: UserParking?,
    ): Result<Unit> {
        val spotId = currentSession?.id
            ?: "manual_${Clock.System.now().toEpochMilliseconds()}"
        val spotType = currentSession?.spotType ?: SpotType.AUTO_DETECTED
        val confidence = currentSession?.detectionReliability ?: 1f
        val sizeCategory = currentSession?.sizeCategory

        // Schedule WorkManager job BEFORE clearing so the spot report is durably
        // enqueued even if the app is killed immediately after.
        reportSpotReleased(lat, lon, spotId, spotType, confidence, sizeCategory)

        return userParkingRepository.clearActive()
    }
}
