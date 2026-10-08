package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.detection.PendingPromptWindow
import com.rndeveloper.paparcar.ui.theme.VehicleWatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [HOME-A-CARS-NEWS-RIDES-ITS-OWN-CARD-001] De QUÉ coche habla cada historia.
 *
 * `isTheCarsQuestion()` contesta si la historia CABE en una card de coche y ya tenía sus tests;
 * esto cubre la pregunta que faltaba —EN CUÁL— y que con un solo coche nadie necesitó.
 */
class DetectionStorySubjectTest {

    @Test
    fun should_nameTheAskedCar_when_theQuestionCarriesItsId() {
        assertEquals(
            "veh-asked",
            DetectionStory.AwaitingAnswer(window(vehicleId = "veh-asked"))
                .subjectVehicleId(parkNudgeVehicleId = "veh-nudge", activeVehicleId = "veh-active"),
        )
    }

    @Test
    fun should_haveNoSubject_when_theQuestionWasPostedBeforeTheIdExisted() {
        // Una ventana persistida por un build anterior al campo. Sin sujeto es MEJOR que adivinar:
        // la noticia se queda en el badge, que es exactamente donde estaba.
        assertNull(
            DetectionStory.AwaitingAnswer(window(vehicleId = null))
                .subjectVehicleId(parkNudgeVehicleId = "veh-nudge", activeVehicleId = "veh-active"),
        )
    }

    @Test
    fun should_nameTheNudgedCar_when_theAskHasNoDeadline() {
        assertEquals(
            "veh-nudge",
            DetectionStory.PendingAsk
                .subjectVehicleId(parkNudgeVehicleId = "veh-nudge", activeVehicleId = "veh-active"),
        )
    }

    @Test
    fun should_nameTheActiveCar_when_itHasNotParkedYet() {
        assertEquals(
            "veh-active",
            DetectionStory.AwaitingFirstPark
                .subjectVehicleId(parkNudgeVehicleId = null, activeVehicleId = "veh-active"),
        )
    }

    @Test
    fun should_haveNoSubject_when_theStoryIsSystemHealth() {
        // ⛔ Null a propósito, no por falta de dato: un permiso, la detección apagada o un centinela
        // muerto repercuten a TODOS los coches, así que no hay a cuál culpar. Es la misma frontera
        // que `isTheCarsQuestion()`, y si alguna de estas llegara a devolver sujeto, la salud del
        // sistema se mudaría a la card de un coche — justo lo que la decisión del 23-09 prohíbe.
        val systemHealth = listOf(
            DetectionStory.BlockedCore,
            DetectionStory.Inactive,
            DetectionStory.NoVehicle,
            DetectionStory.Hidden,
            DetectionStory.Watching("Kamiq", isParked = true, watch = VehicleWatch.Assisted),
        )
        systemHealth.forEach { story ->
            assertNull(
                story.subjectVehicleId(parkNudgeVehicleId = "veh-nudge", activeVehicleId = "veh-active"),
                "$story no puede tener sujeto: es salud del sistema",
            )
        }
    }

    private fun window(vehicleId: String?) =
        PendingPromptWindow(shownAtMs = 1L, vehicleName = "Toyota Corolla", vehicleId = vehicleId)
}
