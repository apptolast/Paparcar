package com.rndeveloper.paparcar.detection

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException

/** In-process acknowledgement: saving must wait until the service's GPS and BT jobs have ended. */
internal object ManualParkingCompletion {
    private val requests = ConcurrentHashMap<String, CompletableDeferred<Result<Unit>>>()

    suspend fun await(send: (String) -> Unit): Result<Unit> {
        val token = UUID.randomUUID().toString()
        val completion = CompletableDeferred<Result<Unit>>()
        requests[token] = completion
        return try {
            send(token)
            withTimeoutOrNull(15_000) { completion.await() }
                ?: Result.failure(IllegalStateException("Detection did not stop"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            requests.remove(token)
        }
    }

    fun complete(token: String?, result: Result<Unit>) {
        token?.let { requests[it]?.complete(result) }
    }
}
