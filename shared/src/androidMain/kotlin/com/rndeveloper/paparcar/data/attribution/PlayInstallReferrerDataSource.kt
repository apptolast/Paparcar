package com.rndeveloper.paparcar.data.attribution

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerClient.InstallReferrerResponse
import com.android.installreferrer.api.InstallReferrerStateListener
import com.rndeveloper.paparcar.domain.attribution.InstallReferrerPort
import com.rndeveloper.paparcar.domain.attribution.InstallReferrerRaw
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Lee el Google Play Install Referrer envolviendo su API de callbacks en una `suspend fun`.
 * [MKT-INSTALL-ATTRIBUTION-CHANNEL-001]
 *
 * Tres respuestas de `InstallReferrerClient`, y sólo una cuenta como éxito con dato:
 *
 * - [InstallReferrerResponse.OK] — hay `ReferrerDetails`. Éxito.
 * - [InstallReferrerResponse.FEATURE_NOT_SUPPORTED] — la versión instalada de Play Store es
 *   demasiado vieja para esta API. **Terminal**: ninguna versión futura de la app lo arregla sin
 *   que el usuario actualice Play Store por su cuenta, así que cuenta como "no hay referrer que
 *   leer", no como un fallo a reintentar.
 * - Cualquier otro código (`SERVICE_UNAVAILABLE`, sin red; `DEVELOPER_ERROR`; o la desconexión del
 *   servicio) — fallo de CONEXIÓN. [Result.failure], para que
 *   [com.rndeveloper.paparcar.domain.usecase.attribution.ResolveInstallAttributionUseCase] lo
 *   reintente en el próximo arranque, que es justo el punto 1 del encargo ("reintentando si falla
 *   la conexión con el servicio").
 */
class PlayInstallReferrerDataSource(
    private val context: Context,
) : InstallReferrerPort {

    override suspend fun read(): Result<InstallReferrerRaw?> = suspendCancellableCoroutine { cont ->
        val client = InstallReferrerClient.newBuilder(context).build()
        cont.invokeOnCancellation { runCatching { client.endConnection() } }

        client.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                when (responseCode) {
                    InstallReferrerResponse.OK -> {
                        val raw = runCatching {
                            val details = client.installReferrer
                            InstallReferrerRaw(
                                installReferrer = details.installReferrer,
                                referrerClickTimestampSeconds = details.referrerClickTimestampSeconds,
                                installBeginTimestampSeconds = details.installBeginTimestampSeconds,
                                googlePlayInstantParam = details.googlePlayInstantParam,
                            )
                        }
                        resumeOnce(cont, raw.fold({ Result.success(it) }, { Result.failure(it) }))
                    }
                    InstallReferrerResponse.FEATURE_NOT_SUPPORTED -> {
                        PaparcarLogger.i(TAG, "Play Store too old for the Install Referrer API — nothing to resolve")
                        resumeOnce(cont, Result.success(null))
                    }
                    else -> {
                        resumeOnce(cont, Result.failure(IllegalStateException("install referrer responseCode=$responseCode")))
                    }
                }
                runCatching { client.endConnection() }
            }

            override fun onInstallReferrerServiceDisconnected() {
                // Fired if the connection drops BEFORE onInstallReferrerSetupFinished ever runs —
                // a real connection failure, not the normal end-of-call teardown (that one calls
                // onInstallReferrerSetupFinished first). Retryable.
                resumeOnce(cont, Result.failure(IllegalStateException("install referrer service disconnected")))
            }
        })
    }

    /** Both callbacks can theoretically race under Play Services update churn; a continuation can
     *  only be resumed once, and a second attempt would crash with `IllegalStateException` instead
     *  of a readable log line. */
    private fun resumeOnce(cont: CancellableContinuation<Result<InstallReferrerRaw?>>, result: Result<InstallReferrerRaw?>) {
        if (cont.isActive) cont.resume(result)
    }

    private companion object {
        const val TAG = "PlayInstallReferrer"
    }
}
