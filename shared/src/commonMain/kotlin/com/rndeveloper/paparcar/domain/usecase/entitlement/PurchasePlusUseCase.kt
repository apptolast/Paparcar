package com.rndeveloper.paparcar.domain.usecase.entitlement

import com.rndeveloper.paparcar.domain.model.PlusPlan
import com.rndeveloper.paparcar.domain.repository.EntitlementRepository

/**
 * Starts the purchase of Paparcar Plus for the chosen plan. The store dialog, the receipt
 * and the entitlement grant all live behind [EntitlementRepository]; success here means
 * `plus` is active and [ObserveIsPlusUseCase] will emit true on its own. [MONEY-001]
 */
class PurchasePlusUseCase(
    private val entitlementRepository: EntitlementRepository,
) {
    suspend operator fun invoke(plan: PlusPlan): Result<Unit> =
        entitlementRepository.purchasePlus(plan)
}
