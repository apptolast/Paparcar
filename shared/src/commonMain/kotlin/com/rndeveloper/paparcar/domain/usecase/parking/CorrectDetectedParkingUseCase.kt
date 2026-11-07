package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.detection.ports.ManualParkingDetection
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ParkingCorrection { CONTINUE_TRIP, WRONG_CAR }

/** A false stop and a trip in someone else's car require different detection lifecycles. */
class CorrectDetectedParkingUseCase(
    private val repository: UserParkingRepository,
    private val revertParking: RevertParkingUseCase,
    private val detection: ManualParkingDetection,
) {
    private val mutex = Mutex()

    suspend operator fun invoke(sessionId: String, correction: ParkingCorrection): Result<Unit> =
        mutex.withLock {
            // Resolve before stopping: a stale card must not stop an unrelated current trip.
            val target = repository.observeActiveSessions().first().firstOrNull { it.id == sessionId }
                ?: return@withLock Result.failure(IllegalStateException("Parking is no longer active"))
            if (correction == ParkingCorrection.CONTINUE_TRIP && target.vehicleId == null) {
                return@withLock Result.failure(IllegalStateException("Parking has no vehicle"))
            }
            detection.finishTrip(rejectTrip = correction == ParkingCorrection.WRONG_CAR)
                .onFailure { return@withLock Result.failure(it) }
            revertParking.revertActive(sessionId).fold(
                onSuccess = { reverted ->
                    if (reverted != null && correction == ParkingCorrection.CONTINUE_TRIP) {
                        detection.resumeTrip(requireNotNull(reverted.vehicleId), reverted.location)
                    }
                    Result.success(Unit)
                },
                onFailure = { Result.failure(it) },
            )
        }
}
