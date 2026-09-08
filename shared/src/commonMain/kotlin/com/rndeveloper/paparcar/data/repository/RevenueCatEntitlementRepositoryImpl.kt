package com.rndeveloper.paparcar.data.repository

import com.revenuecat.purchases.kmp.models.CustomerInfo
import com.revenuecat.purchases.kmp.models.Offering
import com.revenuecat.purchases.kmp.models.Package
import com.rndeveloper.paparcar.data.datasource.remote.RevenueCatDataSource
import com.rndeveloper.paparcar.domain.model.Entitlement
import com.rndeveloper.paparcar.domain.model.PlusOffer
import com.rndeveloper.paparcar.domain.model.PlusPlan
import com.rndeveloper.paparcar.domain.repository.EntitlementRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Maps [RevenueCatDataSource] to the domain's [Entitlement] — the only class that knows what
 * "plus" the entitlement identifier means for Paparcar. It does not talk to the SDK itself;
 * that seam is [RevenueCatDataSource], injected like any other repo's remote datasource
 * (compare [ZoneRepositoryImpl] + `FirebaseDataSource`). [MONEY-001]
 *
 * Subscription state is never mirrored anywhere else (no Firestore, no Room, no prefs):
 * losing this process loses nothing, because the SDK's own cache is the source of truth.
 */
class RevenueCatEntitlementRepositoryImpl(
    private val dataSource: RevenueCatDataSource,
    refreshScope: CoroutineScope,
) : EntitlementRepository {

    private val entitlement = MutableStateFlow(Entitlement.FREE)

    init {
        refreshScope.launch {
            dataSource.observeCustomerInfo().collect { entitlement.value = it.toEntitlement() }
        }
    }

    override fun observeEntitlement(): Flow<Entitlement> = entitlement

    override suspend fun loadOffers(): Result<List<PlusOffer>> = runCatching {
        if (!dataSource.isConfigured) return@runCatching emptyList()
        val offering = defaultOffering() ?: return@runCatching emptyList()
        // Order is the order the paywall shows them in, and it is deliberate: monthly first,
        // because it is the smaller commitment and the honest default to compare against.
        listOfNotNull(
            offering.monthly?.toOffer(PlusPlan.MONTHLY),
            offering.annual?.toOffer(PlusPlan.YEARLY),
        )
    }

    private fun Package.toOffer(plan: PlusPlan): PlusOffer = PlusOffer(
        plan = plan,
        price = storeProduct.price.formatted,
        amountMicros = storeProduct.price.amountMicros,
        // Only the yearly plan says "≈ x/month"; on the monthly one it would restate the price.
        pricePerMonth = storeProduct.pricePerMonth?.formatted.takeIf { plan == PlusPlan.YEARLY },
        // The trial is whatever the store is offering TODAY. Reading it (instead of hardcoding
        // the 7 days the product was designed with) means a trial removed in the dashboard stops
        // being promised in the app immediately, with no release. [MONEY-001]
        freeTrialDays = storeProduct.introductoryDiscount
            ?.takeIf { it.price.amountMicros == 0L }
            ?.let { it.subscriptionPeriod.valueInMonths * DAYS_PER_MONTH }
            ?.roundToInt()
            ?.takeIf { it > 0 },
    )

    private suspend fun defaultOffering(): Offering? = dataSource.getOfferings().let {
        it.getOffering(OFFERING_DEFAULT) ?: it.current
    }

    override suspend fun purchasePlus(plan: PlusPlan): Result<Unit> = runCatching {
        check(dataSource.isConfigured) { "Purchases SDK not configured" }
        val offering = defaultOffering() ?: error("no offering available to purchase")
        val pkg = when (plan) {
            PlusPlan.MONTHLY -> offering.monthly
            PlusPlan.YEARLY -> offering.annual
        } ?: error("offering '${offering.identifier}' has no ${plan.name} package")

        entitlement.value = dataSource.purchase(pkg).toEntitlement()
        check(entitlement.value == Entitlement.PLUS) {
            "purchase finished but the '$ENTITLEMENT_PLUS' entitlement is not active"
        }
    }

    override suspend fun restorePurchases(): Result<Boolean> = runCatching {
        check(dataSource.isConfigured) { "Purchases SDK not configured" }
        entitlement.value = dataSource.restore().toEntitlement()
        entitlement.value == Entitlement.PLUS
    }

    override suspend fun logIn(userId: String): Result<Unit> = runCatching {
        if (!dataSource.isConfigured) return@runCatching
        // Warm starts land here on every Authenticated emission; the same uid already bound
        // means there is nothing to do and no network to spend.
        if (dataSource.appUserID == userId) return@runCatching
        entitlement.value = dataSource.logIn(userId).toEntitlement()
    }

    override suspend fun logOut(): Result<Unit> = runCatching {
        if (!dataSource.isConfigured) return@runCatching
        // Already-anonymous is our steady state (every logged-out cold start passes through
        // the auth collector), and the SDK treats logOut-while-anonymous as an error.
        if (dataSource.isAnonymous) return@runCatching
        entitlement.value = dataSource.logOut().toEntitlement()
    }

    private fun CustomerInfo.toEntitlement(): Entitlement =
        if (entitlements.active.containsKey(ENTITLEMENT_PLUS)) Entitlement.PLUS else Entitlement.FREE

    private companion object {
        // Fixed in the RevenueCat dashboard; the packages inside the offering are the
        // standard $rc_monthly / $rc_annual, reached via Offering.monthly / .annual.
        const val ENTITLEMENT_PLUS = "plus"
        const val OFFERING_DEFAULT = "default"

        /** The store states a trial's length as a subscription PERIOD, and the only common unit
         *  it exposes is months — so "7 days" arrives as 0.2299… months and has to come back.
         *  An average month, because the exact one depends on when the trial starts, and the
         *  copy says "7 days free", not a date. */
        const val DAYS_PER_MONTH = 30.44
    }
}
