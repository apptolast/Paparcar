package com.rndeveloper.paparcar.fakes.data.repository

import com.rndeveloper.paparcar.domain.model.Entitlement
import com.rndeveloper.paparcar.domain.model.PlusGrant
import com.rndeveloper.paparcar.domain.model.PlusOffer
import com.rndeveloper.paparcar.domain.model.PlusOrigin
import com.rndeveloper.paparcar.domain.model.PlusPlan
import com.rndeveloper.paparcar.domain.repository.EntitlementRefresh
import com.rndeveloper.paparcar.domain.repository.EntitlementRepository
import com.rndeveloper.paparcar.fakes.MockScenario
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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


    override fun observeEntitlement(): Flow<Entitlement> =
        scenario?.isPlus?.map { if (it) Entitlement.PLUS else Entitlement.FREE }
            ?: _entitlement

    /**
     * [MONEY-FOUNDERS-WELCOME-001] Tres palancas del escenario en un grant: si hay Plus, si lo
     * regalamos nosotros y cuánto le queda. Sin escenario el grant sale del entitlement en memoria
     * y siempre es COMPRADO — las previews y los tests que sólo quieren «es Plus» no tienen que
     * saber nada de esto.
     *
     * ⛔ La caducidad se calcula al emitir, desde los días del escenario: una fecha absoluta
     * grabada en el preset se queda vieja sola y el momento dejaría de salir sin que nadie tocase
     * la palanca.
     */
    override fun observePlusGrant(): Flow<PlusGrant?> = scenario?.let { sc ->
        combine(sc.isPlus, sc.plusIsPromotional, sc.plusDaysLeft) { isPlus, promo, daysLeft ->
            if (!isPlus) {
                null
            } else {
                PlusGrant(
                    origin = if (promo) PlusOrigin.PROMOTIONAL else PlusOrigin.PURCHASED,
                    expiresAtMillis = daysLeft?.let {
                        Clock.System.now().toEpochMilliseconds() + it * MS_PER_DAY
                    },
                )
            }
        }
    } ?: _entitlement.map {
        if (it == Entitlement.PLUS) {
            PlusGrant(origin = PlusOrigin.PURCHASED, expiresAtMillis = null)
        } else {
            null
        }
    }

    /**
     * Stand-in plans so the paywall's price cards can be reviewed on a device without a store.
     *
     * ⚠️ These numbers are FICTION and only ever reach the mock flavor and previews. Production
     * takes the same route through the real repository, which returns an empty list when there is
     * no store — the paywall then hides the price cards rather than showing anything invented.
     * The rule that prices come from the store is not weakened by a fake that exists to draw the
     * screen; it is the reason this override lives here and not in `commonMain` product code.
     */
    override suspend fun loadOffers(): Result<List<PlusOffer>> = Result.success(
        listOf(
            PlusOffer(
                plan = PlusPlan.MONTHLY,
                price = "1,99 €",
                amountMicros = 1_990_000L,
                freeTrialDays = 7,
            ),
            PlusOffer(
                plan = PlusPlan.YEARLY,
                price = "13,99 €",
                amountMicros = 13_990_000L,
                pricePerMonth = "1,17 €",
                freeTrialDays = 7,
            ),
        ),
    )

    override suspend fun purchasePlus(plan: PlusPlan): Result<Unit> {
        if (scenario != null) {
            scenario.isPlus.value = true
        } else {
            _entitlement.value = Entitlement.PLUS
        }
        return Result.success(Unit)
    }

    /** The mock has no store account to query, so "restore" can only honestly say it found
     *  nothing. The Dev Catalog's own Plus lever is how you turn Plus on here. */
    override suspend fun restorePurchases(): Result<Boolean> = Result.success(false)

    override suspend fun refreshEntitlement(userId: String): Result<EntitlementRefresh> =
        Result.success(EntitlementRefresh(observeEntitlement().first(), Clock.System.now().toEpochMilliseconds()))

    // Identity is meaningless without a store: with a scenario the Dev Catalog lever stays
    // the single authority (logging in/out of the mock session must not knock Plus off),
    // and without one only logOut resets the in-memory entitlement, like the real SDK.
    override suspend fun logIn(userId: String): Result<Unit> = Result.success(Unit)

    override suspend fun logOut(): Result<Unit> {
        if (scenario == null) _entitlement.value = Entitlement.FREE
        return Result.success(Unit)
    }

    private companion object {
        const val MS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
