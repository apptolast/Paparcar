package com.rndeveloper.paparcar.domain.usecase.entitlement

import com.rndeveloper.paparcar.domain.model.Entitlement
import com.rndeveloper.paparcar.domain.repository.EntitlementRefresh
import com.rndeveloper.paparcar.domain.repository.FoundersClaimOutcome
import com.rndeveloper.paparcar.fakes.FakeAppPreferences
import com.rndeveloper.paparcar.fakes.FakeEntitlementRepository
import com.rndeveloper.paparcar.fakes.data.repository.FakeFoundersPromoRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ClaimFoundersPromoUseCaseTest {

    private val repo = FakeFoundersPromoRepository()
    private val prefs = FakeAppPreferences()
    private val entitlement = FakeEntitlementRepository()
    private val claim = ClaimFoundersPromoUseCase(repo, prefs, entitlement)

    @Test
    fun should_recordReceiptWithoutStoreRefresh_when_theAccountWasNotGrantedPlus() = runTest {
        claim(UID)

        assertEquals(listOf(UID), repo.claimedUids)
        assertTrue(entitlement.refreshedUserIds.isEmpty())
        assertTrue(UID in prefs.foundersPromoClaimedUids)
    }

    @Test
    fun should_refreshStoreAccessOnce_when_theServerGrantedPlus() = runTest {
        repo.result = Result.success(FoundersClaimOutcome.Granted(EXPIRES_AT))

        claim(UID)
        claim(UID)

        assertEquals(listOf(UID), repo.claimedUids)
        assertEquals(listOf(UID), entitlement.refreshedUserIds)
        assertTrue(UID in prefs.foundersPromoClaimedUids)
    }

    @Test
    fun should_claimPerAccount_when_anotherUserSignsInOnTheSamePhone() = runTest {
        claim(UID)
        claim(OTHER_UID)

        assertEquals(listOf(UID, OTHER_UID), repo.claimedUids)
    }

    @Test
    fun should_retryClaim_when_thePreviousAttemptFailed() = runTest {
        repo.result = Result.failure(IllegalStateException("offline"))

        claim(UID)
        assertTrue(prefs.foundersPromoClaimedUids.isEmpty())

        repo.result = Result.success(FoundersClaimOutcome.NotGranted)
        claim(UID)

        assertEquals(listOf(UID, UID), repo.claimedUids)
        assertTrue(UID in prefs.foundersPromoClaimedUids)
    }

    @Test
    fun should_retryStoreRefresh_when_freshCustomerInfoWasUnavailable() = runTest {
        repo.result = Result.success(FoundersClaimOutcome.Granted(EXPIRES_AT))
        entitlement.refreshResult = Result.failure(IllegalStateException("offline"))

        claim(UID)
        assertTrue(prefs.foundersPromoClaimedUids.isEmpty())

        entitlement.refreshResult = Result.success(EntitlementRefresh(Entitlement.PLUS, 0L))
        claim(UID)

        assertEquals(listOf(UID, UID), entitlement.refreshedUserIds)
        assertTrue(UID in prefs.foundersPromoClaimedUids)
    }

    @Test
    fun should_retryBeforeProcessingReceipt_when_revenueCatInitiallyReportsFree() = runTest {
        repo.result = Result.success(FoundersClaimOutcome.Granted(EXPIRES_AT))
        entitlement.refreshResult = Result.success(EntitlementRefresh(Entitlement.FREE, 0L))

        launch { claim(UID) }
        runCurrent()
        assertTrue(prefs.foundersPromoClaimedUids.isEmpty())

        entitlement.refreshResult = Result.success(EntitlementRefresh(Entitlement.PLUS, 0L))
        advanceUntilIdle()

        assertEquals(listOf(UID, UID), entitlement.refreshedUserIds)
        assertTrue(UID in prefs.foundersPromoClaimedUids)
    }

    @Test
    fun should_leaveReceiptPending_when_revenueCatStillReportsFreeAfterRetries() = runTest {
        repo.result = Result.success(FoundersClaimOutcome.Granted(null))
        entitlement.refreshResult = Result.success(EntitlementRefresh(Entitlement.FREE, 0L))

        claim(UID)

        assertEquals(4, entitlement.refreshedUserIds.size)
        assertTrue(prefs.foundersPromoClaimedUids.isEmpty())
    }

    @Test
    fun should_processHistoricalReceipt_when_freshStoreTimeIsPastTheGiftExpiry() = runTest {
        repo.result = Result.success(FoundersClaimOutcome.Granted(EXPIRES_AT))
        entitlement.refreshResult = Result.success(EntitlementRefresh(Entitlement.FREE, EXPIRES_AT))

        claim(UID)

        assertEquals(listOf(UID), entitlement.refreshedUserIds)
        assertTrue(UID in prefs.foundersPromoClaimedUids)
    }

    /**
     * La prueba de ser fundador se escribe SOLO cuando la campaña concede.
     * [MONEY-FOUNDERS-PLUS-PROMO-001]
     */
    @Test
    fun should_recordTheFounderEvidence_when_theCampaignGrantedPlus() = runTest {
        repo.result = Result.success(FoundersClaimOutcome.Granted(expiresAtMillis = null))

        claim(UID)

        assertEquals(setOf(UID), prefs.foundersPromoGrantedUids)
    }

    @Test
    fun should_notRecordAnyFounderEvidence_when_theCampaignDidNotGrantPlus() = runTest {
        repo.result = Result.success(FoundersClaimOutcome.NotGranted)

        claim(UID)

        // Sin esto, cualquiera que llegase a Home con la promo cerrada quedaría marcado como
        // fundador y leería «eres uno de los 2.000 primeros» en cuanto recibiera un Plus
        // promocional por cualquier otra vía.
        assertTrue(prefs.foundersPromoGrantedUids.isEmpty())
        assertEquals(setOf(UID), prefs.foundersPromoClaimedUids, "pero la pregunta sí se da por hecha")
    }

    /**
     * ⛔ Se apunta ANTES de reconciliar: el servidor ya dijo que esta cuenta es una de las 2.000, y
     * eso sigue siendo verdad aunque RevenueCat tarde más de lo que aguantan los reintentos.
     * Perder la prueba ahí sería perder la felicitación para siempre en este dispositivo.
     */
    @Test
    fun should_keepTheFounderEvidence_when_revenueCatNeverConfirms() = runTest {
        repo.result = Result.success(FoundersClaimOutcome.Granted(expiresAtMillis = null))
        entitlement.refreshResult = Result.success(EntitlementRefresh(Entitlement.FREE, 0L))

        claim(UID)

        assertEquals(setOf(UID), prefs.foundersPromoGrantedUids)
        assertTrue(prefs.foundersPromoClaimedUids.isEmpty(), "el recibo sigue pendiente, se reintenta")
    }

    private companion object {
        const val EXPIRES_AT = 1_800_000_000_000L
        const val UID = "uid-founder"
        const val OTHER_UID = "uid-second"
    }
}
