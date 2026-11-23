package com.rndeveloper.paparcar.presentation.paywall

import app.cash.turbine.test
import com.rndeveloper.paparcar.domain.model.PlusGrant
import com.rndeveloper.paparcar.domain.model.PlusOrigin
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObservePlusGrantUseCase
import com.rndeveloper.paparcar.fakes.FakeEntitlementRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class PlusStatusViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var entitlementRepo: FakeEntitlementRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        entitlementRepo = FakeEntitlementRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildVm() = PlusStatusViewModel(ObservePlusGrantUseCase(entitlementRepo))

    @Test
    fun `should_mirrorOriginAndExpiry_when_theGrantIsPurchasedWithARenewalDate`() = runTest {
        entitlementRepo.emitGrant(PlusGrant(origin = PlusOrigin.PURCHASED, expiresAtMillis = 1_000_000L))
        val vm = buildVm()

        assertEquals(PlusOrigin.PURCHASED, vm.state.value.origin)
        assertEquals(1_000_000L, vm.state.value.expiresAtMillis)
    }

    @Test
    fun `should_mirrorALifetimeGift_when_theGrantIsPromotionalWithNoEndDate`() = runTest {
        entitlementRepo.emitGrant(PlusGrant(origin = PlusOrigin.PROMOTIONAL, expiresAtMillis = null))
        val vm = buildVm()

        assertEquals(PlusOrigin.PROMOTIONAL, vm.state.value.origin)
        assertNull(vm.state.value.expiresAtMillis)
    }

    /** "Gestionar suscripción" abre la puerta de la TIENDA, nunca una llamada de compra propia.
     *  [MONEY-A-PROMO-CODE-IS-REDEEMED-WHERE-THE-STORE-LIVES-001] */
    @Test
    fun `should_openTheStoreSubscriptionsUrl_when_manageSubscriptionIsTapped`() = runTest {
        val vm = buildVm()
        vm.effect.test {
            vm.handleIntent(PlusStatusIntent.ManageSubscription)
            val effect = awaitItem()
            assertEquals(true, effect is PlusStatusEffect.OpenUrl)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
