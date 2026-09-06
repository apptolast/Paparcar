package com.rndeveloper.paparcar.fakes.data.repository

import com.rndeveloper.paparcar.domain.model.Entitlement
import com.rndeveloper.paparcar.domain.model.PlusPlan
import com.rndeveloper.paparcar.domain.repository.EntitlementRepository
import com.rndeveloper.paparcar.fakes.MockScenario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Scenario-aware entitlement for the mock flavor: the Dev Catalog's "Plus activo" lever
 * writes [MockScenario.isPlus] and every gate in the real graph reacts live — including
 * the downgrade path (Plus → free) that is hard to exercise against a real store.
 * Without a scenario it behaves like a plain in-memory repository (previews, tests).
 */
class FakeEntitlementRepository(
    private val scenario: MockScenario? = null,
) : EntitlementRepository {

    private val _entitlement = MutableStateFlow(Entitlement.FREE)

    // The mock flavor never calls Purchases.configure() — there is no store to sell against,
    // so the paywall always falls back to its static frame here. [MONEY-001]
    override val isStoreAvailable: Boolean = false

    override fun observeEntitlement(): Flow<Entitlement> =
        scenario?.isPlus?.map { if (it) Entitlement.PLUS else Entitlement.FREE }
            ?: _entitlement

    override suspend fun purchasePlus(plan: PlusPlan): Result<Unit> {
        if (scenario != null) {
            scenario.isPlus.value = true
        } else {
            _entitlement.value = Entitlement.PLUS
        }
        return Result.success(Unit)
    }

    // Identity is meaningless without a store: with a scenario the Dev Catalog lever stays
    // the single authority (logging in/out of the mock session must not knock Plus off),
    // and without one only logOut resets the in-memory entitlement, like the real SDK.
    override suspend fun logIn(userId: String): Result<Unit> = Result.success(Unit)

    override suspend fun logOut(): Result<Unit> {
        if (scenario == null) _entitlement.value = Entitlement.FREE
        return Result.success(Unit)
    }
}
