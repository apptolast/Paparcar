package com.rndeveloper.paparcar.presentation.paywall

import com.rndeveloper.paparcar.domain.usecase.entitlement.ObservePlusGrantUseCase
import com.rndeveloper.paparcar.presentation.base.BaseViewModel
import com.rndeveloper.paparcar.presentation.util.storeManageSubscriptionUrl
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * El recap de solo lectura de Paparcar Plus — para quien ya lo tiene.
 * [MONEY-PLUS-CAN-SEE-ITS-OWN-BENEFITS-001]
 *
 * Hermano de [PaywallViewModel], no su sustituto: ese vende, este sólo informa. No dispara
 * `AnalyticsEvent.PaywallShown` — esa cuenta pertenece al funnel de venta, y aquí no hay nada que
 * vender ni que medir como tal.
 */
class PlusStatusViewModel(
    observePlusGrant: ObservePlusGrantUseCase,
) : BaseViewModel<PlusStatusState, PlusStatusIntent, PlusStatusEffect>() {

    override fun initState(): PlusStatusState = PlusStatusState(
        canManageSubscription = storeManageSubscriptionUrl() != null,
    )

    init {
        observePlusGrant()
            .onEach { grant ->
                updateState {
                    copy(origin = grant?.origin, expiresAtMillis = grant?.expiresAtMillis)
                }
            }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: PlusStatusIntent) {
        when (intent) {
            PlusStatusIntent.ManageSubscription ->
                storeManageSubscriptionUrl()?.let { sendEffect(PlusStatusEffect.OpenUrl(it)) }
        }
    }
}
