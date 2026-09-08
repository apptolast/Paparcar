package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.model.Entitlement
import com.rndeveloper.paparcar.domain.model.PlusOffer
import com.rndeveloper.paparcar.domain.model.PlusPlan
import com.rndeveloper.paparcar.domain.repository.EntitlementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeEntitlementRepository(
    initialEntitlement: Entitlement = Entitlement.FREE,
) : EntitlementRepository {

    private val _entitlement = MutableStateFlow(initialEntitlement)

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

    override suspend fun loadOffers(): Result<List<PlusOffer>> = offersResult

    var restoreCallCount = 0
        private set

    /** Defaults to "nothing to restore" — the common case, and the one with a message to show. */
    var restoreResult: Result<Boolean> = Result.success(false)

    override suspend fun restorePurchases(): Result<Boolean> {
        restoreCallCount++
        // Mirror the real repository: a restore that finds Plus makes the stream emit it.
        restoreResult.onSuccess { if (it) _entitlement.value = Entitlement.PLUS }
        return restoreResult
    }

    override suspend fun purchasePlus(plan: PlusPlan): Result<Unit> {
        purchasePlusCallCount++
        lastPurchasedPlan = plan
        // Mirror the real repository: a successful purchase makes the entitlement stream
        // emit PLUS on its own, without any extra call from the caller.
        purchasePlusResult.onSuccess { _entitlement.value = Entitlement.PLUS }
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
        logOutResult.onSuccess { _entitlement.value = Entitlement.FREE }
        return logOutResult
    }

    fun emitEntitlement(entitlement: Entitlement) {
        _entitlement.value = entitlement
    }
}
