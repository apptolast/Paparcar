package com.rndeveloper.paparcar.presentation.paywall

import app.cash.turbine.test
import com.rndeveloper.paparcar.domain.model.Entitlement
import com.rndeveloper.paparcar.domain.usecase.entitlement.GetPlusOffersUseCase
import com.rndeveloper.paparcar.domain.model.PlusGrant
import com.rndeveloper.paparcar.domain.model.PlusOrigin
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObservePlusGrantUseCase
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
import kotlin.test.assertNull
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

    private fun buildVm(
        reason: com.rndeveloper.paparcar.domain.entitlement.PaywallReason =
            com.rndeveloper.paparcar.domain.entitlement.PaywallReason.GENERAL,
        tracker: com.rndeveloper.paparcar.fakes.FakeAnalyticsTracker? = null,
    ) = PaywallViewModel(
        ObservePlusGrantUseCase(entitlementRepo),
        GetPlusOffersUseCase(entitlementRepo),
        PurchasePlusUseCase(entitlementRepo),
        RestorePurchasesUseCase(entitlementRepo),
        reason = reason,
        analyticsTracker = tracker,
    )

    // ── Funnel entry [FREE-LIMITS-001] ───────────────────────────────────────

    @Test
    fun `should_emitOnePaywallShownWithItsReason_when_opened`() = runTest {
        val tracker = com.rndeveloper.paparcar.fakes.FakeAnalyticsTracker()

        buildVm(reason = com.rndeveloper.paparcar.domain.entitlement.PaywallReason.ZONES, tracker = tracker)

        val shown = tracker.events
            .filterIsInstance<com.rndeveloper.paparcar.domain.analytics.AnalyticsEvent.PaywallShown>()
            .single()
        assertEquals(com.rndeveloper.paparcar.domain.entitlement.PaywallReason.ZONES, shown.reason)
    }

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

    // ── Un Plus de REGALO que se acaba todavía tiene algo que vender ──────────
    // [MONEY-FOUNDERS-WELCOME-001]

    /**
     * ⛔ El defecto que este ticket tuvo que arreglar para que el aviso de fin de promo no fuera
     * una puerta tapiada: la pantalla se cerraba sola con sólo ver `isPlus`, así que el único
     * usuario con una decisión real que tomar no podía ni leerla.
     */
    @Test
    fun `should_stayOpen_when_thePlusWasGiftedAndExpires`() = runTest {
        val endsAt = 1_800_000_000_000L
        entitlementRepo.emitGrant(PlusGrant(PlusOrigin.PROMOTIONAL, endsAt))
        val vm = buildVm()

        assertTrue(vm.state.value.isPlus)
        assertEquals(endsAt, vm.state.value.plusPromoEndsAtMillis, "y la pantalla puede decir hasta cuándo")
        assertFalse(vm.state.value.dismissesItself, "queda la continuación por vender")
    }

    @Test
    fun `should_dismissItself_when_thePlusWasBought`() = runTest {
        entitlementRepo.emitGrant(PlusGrant(PlusOrigin.PURCHASED, null))
        val vm = buildVm()

        assertNull(vm.state.value.plusPromoEndsAtMillis)
        assertTrue(vm.state.value.dismissesItself, "una suscripcion que renueva no tiene nada que vender aqui")
    }

    /** Un regalo VITALICIO tampoco se acaba, así que tampoco hay nada que vender. */
    @Test
    fun `should_dismissItself_when_theGiftedPlusHasNoEndDate`() = runTest {
        entitlementRepo.emitGrant(PlusGrant(PlusOrigin.PROMOTIONAL, expiresAtMillis = null))
        val vm = buildVm()

        assertTrue(vm.state.value.dismissesItself)
    }
}
