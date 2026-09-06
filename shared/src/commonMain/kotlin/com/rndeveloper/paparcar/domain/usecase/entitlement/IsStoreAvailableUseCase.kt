package com.rndeveloper.paparcar.domain.usecase.entitlement

import com.rndeveloper.paparcar.domain.repository.EntitlementRepository

/**
 * Whether the store can actually sell right now. The paywall reads this to choose between
 * the remote template and its static fallback — through the repository, never by asking the
 * vendor SDK directly from presentation. [MONEY-001]
 */
class IsStoreAvailableUseCase(
    private val entitlementRepository: EntitlementRepository,
) {
    operator fun invoke(): Boolean = entitlementRepository.isStoreAvailable
}
