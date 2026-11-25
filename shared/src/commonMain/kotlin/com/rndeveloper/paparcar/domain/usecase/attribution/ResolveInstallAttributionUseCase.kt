@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.attribution

import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.domain.analytics.AnalyticsEvent
import com.rndeveloper.paparcar.domain.analytics.AnalyticsTracker
import com.rndeveloper.paparcar.domain.analytics.AnalyticsUserProperty
import com.rndeveloper.paparcar.domain.attribution.FriendReferral
import com.rndeveloper.paparcar.domain.attribution.InstallChannel
import com.rndeveloper.paparcar.domain.attribution.InstallReferrerPort
import com.rndeveloper.paparcar.domain.attribution.MetaReferrerDecryptPort
import com.rndeveloper.paparcar.domain.attribution.PendingInstallAttribution
import com.rndeveloper.paparcar.domain.attribution.UTM_MEDIUM
import com.rndeveloper.paparcar.domain.attribution.UTM_SOURCE
import com.rndeveloper.paparcar.domain.attribution.classifyInstallChannel
import com.rndeveloper.paparcar.domain.attribution.parseReferrerParams
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.UserProfileRepository
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * Resuelve de qué canal vino esta instalación, UNA vez. [MKT-INSTALL-ATTRIBUTION-CHANNEL-001]
 *
 * El Google Play Install Referrer solo contesta con datos útiles la primera vez que una app se
 * conecta a su servicio tras instalarse — por eso el gate vive en [AppPreferences], no en una
 * comprobación de "¿ya tengo un valor?" contra Firestore, que necesitaría red para saber si debe
 * ahorrarse una llamada que ya es imposible de repetir con éxito.
 *
 * ⛔ **Un fallo de CONEXIÓN al servicio de Play sí se reintenta** (el gate no se marca): es
 * exactamente el caso que el punto 1 del encargo pide ("reintentando si falla la conexión con el
 * servicio"). Un referrer vacío o ilegible, en cambio, SÍ es una respuesta final — Play contestó,
 * simplemente no hay campaña que contar, y seguir reintentando no cambiaría eso.
 *
 * ⛔ **Leer el referrer y publicarlo en Firestore son dos relojes distintos.**
 * [MKT-A-REFERRER-READ-BEFORE-LOGIN-MUST-SURVIVE-001] `AppViewModel` dispara [resolve] en su
 * `init`, por encima del nav graph de Splash/Auth — es decir, casi siempre ANTES de que haya
 * sesión. Como el referrer sólo se puede leer una vez, [resolve] clasifica y cierra el gate de
 * LECTURA incondicionalmente, pero NUNCA publica directo: guarda el resultado en
 * [AppPreferences.pendingInstallAttribution] y delega en [flushPending], que sí puede esperar
 * todo lo que haga falta a que exista un `userId` — reintentándolo en cada llamada a [resolve]
 * (próximos arranques) y también desde `AppViewModel` en cuanto la sesión aparece en el mismo
 * proceso.
 */
class ResolveInstallAttributionUseCase(
    private val referrerPort: InstallReferrerPort,
    private val metaDecrypt: MetaReferrerDecryptPort,
    private val appPreferences: AppPreferences,
    private val userProfileRepository: UserProfileRepository,
    private val authRepository: AuthRepository,
    private val analyticsTracker: AnalyticsTracker,
) {

    private val attributionLock = Mutex()

    suspend fun resolve() = attributionLock.withLock {
        if (appPreferences.hasResolvedInstallAttribution) {
            publishPending()
            return@withLock
        }

        val result = referrerPort.read()
        if (result.isFailure) {
            // A transient failure to CONNECT to the Play service — the gate stays open so the
            // next cold start tries again. [MKT-INSTALL-ATTRIBUTION-CHANNEL-001]
            PaparcarLogger.w(TAG, "install referrer unavailable, will retry: ${result.exceptionOrNull()?.message}")
            return@withLock
        }
        val raw = result.getOrNull()
        if (raw == null) {
            // Play answered "nothing to resolve" (iOS's NoOp port, or a device with no Play
            // Services at all) — a final answer, not a connection problem. Retrying would only
            // repeat the same null forever, and there is nothing honest to write, so the gate
            // closes to stop asking every cold start.
            appPreferences.setInstallAttributionResolved()
            return@withLock
        }

        val params = parseReferrerParams(raw.installReferrer)
        val metaIds = params[UTM_CONTENT]
            ?.let { metaDecrypt.decrypt(it).getOrNull() }
        val channel = classifyInstallChannel(params, metaIds)

        val taggedReferral = FriendReferral.isTagged(params)
        val referralCode = FriendReferral.codeFrom(params).takeIf { channel == InstallChannel.FRIEND_REFERRAL }

        appPreferences.saveResolvedInstallAttribution(
            PendingInstallAttribution(
                channel = channel.wire,
                source = params[UTM_SOURCE],
                medium = params[UTM_MEDIUM],
                campaign = if (taggedReferral) null else params[UTM_CAMPAIGN],
                metaCampaignId = metaIds?.campaignId,
                metaAdsetId = metaIds?.adsetId,
                metaAdId = metaIds?.adId,
                rawReferrer = if (taggedReferral) referralCode?.let(FriendReferral::referrer) else raw.installReferrer,
                resolvedAtMs = Clock.System.now().toEpochMilliseconds(),
            ),
        )
        // These report the install channel even before a user has signed in.
        analyticsTracker.setUserProperty(AnalyticsUserProperty.InstallChannelProperty(channel))
        analyticsTracker.track(AnalyticsEvent.InstallAttributionResolved(channel))
        publishPending()
    }

    /**
     * Publishes the pending classified result to Firestore, if there is one AND a session exists
     * right now. [MKT-A-REFERRER-READ-BEFORE-LOGIN-MUST-SURVIVE-001]
     *
     * Safe to call any number of times, from any number of places: with nothing pending, or no
     * session yet, it is a no-op. A successful publish clears the pending record so it is never
     * resent; a failure (network, Firestore rules) leaves it for the next call to retry — unlike
     * the Play read, Firestore can be asked again.
     */
    suspend fun flushPending() = attributionLock.withLock { publishPending() }

    private suspend fun publishPending() {
        val pending = appPreferences.pendingInstallAttribution ?: return
        val userId = authRepository.getCurrentSession()?.userId ?: return
        userProfileRepository.publishInstallAttribution(
            userId = userId,
            channel = pending.channel,
            source = pending.source,
            medium = pending.medium,
            campaign = pending.campaign,
            metaCampaignId = pending.metaCampaignId,
            metaAdsetId = pending.metaAdsetId,
            metaAdId = pending.metaAdId,
            rawReferrer = pending.rawReferrer,
            resolvedAtMs = pending.resolvedAtMs,
        ).onSuccess {
            appPreferences.clearPendingInstallAttribution()
        }.onFailure { e ->
            PaparcarLogger.w(TAG, "pending install attribution not published, will retry: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "ResolveInstallAttribution"
        const val UTM_CAMPAIGN = "utm_campaign"
        const val UTM_CONTENT = "utm_content"
    }
}
