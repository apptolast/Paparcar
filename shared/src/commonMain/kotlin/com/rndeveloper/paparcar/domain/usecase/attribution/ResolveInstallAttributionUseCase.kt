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
import com.rndeveloper.paparcar.domain.attribution.looksLikeMetaReferrerBlock
import com.rndeveloper.paparcar.domain.attribution.parseReferrerParams
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.UserProfileRepository
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * Captures the Play referrer at app startup and keeps it pending until it can be published.
 * Meta attribution is finalized after sign-in because the decrypt endpoint requires authentication.
 */
class ResolveInstallAttributionUseCase(
    private val referrerPort: InstallReferrerPort,
    private val metaDecrypt: MetaReferrerDecryptPort,
    private val appPreferences: AppPreferences,
    private val userProfileRepository: UserProfileRepository,
    private val authRepository: AuthRepository,
    private val analyticsTracker: AnalyticsTracker,
) {

    private val mutex = Mutex()

    suspend fun resolve() = mutex.withLock {
        if (appPreferences.pendingInstallAttribution != null || appPreferences.hasResolvedInstallAttribution) {
            flushPendingLocked()
            return@withLock
        }

        val raw = referrerPort.read().getOrElse {
            if (it is CancellationException) throw it
            PaparcarLogger.w(TAG, "install referrer unavailable, will retry")
            return@withLock
        }
        if (raw == null) {
            appPreferences.setInstallAttributionResolved()
            return@withLock
        }

        val params = parseReferrerParams(raw.installReferrer)
        val channel = classifyInstallChannel(params, metaIds = null)
        val taggedReferral = FriendReferral.isTagged(params)
        val referralCode = FriendReferral.codeFrom(params).takeIf { channel == InstallChannel.FRIEND_REFERRAL }
        val savedReferrer = if (taggedReferral) referralCode?.let(FriendReferral::referrer) else raw.installReferrer

        appPreferences.saveResolvedInstallAttribution(
            PendingInstallAttribution(
                channel = channel.wire,
                source = params[UTM_SOURCE],
                medium = params[UTM_MEDIUM],
                campaign = if (taggedReferral) null else params[UTM_CAMPAIGN],
                metaCampaignId = null,
                metaAdsetId = null,
                metaAdId = null,
                rawReferrer = savedReferrer,
                resolvedAtMs = Clock.System.now().toEpochMilliseconds(),
            ),
        )
        if (taggedReferral || params[UTM_CONTENT]?.let(::looksLikeMetaReferrerBlock) != true) {
            track(channel)
        }

        flushPendingLocked()
    }

    suspend fun flushPending() = mutex.withLock { flushPendingLocked() }

    private suspend fun flushPendingLocked() {
        val pending = appPreferences.pendingInstallAttribution ?: return
        val userId = authRepository.getCurrentSession()?.userId ?: return
        val params = parseReferrerParams(pending.rawReferrer)
        val metaContent = params[UTM_CONTENT]?.takeIf(::looksLikeMetaReferrerBlock)
        val metaIds = metaContent?.let { content ->
            metaDecrypt.decrypt(content).getOrElse {
                if (it is CancellationException) throw it
                PaparcarLogger.w(TAG, "Meta install referrer unavailable, will retry")
                return
            }
        }
        if (authRepository.getCurrentSession()?.userId != userId) return

        val channel = classifyInstallChannel(params, metaIds)
        userProfileRepository.publishInstallAttribution(
            userId = userId,
            channel = if (metaContent == null) pending.channel else channel.wire,
            source = pending.source,
            medium = pending.medium,
            campaign = pending.campaign,
            metaCampaignId = metaIds?.campaignId ?: pending.metaCampaignId,
            metaAdsetId = metaIds?.adsetId ?: pending.metaAdsetId,
            metaAdId = metaIds?.adId ?: pending.metaAdId,
            rawReferrer = pending.rawReferrer,
            resolvedAtMs = pending.resolvedAtMs,
        ).onSuccess {
            appPreferences.clearPendingInstallAttribution()
            if (metaContent != null) track(channel)
        }.onFailure {
            if (it is CancellationException) throw it
            PaparcarLogger.w(TAG, "pending install attribution not published, will retry")
        }
    }

    private fun track(channel: InstallChannel) {
        analyticsTracker.setUserProperty(AnalyticsUserProperty.InstallChannelProperty(channel))
        analyticsTracker.track(AnalyticsEvent.InstallAttributionResolved(channel))
    }

    private companion object {
        const val TAG = "ResolveInstallAttribution"
        const val UTM_CAMPAIGN = "utm_campaign"
        const val UTM_CONTENT = "utm_content"
    }
}
