package io.apptolast.paparcar.domain.usecase.user

import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.repository.ZoneRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Pulls every user-scoped table from Firestore into Room in parallel. Called after
 * a successful authentication (and after the previous user's cache has been wiped)
 * so the next session starts with the right user's data already cached locally.
 *
 * Behaviour:
 *  - **Parallel.** The three syncs hit independent Firestore subcollections and
 *    independent Room tables — running them via `coroutineScope { async }` cuts
 *    bootstrap latency to `max(t_parking, t_zones, t_vehicles)` instead of the sum.
 *  - **Fail-fast.** Structured concurrency: if any [getOrThrow] inside an `async`
 *    throws, `coroutineScope` cancels the siblings and propagates the exception
 *    out of [invoke]. The caller (SplashViewModel) treats failure as fatal — back
 *    to login screen — because Paparcar requires connectivity at login. Partial
 *    data is never preferable to a clean retry.
 *
 * [SESSION-ISOLATION-001]
 */
class BootstrapUserDataUseCase(
    private val vehicleRepository: VehicleRepository,
    private val userParkingRepository: UserParkingRepository,
    private val zoneRepository: ZoneRepository,
) {
    suspend operator fun invoke(userId: String): Result<Unit> = runCatching {
        coroutineScope {
            awaitAll(
                async { vehicleRepository.syncFromRemote(userId).getOrThrow() },
                async { userParkingRepository.syncParkingHistoryFromRemote(userId).getOrThrow() },
                async { zoneRepository.syncFromRemote(userId).getOrThrow() },
            )
        }
    }
}
