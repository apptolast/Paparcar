package com.rndeveloper.paparcar.data.repository

import com.revenuecat.purchases.kmp.models.CustomerInfo
import com.revenuecat.purchases.kmp.models.Offering
import com.revenuecat.purchases.kmp.models.Package
import com.revenuecat.purchases.kmp.models.Store
import com.rndeveloper.paparcar.data.datasource.remote.RevenueCatDataSource
import com.rndeveloper.paparcar.domain.model.Entitlement
import com.rndeveloper.paparcar.domain.model.PlusGrant
import com.rndeveloper.paparcar.domain.model.PlusOffer
import com.rndeveloper.paparcar.domain.model.PlusOrigin
import com.rndeveloper.paparcar.domain.model.PlusPlan
import com.rndeveloper.paparcar.domain.repository.EntitlementRefresh
import com.rndeveloper.paparcar.domain.repository.EntitlementRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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

    /**
     * ⛔ The ONE piece of state this class holds, and the reason it is the grant and not the enum:
     * the enum is derivable from the grant and the grant is not derivable from the enum, so storing
     * the enum would have meant a second field to keep in step. [MONEY-FOUNDERS-WELCOME-001]
     */
    private val grant = MutableStateFlow<PlusGrant?>(null)

    init {
        refreshScope.launch {
            dataSource.observeCustomerInfo().collect { grant.value = it.toGrant() }
        }
    }

    override fun observeEntitlement(): Flow<Entitlement> =
        grant.map { if (it != null) Entitlement.PLUS else Entitlement.FREE }

    override fun observePlusGrant(): Flow<PlusGrant?> = grant

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

        grant.value = dataSource.purchase(pkg).toGrant()
        check(grant.value != null) {
            "purchase finished but the '$ENTITLEMENT_PLUS' entitlement is not active"
        }
    }

    override suspend fun restorePurchases(): Result<Boolean> = runCatching {
        check(dataSource.isConfigured) { "Purchases SDK not configured" }
        grant.value = dataSource.restore().toGrant()
        grant.value != null
    }

    override suspend fun refreshEntitlement(userId: String): Result<EntitlementRefresh> = try {
        check(dataSource.isConfigured && dataSource.appUserID == userId) {
            "RevenueCat identity does not match the account"
        }
        val info = dataSource.fetchCurrentCustomerInfo()
        currentCoroutineContext().ensureActive()
        check(dataSource.appUserID == userId) { "RevenueCat identity changed during refresh" }
        val activeGrant = info.toGrant()
        grant.value = activeGrant
        val entitlement = if (activeGrant != null) Entitlement.PLUS else Entitlement.FREE
        Result.success(EntitlementRefresh(entitlement, info.requestDateMillis))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    override suspend fun logIn(userId: String): Result<Unit> = runCatching {
        if (!dataSource.isConfigured) return@runCatching
        // Warm starts land here on every Authenticated emission; the same uid already bound
        // means there is nothing to do and no network to spend.
        if (dataSource.appUserID == userId) return@runCatching
        grant.value = dataSource.logIn(userId).toGrant()
    }

    override suspend fun logOut(): Result<Unit> = runCatching {
        if (!dataSource.isConfigured) return@runCatching
        // Already-anonymous is our steady state (every logged-out cold start passes through
        // the auth collector), and the SDK treats logOut-while-anonymous as an error.
        if (dataSource.isAnonymous) return@runCatching
        grant.value = dataSource.logOut().toGrant()
    }

    /**
     * The only place that reads the vendor's view of the subscription, and now it reads three
     * fields instead of one. [MONEY-FOUNDERS-WELCOME-001]
     *
     * ⛔ Every store that is not [Store.PROMOTIONAL] maps to [PlusOrigin.PURCHASED], including
     * `UNKNOWN_STORE` and any store RevenueCat adds after this build. That direction is deliberate:
     * being wrong here means a gifted Plus is treated as bought — the user misses a celebration —
     * while the other direction would congratulate a PAYING customer on their free Plus and then
     * warn them it is about to end. Asymmetric failure, cheapest side.
     */
    private fun CustomerInfo.toGrant(): PlusGrant? =
        entitlements.active[ENTITLEMENT_PLUS]?.let { info ->
            PlusGrant(
                origin = if (info.store == Store.PROMOTIONAL) {
                    PlusOrigin.PROMOTIONAL
                } else {
                    PlusOrigin.PURCHASED
                },
                // Null for lifetime access, says the SDK — and for us that is "nothing to count
                // down to", not "unknown". It is passed through rather than defaulted.
                expiresAtMillis = info.expirationDateMillis,
            )
        }

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
