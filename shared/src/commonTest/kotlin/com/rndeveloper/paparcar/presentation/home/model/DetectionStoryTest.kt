package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.detection.DetectionPhase
import com.rndeveloper.paparcar.domain.detection.PendingPromptWindow
import com.rndeveloper.paparcar.domain.onboarding.FirstStepsOwnership
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.presentation.home.DrivingMeta
import com.rndeveloper.paparcar.presentation.home.VehicleCard
import com.rndeveloper.paparcar.ui.theme.VehicleWatch
import kotlin.test.Test
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
    fun should_passThrough_the_four_action_states() {
        val cards = listOf(activeCard)
        assertEquals(DetectionStory.BlockedCore, resolveDetectionStory(DetectionUiState.BlockedCore, null, cards))
        assertEquals(DetectionStory.NoVehicle, resolveDetectionStory(DetectionUiState.NoVehicle, null, cards))
        assertEquals(DetectionStory.Inactive, resolveDetectionStory(DetectionUiState.Inactive, null, cards))
        assertEquals(
            DetectionStory.AwaitingFirstPark,
            resolveDetectionStory(DetectionUiState.AwaitingFirstPark, null, cards),
        )
    }

    @Test
    fun should_tell_driving_with_the_trips_own_vehicle_name() {
        val story = resolveDetectionStory(
            uiState = DetectionUiState.Monitoring,
            drivingMeta = DrivingMeta(vehicleId = "v-other", phase = DetectionPhase.Driving),
            vehicleCards = listOf(activeCard, otherCard),
        )
        // The Ibiza is neither active nor BT-paired, so its watch method is Off — and the story
        // carries that truthfully. Before the model carried VehicleWatch this projected to
        // `viaBluetooth = false` and the row silently wore the assisted green while the garage
        // painted the same car grey. [UI-SEVEN-STRAYS-FROM-THE-CANON-001]
        assertEquals(
            DetectionStory.Driving(vehicleName = "Seat Ibiza", isCandidate = false, watch = VehicleWatch.Off),
            story,
        )
    }

    @Test
    fun should_flag_candidate_when_the_trip_is_confirming_a_spot() {
        val story = resolveDetectionStory(
            uiState = DetectionUiState.Monitoring,
            drivingMeta = DrivingMeta(vehicleId = "v-active", phase = DetectionPhase.Candidate),
            vehicleCards = listOf(activeCard),
        )
        assertEquals(DetectionStory.Driving(vehicleName = "Skoda Kamiq", isCandidate = true), story)
    }

    @Test
    fun should_fall_back_to_the_active_vehicle_when_the_trip_has_no_resolved_vehicle() {
        val story = resolveDetectionStory(
            uiState = DetectionUiState.Monitoring,
            drivingMeta = DrivingMeta(vehicleId = null, phase = DetectionPhase.Driving),
            vehicleCards = listOf(otherCard, activeCard),
        )
        assertEquals(DetectionStory.Driving(vehicleName = "Skoda Kamiq", isCandidate = false), story)
    }

    @Test
    fun should_watch_the_active_vehicle_when_parked() {
        val story = resolveDetectionStory(
            uiState = DetectionUiState.Parked,
            drivingMeta = null,
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
            drivingMeta = null,
            vehicleCards = listOf(btCard, otherCard),
        )
        assertEquals(
            DetectionStory.Watching(vehicleName = "Skoda Kamiq", isParked = false, watch = VehicleWatch.Bluetooth),
            story,
        )
    }

    @Test
    fun should_colour_the_driving_story_with_the_trip_vehicles_bt_method() {
        // A BT-watched car driving keeps its blue identity in the detection row. [UI-COLOR-DOCTRINE-001]
        val btOther = VehicleCard(
            vehicle("v-other", brand = "Seat", model = "Ibiza", bluetoothDeviceId = "AA:BB"),
            session = null,
        )
        val story = resolveDetectionStory(
            uiState = DetectionUiState.Monitoring,
            drivingMeta = DrivingMeta(vehicleId = "v-other", phase = DetectionPhase.Driving),
            vehicleCards = listOf(activeCard, btOther),
        )
        assertEquals(
            DetectionStory.Driving(vehicleName = "Seat Ibiza", isCandidate = false, watch = VehicleWatch.Bluetooth),
            story,
        )
    }

    @Test
    fun should_carry_fragile_watch_badge_when_setup_is_fragile() {
        // [DET-WATCH-HONEST-001] The parked line stays "watching" but warns + offers the exemption.
        val story = resolveDetectionStory(
            uiState = DetectionUiState.Parked,
            drivingMeta = null,
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
            drivingMeta = null,
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
            drivingMeta = null,
            vehicleCards = listOf(otherCard),
        )
        assertEquals(DetectionStory.Hidden, story)
    }

    @Test
    fun should_hide_when_silent() {
        assertEquals(
            DetectionStory.Hidden,
            resolveDetectionStory(DetectionUiState.Silent, null, listOf(activeCard)),
        )
    }

    // ── The two pending questions. [DET-ASK-STATE-001] ───────────────────────

    @Test
    fun should_ask_the_open_question_over_the_live_trip() {
        // The field bug: with a prompt posted, Home narrated "following your trip" and the one
        // thing the user could do was invisible.
        val story = resolveDetectionStory(
            uiState = DetectionUiState.Monitoring,
            drivingMeta = DrivingMeta(vehicleId = "v-active", phase = DetectionPhase.Candidate),
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
                DetectionUiState.Monitoring, null, listOf(activeCard), promptWindow = window(null),
            ),
        )
    }

    @Test
    fun should_ask_the_open_question_over_the_pending_nudge() {
        // A live question with a deadline outranks a deadline-less one.
        val story = resolveDetectionStory(
            uiState = DetectionUiState.AwaitingFirstPark,
            drivingMeta = null,
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
                resolveDetectionStory(state, null, listOf(activeParkedCard), showParkNudge = true),
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
                DetectionUiState.BlockedCore, null, listOf(activeCard),
                promptWindow = window("Škoda Kamiq"), showParkNudge = true,
            ),
        )
    }

    @Test
    fun should_keep_telling_the_ordinary_story_when_nothing_is_pending() {
        // Guard against the questions leaking into the happy path: no window, no nudge, no change.
        assertEquals(
            DetectionStory.Watching("Skoda Kamiq", isParked = true, watch = VehicleWatch.Assisted),
            resolveDetectionStory(DetectionUiState.Parked, null, listOf(activeParkedCard)),
        )
    }

    // ── Guided first steps take over the cold start [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001] ──

    @Test
    fun should_standDownTheColdStartRow_when_theChecklistIsAskingForTheFirstParking() {
        // Two rows asking for one action is the drift this projection exists to prevent.
        assertEquals(
            DetectionStory.Hidden,
            resolveDetectionStory(
                DetectionUiState.AwaitingFirstPark, null, listOf(activeCard),
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
            resolveDetectionStory(DetectionUiState.BlockedCore, null, cards, firstStepsOwns = FirstStepsOwnership.COLD_START),
        )
        assertEquals(
            DetectionStory.NoVehicle,
            resolveDetectionStory(DetectionUiState.NoVehicle, null, cards, firstStepsOwns = FirstStepsOwnership.COLD_START),
        )
        assertEquals(
            DetectionStory.Inactive,
            resolveDetectionStory(DetectionUiState.Inactive, null, cards, firstStepsOwns = FirstStepsOwnership.COLD_START),
        )
        assertEquals(
            DetectionStory.Watching("Skoda Kamiq", isParked = true, watch = VehicleWatch.Assisted),
            resolveDetectionStory(
                DetectionUiState.Parked, null, listOf(activeParkedCard),
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
                DetectionUiState.AwaitingFirstPark, null, listOf(activeCard),
                promptWindow = window("Škoda Kamiq"), firstStepsOwns = FirstStepsOwnership.COLD_START,
            ),
        )
        assertEquals(
            DetectionStory.PendingAsk,
            resolveDetectionStory(
                DetectionUiState.AwaitingFirstPark, null, listOf(activeCard),
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
                DetectionUiState.AwaitingFirstPark, null, listOf(activeCard),
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
                DetectionUiState.Inactive, null, listOf(activeCard),
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
                DetectionUiState.AwaitingFirstPark, null, listOf(activeCard),
                firstStepsOwns = FirstStepsOwnership.DETECTION_OFF,
            ),
        )
        assertEquals(
            DetectionStory.Inactive,
            resolveDetectionStory(
                DetectionUiState.Inactive, null, listOf(activeCard),
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
                DetectionUiState.Parked, null, listOf(activeParkedCard),
                parkedWatchBadge = ParkedWatchBadge.WATCH_INTERRUPTED,
                firstStepsOwns = FirstStepsOwnership.DETECTION_OFF,
            ),
        )
    }

    private fun window(vehicleName: String?) = PendingPromptWindow(shownAtMs = 1L, vehicleName = vehicleName)
}
