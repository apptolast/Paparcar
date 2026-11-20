package com.rndeveloper.paparcar.domain.usecase.entitlement

import com.rndeveloper.paparcar.domain.model.PlusGrant
import com.rndeveloper.paparcar.domain.repository.EntitlementRepository
import kotlinx.coroutines.flow.Flow

/**
 * HOW the active Plus arrived and when it ends — null while free. [MONEY-FOUNDERS-WELCOME-001]
 *
 * Sibling of [ObserveIsPlusUseCase], not a replacement: that one answers the question the six
 * gates ask (*can they?*) and is the one to reach for by default. This one answers *where did it
 * come from*, and it has exactly two callers — the root ViewModel that celebrates a gift and warns
 * when it ends, and the paywall, which must not close itself on a Plus that is about to lapse.
 *
 * Presentation still never names the store SDK: `Store.PROMOTIONAL` is mapped once, in the data
 * layer. [MONEY-001]
 */
class ObservePlusGrantUseCase(
    private val entitlementRepository: EntitlementRepository,
) {
    operator fun invoke(): Flow<PlusGrant?> = entitlementRepository.observePlusGrant()
}
