package com.rndeveloper.paparcar.domain.detection.stages

import com.rndeveloper.paparcar.domain.detection.state.AnchorTrust
import com.rndeveloper.paparcar.domain.detection.state.ConfirmationLifecycle
import com.rndeveloper.paparcar.domain.detection.state.DetectionSessionState
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.usecase.parking.EvaluateUnattendedParkingSaveUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.PromptReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * **El eslabón que el compilador no puede vigilar** [DET-A-DEGRADED-PROMPT-MUST-NOT-AUTO-CONFIRM-001].
 *
 * `EvaluateUnattendedParkingSaveUseCaseTest` fija la DECISIÓN —el evaluador puro rechaza guardar
 * cuando la pregunta nació de una degradación— y el compilador obliga a los cuatro productores de
 * `Notified` a decidir su `degradedFrom`. Entre las dos cosas queda un hueco que ninguna cubre: que
 * ese dato VIAJE desde `ConfirmationPhase` hasta `UnattendedSaveInput` a través de
 * `unattendedSaveInput(...)`. Pasar `null` ahí compila, pasa todos los tests del evaluador, y
 * reabre el falso positivo entero.
 *
 * Por eso este test no construye el input a mano: arranca del ESTADO, que es de donde salió el
 * fallo de campo, y comprueba el efecto que sale por el otro lado.
 *
 * Campo 2026-10-05, Oppo (uid `0l7X59ptXeQ4WUMlpFOZHLPIJAI3`): sesiones `1791232803918`
 * (`egress_not_at_anchor` → zona de 155,7 m a 58,8 m del sitio) y `1791123836936` (`weak_evidence`
 * → pin en una calle peatonal donde el coche no puede entrar).
 */
class ResponseTimeoutDegradedPromptTest {

    private val config = ParkingDetectionConfig()
    private val stage = ResponseTimeoutStage(EvaluateUnattendedParkingSaveUseCase(config))

    /** Donde el coche descansa: ancla fijada por reposo, de las que ganan un `SaveExact` limpio. */
    private val anchor = GpsPoint(36.6084, -6.2781, accuracy = 4f, timestamp = 1_000L, speed = 0f)
    private val fix = anchor.copy(timestamp = 2_000L)

    private val shownAt = 10_000L
    private val expired = shownAt + config.confirmationResponseTimeoutMs + 1_000L

    /**
     * Una sesión que ya PROBÓ conducción y descansa sobre un ancla congelada — es decir, la que
     * acaba en `SaveExact`. Lo único que cambia entre los tests es de qué clase fue la pregunta.
     */
    private fun stateWith(degradedFrom: PromptReason?) = DetectionSessionState(
        confirmation = ConfirmationLifecycle().notified(shownAt, degradedFrom = degradedFrom),
        anchorTrust = AnchorTrust(
            anchor = anchor,
            frozenByRest = true,
            stopStartedAt = 1_000L,
            stopEvidenceSince = 1_000L,
        ),
    ).let { it.copy(drive = it.drive.copy(provenMaxSpeedMps = config.minimumTripSpeedMps + 5f)) }

    private fun evaluate(degradedFrom: PromptReason?) = stage.evaluate(
        state = stateWith(degradedFrom),
        fix = fix,
        now = expired,
        stoppedDurationMs = config.sustainedStopForSaveMs + 1_000L,
        config = config,
    )

    @Test
    fun should_placeNothingAndCloseTheQuestion_when_aDegradedPromptOutlivesItsWindow() {
        val verdict = assertIs<StageVerdict.Handled>(evaluate(PromptReason.WEAK_EVIDENCE))

        val close = verdict.effects.filterIsInstance<DetectionEffect.CloseDegradedPrompt>().single()
        assertEquals(PromptReason.WEAK_EVIDENCE.key, close.reasonKey, "la traza tiene que decir QUÉ negativa sobrevivió")
        assertTrue(
            verdict.effects.none {
                it is DetectionEffect.SaveUnattended || it is DetectionEffect.SaveZone
            },
            "ni pin ni zona: el silencio no añade pruebas — ${verdict.effects}",
        )
        assertTrue(verdict.stopsIteration, "la ventana se acabó; la sesión termina igual")
    }

    /**
     * ⛔ La discriminante, y la mitad que un arreglo descuidado rompe. Mismo estado, misma espera,
     * pregunta ORDINARIA: [DET-RECONCILE-001] sigue en pie y guarda. Sin este test, un stage que
     * no guardase nunca pasaría el de arriba por el motivo equivocado.
     */
    @Test
    fun should_stillSave_when_theQuestionWasTheOrdinaryPrompt() {
        val verdict = assertIs<StageVerdict.Handled>(evaluate(degradedFrom = null))

        assertTrue(
            verdict.effects.any { it is DetectionEffect.SaveUnattended },
            "un prompt ordinario sin respuesta sigue salvando el aparcamiento — ${verdict.effects}",
        )
    }

    /** Antes de que expire no se decide nada, sea la pregunta de la clase que sea. */
    @Test
    fun should_waitItsFullWindow_when_theDegradedPromptIsStillYoung() {
        val verdict = stage.evaluate(
            state = stateWith(PromptReason.WEAK_EVIDENCE),
            fix = fix,
            now = shownAt + config.confirmationResponseTimeoutMs - 1_000L,
            stoppedDurationMs = config.sustainedStopForSaveMs + 1_000L,
            config = config,
        )

        assertIs<StageVerdict.Skip>(verdict)
    }
}
