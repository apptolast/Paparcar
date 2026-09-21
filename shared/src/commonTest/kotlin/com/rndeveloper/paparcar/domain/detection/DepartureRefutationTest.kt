package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.detection.physics.SessionOutcome
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DET-A-REFUSED-TRIP-GIVES-THE-CAR-BACK-001]
 *
 * Una salida PRESENCIADA entrega el coche: publica la plaza y cierra la sesión. Estas son las dos
 * razones por las que hay que devolverlo — y la segunda es la que faltaba.
 */
class DepartureRefutationTest {

    @Test
    fun should_giveTheCarBack_when_theUserSaysItWasNotTheirCar() {
        // ⛔ EL TESTIGO. La condición era sólo `IMPOSSIBLE`, así que este caso —el amigo que te
        // recoge junto a tu coche y la detección se dispara— salía con `false` y el coche se
        // quedaba entregado. Con la condición vieja este test FALLA.
        assertTrue(
            witnessedDepartureIsRefuted(
                roadPlausibility = RoadPlausibility.PLAUSIBLE,
                outcome = SessionOutcome.StoppedByUser,
            ),
            "el conductor es la fuente primaria: su «no» refuta la salida igual que la carretera",
        )
    }

    @Test
    fun should_giveTheCarBack_when_theRoadWasImpossible() {
        // La razón con la que nació el `restore`: el AVE que confirmó salida desde el andén.
        assertTrue(
            witnessedDepartureIsRefuted(
                roadPlausibility = RoadPlausibility.IMPOSSIBLE,
                outcome = SessionOutcome.StoppedByUser,
            ),
        )
    }

    @Test
    fun should_keepTheDeparture_when_nobodyRefutedIt() {
        // Un viaje normal que termina por cualquier otra vía NO devuelve nada: la salida ocurrió y
        // la plaza es de verdad. Devolver aquí sería resucitar sesiones cerradas legítimamente.
        assertFalse(
            witnessedDepartureIsRefuted(
                roadPlausibility = RoadPlausibility.PLAUSIBLE,
                outcome = SessionOutcome.Ended,
            ),
            "una salida real se queda donde está",
        )
    }

    @Test
    fun should_keepTheDeparture_when_theRoadWasMerelyDoubtful() {
        // ⛔ DOUBTFUL no basta, y es deliberado: la duda ya tiene su propio remedio —se PREGUNTA—
        // y devolver el coche ante una sospecha convertiría cada tramo raro de autovía en una
        // sesión resucitada. Sólo lo imposible y la persona refutan. [DET-A-TRAIN-IS-NOT-A-CAR-001]
        assertFalse(
            witnessedDepartureIsRefuted(
                roadPlausibility = RoadPlausibility.DOUBTFUL,
                outcome = SessionOutcome.Ended,
            ),
        )
    }
}
