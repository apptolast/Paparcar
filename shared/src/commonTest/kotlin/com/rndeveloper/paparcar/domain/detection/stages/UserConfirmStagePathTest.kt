package com.rndeveloper.paparcar.domain.detection.stages

import com.rndeveloper.paparcar.domain.detection.DetectionPath
import com.rndeveloper.paparcar.domain.detection.ParkingDetectionSource
import com.rndeveloper.paparcar.domain.detection.state.ConfirmationLifecycle
import com.rndeveloper.paparcar.domain.detection.state.DetectionSessionState
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * **Quién escribe `"user"`** — el testigo que faltaba. [DET-A-YES-IS-NOT-A-HAND-001]
 *
 * `DetectionPath.UserAnswered` afirmaba en su KDoc que **nadie** escribe ese label, y que un «sí»
 * *«confirms a detection, it does not replace it»*. Las dos frases eran falsas: [UserConfirmStage]
 * se dispara con `confirmation.userConfirmed` —el flag que pone `userSaidYes()`— y planta el pin
 * con `pathLabel = "user"`, sustituyendo la procedencia.
 *
 * Nadie podía cazarlo porque **la afirmación vivía en un comentario, y un comentario no se
 * ejecuta**. Lo desmintió el campo: la issue #121 llegó con tres pines `"user"` posteriores a la
 * fecha en que la doc daba el label por muerto, y el activo llevaba además
 * `armEvidence = "verified_late"` — la app armó y vigiló — mientras la ficha decía «Aviso manual».
 *
 * Este test fija los dos hechos que aquel comentario se inventaba: que el escritor existe, y de qué
 * cajón sale la frase que lee el usuario.
 */
class UserConfirmStagePathTest {

    private val config = ParkingDetectionConfig()
    private val stage = UserConfirmStage()
    private val fix = GpsPoint(36.6084, -6.2781, accuracy = 4f, timestamp = 1_000L, speed = 0f)

    private fun evaluate(state: DetectionSessionState) =
        stage.evaluate(state, fix, now = 2_000L, stoppedDurationMs = 40_000L, config = config)

    @Test
    fun should_stampTheUserPath_when_theUserAnsweredYes() {
        val state = DetectionSessionState(
            confirmation = ConfirmationLifecycle().userSaidYes(),
        )

        val verdict = assertIs<StageVerdict.Handled>(evaluate(state))
        val confirm = assertNotNull(
            verdict.effects.filterIsInstance<DetectionEffect.Confirm>().firstOrNull(),
            "un «sí» planta el pin: ${verdict.effects}",
        )
        assertEquals(
            DetectionPath.UserAnswered.label,
            confirm.pathLabel,
            "ESTE es el escritor que el KDoc decía que no existía",
        )
    }

    @Test
    fun should_readAsConfirmedNotManual_when_thatPathReachesTheUser() {
        // ⛔ La mitad que el usuario VE. La app preguntó y él contestó; decirle «Aviso manual» le
        // atribuye una mano que no puso — issue #121, «no saqué el móvil del bolsillo».
        assertEquals(
            ParkingDetectionSource.Confirmed,
            assertNotNull(DetectionPath.ofLabel(DetectionPath.UserAnswered.label)).source,
        )
    }

    @Test
    fun should_notStampAnything_when_nobodyAnswered() {
        // La discriminante: mismo estado, sin respuesta. Sin ella el primer test pasaría por el
        // motivo equivocado (una etapa que confirmase siempre).
        assertIs<StageVerdict.Skip>(evaluate(DetectionSessionState()))
    }
}
