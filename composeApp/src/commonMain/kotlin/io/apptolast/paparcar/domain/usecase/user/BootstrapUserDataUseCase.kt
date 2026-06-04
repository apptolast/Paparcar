package io.apptolast.paparcar.domain.usecase.user

import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.repository.ZoneRepository
import io.apptolast.paparcar.domain.util.PaparcarLogger
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
    suspend operator fun invoke(userId: String): Result<Unit> {
        PaparcarLogger.d(DIAG, "▶ Bootstrap.invoke userId=$userId")
        return runCatching {
            coroutineScope {
                val vehiclesJob = async {
                    PaparcarLogger.d(DIAG, "  → syncVehicles START")
                    vehicleRepository.syncFromRemote(userId)
                        .also { r -> PaparcarLogger.d(DIAG, "  ← syncVehicles END isSuccess=${r.isSuccess}") }
                        .getOrThrow()
                }
                val parkingJob = async {
                    PaparcarLogger.d(DIAG, "  → syncParkingHistory START")
                    userParkingRepository.syncFromRemote(userId)
                        .also { r -> PaparcarLogger.d(DIAG, "  ← syncParkingHistory END isSuccess=${r.isSuccess}") }
                        .getOrThrow()
                }
                val zonesJob = async {
                    PaparcarLogger.d(DIAG, "  → syncZones START")
                    zoneRepository.syncFromRemote(userId)
                        .also { r -> PaparcarLogger.d(DIAG, "  ← syncZones END isSuccess=${r.isSuccess}") }
                        .getOrThrow()
                }
                awaitAll(vehiclesJob, parkingJob, zonesJob)
                Unit
            }
        }.also { r ->
            if (r.isSuccess) PaparcarLogger.d(DIAG, "■ Bootstrap.invoke SUCCESS")
            else PaparcarLogger.e(DIAG, "■ Bootstrap.invoke FAILED", r.exceptionOrNull())
        }
    }

    private companion object {
        const val DIAG = "PARKDIAG/Bootstrap"
    }
}
