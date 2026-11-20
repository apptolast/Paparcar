package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.model.Entitlement
import com.rndeveloper.paparcar.domain.model.PlusGrant
import com.rndeveloper.paparcar.domain.model.PlusOffer
import com.rndeveloper.paparcar.domain.model.PlusOrigin
import com.rndeveloper.paparcar.domain.model.PlusPlan
import com.rndeveloper.paparcar.domain.repository.EntitlementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeEntitlementRepository(
    initialEntitlement: Entitlement = Entitlement.FREE,
) : EntitlementRepository {

    /**
     * ⛔ Mirrors the real repository's shape: the GRANT is the state and the enum is its
     * projection. A fake that kept the two apart would let a test set up a combination the
     * production class cannot produce — Plus with no grant — and then pass on it.
     * [MONEY-FOUNDERS-WELCOME-001]
     */
    private val _grant = MutableStateFlow(
        if (initialEntitlement == Entitlement.PLUS) PURCHASED_FOREVER else null,
    )

    private val _entitlement = _grant.map { if (it != null) Entitlement.PLUS else Entitlement.FREE }

    var purchasePlusCallCount = 0
        private set
    var lastPurchasedPlan: PlusPlan? = null
        private set
    var purchasePlusResult: Result<Unit> = Result.success(Unit)

    var logInCallCount = 0
        private set
    var lastLoggedInUserId: String? = null
        private set
    var logInResult: Result<Unit> = Result.success(Unit)

    var logOutCallCount = 0
        private set
    var logOutResult: Result<Unit> = Result.success(Unit)

    /** Defaults to EMPTY — the state a build with no store is in. A test that wants price cards
     *  says so, so no test accidentally passes because plans appeared out of nowhere. */
    var offersResult: Result<List<PlusOffer>> = Result.success(emptyList())

    override fun observeEntitlement(): Flow<Entitlement> = _entitlement

    override fun observePlusGrant(): Flow<PlusGrant?> = _grant

    override suspend fun loadOffers(): Result<List<PlusOffer>> = offersResult

    var restoreCallCount = 0
        private set

    /** Defaults to "nothing to restore" — the common case, and the one with a message to show. */
    var restoreResult: Result<Boolean> = Result.success(false)

    override suspend fun restorePurchases(): Result<Boolean> {
        restoreCallCount++
        // Mirror the real repository: a restore that finds Plus makes the stream emit it.
        restoreResult.onSuccess { if (it) _grant.value = PURCHASED_FOREVER }
        return restoreResult
    }

    override suspend fun purchasePlus(plan: PlusPlan): Result<Unit> {
        purchasePlusCallCount++
        lastPurchasedPlan = plan
        // Mirror the real repository: a successful purchase makes the entitlement stream
        // emit PLUS on its own, without any extra call from the caller.
        purchasePlusResult.onSuccess { _grant.value = PURCHASED_FOREVER }
        return purchasePlusResult
    }

    override suspend fun logIn(userId: String): Result<Unit> {
        logInCallCount++
        lastLoggedInUserId = userId
        return logInResult
    }

    override suspend fun logOut(): Result<Unit> {
        logOutCallCount++
        // Mirror the real repository: back to an anonymous profile with no entitlements.
        logOutResult.onSuccess { _grant.value = null }
        return logOutResult
    }

    /** Plus arrives the way a purchase makes it arrive — the plain case most tests mean. */
    fun emitEntitlement(entitlement: Entitlement) {
        _grant.value = if (entitlement == Entitlement.PLUS) PURCHASED_FOREVER else null
    }

    /** Plus arrives with a provenance: a gift, an expiry date, a plan about to lapse.
     *  [MONEY-FOUNDERS-WELCOME-001] */
    fun emitGrant(grant: PlusGrant?) {
        _grant.value = grant
    }

    private companion object {
        /** What a bought subscription looks like to the two consumers of the grant: paid for, and
         *  with no end to count down to. */
        val PURCHASED_FOREVER = PlusGrant(origin = PlusOrigin.PURCHASED, expiresAtMillis = null)
    }
}
