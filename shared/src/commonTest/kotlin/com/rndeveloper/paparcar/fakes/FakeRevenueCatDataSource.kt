package com.rndeveloper.paparcar.fakes

import com.revenuecat.purchases.kmp.models.CustomerInfo
import com.revenuecat.purchases.kmp.models.EntitlementInfo
import com.revenuecat.purchases.kmp.models.EntitlementInfos
import com.revenuecat.purchases.kmp.models.Offerings
import com.revenuecat.purchases.kmp.models.OwnershipType
import com.revenuecat.purchases.kmp.models.Package
import com.revenuecat.purchases.kmp.models.PeriodType
import com.revenuecat.purchases.kmp.models.Store
import com.revenuecat.purchases.kmp.models.VerificationResult
import com.rndeveloper.paparcar.data.datasource.remote.RevenueCatDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The vendor seam, faked. [MONEY-FOUNDERS-WELCOME-001]
 *
 * ⛔ It exists so the ONE mapping that reads the store —`CustomerInfo` → `PlusGrant`— can have a
 * witness, and it had none. That mapping is where the product rule *«a Play promo code must not be
 * congratulated as a founder»* is actually enforced: the domain only ever sees `PURCHASED` or
 * `PROMOTIONAL`, so a test at that level cannot tell a redeemed code from a card payment. The
 * difference lives here, in `Store`.
 *
 * Builds real SDK objects rather than stubbing the interface's return type, because the question
 * under test is precisely which FIELD of a real `EntitlementInfo` we read.
 */
class FakeRevenueCatDataSource(
    override val isConfigured: Boolean = true,
    override var appUserID: String? = "anonymous",
    override var isAnonymous: Boolean = true,
) : RevenueCatDataSource {

    private val customerInfo = MutableStateFlow(customerInfo(plus = null))

    override fun observeCustomerInfo(): Flow<CustomerInfo> = customerInfo

    /** Push what the store now says about the `plus` entitlement — null for "not active". */
    fun emit(plus: EntitlementInfo?) {
        customerInfo.value = customerInfo(plus = plus)
    }

    override suspend fun getOfferings(): Offerings =
        throw NotImplementedError("no test here asks the fake for prices")

    var restoreResult: EntitlementInfo? = null
    override suspend fun restore(): CustomerInfo = customerInfo(plus = restoreResult)

    var purchaseResult: EntitlementInfo? = null
    override suspend fun purchase(pkg: Package): CustomerInfo = customerInfo(plus = purchaseResult)

    var logInResult: EntitlementInfo? = null
    override suspend fun logIn(userId: String): CustomerInfo {
        appUserID = userId
        isAnonymous = false
        return customerInfo(plus = logInResult)
    }

    override suspend fun logOut(): CustomerInfo {
        appUserID = "anonymous"
        isAnonymous = true
        return customerInfo(plus = null)
    }

    companion object {
        /**
         * An active `plus` entitlement as the given store would report it.
         *
         * @param store `PROMOTIONAL` is a grant made inside RevenueCat (the «Fundadores» promo);
         *   `PLAY_STORE` is what a REDEEMED PLAY PROMO CODE looks like, because the code buys a
         *   real Play subscription — which is exactly why the two must not be confused.
         */
        fun plusEntitlement(
            store: Store,
            expiresAtMillis: Long? = null,
            periodType: PeriodType = PeriodType.NORMAL,
        ) = EntitlementInfo(
            identifier = "plus",
            isActive = true,
            willRenew = store != Store.PROMOTIONAL,
            periodType = periodType,
            latestPurchaseDateMillis = 0L,
            originalPurchaseDateMillis = 0L,
            expirationDateMillis = expiresAtMillis,
            store = store,
            productIdentifier = "paparcar_plus:monthly",
            productPlanIdentifier = "monthly",
            isSandbox = false,
            unsubscribeDetectedAtMillis = null,
            billingIssueDetectedAtMillis = null,
            ownershipType = OwnershipType.PURCHASED,
            verification = VerificationResult.NOT_REQUESTED,
        )

        /** An entitlement that has lapsed: present in `all`, absent from `active`. */
        fun lapsedPlusEntitlement(store: Store) =
            plusEntitlement(store).let { live ->
                EntitlementInfo(
                    identifier = live.identifier,
                    isActive = false,
                    willRenew = false,
                    periodType = live.periodType,
                    latestPurchaseDateMillis = live.latestPurchaseDateMillis,
                    originalPurchaseDateMillis = live.originalPurchaseDateMillis,
                    expirationDateMillis = live.expirationDateMillis,
                    store = live.store,
                    productIdentifier = live.productIdentifier,
                    productPlanIdentifier = live.productPlanIdentifier,
                    isSandbox = live.isSandbox,
                    unsubscribeDetectedAtMillis = null,
                    billingIssueDetectedAtMillis = null,
                    ownershipType = live.ownershipType,
                    verification = live.verification,
                )
            }

        private fun customerInfo(plus: EntitlementInfo?) = CustomerInfo(
            activeSubscriptions = emptySet(),
            allExpirationDateMillis = emptyMap(),
            allPurchaseDateMillis = emptyMap(),
            allPurchasedProductIdentifiers = emptySet(),
            entitlements = EntitlementInfos(
                all = plus?.let { mapOf(it.identifier to it) }.orEmpty(),
                verification = VerificationResult.NOT_REQUESTED,
            ),
            firstSeenMillis = 0L,
            latestExpirationDateMillis = null,
            managementUrlString = null,
            subscriptionsByProductIdentifier = emptyMap(),
            nonSubscriptionTransactions = emptyList(),
            originalAppUserId = "uid-1",
            originalApplicationVersion = null,
            originalPurchaseDateMillis = null,
            requestDateMillis = 0L,
        )
    }
}
