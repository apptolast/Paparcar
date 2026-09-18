package com.rndeveloper.paparcar.fakes.data.repository

import com.rndeveloper.paparcar.domain.fuel.AverageFuelPriceTable
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.repository.AverageFuelPricesRepository
import com.rndeveloper.paparcar.domain.push.PushTokenProvider
import com.rndeveloper.paparcar.domain.repository.ZoneAlertSubscriptionRepository
import com.rndeveloper.paparcar.domain.sharedcar.SharedCarMirror
import com.rndeveloper.paparcar.domain.zone.ZoneAlertSubscription

/**
 * The three `dataModule` ports the mock graph had no twin for.
 * [MOCK-A-PORT-DECLARED-IN-PROD-MUST-EXIST-IN-MOCK-001]
 *
 * ⛔ **The mock stack has no `dataModule`** — it stands in for it port by port, so anything bound
 * there needs a fake here or the screen that asks dies on `NoDefinitionFoundException`. The first of
 * these killed the whole Vehicles tab; the other two were the next two in the queue, found by the
 * parity test rather than by tapping. They live together in one file precisely because they were
 * one omission, not three.
 */

/**
 * National averages, frozen at the table shipped in the binary.
 *
 * This is not a degraded fake: it is exactly what production quotes on a cold start with the radio
 * off, since [AverageFuelPriceTable.Embedded] is the floor the real repository starts from. The Dev
 * Catalog therefore prices a drive with the same figures a real first launch would.
 */
class FakeAverageFuelPricesRepository : AverageFuelPricesRepository {
    override fun current(): AverageFuelPriceTable = AverageFuelPriceTable.Embedded
    override suspend fun refresh(): AverageFuelPriceTable = AverageFuelPriceTable.Embedded
}

/**
 * The server-side index of who wants to be told about which ground — in memory.
 *
 * `replaceAll` is what production does: the whole list is the state, so a shorter list is a
 * DELETION. Keeping the last one lets a scenario assert what the catalog would have pushed.
 */
class FakeZoneAlertSubscriptionRepository : ZoneAlertSubscriptionRepository {

    private val byUid = mutableMapOf<String, List<ZoneAlertSubscription>>()

    /** What the server would be holding right now, per user. */
    fun subscriptionsOf(uid: String): List<ZoneAlertSubscription> = byUid[uid].orEmpty()

    /** Un servidor inalcanzable, para el caso que importa: salir aunque la retirada falle.
     *  [PRIV-A-SIGNED-OUT-PHONE-STILL-HEARS-THE-LAST-ACCOUNT-001] */
    var failNext = false

    override suspend fun replaceAll(
        uid: String,
        subscriptions: List<ZoneAlertSubscription>,
    ): Result<Unit> {
        if (failNext) return Result.failure(IllegalStateException("sin red"))
        byUid[uid] = subscriptions
        return Result.success(Unit)
    }
}

/**
 * The shared-car mirror, writing to nothing.
 *
 * A no-op is the honest fake here and not a shortcut: the contract already says **nothing in the
 * mirror may fail the personal save** — every implementation swallows — so a catalog with no group
 * behind it behaves exactly like a real one whose vehicle belongs to no group.
 */
class FakeSharedCarMirror : SharedCarMirror {
    override suspend fun onSessionSaved(session: UserParking) = Unit
    override suspend fun onSessionClosed(sessionId: String, endedAtMs: Long) = Unit
    override suspend fun onAddressResolved(
        sessionId: String,
        address: AddressInfo?,
        placeInfo: PlaceInfo?,
    ) = Unit
}

/**
 * Un móvil sin push. [PRIV-A-SIGNED-OUT-PHONE-STILL-HEARS-THE-LAST-ACCOUNT-001]
 *
 * `currentToken()` devuelve null —que es el caso real de un emulador sin Play Services— y
 * `retireToken()` solo anota que se pidió: lo que los tests necesitan comprobar es que el cierre de
 * sesión LLEGA a pedirlo, no qué hace FCM con ello.
 */
class FakeNoopPushTokenProvider(private val token: String? = null) : PushTokenProvider {
    var retired = false
    override suspend fun currentToken(): String? = token
    override suspend fun retireToken() { retired = true }
}

