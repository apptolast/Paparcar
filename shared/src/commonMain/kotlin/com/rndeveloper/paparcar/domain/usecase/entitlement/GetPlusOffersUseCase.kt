package com.rndeveloper.paparcar.domain.usecase.entitlement

import com.rndeveloper.paparcar.domain.model.PlusOffer
import com.rndeveloper.paparcar.domain.repository.EntitlementRepository

/**
 * The plans the store can sell right now, with live localised prices. [MONEY-001]
 *
 * A verdict, not a predicate: its result is what the user reads on the paywall and what decides
 * whether the price cards appear at all, so it earns its own use case.
 * [DET-VERDICT-NOT-PREDICATE-001]
 *
 * A failure is folded into an empty list on purpose. The paywall's answer to "the store is
 * unreachable" and to "the store has nothing registered" is the same screen — the comparison
 * without price cards and one honest line — so making the caller branch on an exception would
 * buy a distinction it cannot act on.
 */
class GetPlusOffersUseCase(
    private val entitlementRepository: EntitlementRepository,
) {
    suspend operator fun invoke(): List<PlusOffer> =
        entitlementRepository.loadOffers().getOrDefault(emptyList())
}
