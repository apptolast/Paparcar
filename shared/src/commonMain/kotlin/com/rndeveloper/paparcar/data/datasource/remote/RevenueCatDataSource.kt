package com.rndeveloper.paparcar.data.datasource.remote

import com.revenuecat.purchases.kmp.models.CustomerInfo
import com.revenuecat.purchases.kmp.models.Offerings
import com.revenuecat.purchases.kmp.models.Package
import kotlinx.coroutines.flow.Flow

/**
 * The only place in Paparcar that names the RevenueCat SDK. [MONEY-001]
 *
 * Same seam as [FirebaseDataSource] for Firestore: [RevenueCatEntitlementRepositoryImpl]
 * depends on this interface, never on `Purchases.sharedInstance` directly — the repository
 * owns the business mapping (CustomerInfo → Entitlement, plan → Package), this datasource
 * owns talking to the vendor and can be faked in tests without touching the real SDK.
 */
interface RevenueCatDataSource {

    /** False for the whole process lifetime of a build with no API key (release before the
     *  goog_ key exists, or the mock flavor, which never calls `configure()`). Every other
     *  member is then either a no-op or throws, by contract of the caller checking this first. */
    val isConfigured: Boolean

    /** Null while [isConfigured] is false. */
    val appUserID: String?

    val isAnonymous: Boolean

    /** Emits the current [CustomerInfo] once (if configured) and again on every SDK-pushed
     *  update — purchase, renewal, expiration, restore, including from another device. */
    fun observeCustomerInfo(): Flow<CustomerInfo>

    suspend fun getOfferings(): Offerings

    /** Asks the store which purchases the signed-in Google account already owns, and re-grants
     *  the entitlements they imply. Charges nothing. */
    suspend fun restore(): CustomerInfo

    suspend fun purchase(pkg: Package): CustomerInfo

    suspend fun logIn(userId: String): CustomerInfo

    suspend fun logOut(): CustomerInfo
}
