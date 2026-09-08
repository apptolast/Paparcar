package com.rndeveloper.paparcar.presentation.paywall

import app.cash.turbine.test
import com.rndeveloper.paparcar.domain.model.Entitlement
import com.rndeveloper.paparcar.domain.usecase.entitlement.GetPlusOffersUseCase
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObserveIsPlusUseCase
import com.rndeveloper.paparcar.domain.usecase.entitlement.PurchasePlusUseCase
import com.rndeveloper.paparcar.domain.usecase.entitlement.RestorePurchasesUseCase
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PaywallViewModelTest {

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

    private fun buildVm() = PaywallViewModel(
        ObserveIsPlusUseCase(entitlementRepo),
        GetPlusOffersUseCase(entitlementRepo),
        PurchasePlusUseCase(entitlementRepo),
        RestorePurchasesUseCase(entitlementRepo),
    )

    @Test
    fun `should_mirrorTheEntitlementStream_when_plusArrivesByAnyRoute`() = runTest {
        val vm = buildVm()
        assertFalse(vm.state.value.isPlus)

        // The screen's auto-dismiss watches exactly this flip — a restore or a purchase on
        // another device closes the paywall the same way a purchase here does. [MONEY-001]
        entitlementRepo.emitEntitlement(Entitlement.PLUS)

        assertTrue(vm.state.value.isPlus)
    }

    @Test
    fun `should_grantPlus_when_theStoreAccountAlreadyOwnsIt`() = runTest {
        // The reinstall / new-phone case: the user paid, the install knows nothing.
        entitlementRepo.restoreResult = Result.success(true)
        val vm = buildVm()

        vm.handleIntent(PaywallIntent.Restore)

        assertEquals(1, entitlementRepo.restoreCallCount)
        // Plus arrives through the entitlement stream, exactly as a purchase does — which is what
        // makes the screen dismiss itself without restore needing a success flag of its own.
        assertTrue(vm.state.value.isPlus)
        assertFalse(vm.state.value.restoreFoundNothing)
    }

    @Test
    fun `should_sayItFoundNothing_when_theStoreAccountOwnsNoSubscription`() = runTest {
        val vm = buildVm()

        vm.handleIntent(PaywallIntent.Restore)

        assertFalse(vm.state.value.isPlus)
        assertTrue(vm.state.value.restoreFoundNothing)
        assertFalse(vm.state.value.restoring)
    }

    @Test
    fun `should_sayItFoundNothing_when_theRestoreCallItselfFails`() = runTest {
        // A network failure and an empty account are the same sentence to the user: we could not
        // give anything back. Nothing must be granted on a failure.
        entitlementRepo.restoreResult = Result.failure(IllegalStateException("offline"))
        val vm = buildVm()

        vm.handleIntent(PaywallIntent.Restore)

        assertFalse(vm.state.value.isPlus)
        assertTrue(vm.state.value.restoreFoundNothing)
    }

    @Test
    fun `should_emitDismiss_when_closeIsRequested`() = runTest {
        val vm = buildVm()

        vm.effect.test {
            vm.handleIntent(PaywallIntent.Close)
            assertEquals(PaywallEffect.Dismiss, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
