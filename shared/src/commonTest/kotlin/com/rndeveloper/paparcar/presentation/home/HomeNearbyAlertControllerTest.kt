package com.rndeveloper.paparcar.presentation.home

import app.cash.turbine.test
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.notification.NearbySpotAlerts
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Lo que el controller añade a la decisión pura: la memoria de sesión, el reloj y — sobre todo — que
 * el uid llegue al veredicto. [ZONE-AN-ALERT-NEAR-YOU-IS-NOT-AN-ALERT-IN-YOUR-ZONE-001]
 *
 * ⚠️ `NearbySpotAlertPolicyTest` ya cubre CUÁNDO se avisa. Esto cubre que se pregunte por el uid y
 * que se recuerde entre miradas: un controller que pasara `null` como `ownUserId` pasaría todos los
 * tests de la política y aun así anunciaría al usuario la plaza que acaba de dejar.
 */
class HomeNearbyAlertControllerTest {

    private val me = "me"
    private val anchor = GpsPoint(40.0, -3.0, accuracy = 5f, timestamp = 1_000L, speed = 0f)
    private val alerts = NearbySpotAlerts()

    private fun controller(signedIn: Boolean = true) = HomeNearbyAlertController(
        authRepository = if (signedIn) {
            FakeAuthRepository(initialSession = FakeAuthRepository.authenticatedSession(userId = me))
        } else {
            FakeAuthRepository()
        },
        nearbySpotAlerts = alerts,
        clock = { NOW },
    )

    private fun spot(id: String, metresEast: Double, reportedBy: String = "someone-else") = Spot(
        id = id,
        location = GpsPoint(
            latitude = 40.0,
            longitude = -3.0 + metresEast / 85_000.0,
            accuracy = 5f,
            timestamp = 1_000L,
            speed = 0f,
        ),
        reportedBy = reportedBy,
        expiresAt = 0L,
    )

    @Test
    fun should_neverAnnounceTheUsersOwnSpot_when_itIsTheNearestThingAround() = runTest {
        val controller = controller()
        alerts.alerts.test {
            controller.look(anchor, emptyList())               // la primera mirada mide el suelo
            controller.look(anchor, listOf(spot("mine", 5.0, reportedBy = me)))

            expectNoEvents()
        }
    }

    @Test
    fun should_announce_when_aFreeSpotAppearsNearby() = runTest {
        val controller = controller()
        alerts.alerts.test {
            controller.look(anchor, emptyList())
            controller.look(anchor, listOf(spot("theirs", 120.0)))

            assertEquals("theirs", awaitItem().id)
        }
    }

    @Test
    fun should_announceOnce_when_theSameSpotArrivesInEveryBatch() = runTest {
        val controller = controller()
        alerts.alerts.test {
            controller.look(anchor, emptyList())
            val batch = listOf(spot("theirs", 120.0))
            controller.look(anchor, batch)
            assertEquals("theirs", awaitItem().id)

            controller.look(anchor, batch)
            controller.look(anchor, batch)

            expectNoEvents()
        }
    }

    @Test
    fun should_stayQuiet_when_thereIsNoFixYet() = runTest {
        val controller = controller()
        alerts.alerts.test {
            // Sin saber dónde está el usuario, «cerca de ti» no significa nada — y, sobre todo, no se
            // toma baseline: la primera mirada CON fix tiene que seguir siendo la que mide el suelo.
            controller.look(anchor = null, spots = listOf(spot("theirs", 120.0)))
            controller.look(anchor = null, spots = listOf(spot("theirs", 120.0)))

            expectNoEvents()

            controller.look(anchor, listOf(spot("theirs", 120.0)))
            expectNoEvents()
        }
    }

    @Test
    fun should_stillAnnounce_when_nobodyIsSignedIn() = runTest {
        // Sin sesión no hay plaza propia que vetar, y el carril tiene que seguir funcionando: el uid
        // es un filtro, no un requisito.
        val controller = controller(signedIn = false)
        alerts.alerts.test {
            controller.look(anchor, emptyList())
            controller.look(anchor, listOf(spot("theirs", 120.0)))

            assertEquals("theirs", awaitItem().id)
        }
    }

    private companion object {
        const val NOW = 10_000L
    }
}
