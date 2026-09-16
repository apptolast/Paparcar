package com.rndeveloper.paparcar.push

import com.apptolast.baselogin.domain.model.UserSession
import com.rndeveloper.paparcar.domain.model.Entitlement
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.push.PushTokenProvider
import com.rndeveloper.paparcar.domain.repository.ZoneAlertSubscriptionRepository
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObserveIsPlusUseCase
import com.rndeveloper.paparcar.domain.zone.ZoneAlertSubscription
import com.rndeveloper.paparcar.fakes.FakeAppPreferences
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.FakeEntitlementRepository
import com.rndeveloper.paparcar.fakes.FakeZoneRepository
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La disciplina del token, ahora que el token puede venir de dos sitios.
 * [IOS-THE-GROUP-CANNOT-REACH-AN-IPHONE-001]
 *
 * La clase se movió a `commonMain` entera y **ninguna de sus reglas cambió**; lo único que cambió es
 * que `FirebaseMessaging` pasó a ser [PushTokenProvider]. Ese cambio es justo el que puede romper en
 * silencio la regla más fácil de perder de vista: **un token no se pide si no hay nada que
 * suscribir**. En iOS eso pesa el doble, porque pedirlo es registrar el dispositivo en APNs.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ZoneAlertSubscriptionSyncTest {

    // ⛔ `UnconfinedTestDispatcher`, y no el estándar: el colector arranca en el `scope` inyectado y
    // con el dispatcher estándar la primera emisión sigue en la cola cuando el test mira, así que
    // los cuatro casos de «no se pide el token» pasarían sin haber ejecutado nada — un verde sin
    // sujeto. Con el unconfined, `start()` deja el colector ya corrido al volver.

    private class CountingTokenProvider(private val token: String? = "tok-1") : PushTokenProvider {
        override suspend fun retireToken() = Unit

        var calls = 0
            private set

        override suspend fun currentToken(): String? {
            calls++
            return token
        }
    }

    private class RecordingSubscriptionRepository : ZoneAlertSubscriptionRepository {
        val writes = mutableListOf<List<ZoneAlertSubscription>>()

        override suspend fun replaceAll(
            uid: String,
            subscriptions: List<ZoneAlertSubscription>,
        ): Result<Unit> {
            writes.add(subscriptions)
            return Result.success(Unit)
        }
    }

    private fun zone(id: String, notify: Boolean) = Zone(
        id = id,
        userId = "u-1",
        name = "Centro",
        lat = 40.4,
        lon = -3.7,
        iconKey = "HOME",
        createdAt = 0L,
        radiusMeters = 250f,
        isPrivate = false,
        notifyOnSpot = notify,
    )

    private fun TestScope.sync(
        zones: FakeZoneRepository,
        subs: RecordingSubscriptionRepository,
        tokens: CountingTokenProvider,
        entitlement: Entitlement,
        prefs: FakeAppPreferences = FakeAppPreferences(),
    ) = ZoneAlertSubscriptionSync(
        zoneRepository = zones,
        subscriptionRepository = subs,
        observeIsPlus = ObserveIsPlusUseCase(FakeEntitlementRepository(entitlement)),
        appPreferences = prefs,
        authRepository = FakeAuthRepository(
            initialSession = UserSession(
                userId = "u-1",
                email = null,
                displayName = null,
                photoUrl = null,
            ),
        ),
        pushTokens = tokens,
        clock = { 1_000L },
        // ⛔ El scope del test, no el `Dispatchers.Default` de producción: si no, `advanceUntilIdle`
        // no espera a nada y estos tests pasarían sin haber mirado.
        scope = backgroundScope,
    )

    @Test
    fun should_askForTheToken_when_thereIsSomethingToSubscribe() = runTest(UnconfinedTestDispatcher()) {
        val zones = FakeZoneRepository().apply { this.zones = listOf(zone("z-1", notify = true)) }
        val subs = RecordingSubscriptionRepository()
        val tokens = CountingTokenProvider()

        sync(zones, subs, tokens, Entitlement.PLUS).start()
        advanceUntilIdle()

        assertEquals(1, tokens.calls)
        assertEquals(listOf("tok-1"), subs.writes.last().single().tokens)
    }

    /**
     * ⛔ Un token es un identificador de dispositivo, y en iOS pedirlo registra la app en APNs. No
     * pedírselo a quien nunca ha encendido esto es el comportamiento honesto, no una optimización.
     */
    @Test
    fun should_neverAskForTheToken_when_theUserIsNotPlus() = runTest(UnconfinedTestDispatcher()) {
        val zones = FakeZoneRepository().apply { this.zones = listOf(zone("z-1", notify = true)) }
        val subs = RecordingSubscriptionRepository()
        val tokens = CountingTokenProvider()

        sync(zones, subs, tokens, Entitlement.FREE).start()
        advanceUntilIdle()

        assertEquals(0, tokens.calls)
    }

    @Test
    fun should_neverAskForTheToken_when_theSettingsSwitchIsOff() = runTest(UnconfinedTestDispatcher()) {
        val zones = FakeZoneRepository().apply { this.zones = listOf(zone("z-1", notify = true)) }
        val subs = RecordingSubscriptionRepository()
        val tokens = CountingTokenProvider()
        val prefs = FakeAppPreferences().apply { setNotifyZoneSpots(false) }

        sync(zones, subs, tokens, Entitlement.PLUS, prefs).start()
        advanceUntilIdle()

        assertEquals(0, tokens.calls)
    }

    @Test
    fun should_neverAskForTheToken_when_noZoneAsksToBeWatched() = runTest(UnconfinedTestDispatcher()) {
        val zones = FakeZoneRepository().apply { this.zones = listOf(zone("z-1", notify = false)) }
        val subs = RecordingSubscriptionRepository()
        val tokens = CountingTokenProvider()

        sync(zones, subs, tokens, Entitlement.PLUS).start()
        advanceUntilIdle()

        assertEquals(0, tokens.calls)
        // Replace, not upsert: la lista vacía es lo que BORRA la fila del servidor.
        assertTrue(subs.writes.last().isEmpty())
    }

    /** Sin token no se calla el sync: la fila se escribe igual, sin dispositivos. */
    @Test
    fun should_stillWriteTheRow_when_theTokenCouldNotBeObtained() = runTest(UnconfinedTestDispatcher()) {
        val zones = FakeZoneRepository().apply { this.zones = listOf(zone("z-1", notify = true)) }
        val subs = RecordingSubscriptionRepository()
        val tokens = CountingTokenProvider(token = null)

        sync(zones, subs, tokens, Entitlement.PLUS).start()
        advanceUntilIdle()

        assertEquals(1, tokens.calls)
        // ⛔ No token, no row: una suscripción a la que nadie puede avisar es una fila que el
        // servidor leería en cada plaza cercana para nada. La fila se escribe —vacía—, que es lo que
        // BORRA la anterior.
        assertTrue(subs.writes.last().isEmpty())
    }
}
