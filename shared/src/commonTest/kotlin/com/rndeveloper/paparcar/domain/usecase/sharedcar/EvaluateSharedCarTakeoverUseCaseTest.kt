package com.rndeveloper.paparcar.domain.usecase.sharedcar

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.SharedCar
import com.rndeveloper.paparcar.domain.model.SharedCarState
import com.rndeveloper.paparcar.domain.model.UserParking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cuándo el grupo se entera de que me llevo el coche.
 * [SHARED-A-BT-CONNECT-PROVES-WHO-TOOK-THE-CAR-001]
 *
 * El caso que este veredicto existe para NO hacer es el del medio: volver a subirme a mi propio
 * coche aparcado. Esa salida ya la cuenta el espejo cuando mi sesión se cierra, y contarla aquí
 * sería la misma noticia dos veces — la primera, antes de que mi propia detección lo supiera.
 */
class EvaluateSharedCarTakeoverUseCaseTest {

    private val useCase = EvaluateSharedCarTakeoverUseCase()

    private val fix = GpsPoint(latitude = 40.4168, longitude = -3.7038, accuracy = 8f, timestamp = 1_000L, speed = 0f)

    private fun car(state: SharedCarState?) = SharedCar(
        carId = "car-1",
        ownerUid = "marta",
        displayName = "El Kamiq",
        state = state,
    )

    private fun state(
        sessionId: String = "s-marta",
        authorUid: String = "marta",
        isParked: Boolean = true,
    ) = SharedCarState(
        sessionId = sessionId,
        location = fix,
        parkedAtMs = 1_000L,
        authorUid = authorUid,
        authorName = "Marta",
        isParked = isParked,
    )

    private fun mySession(id: String) = UserParking(
        id = id,
        userId = "antonio",
        vehicleId = "car-1",
        location = fix,
    )

    @Test
    fun should_tellTheGroup_when_iDriveOffWithTheCarSomebodyElseParked() {
        val verdict = useCase(car = car(state()), myUid = "antonio", myActiveSessionForCar = null)
        assertEquals(SharedCarTakeover.Announce(endingSessionId = "s-marta"), verdict)
    }

    @Test
    fun should_stayQuiet_when_itIsMyOwnParkingAndStillMine() {
        // Mi coche, mi sesión viva: la salida la anunciará el cierre de esa sesión, que además la
        // habrá medido. Aquí sería la misma noticia dos veces.
        val verdict = useCase(
            car = car(state(sessionId = "s-antonio", authorUid = "antonio")),
            myUid = "antonio",
            myActiveSessionForCar = mySession("s-antonio"),
        )
        assertTrue(verdict is SharedCarTakeover.Silent)
    }

    @Test
    fun should_tellTheGroup_when_theParkingIsMineButIDoNotHoldItAnyMore() {
        // Mismo autor, sesión distinta: el espejo enseña un aparcamiento mío que mi móvil ya no
        // tiene abierto (otro móvil mío, una reinstalación). Volver a conducirlo SÍ es noticia.
        val verdict = useCase(
            car = car(state(sessionId = "s-vieja", authorUid = "antonio")),
            myUid = "antonio",
            myActiveSessionForCar = null,
        )
        assertEquals(SharedCarTakeover.Announce(endingSessionId = "s-vieja"), verdict)
    }

    @Test
    fun should_stayQuiet_when_theGroupAlreadyKnowsTheCarIsMoving() {
        val verdict = useCase(
            car = car(state(isParked = false)),
            myUid = "antonio",
            myActiveSessionForCar = null,
        )
        assertTrue(verdict is SharedCarTakeover.Silent)
    }

    @Test
    fun should_stayQuiet_when_theGroupKnowsOfNoParkingAtAll() {
        val verdict = useCase(car = car(state = null), myUid = "antonio", myActiveSessionForCar = null)
        assertTrue(verdict is SharedCarTakeover.Silent)
    }
}
