package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.detection.PendingPromptWindow
import com.rndeveloper.paparcar.domain.onboarding.FirstStepsOwnership
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.presentation.home.VehicleCard
import com.rndeveloper.paparcar.ui.theme.VehicleWatch
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

/**
 * Precedence tests for [resolveDetectionStory] — the single detection story Home tells.
 * [UX-DETECTION-STORY-001]
 */
class DetectionStoryTest {

    private fun vehicle(
        id: String,
        isActive: Boolean = false,
        brand: String = "Skoda",
        model: String = "Kamiq",
        bluetoothDeviceId: String? = null,
    ) = Vehicle(
        id = id,
        userId = "user-1",
        brand = brand,
        model = model,
        sizeCategory = VehicleSize.MEDIUM_SUV,
        isActive = isActive,
        bluetoothDeviceId = bluetoothDeviceId,
    )

    private fun session(vehicleId: String) = UserParking(
        id = "s-$vehicleId",
        userId = "user-1",
        vehicleId = vehicleId,
        location = GpsPoint(latitude = 40.0, longitude = -3.0, accuracy = 8f, timestamp = 0L, speed = 0f),
        geofenceId = "gf-1",
        isActive = true,
    )

    private val activeCard = VehicleCard(vehicle("v-active", isActive = true), session = null)
    private val activeParkedCard = VehicleCard(vehicle("v-active", isActive = true), session = session("v-active"))
    private val otherCard = VehicleCard(vehicle("v-other", brand = "Seat", model = "Ibiza"), session = null)

    @Test
    fun should_standDown_theBlockedCoreRow_when_theLocationAskIsStillOnScreen() {
        // El checklist guiado abre la hoja de golpe en la primera entrada, así que esta fila SÍ está
        // detrás del diálogo del sistema — y diría «activa la ubicación» mientras el usuario tiene
        // delante el botón que la activa. [HOME-A-PENDING-ASK-IS-NOT-A-REFUSAL-001]
        val cards = listOf(activeCard)
        assertEquals(
            DetectionStory.Hidden,
            resolveDetectionStory(DetectionUiState.BlockedCore, cards, locationAskPending = true),
        )
        // Contestada la pregunta, la fila vuelve sola: no se ha escondido, es que no le tocaba.
        assertEquals(
            DetectionStory.BlockedCore,
            resolveDetectionStory(DetectionUiState.BlockedCore, cards, locationAskPending = false),
        )
    }

    @Test
    fun should_notStandDown_anyOtherRow_when_theLocationAskIsOnScreen() {
        // Aparta UNA fila y ninguna más: sin permiso y sin coche la historia sigue siendo la suya.
        assertEquals(
            DetectionStory.NoVehicle,
            resolveDetectionStory(DetectionUiState.NoVehicle, emptyList(), locationAskPending = true),
        )
    }

    @Test
    fun should_passThrough_the_four_action_states() {
        val cards = listOf(activeCard)
        assertEquals(DetectionStory.BlockedCore, resolveDetectionStory(DetectionUiState.BlockedCore, cards))
        // ⛔ Éste con la lista VACÍA, que es la única situación en la que su frase es verdad: con
        // cards delante la proyección calla, y el testigo de eso vive abajo.
        assertEquals(DetectionStory.NoVehicle, resolveDetectionStory(DetectionUiState.NoVehicle, emptyList()))
        assertEquals(DetectionStory.Inactive, resolveDetectionStory(DetectionUiState.Inactive, cards))
        assertEquals(
            DetectionStory.AwaitingFirstPark,
            resolveDetectionStory(DetectionUiState.AwaitingFirstPark, cards),
        )
    }

    @Test
    fun should_hideTheLiveTrip_because_theVehicleCardTellsItWholeAndCarriesTheStop() {
        // A live trip has NO story here any more: the vehicle card already tells it (halo, identity
        // border, route glyph, "En ruta · place") and carries the stop CTA — and the old driving
        // story could only ever render when that card was on screen, because it needed a card to
        // name. Two surfaces with the same news is the duplication this projection prevents.
        // [HOME-A-DRIVE-IS-ONE-NEWS-AND-THE-CARD-CARRIES-THE-STOP-001]
        assertEquals(
            DetectionStory.Hidden,
            resolveDetectionStory(DetectionUiState.Monitoring, listOf(activeCard, otherCard)),
        )
    }

    @Test
    fun should_watch_the_active_vehicle_when_parked() {
        val story = resolveDetectionStory(
            uiState = DetectionUiState.Parked,
            vehicleCards = listOf(otherCard, activeParkedCard),
        )
        assertEquals(
            DetectionStory.Watching(vehicleName = "Skoda Kamiq", isParked = true, watch = VehicleWatch.Assisted),
            story,
        )
    }

    @Test
    fun should_watch_via_bluetooth_when_bt_armed() {
        // The watch method is read off the VEHICLE (it owns the identity colour), not
        // hardcoded per state. [UI-COLOR-DOCTRINE-001]
        val btCard = VehicleCard(vehicle("v-active", isActive = true, bluetoothDeviceId = "AA:BB"), session = null)
        val story = resolveDetectionStory(
            uiState = DetectionUiState.ArmedBluetooth,
            vehicleCards = listOf(btCard, otherCard),
        )
        assertEquals(
            DetectionStory.Watching(vehicleName = "Skoda Kamiq", isParked = false, watch = VehicleWatch.Bluetooth),
            story,
        )
    }

    @Test
    fun should_carry_fragile_watch_badge_when_setup_is_fragile() {
        // [DET-WATCH-HONEST-001] The parked line stays "watching" but warns + offers the exemption.
        val story = resolveDetectionStory(
            uiState = DetectionUiState.Parked,
            vehicleCards = listOf(activeParkedCard),
            parkedWatchBadge = ParkedWatchBadge.WATCHING_FRAGILE,
        )
        assertEquals(
            DetectionStory.Watching(
                vehicleName = "Skoda Kamiq", isParked = true, watch = VehicleWatch.Assisted,
                watchBadge = ParkedWatchBadge.WATCHING_FRAGILE,
            ),
            story,
        )
    }

    @Test
    fun should_carry_interrupted_watch_badge_when_service_dead() {
        // [DET-WATCH-HONEST-001] The OS killed the watcher → degrade to a "reactivate" ask, not a lie.
        val story = resolveDetectionStory(
            uiState = DetectionUiState.Parked,
            vehicleCards = listOf(activeParkedCard),
            parkedWatchBadge = ParkedWatchBadge.WATCH_INTERRUPTED,
        )
        assertEquals(
            DetectionStory.Watching(
                vehicleName = "Skoda Kamiq", isParked = true, watch = VehicleWatch.Assisted,
                watchBadge = ParkedWatchBadge.WATCH_INTERRUPTED,
            ),
            story,
        )
    }

    @Test
    fun should_hide_watching_when_no_vehicle_is_active() {
        // Watching names the ACTIVE vehicle ONLY — no ranked/first fallback (user decision:
        // it is the one the Coordinator works for). Without an active car, better silence
        // than a lie. [UX-DETECTION-STORY-001]
        val story = resolveDetectionStory(
            uiState = DetectionUiState.Parked,
            vehicleCards = listOf(otherCard),
        )
        assertEquals(DetectionStory.Hidden, story)
    }

    @Test
    fun should_hide_when_silent() {
        assertEquals(
            DetectionStory.Hidden,
            resolveDetectionStory(DetectionUiState.Silent, listOf(activeCard)),
        )
    }

    // ── The two pending questions. [DET-ASK-STATE-001] ───────────────────────

    @Test
    fun should_ask_the_open_question_over_the_live_trip() {
        // The field bug: with a prompt posted, Home narrated "following your trip" and the one
        // thing the user could do was invisible.
        val story = resolveDetectionStory(
            uiState = DetectionUiState.Monitoring,
            vehicleCards = listOf(activeCard),
            promptWindow = window("Škoda Kamiq"),
        )
        assertEquals(DetectionStory.AwaitingAnswer(window("Škoda Kamiq")), story)
    }

    @Test
    fun should_word_the_question_exactly_as_the_notification_did() {
        // The row repeats the name the notification used — including its ABSENCE, which is the
        // generic wording, not a chance to guess a car the tray never named.
        assertEquals(
            DetectionStory.AwaitingAnswer(window(null)),
            resolveDetectionStory(
                DetectionUiState.Monitoring, listOf(activeCard), promptWindow = window(null),
            ),
        )
    }

    // ── Quién hospeda la noticia [HOME-AN-OPEN-QUESTION-IS-THE-CARS-NEWS-001] ──────────────────

    @Test
    fun should_let_the_card_host_the_questions_that_car_can_answer() {
        // ⛔ EL TESTIGO de la issue #129. La hoja apilaba DOS tarjetas sobre el mismo coche: el badge
        // preguntando «¿has aparcado?» y la card afirmando «En ruta» tres milímetros debajo, cada
        // una con sus botones. Lo que se muda a la card son las PREGUNTAS de ese coche.
        val itsOwnQuestions = listOf(
            DetectionStory.AwaitingAnswer(window("Škoda Kamiq")),
            DetectionStory.PendingAsk,
            DetectionStory.AwaitingFirstPark,
        )

        itsOwnQuestions.forEach { story ->
            assertTrue(story.isTheCarsQuestion(), "$story lo contesta ese coche: lo hospeda su card")
        }
    }

    @Test
    fun should_keep_system_health_out_of_the_card_because_it_hits_every_vehicle() {
        // ⛔⛔ La corrección del user (23-09), mirando el dibujo: permisos y detección NO son de un
        // coche. Un permiso, un ajuste y UN servicio en primer plano sirven a todos los coches, así
        // que colgarlos de una card nombra al culpable equivocado — y con dos, obliga a elegir a
        // cuál acusar. Y dentro de la card el aviso TAPA la información del coche con un botón a
        // ancho completo, empujando a pulsar algo que quizá no se quiere ahora.
        val systemHealth = listOf(
            DetectionStory.BlockedCore,
            DetectionStory.Inactive,
            DetectionStory.Watching(
                "Škoda Kamiq",
                isParked = true,
                watch = VehicleWatch.Assisted,
                watchBadge = ParkedWatchBadge.WATCHING_FRAGILE,
            ),
            DetectionStory.Watching(
                "Škoda Kamiq",
                isParked = true,
                watch = VehicleWatch.Assisted,
                watchBadge = ParkedWatchBadge.WATCH_INTERRUPTED,
            ),
        )

        systemHealth.forEach { story ->
            assertFalse(story.isTheCarsQuestion(), "$story es salud del sistema: se queda en el badge")
        }
        // Y la mitad que impide el fallo peor: apartarse sin que nadie recoja la noticia la haría
        // DESAPARECER. «No tienes coche» es literalmente eso — no hay card a la que mudarlo, y es
        // lo que justifica que el badge siga existiendo.
        assertFalse(DetectionStory.NoVehicle.isTheCarsQuestion())
        assertFalse(DetectionStory.Hidden.isTheCarsQuestion())
    }

    @Test
    fun should_never_say_there_is_no_vehicle_while_holding_a_vehicle_card() {
        // ⛔ La disponibilidad por defecto es `Disabled(NO_VEHICLE)`, así que basta con no pasarla
        // para que la proyección diga «Añade tu coche» encima de la tira de coches. Con la lista
        // delante, callar es lo único honesto.
        assertEquals(
            DetectionStory.Hidden,
            resolveDetectionStory(DetectionUiState.NoVehicle, listOf(activeCard)),
        )
        // Y la otra mitad: sin cards sí se dice, que es el único caso que justifica el badge.
        assertEquals(
            DetectionStory.NoVehicle,
            resolveDetectionStory(DetectionUiState.NoVehicle, emptyList()),
        )
    }

    @Test
    fun should_ask_the_open_question_over_the_pending_nudge() {
        // A live question with a deadline outranks a deadline-less one.
        val story = resolveDetectionStory(
            uiState = DetectionUiState.AwaitingFirstPark,
            vehicleCards = listOf(activeCard),
            promptWindow = window("Škoda Kamiq"),
            showParkNudge = true,
        )
        assertEquals(DetectionStory.AwaitingAnswer(window("Škoda Kamiq")), story)
    }

    @Test
    fun should_show_the_nudge_over_every_state_the_app_merely_narrates() {
        // Was an `if` inside the composable until this ticket — untested, and outside the
        // precedence this function declares.
        listOf(
            DetectionUiState.NoVehicle,
            DetectionUiState.Inactive,
            DetectionUiState.AwaitingFirstPark,
            DetectionUiState.Monitoring,
            DetectionUiState.Parked,
            DetectionUiState.ArmedBluetooth,
            DetectionUiState.Silent,
        ).forEach { state ->
            assertEquals(
                DetectionStory.PendingAsk,
                resolveDetectionStory(state, listOf(activeParkedCard), showParkNudge = true),
                "the pending nudge must outrank $state",
            )
        }
    }

    @Test
    fun should_block_on_core_permission_over_both_questions() {
        // Neither answer can be acted on with location off, and the app barely works.
        assertEquals(
            DetectionStory.BlockedCore,
            resolveDetectionStory(
                DetectionUiState.BlockedCore, listOf(activeCard),
                promptWindow = window("Škoda Kamiq"), showParkNudge = true,
            ),
        )
    }

    @Test
    fun should_keep_telling_the_ordinary_story_when_nothing_is_pending() {
        // Guard against the questions leaking into the happy path: no window, no nudge, no change.
        assertEquals(
            DetectionStory.Watching("Skoda Kamiq", isParked = true, watch = VehicleWatch.Assisted),
            resolveDetectionStory(DetectionUiState.Parked, listOf(activeParkedCard)),
        )
    }

    /**
     * ⛔⛔ **Sin noticias del servicio no hay historia que contar.**
     * [DET-A-SILENT-SERVICE-IS-NOT-A-DEAD-ONE-001]
     *
     * Este testigo **faltaba**, y lo destapó falsificar: quitar el filtro de `watchingStory()` no
     * ponía rojo ni un test, así que la regla vivía sin vigilancia. Es la segunda vez en la misma
     * tanda que la falsificación encuentra algo que los tests verdes no — una, un testigo que
     * defendía el bug; esta, un testigo que no existía.
     */
    @Test
    fun should_tellNoStory_when_theServiceHasNotSpokenYet() {
        assertEquals(
            DetectionStory.Hidden,
            resolveDetectionStory(
                DetectionUiState.Parked,
                listOf(activeParkedCard),
                parkedWatchBadge = ParkedWatchBadge.WATCH_UNKNOWN,
            ),
        )
    }

    /** Y en cuanto el servicio habla, la historia vuelve — el silencio dura un beat, no una sesión. */
    @Test
    fun should_tellTheStoryAgain_onceTheServiceSpeaks() {
        assertEquals(
            DetectionStory.Watching(
                "Skoda Kamiq",
                isParked = true,
                watch = VehicleWatch.Assisted,
                watchBadge = ParkedWatchBadge.WATCHING,
            ),
            resolveDetectionStory(
                DetectionUiState.Parked,
                listOf(activeParkedCard),
                parkedWatchBadge = ParkedWatchBadge.WATCHING,
            ),
        )
    }

    // ── Guided first steps take over the cold start [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001] ──

    @Test
    fun should_standDownTheColdStartRow_when_theChecklistIsAskingForTheFirstParking() {
        // Two rows asking for one action is the drift this projection exists to prevent.
        assertEquals(
            DetectionStory.Hidden,
            resolveDetectionStory(
                DetectionUiState.AwaitingFirstPark, listOf(activeCard),
                firstStepsOwns = FirstStepsOwnership.COLD_START,
            ),
        )
    }

    @Test
    fun should_suppressNothingElse_when_theChecklistOwnsTheColdStart() {
        // The flag takes over ONE story. Every other state keeps its voice — a tutorial is never a
        // reason to go quiet about a blocked permission or a dead watch.
        val cards = listOf(activeCard)
        assertEquals(
            DetectionStory.BlockedCore,
            resolveDetectionStory(DetectionUiState.BlockedCore, cards, firstStepsOwns = FirstStepsOwnership.COLD_START),
        )
        // Sin cards: es el único escenario en que «no tienes coche» es verdad, y lo que este test
        // comprueba es que el checklist no lo calla — no de dónde sale la lista.
        assertEquals(
            DetectionStory.NoVehicle,
            resolveDetectionStory(
                DetectionUiState.NoVehicle, emptyList(),
                firstStepsOwns = FirstStepsOwnership.COLD_START,
            ),
        )
        assertEquals(
            DetectionStory.Inactive,
            resolveDetectionStory(DetectionUiState.Inactive, cards, firstStepsOwns = FirstStepsOwnership.COLD_START),
        )
        assertEquals(
            DetectionStory.Watching("Skoda Kamiq", isParked = true, watch = VehicleWatch.Assisted),
            resolveDetectionStory(
                DetectionUiState.Parked, listOf(activeParkedCard),
                firstStepsOwns = FirstStepsOwnership.COLD_START,
            ),
        )
    }

    @Test
    fun should_stillOutrankTheChecklist_when_aQuestionOrNudgeIsPending() {
        // Both things the app is WAITING ON THE USER for keep beating the cold-start suppression:
        // they are answers the app cannot do its job without.
        assertEquals(
            DetectionStory.AwaitingAnswer(window("Škoda Kamiq")),
            resolveDetectionStory(
                DetectionUiState.AwaitingFirstPark, listOf(activeCard),
                promptWindow = window("Škoda Kamiq"), firstStepsOwns = FirstStepsOwnership.COLD_START,
            ),
        )
        assertEquals(
            DetectionStory.PendingAsk,
            resolveDetectionStory(
                DetectionUiState.AwaitingFirstPark, listOf(activeCard),
                showParkNudge = true, firstStepsOwns = FirstStepsOwnership.COLD_START,
            ),
        )
    }

    @Test
    fun should_keepTheColdStartRow_when_theChecklistIsNotOnThatStep() {
        // The default, and the state every existing caller is in.
        assertEquals(
            DetectionStory.AwaitingFirstPark,
            resolveDetectionStory(
                DetectionUiState.AwaitingFirstPark, listOf(activeCard),
                firstStepsOwns = FirstStepsOwnership.NOTHING,
            ),
        )
    }

    // ── The second row the checklist can take over ──
    // [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001]

    @Test
    fun should_standDownTheInactiveRow_when_theChecklistIsAskingToTurnDetectionOn() {
        assertEquals(
            DetectionStory.Hidden,
            resolveDetectionStory(
                DetectionUiState.Inactive, listOf(activeCard),
                firstStepsOwns = FirstStepsOwnership.DETECTION_OFF,
            ),
        )
    }

    @Test
    fun should_keepEachRow_when_theChecklistOwnsTheOtherOne() {
        // Ownership is one value, not a blanket: the step asking to turn detection on must not
        // silence the cold-start ask, and vice versa.
        assertEquals(
            DetectionStory.AwaitingFirstPark,
            resolveDetectionStory(
                DetectionUiState.AwaitingFirstPark, listOf(activeCard),
                firstStepsOwns = FirstStepsOwnership.DETECTION_OFF,
            ),
        )
        assertEquals(
            DetectionStory.Inactive,
            resolveDetectionStory(
                DetectionUiState.Inactive, listOf(activeCard),
                firstStepsOwns = FirstStepsOwnership.COLD_START,
            ),
        )
    }

    @Test
    fun should_keepAnInterruptedWatchLoud_when_theChecklistOwnsDetectionOff() {
        // A fragile or killed watch names its own cause; the step could only say something vaguer,
        // so it never takes that row over. [DET-WATCH-HONEST-001]
        assertEquals(
            DetectionStory.Watching(
                "Skoda Kamiq",
                isParked = true,
                watch = VehicleWatch.Assisted,
                watchBadge = ParkedWatchBadge.WATCH_INTERRUPTED,
            ),
            resolveDetectionStory(
                DetectionUiState.Parked, listOf(activeParkedCard),
                parkedWatchBadge = ParkedWatchBadge.WATCH_INTERRUPTED,
                firstStepsOwns = FirstStepsOwnership.DETECTION_OFF,
            ),
        )
    }

    private fun window(vehicleName: String?) = PendingPromptWindow(shownAtMs = 1L, vehicleName = vehicleName)
}
