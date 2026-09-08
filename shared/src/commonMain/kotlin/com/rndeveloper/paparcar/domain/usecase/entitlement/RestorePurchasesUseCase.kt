package com.rndeveloper.paparcar.domain.usecase.entitlement

import com.rndeveloper.paparcar.domain.repository.EntitlementRepository

/**
 * Gives back what the user already paid for. [MONEY-001]
 *
 * A subscription belongs to the GOOGLE ACCOUNT that bought it, not to the phone or to the install.
 * So there are ordinary situations where a paying user opens Paparcar and the app has no idea:
 * a reinstall, a new phone, cleared app data, or a purchase made before signing in. Without this,
 * the only route those users can see is paying a second time.
 *
 * Signing in already covers most of it — `logIn(uid)` binds the store identity to the Paparcar
 * account, so Plus usually returns on its own. This is the net under the rest, and Play's
 * subscription policy requires it to exist at all.
 *
 * Returns true when Plus is active afterwards. False is not a failure: it means that store account
 * genuinely owns nothing, which the paywall says in one plain sentence.
 */
class RestorePurchasesUseCase(
    private val entitlementRepository: EntitlementRepository,
) {
    suspend operator fun invoke(): Result<Boolean> = entitlementRepository.restorePurchases()
}
