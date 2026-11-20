package com.rndeveloper.paparcar.data.repository

import app.cash.turbine.test
import com.revenuecat.purchases.kmp.models.PeriodType
import com.revenuecat.purchases.kmp.models.Store
import com.rndeveloper.paparcar.domain.model.Entitlement
import com.rndeveloper.paparcar.domain.model.PlusGrant
import com.rndeveloper.paparcar.domain.model.PlusOrigin
import com.rndeveloper.paparcar.fakes.FakeRevenueCatDataSource
import com.rndeveloper.paparcar.fakes.FakeRevenueCatDataSource.Companion.lapsedPlusEntitlement
import com.rndeveloper.paparcar.fakes.FakeRevenueCatDataSource.Companion.plusEntitlement
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The ONE place that reads the vendor's view of the subscription.
 * [MONEY-FOUNDERS-WELCOME-001] [MONEY-001]
 *
 * It had no test, and now it has to: a product decision rides on this mapping. **Plus given away
 * to friends goes out as PLAY PROMO CODES, not as a RevenueCat grant**, precisely so those people
 * are not told they are one of the first 2,000 founders. A redeemed Play code buys a real Play
 * subscription, so the store says `PLAY_STORE` — and that is the only thing in the whole app that
 * tells the two kinds of free Plus apart. Below the data layer both look like [Entitlement.PLUS].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RevenueCatEntitlementRepositoryImplTest {

    /**
     * ⛔ `backgroundScope` and not the test scope itself. The repository collects the SDK's
     * `CustomerInfo` for as long as it lives, and that collector never completes — handed the test
     * scope, every test here died with `UncompletedCoroutinesError` instead of asserting anything.
     * `backgroundScope` is cancelled when the test body ends, which is exactly the lifetime the
     * real `refreshScope` has in the app.
     */
    private fun TestScope.repository(dataSource: FakeRevenueCatDataSource) =
        RevenueCatEntitlementRepositoryImpl(dataSource = dataSource, refreshScope = backgroundScope)

    // ── Where the Plus came from ─────────────────────────────────────────────

    @Test
    fun should_readTheGrantAsPromotional_when_theStoreSaysItWasGrantedInRevenueCat() = runTest {
        val source = FakeRevenueCatDataSource()
        val repo = repository(source)
        source.emit(plusEntitlement(Store.PROMOTIONAL, expiresAtMillis = ENDS_AT))
        runCurrent()

        repo.observePlusGrant().test {
            assertEquals(PlusGrant(PlusOrigin.PROMOTIONAL, ENDS_AT), awaitItem())
        }
    }

    /**
     * ⛔ The one that protects the product decision: a **Play promo code** is a real purchase, so
     * it must come out as [PlusOrigin.PURCHASED] and never trigger the founders welcome. If this
     * test ever fails, every friend who redeems a gift code is told they are one of the first
     * 2,000 — which they are not.
     */
    @Test
    fun should_readTheGrantAsPurchased_when_aPlayPromoCodeBoughtTheSubscription() = runTest {
        val source = FakeRevenueCatDataSource()
        val repo = repository(source)
        // A redeemed code can arrive inside its intro period; neither field changes the origin.
        source.emit(
            plusEntitlement(Store.PLAY_STORE, expiresAtMillis = ENDS_AT, periodType = PeriodType.INTRO),
        )
        runCurrent()

        repo.observePlusGrant().test {
            assertEquals(PlusGrant(PlusOrigin.PURCHASED, ENDS_AT), awaitItem())
        }
    }

    /** Every store that is not RevenueCat's own grant is a purchase, including ones this build has
     *  never seen. Being wrong here costs a missed celebration; being wrong the other way would
     *  congratulate a paying customer and then warn them their plan is ending. */
    @Test
    fun should_readTheGrantAsPurchased_when_theStoreIsOneThisBuildDoesNotKnow() = runTest {
        val source = FakeRevenueCatDataSource()
        val repo = repository(source)
        source.emit(plusEntitlement(Store.UNKNOWN_STORE))
        runCurrent()

        repo.observePlusGrant().test {
            assertEquals(PlusGrant(PlusOrigin.PURCHASED, expiresAtMillis = null), awaitItem())
        }
    }

    /** A lifetime grant has no expiry, and that is passed through rather than defaulted: the copy
     *  then promises no date instead of inventing one. */
    @Test
    fun should_carryNoExpiry_when_thePromotionalGrantHasNoEndDate() = runTest {
        val source = FakeRevenueCatDataSource()
        val repo = repository(source)
        source.emit(plusEntitlement(Store.PROMOTIONAL, expiresAtMillis = null))
        runCurrent()

        repo.observePlusGrant().test {
            assertEquals(PlusGrant(PlusOrigin.PROMOTIONAL, expiresAtMillis = null), awaitItem())
        }
    }

    // ── The grant is the state; the enum is its projection ───────────────────

    @Test
    fun should_reportFreeAndNoGrant_when_thePlusEntitlementIsNotActive() = runTest {
        val source = FakeRevenueCatDataSource()
        val repo = repository(source)
        // Present in `all`, absent from `active` — a lapsed promo, which is how the three months
        // end. The repository reads `active`, so this is FREE.
        source.emit(lapsedPlusEntitlement(Store.PROMOTIONAL))
        runCurrent()

        repo.observePlusGrant().test { assertNull(awaitItem()) }
        repo.observeEntitlement().test { assertEquals(Entitlement.FREE, awaitItem()) }
    }

    /** ⛔ The invariant that makes two streams safe: the enum is DERIVED, so the gates and the
     *  welcome can never disagree about whether this user is Plus. */
    @Test
    fun should_keepTheEnumInStepWithTheGrant_when_theStorePushesAnUpdate() = runTest {
        val source = FakeRevenueCatDataSource()
        val repo = repository(source)

        repo.observeEntitlement().test {
            assertEquals(Entitlement.FREE, awaitItem())

            source.emit(plusEntitlement(Store.PROMOTIONAL, expiresAtMillis = ENDS_AT))
            runCurrent()
            assertEquals(Entitlement.PLUS, awaitItem())

            source.emit(null)
            runCurrent()
            assertEquals(Entitlement.FREE, awaitItem())
        }
    }

    // ── Identity ─────────────────────────────────────────────────────────────

    @Test
    fun should_notTouchTheNetwork_when_theSameUidIsAlreadyBound() = runTest {
        val source = FakeRevenueCatDataSource(appUserID = "uid-1", isAnonymous = false)
        val repo = repository(source)
        source.logInResult = plusEntitlement(Store.PROMOTIONAL)

        repo.logIn("uid-1")
        runCurrent()

        // The same-uid guard returned early, so the grant never arrived from logIn.
        repo.observePlusGrant().test { assertNull(awaitItem()) }
    }

    @Test
    fun should_dropTheGrant_when_theStoreIdentityIsUnbound() = runTest {
        val source = FakeRevenueCatDataSource(appUserID = "uid-1", isAnonymous = false)
        val repo = repository(source)
        source.emit(plusEntitlement(Store.PROMOTIONAL, expiresAtMillis = ENDS_AT))
        runCurrent()

        repo.logOut()
        runCurrent()

        repo.observePlusGrant().test { assertNull(awaitItem()) }
    }

    private companion object {
        const val ENDS_AT = 1_800_000_000_000L
    }
}
