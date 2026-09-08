package com.rndeveloper.paparcar.data.datasource.remote

import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesDelegate
import com.revenuecat.purchases.kmp.ktx.awaitCustomerInfo
import com.revenuecat.purchases.kmp.ktx.awaitLogIn
import com.revenuecat.purchases.kmp.ktx.awaitLogOut
import com.revenuecat.purchases.kmp.ktx.awaitOfferings
import com.revenuecat.purchases.kmp.ktx.awaitPurchase
import com.revenuecat.purchases.kmp.ktx.awaitRestore
import com.revenuecat.purchases.kmp.models.CustomerInfo
import com.revenuecat.purchases.kmp.models.Offerings
import com.revenuecat.purchases.kmp.models.Package
import com.revenuecat.purchases.kmp.models.PurchasesError
import com.revenuecat.purchases.kmp.models.StoreProduct
import com.revenuecat.purchases.kmp.models.StoreTransaction
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** Wraps the RevenueCat SDK's global singleton behind [RevenueCatDataSource]. [MONEY-001] */
class RevenueCatDataSourceImpl : RevenueCatDataSource {

    // The delegate is the SDK's single push channel; replay=1 so a subscriber that starts
    // collecting between two pushes still gets the latest one instead of missing it.
    private val updates = MutableSharedFlow<CustomerInfo>(replay = 1, extraBufferCapacity = 1)

    override val isConfigured: Boolean get() = Purchases.isConfigured
    override val appUserID: String? get() = if (isConfigured) Purchases.sharedInstance.appUserID else null
    override val isAnonymous: Boolean get() = isConfigured && Purchases.sharedInstance.isAnonymous

    init {
        if (isConfigured) {
            Purchases.sharedInstance.delegate = object : PurchasesDelegate {
                override fun onCustomerInfoUpdated(customerInfo: CustomerInfo) {
                    updates.tryEmit(customerInfo)
                }

                override fun onPurchasePromoProduct(
                    product: StoreProduct,
                    startPurchase: (
                        onError: (error: PurchasesError, userCancelled: Boolean) -> Unit,
                        onSuccess: (storeTransaction: StoreTransaction, customerInfo: CustomerInfo) -> Unit,
                    ) -> Unit,
                ) {
                    // Paparcar sells no store-promoted products; deliberately not started.
                }
            }
        }
    }

    override fun observeCustomerInfo(): Flow<CustomerInfo> = flow {
        if (isConfigured) {
            runCatching { Purchases.sharedInstance.awaitCustomerInfo() }
                .onSuccess { emit(it) }
                .onFailure { e ->
                    // Offline cold start: stay on whatever the repo already had until the
                    // delegate hears otherwise — never abort the stream over one failed fetch.
                    PaparcarLogger.w(TAG, "Initial customer info fetch failed", e)
                }
        }
        emitAll(updates)
    }

    override suspend fun getOfferings(): Offerings = Purchases.sharedInstance.awaitOfferings()

    override suspend fun restore(): CustomerInfo = Purchases.sharedInstance.awaitRestore()

    override suspend fun purchase(pkg: Package): CustomerInfo =
        Purchases.sharedInstance.awaitPurchase(pkg).customerInfo

    override suspend fun logIn(userId: String): CustomerInfo =
        Purchases.sharedInstance.awaitLogIn(userId).customerInfo

    override suspend fun logOut(): CustomerInfo = Purchases.sharedInstance.awaitLogOut()

    private companion object {
        const val TAG = "RevenueCatDataSource"
    }
}
