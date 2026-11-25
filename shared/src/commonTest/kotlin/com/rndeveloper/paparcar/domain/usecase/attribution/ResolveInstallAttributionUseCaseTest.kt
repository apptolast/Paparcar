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
        val authRepository = FakeAuthRepository(initialSession = null)
        val analyticsTracker = FakeAnalyticsTracker()
        val referrerPort = FakeInstallReferrerPort(
            result = Result.success(
                InstallReferrerRaw(
                    installReferrer = "utm_source=apps.instagram.com&utm_content=$metaBlock",
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

        val useCase = useCase(
            referrerPort = referrerPort,
            decryptPort = decryptPort,
            userProfileRepository = userProfileRepository,
            authRepository = authRepository,
            analyticsTracker = analyticsTracker,
        )
        useCase.resolve()

        assertEquals(null, decryptPort.lastContent)
        assertTrue(analyticsTracker.events.isEmpty())
        authRepository.emitState(com.apptolast.baselogin.domain.model.AuthState.Authenticated(session))
        val successfulResult = decryptPort.result
        decryptPort.result = Result.failure(IllegalStateException("offline"))
        useCase.flushPending()
        assertTrue(userProfileRepository.installAttributionWrites.isEmpty())
        assertTrue(analyticsTracker.events.isEmpty())

        decryptPort.result = successfulResult
        useCase.flushPending()

        assertEquals(1, referrerPort.readCallCount)
        assertEquals(
            listOf<AnalyticsEvent>(AnalyticsEvent.InstallAttributionResolved(InstallChannel.META_PAID)),
            analyticsTracker.events,
        )
        assertEquals(metaBlock, decryptPort.lastContent)
        val write = userProfileRepository.installAttributionWrites.single()
        assertEquals(InstallChannel.META_PAID.wire, write.channel)
        assertEquals("C1", write.metaCampaignId)
        assertEquals("AS1", write.metaAdsetId)
        assertEquals("AD1", write.metaAdId)
    }

    @Test
    fun should_classifyInstagramAsOrganic_when_thereIsNoMetaBlock() = runTest {
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
        // [MKT-A-REFERRER-READ-BEFORE-LOGIN-MUST-SURVIVE-001] The classified result is NOT lost —
        // it waits here for a session, since Play will never answer a second time.
        val pending = appPreferences.pendingInstallAttribution
        assertEquals(InstallChannel.OWNED_LINK.wire, pending?.channel)
        assertEquals("web", pending?.source)
    }

    /** [MKT-A-REFERRER-READ-BEFORE-LOGIN-MUST-SURVIVE-001] The bug this whole class of test
     *  guards against: a real install, on every real cold start before this fix, that never
     *  published to Firestore because `resolve()` always ran before login. */
    @Test
    fun should_publishThePendingResult_when_aSessionAppearsOnALaterCall() = runTest {
        val referrerPort = FakeInstallReferrerPort(
            result = Result.success(
                InstallReferrerRaw(
                    installReferrer = "utm_source=tiktok&utm_medium=paid&utm_campaign=z-tiktok",
                    referrerClickTimestampSeconds = 1L,
                    installBeginTimestampSeconds = 2L,
                    googlePlayInstantParam = false,
                ),
            ),
        )
        val appPreferences = FakeAppPreferences()
        val userProfileRepository = FakeUserProfileRepository()

        // First launch: no session yet, same as every real cold start before login.
        useCase(
            referrerPort = referrerPort,
            appPreferences = appPreferences,
            userProfileRepository = userProfileRepository,
            authRepository = FakeAuthRepository(initialSession = null),
        ).resolve()

        assertEquals(0, userProfileRepository.installAttributionWrites.size)
        assertEquals(1, referrerPort.readCallCount)

        // A later launch (or the same process, once AppViewModel observes the session): Play is
        // NEVER asked again — the gate is already closed — but the pending result now publishes.
        useCase(
            referrerPort = referrerPort,
            appPreferences = appPreferences,
            userProfileRepository = userProfileRepository,
            authRepository = FakeAuthRepository(initialSession = session),
        ).resolve()

        assertEquals(1, referrerPort.readCallCount, "Play must never be asked twice")
        val write = userProfileRepository.installAttributionWrites.single()
        assertEquals(UID, write.userId)
        assertEquals(InstallChannel.TIKTOK_PAID.wire, write.channel)
        assertEquals("z-tiktok", write.campaign)
        assertEquals(null, appPreferences.pendingInstallAttribution, "published, so nothing left waiting")
    }

    @Test
    fun should_publishTheCanonicalInvitation_when_loginFollowsTheInstall() = runTest {
        val invitation = "utm_source=friend&utm_medium=referral&invite_code=$INVITATION_CODE"
        val referrerPort = FakeInstallReferrerPort(
            result = Result.success(InstallReferrerRaw("$invitation&utm_campaign=untrusted", null, null, null)),
        )
        val preferences = FakeAppPreferences()
        val profiles = FakeUserProfileRepository()
        val analytics = FakeAnalyticsTracker()

        useCase(
            referrerPort = referrerPort,
            appPreferences = preferences,
            userProfileRepository = profiles,
            authRepository = FakeAuthRepository(),
            analyticsTracker = analytics,
        ).resolve()

        assertTrue(preferences.hasResolvedInstallAttribution)
        assertEquals(invitation, preferences.pendingInstallAttribution?.rawReferrer)
        assertTrue(profiles.installAttributionWrites.isEmpty())
        val event = AnalyticsEvent.InstallAttributionResolved(InstallChannel.FRIEND_REFERRAL)
        assertEquals(listOf<AnalyticsEvent>(event), analytics.events)

        useCase(
            referrerPort = referrerPort,
            appPreferences = preferences,
            userProfileRepository = profiles,
        ).resolve()

        val write = profiles.installAttributionWrites.single()
        assertEquals(InstallChannel.FRIEND_REFERRAL.wire, write.channel)
        assertEquals(invitation, write.rawReferrer)
        assertEquals(null, write.campaign)
        assertEquals(1, referrerPort.readCallCount)
        assertEquals(null, preferences.pendingInstallAttribution)
    }

    @Test
    fun should_discardInvitationData_when_theReferralCodeIsInvalid() = runTest {
        val referrer = "utm_source=friend&utm_medium=referral&invite_code=invalid"
        val referrerPort = FakeInstallReferrerPort(
            result = Result.success(InstallReferrerRaw(referrer, null, null, null)),
        )
        val profiles = FakeUserProfileRepository()

        useCase(referrerPort = referrerPort, userProfileRepository = profiles).resolve()

        val write = profiles.installAttributionWrites.single()
        assertEquals(InstallChannel.UNKNOWN.wire, write.channel)
        assertEquals(null, write.rawReferrer)
    }

    @Test
    fun should_doNothing_when_flushingWithNothingPending() = runTest {
        val userProfileRepository = FakeUserProfileRepository()

        useCase(userProfileRepository = userProfileRepository).flushPending()

        assertEquals(0, userProfileRepository.installAttributionWrites.size)
    }

    @Test
    fun should_keepThePending_when_firestorePublishFails() = runTest {
        val appPreferences = FakeAppPreferences()
        appPreferences.saveResolvedInstallAttribution(
            com.rndeveloper.paparcar.domain.attribution.PendingInstallAttribution(
                channel = InstallChannel.TIKTOK_ORGANIC.wire,
                source = "tiktok",
                medium = "social",
                campaign = "tiktok",
                metaCampaignId = null,
                metaAdsetId = null,
                metaAdId = null,
                rawReferrer = "utm_source=tiktok&utm_medium=social&utm_campaign=tiktok",
                resolvedAtMs = 123L,
            ),
        )
        val userProfileRepository = FakeUserProfileRepository().apply {
            installAttributionResult = Result.failure(IllegalStateException("offline"))
        }

        useCase(appPreferences = appPreferences, userProfileRepository = userProfileRepository).flushPending()

        assertEquals(1, userProfileRepository.installAttributionWrites.size)
        assertEquals(InstallChannel.TIKTOK_ORGANIC.wire, appPreferences.pendingInstallAttribution?.channel, "a failed publish must be retried, not dropped")
    }

    private companion object {
        const val UID = "uid-1"
        const val INVITATION_CODE = "0123456789abcdef0123456789abcdef"
    }
}
