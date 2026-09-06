package com.rndeveloper.paparcar.presentation.paywall

import app.cash.turbine.test
import com.rndeveloper.paparcar.domain.model.Entitlement
import com.rndeveloper.paparcar.domain.usecase.entitlement.IsStoreAvailableUseCase
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObserveIsPlusUseCase
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
        IsStoreAvailableUseCase(entitlementRepo),
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
    fun `should_emitDismiss_when_closeIsRequested`() = runTest {
        val vm = buildVm()

        vm.effect.test {
            vm.handleIntent(PaywallIntent.Close)
            assertEquals(PaywallEffect.Dismiss, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
