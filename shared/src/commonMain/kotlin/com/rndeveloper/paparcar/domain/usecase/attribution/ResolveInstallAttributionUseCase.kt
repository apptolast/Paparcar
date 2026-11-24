@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.attribution

import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.domain.analytics.AnalyticsEvent
import com.rndeveloper.paparcar.domain.analytics.AnalyticsTracker
import com.rndeveloper.paparcar.domain.analytics.AnalyticsUserProperty
import com.rndeveloper.paparcar.domain.attribution.InstallChannel
import com.rndeveloper.paparcar.domain.attribution.InstallReferrerPort
import com.rndeveloper.paparcar.domain.attribution.MetaReferrerDecryptPort
import com.rndeveloper.paparcar.domain.attribution.classifyInstallChannel
import com.rndeveloper.paparcar.domain.attribution.parseReferrerParams
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.UserProfileRepository
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
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
 */
class ResolveInstallAttributionUseCase(
    private val referrerPort: InstallReferrerPort,
    private val metaDecrypt: MetaReferrerDecryptPort,
    private val appPreferences: AppPreferences,
    private val userProfileRepository: UserProfileRepository,
    private val authRepository: AuthRepository,
    private val analyticsTracker: AnalyticsTracker,
) {

    suspend fun resolve() {
        if (appPreferences.hasResolvedInstallAttribution) return

        val result = referrerPort.read()
        if (result.isFailure) {
            // A transient failure to CONNECT to the Play service — the gate stays open so the
            // next cold start tries again. [MKT-INSTALL-ATTRIBUTION-CHANNEL-001]
            PaparcarLogger.w(TAG, "install referrer unavailable, will retry: ${result.exceptionOrNull()?.message}")
            return
        }
        val raw = result.getOrNull()
        if (raw == null) {
            // Play answered "nothing to resolve" (iOS's NoOp port, or a device with no Play
            // Services at all) — a final answer, not a connection problem. Retrying would only
            // repeat the same null forever, and there is nothing honest to write, so the gate
            // closes to stop asking every cold start.
            appPreferences.setInstallAttributionResolved()
            return
        }

        val params = parseReferrerParams(raw.installReferrer)
        val metaIds = params[UTM_CONTENT]
            ?.let { metaDecrypt.decrypt(it).getOrNull() }
        val channel = classifyInstallChannel(params, metaIds)

        val userId = authRepository.getCurrentSession()?.userId
        if (userId != null) {
            userProfileRepository.publishInstallAttribution(
                userId = userId,
                channel = channel.wire,
                source = params[UTM_SOURCE],
                medium = params[UTM_MEDIUM],
                campaign = params[UTM_CAMPAIGN],
                metaCampaignId = metaIds?.campaignId,
                metaAdsetId = metaIds?.adsetId,
                metaAdId = metaIds?.adId,
                rawReferrer = raw.installReferrer,
                resolvedAtMs = Clock.System.now().toEpochMilliseconds(),
            ).onFailure { e -> PaparcarLogger.w(TAG, "install attribution not published: ${e.message}") }
        }
        // Set unconditionally, even signed out: the referrer itself was read successfully, and a
        // signed-out resolve can never happen again (the next launch is either the same anonymous
        // state — nothing changed — or a login, which still has no second chance at reading Play).
        analyticsTracker.setUserProperty(AnalyticsUserProperty.InstallChannelProperty(channel))
        analyticsTracker.track(AnalyticsEvent.InstallAttributionResolved(channel))
        appPreferences.setInstallAttributionResolved()
    }

    private companion object {
        const val TAG = "ResolveInstallAttribution"
        const val UTM_SOURCE = "utm_source"
        const val UTM_MEDIUM = "utm_medium"
        const val UTM_CAMPAIGN = "utm_campaign"
        const val UTM_CONTENT = "utm_content"
    }
}
