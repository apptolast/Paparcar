package com.rndeveloper.paparcar.domain.usecase.attribution

import com.apptolast.baselogin.domain.model.UserSession
import com.rndeveloper.paparcar.domain.analytics.AnalyticsEvent
import com.rndeveloper.paparcar.domain.analytics.AnalyticsUserProperty
import com.rndeveloper.paparcar.domain.attribution.InstallChannel
import com.rndeveloper.paparcar.domain.attribution.InstallReferrerRaw
import com.rndeveloper.paparcar.domain.attribution.MetaInstallIds
import com.rndeveloper.paparcar.fakes.FakeAnalyticsTracker
import com.rndeveloper.paparcar.fakes.FakeAppPreferences
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.FakeInstallReferrerPort
import com.rndeveloper.paparcar.fakes.FakeMetaReferrerDecryptPort
import com.rndeveloper.paparcar.fakes.FakeUserProfileRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [MKT-INSTALL-ATTRIBUTION-CHANNEL-001] */
class ResolveInstallAttributionUseCaseTest {

    private val session = UserSession(userId = UID, email = null, displayName = null, photoUrl = null)

    private fun useCase(
        referrerPort: FakeInstallReferrerPort = FakeInstallReferrerPort(),
        decryptPort: FakeMetaReferrerDecryptPort = FakeMetaReferrerDecryptPort(),
        appPreferences: FakeAppPreferences = FakeAppPreferences(),
        userProfileRepository: FakeUserProfileRepository = FakeUserProfileRepository(),
        authRepository: FakeAuthRepository = FakeAuthRepository(initialSession = session),
        analyticsTracker: FakeAnalyticsTracker = FakeAnalyticsTracker(),
    ) = ResolveInstallAttributionUseCase(
        referrerPort = referrerPort,
        metaDecrypt = decryptPort,
        appPreferences = appPreferences,
        userProfileRepository = userProfileRepository,
        authRepository = authRepository,
        analyticsTracker = analyticsTracker,
    )

    @Test
    fun should_doNothing_when_alreadyResolved() = runTest {
        val referrerPort = FakeInstallReferrerPort()
        val appPreferences = FakeAppPreferences().apply { setInstallAttributionResolved() }

        useCase(referrerPort = referrerPort, appPreferences = appPreferences).resolve()

        assertEquals(0, referrerPort.readCallCount)
    }

    @Test
    fun should_leaveTheGateOpen_when_theServiceConnectionFails() = runTest {
        val referrerPort = FakeInstallReferrerPort(result = Result.failure(IllegalStateException("SERVICE_DISCONNECTED")))
        val appPreferences = FakeAppPreferences()

        useCase(referrerPort = referrerPort, appPreferences = appPreferences).resolve()

        assertTrue(!appPreferences.hasResolvedInstallAttribution, "a connection failure must be retried on the next launch")
    }

    @Test
    fun should_closeTheGateWithoutWriting_when_playHasNoReferrerAtAll() = runTest {
        // iOS's NoOpInstallReferrerPort, or an Android device with no Play Services.
        val referrerPort = FakeInstallReferrerPort(result = Result.success(null))
        val appPreferences = FakeAppPreferences()
        val userProfileRepository = FakeUserProfileRepository()

        useCase(referrerPort = referrerPort, appPreferences = appPreferences, userProfileRepository = userProfileRepository).resolve()

        assertTrue(appPreferences.hasResolvedInstallAttribution)
        assertEquals(0, userProfileRepository.installAttributionWrites.size)
    }

    @Test
    fun should_publishOwnedLink_when_theReferrerCarriesOurOwnUtm() = runTest {
        val referrerPort = FakeInstallReferrerPort(
            result = Result.success(
                InstallReferrerRaw(
                    installReferrer = "utm_source=web&utm_medium=owned&utm_campaign=home&utm_content=cta_hero",
                    referrerClickTimestampSeconds = 1L,
                    installBeginTimestampSeconds = 2L,
                    googlePlayInstantParam = false,
                ),
            ),
        )
        val appPreferences = FakeAppPreferences()
        val userProfileRepository = FakeUserProfileRepository()
        val analyticsTracker = FakeAnalyticsTracker()

        useCase(
            referrerPort = referrerPort,
            appPreferences = appPreferences,
            userProfileRepository = userProfileRepository,
            analyticsTracker = analyticsTracker,
        ).resolve()

        assertTrue(appPreferences.hasResolvedInstallAttribution)
        val write = userProfileRepository.installAttributionWrites.single()
        assertEquals(UID, write.userId)
        assertEquals(InstallChannel.OWNED_LINK.wire, write.channel)
        assertEquals("web", write.source)
        assertEquals("home", write.campaign)
        assertEquals(
            listOf<AnalyticsUserProperty>(AnalyticsUserProperty.InstallChannelProperty(InstallChannel.OWNED_LINK)),
            analyticsTracker.properties,
        )
        assertEquals(
            listOf<AnalyticsEvent>(AnalyticsEvent.InstallAttributionResolved(InstallChannel.OWNED_LINK)),
            analyticsTracker.events,
        )
    }

    @Test
    fun should_decryptAndPublishMetaPaid_when_theUtmContentCarriesTheMetaBlock() = runTest {
        val metaBlock = """{"app":"123","source":{"data":"abcd","nonce":"ef01"}}"""
        val referrerPort = FakeInstallReferrerPort(
            result = Result.success(
                InstallReferrerRaw(
                    installReferrer = "utm_content=$metaBlock",
                    referrerClickTimestampSeconds = 1L,
                    installBeginTimestampSeconds = 2L,
                    googlePlayInstantParam = false,
                ),
            ),
        )
        val decryptPort = FakeMetaReferrerDecryptPort(
            result = Result.success(MetaInstallIds(campaignId = "C1", adsetId = "AS1", adId = "AD1")),
        )
        val userProfileRepository = FakeUserProfileRepository()

        useCase(
            referrerPort = referrerPort,
            decryptPort = decryptPort,
            userProfileRepository = userProfileRepository,
        ).resolve()

        assertEquals(metaBlock, decryptPort.lastContent)
        val write = userProfileRepository.installAttributionWrites.single()
        assertEquals(InstallChannel.META_PAID.wire, write.channel)
        assertEquals("C1", write.metaCampaignId)
        assertEquals("AS1", write.metaAdsetId)
        assertEquals("AD1", write.metaAdId)
    }

    @Test
    fun should_fallBackToOrganicClassification_when_decryptFails() = runTest {
        val referrerPort = FakeInstallReferrerPort(
            result = Result.success(
                InstallReferrerRaw(
                    installReferrer = "utm_source=apps.instagram.com&utm_content=garbage",
                    referrerClickTimestampSeconds = 1L,
                    installBeginTimestampSeconds = 2L,
                    googlePlayInstantParam = false,
                ),
            ),
        )
        val decryptPort = FakeMetaReferrerDecryptPort(result = Result.failure(IllegalArgumentException("bad payload")))
        val userProfileRepository = FakeUserProfileRepository()

        useCase(
            referrerPort = referrerPort,
            decryptPort = decryptPort,
            userProfileRepository = userProfileRepository,
        ).resolve()

        val write = userProfileRepository.installAttributionWrites.single()
        assertEquals(InstallChannel.META_ORGANIC_IG.wire, write.channel)
    }

    @Test
    fun should_skipFirestoreWriteButStillCloseTheGate_when_signedOut() = runTest {
        val referrerPort = FakeInstallReferrerPort(
            result = Result.success(
                InstallReferrerRaw(
                    installReferrer = "utm_source=web&utm_medium=owned",
                    referrerClickTimestampSeconds = null,
                    installBeginTimestampSeconds = null,
                    googlePlayInstantParam = null,
                ),
            ),
        )
        val appPreferences = FakeAppPreferences()
        val userProfileRepository = FakeUserProfileRepository()
        val authRepository = FakeAuthRepository(initialSession = null)
        val analyticsTracker = FakeAnalyticsTracker()

        useCase(
            referrerPort = referrerPort,
            appPreferences = appPreferences,
            userProfileRepository = userProfileRepository,
            authRepository = authRepository,
            analyticsTracker = analyticsTracker,
        ).resolve()

        assertTrue(appPreferences.hasResolvedInstallAttribution)
        assertEquals(0, userProfileRepository.installAttributionWrites.size)
        // The analytics property/event describe the INSTALL, not the account — they still fire.
        assertEquals(1, analyticsTracker.properties.size)
        assertEquals(1, analyticsTracker.events.size)
    }

    private companion object {
        const val UID = "uid-1"
    }
}
