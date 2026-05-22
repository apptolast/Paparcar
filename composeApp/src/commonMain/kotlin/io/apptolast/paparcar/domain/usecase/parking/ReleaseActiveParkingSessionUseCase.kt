@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import kotlin.time.Clock

/**
 * Releases the user's active parking session.
 *
 * When [publishSpot] is `true` (default), the freed parking is also published
 * to the community via a durable WorkManager job so it survives process death.
 * When `false`, only the local + remote session is cleared and no community
 * spot is reported — used by the "Just delete" path in the release dialog
 * when the user doesn't want to share. [PEEK-ACTIONS-001]
 *
 * Returns [Result.failure] if clearing the session fails. When publishing,
 * the spot report is enqueued first so the community spot is never lost even
 * if the clear fails.
 */
class ReleaseActiveParkingSessionUseCase(
    private val reportSpotReleased: ReportSpotReleasedUseCase,
    private val userParkingRepository: UserParkingRepository,
) {
    suspend operator fun invoke(
        lat: Double,
        lon: Double,
        currentSession: UserParking?,
        publishSpot: Boolean = true,
    ): Result<Unit> {
        if (publishSpot) {
            val spotId = currentSession?.id
                ?: "manual_${Clock.System.now().toEpochMilliseconds()}"
            val spotType = currentSession?.spotType ?: SpotType.AUTO_DETECTED
            val confidence = currentSession?.detectionReliability ?: 1f
            val sizeCategory = currentSession?.sizeCategory

            // Schedule WorkManager job BEFORE clearing so the spot report is durably
            // enqueued even if the app is killed immediately after.
            reportSpotReleased(lat, lon, spotId, spotType, confidence, sizeCategory)
        }

        // Under multi-parking we can only clear *this* specific session — clearing
        // by id leaves other vehicles' active sessions intact. If the caller did
        // not supply a session (legacy / manual delete), nothing to clear locally.
        val sessionId = currentSession?.id ?: return Result.success(Unit)
        return userParkingRepository.clearActiveById(sessionId)
    }
}
