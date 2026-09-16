package com.rndeveloper.paparcar.domain.usecase.user

import com.apptolast.baselogin.domain.model.UserSession
import com.rndeveloper.paparcar.domain.zone.ZoneAlertSubscription
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.data.repository.FakeNoopPushTokenProvider
import com.rndeveloper.paparcar.fakes.data.repository.FakeSharedCarRepository
import com.rndeveloper.paparcar.fakes.data.repository.FakeZoneAlertSubscriptionRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cerrar sesión retira lo que seguiría llegando a este móvil.
 * [PRIV-A-SIGNED-OUT-PHONE-STILL-HEARS-THE-LAST-ACCOUNT-001]
 *
 * Lo que se prueba no es que la sesión se cierre —eso ya lo hacía— sino que **antes** se vacíen las
 * dos listas de tokens. Si no, el siguiente que entre en este teléfono recibe plazas de zonas
 * ajenas y dónde aparcó el coche de un grupo al que no pertenece, durante los 30 días que dura una
 * suscripción.
 */
class SignOutUseCaseTest {

    private val session = UserSession(userId = "nacho", email = null, displayName = null, photoUrl = null)

    private fun useCase(
        auth: FakeAuthRepository,
        subs: FakeZoneAlertSubscriptionRepository = FakeZoneAlertSubscriptionRepository(),
        cars: FakeSharedCarRepository = FakeSharedCarRepository(),
        push: FakeNoopPushTokenProvider = FakeNoopPushTokenProvider(),
    ) = SignOutUseCase(
        authRepository = auth,
        zoneAlertSubscriptions = subs,
        sharedCarRepository = cars,
        pushTokens = push,
    )

    @Test
    fun should_emptyBothTokenLists_when_signingOut() = runTest {
        val auth = FakeAuthRepository(session)
        val subs = FakeZoneAlertSubscriptionRepository().apply {
            replaceAll(
                "nacho",
                listOf(
                    ZoneAlertSubscription(
                        id = "nacho_zone-1",
                        uid = "nacho",
                        zoneId = "zone-1",
                        zoneName = "Casa",
                        latitude = 40.4,
                        longitude = -3.7,
                        radiusMeters = 300f,
                        cells = listOf("ezjm"),
                        tokens = listOf("token-de-este-movil"),
                        expiresAt = 9_999L,
                    ),
                ),
            )
        }
        val cars = FakeSharedCarRepository()
        val push = FakeNoopPushTokenProvider()

        useCase(auth, subs, cars, push)().getOrThrow()

        assertEquals(emptyList(), subs.subscriptionsOf("nacho"))
        assertTrue(cars.pushTokensRetired)
        assertTrue(push.retired)
    }

    @Test
    fun should_stillSignOut_when_retiringTheTokensFails() = runTest {
        val auth = FakeAuthRepository(session)
        val subs = FakeZoneAlertSubscriptionRepository().apply { failNext = true }

        val result = useCase(auth, subs)()

        // ⛔ Salir es una petición del usuario, no una transacción: sin red se sale igual. Lo que
        // cubre ese hueco es el filtro de destinatario del handler, no un reintento aquí.
        assertTrue(result.isSuccess)
        assertTrue(auth.getCurrentSession() == null)
    }

    @Test
    fun should_notTouchTheServer_when_thereIsNoSessionToRetire() = runTest {
        val auth = FakeAuthRepository(initialSession = null)
        val cars = FakeSharedCarRepository()

        useCase(auth, cars = cars)().getOrThrow()

        // Sin uid no hay filas que sean de nadie: escribir aquí sería escribir a ciegas.
        assertTrue(!cars.pushTokensRetired)
    }
}
