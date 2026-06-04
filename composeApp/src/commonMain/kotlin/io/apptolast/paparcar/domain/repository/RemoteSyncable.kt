package io.apptolast.paparcar.domain.repository

/**
 * Contract for repositories that can pull their user-scoped data
 * from the remote backend into the local Room cache.
 *
 * All implementors are invoked in parallel during the app bootstrap
 * phase to minimise startup latency.
 *
 * @see io.apptolast.paparcar.domain.usecase.user.BootstrapUserDataUseCase
 */
interface RemoteSyncable {
    suspend fun syncFromRemote(userId: String): Result<Unit>
}
