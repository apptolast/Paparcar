package com.rndeveloper.paparcar.presentation.paywall

import com.rndeveloper.paparcar.domain.analytics.AnalyticsEvent
import com.rndeveloper.paparcar.domain.analytics.AnalyticsTracker
import com.rndeveloper.paparcar.domain.entitlement.PaywallReason
import com.rndeveloper.paparcar.domain.usecase.entitlement.GetPlusOffersUseCase
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObserveIsPlusUseCase
import com.rndeveloper.paparcar.domain.usecase.entitlement.PurchasePlusUseCase
import com.rndeveloper.paparcar.domain.usecase.entitlement.RestorePurchasesUseCase
import com.rndeveloper.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * The Paparcar Plus paywall. [MONEY-001]
 *
 * Paparcar draws and drives its own paywall: the plans and their prices are read live from the
 * store through [GetPlusOffersUseCase], and buying goes through [PurchasePlusUseCase]. What the
 * store owns is the money — prices, currency, trial, the payment sheet and the receipt. What the
 * app owns is the pitch. [UI-PAYWALL-THE-PITCH-IS-OURS-THE-PRICE-IS-THE-STORES-001]
 *
 * Presentation never names the vendor SDK; every signal arrives through a domain use case, same
 * as every other screen.
 */
class PaywallViewModel(
    observeIsPlus: ObserveIsPlusUseCase,
    private val getPlusOffers: GetPlusOffersUseCase,
    private val purchasePlus: PurchasePlusUseCase,
    private val restorePurchases: RestorePurchasesUseCase,
    /** WHICH limit (or entrance) opened this paywall — rides the route via
     *  `koinViewModel { parametersOf(reason) }`. [FREE-LIMITS-001] */
    reason: PaywallReason = PaywallReason.GENERAL,
    // Fire-and-forget product analytics; nullable for test doubles. [ANALYTICS-EVENTS-001]
    analyticsTracker: AnalyticsTracker? = null,
) : BaseViewModel<PaywallState, PaywallIntent, PaywallEffect>() {

    override fun initState(): PaywallState = PaywallState()

    init {
        // One VM = one paywall open → exactly one funnel entry. [FREE-LIMITS-001]
        analyticsTracker?.track(AnalyticsEvent.PaywallShown(reason))
        observeIsPlus()
            .onEach { isPlus -> updateState { copy(isPlus = isPlus) } }
            .launchIn(viewModelScope)
        // Prices are fetched per screen open rather than cached: a stale price is the one thing
        // this screen must never show, and the store's own cache already makes the repeat cheap.
        viewModelScope.launch {
            val offers = getPlusOffers()
            updateState { copy(offers = offers) }
        }
    }

    override fun handleIntent(intent: PaywallIntent) {
        when (intent) {
            PaywallIntent.Close -> sendEffect(PaywallEffect.Dismiss)
            is PaywallIntent.SelectPlan -> updateState { copy(selectedPlan = intent.plan) }
            PaywallIntent.Purchase -> purchaseSelected()
            PaywallIntent.Restore -> restore()
        }
    }

    private fun restore() {
        if (state.value.restoring) return
        updateState { copy(restoring = true, restoreFoundNothing = false) }
        viewModelScope.launch {
            val found = restorePurchases().getOrDefault(false)
            // A failed call and an account that owns nothing land on the same message on purpose:
            // from the user's side both mean "we could not give you anything back", and inventing
            // a second wording for a network error helps nobody standing in a car park.
            updateState { copy(restoring = false, restoreFoundNothing = !found) }
        }
    }

    private fun purchaseSelected() {
        if (state.value.purchasing) return
        val plan = state.value.selectedOffer?.plan ?: return
        updateState { copy(purchasing = true, purchaseFailed = false) }
        viewModelScope.launch {
            val result = purchasePlus(plan)
            // Success needs no state of its own: the entitlement stream emits and the screen
            // dismisses itself. Only the failure has to be said out loud — and a user who
            // cancelled the store sheet lands here too, which is why the copy is neutral about
            // whose "fault" it was.
            updateState {
                copy(purchasing = false, purchaseFailed = result.isFailure)
            }
        }
    }
}
