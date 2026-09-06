package com.rndeveloper.paparcar.presentation.paywall

import com.rndeveloper.paparcar.domain.usecase.entitlement.IsStoreAvailableUseCase
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObserveIsPlusUseCase
import com.rndeveloper.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * The Paparcar Plus paywall. [MONEY-001]
 *
 * The purchase itself belongs to the remote paywall composable (purchases-kmp-ui) layered in
 * on top of this state — this ViewModel only answers "is the user already Plus?" so the screen
 * can dismiss itself the moment the entitlement lands, whatever flow granted it (this purchase,
 * a restore, another device). Presentation never names the vendor SDK — both signals arrive
 * through the domain use cases, same as every other screen.
 */
class PaywallViewModel(
    observeIsPlus: ObserveIsPlusUseCase,
    isStoreAvailable: IsStoreAvailableUseCase,
) : BaseViewModel<PaywallState, PaywallIntent, PaywallEffect>() {

    override fun initState(): PaywallState = PaywallState()

    init {
        // Configuration cannot change mid-process (a blank key never becomes a key), so one
        // read at construction is the truth for this screen's lifetime. Kept in state so the
        // content stays state-driven and the gallery can render both variants. [MONEY-001]
        updateState { copy(storeAvailable = isStoreAvailable()) }
        observeIsPlus()
            .onEach { isPlus -> updateState { copy(isPlus = isPlus) } }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: PaywallIntent) {
        when (intent) {
            PaywallIntent.Close -> sendEffect(PaywallEffect.Dismiss)
        }
    }
}
