package com.rndeveloper.paparcar.presentation.home

import com.rndeveloper.paparcar.domain.detection.PendingPromptWindow
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.SpotStatus
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleMonitoringStatus
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Projection tests for the per-section slices of [HomeState]. [HOME-ATOMIZE-001 F1]
 * Focus: the list-materialising logic that used to live as computed `get()`s on
 * HomeState (size filter, vehicle-card join) and the selection resolution the
 * peek slice mirrors.
 */
class HomeSlicesTest {

    private val gps = GpsPoint(latitude = 40.4165, longitude = -3.7030, accuracy = 10f, timestamp = 1_000L, speed = 0f)

    private fun spot(
        id: String,
        size: VehicleSize? = null,
        status: SpotStatus = SpotStatus.CONFIRMED,
    ) = Spot(
        id = id,
        location = gps,
        reportedBy = "user-1",
        sizeCategory = size,
        status = status,
    )

    private fun vehicle(
        id: String,
        isActive: Boolean = false,
        bluetoothDeviceId: String? = null,
    ) = Vehicle(
        id = id,
        userId = "user-1",
        brand = "Toyota",
        model = "Corolla",
        sizeCategory = VehicleSize.MEDIUM_SUV,
        isActive = isActive,
        bluetoothDeviceId = bluetoothDeviceId,
    )

    private fun session(id: String, vehicleId: String, parkedAt: Long = 1_000L) = UserParking(
        id = id,
        userId = "user-1",
        vehicleId = vehicleId,
        location = gps.copy(timestamp = parkedAt),
        isActive = true,
    )

    // ── The provisional spot and its own session [UI-PROVISIONAL-SPOT-IS-NOT-ITS-SESSION-001] ──
    // A deduced departure publishes the freed spot under its SESSION's id and deliberately keeps
    // the session alive [DET-HANDOFF-NOT-MANUAL-001 §B]. Field 2026-08-21 23:46: the two collided,
    // the spot became unselectable, and the owner saw a free space on top of his own parked car.

    /** The spot that a deduced departure publishes: same id, same place as the live session. */
    private fun provisionalTwinOf(session: UserParking) = Spot(
        id = session.id,
        location = session.location,
        reportedBy = "user-1",
        status = SpotStatus.PROVISIONAL,
    )

    @Test
    fun should_resolve_the_spot_when_a_spot_selection_shares_its_session_id() {
        val mine = session("collision", "veh-A")
        val state = HomeState(
            activeSessions = listOf(mine),
            nearbySpots = listOf(provisionalTwinOf(mine)),
            selection = HomeSelection.Spot("collision"),
        )

        assertEquals("collision", state.selectedSpot?.id, "the spot must be reachable")
        assertNull(state.selectedSession, "…and must not be shadowed by the session")
        assertFalse(state.isParkingSelected)
        // The peek slice mirrors HomeState — the two must never disagree about the kind.
        assertEquals("collision", state.toPeekSlice().selectedSpot?.id)
        assertNull(state.toPeekSlice().selectedSession)
    }

    @Test
    fun should_resolve_the_session_when_a_parking_selection_shares_its_spot_id() {
        val mine = session("collision", "veh-A")
        val state = HomeState(
            activeSessions = listOf(mine),
            nearbySpots = listOf(provisionalTwinOf(mine)),
            selection = HomeSelection.Parking("collision"),
        )

        assertEquals("collision", state.selectedSession?.id)
        assertNull(state.selectedSpot, "the same id must not also resolve as a spot")
        assertTrue(state.isParkingSelected)
        assertEquals("collision", state.toPeekSlice().selectedSession?.id)
        assertNull(state.toPeekSlice().selectedSpot)
    }

    @Test
    fun should_not_offer_my_own_still_parked_car_as_a_free_spot() {
        val mine = session("collision", "veh-A")
        val state = HomeState(
            activeSessions = listOf(mine),
            nearbySpots = listOf(provisionalTwinOf(mine), spot("someone-else")),
        )

        assertEquals(listOf("someone-else"), state.filteredNearbySpots().map { it.id })
        assertEquals(listOf("someone-else"), state.toMapSlice().nearbySpots.map { it.id })
        assertEquals(1, state.toPeekSlice().freeCount, "the counter must not count my own car")
    }

    @Test
    fun should_report_nothing_on_offer_when_the_only_spot_is_my_own_parked_car() {
        val mine = session("collision", "veh-A")
        val state = HomeState(
            activeSessions = listOf(mine),
            nearbySpots = listOf(provisionalTwinOf(mine)),
        )

        assertFalse(state.toBrowseListSlice().hasAnySpots)
    }

    @Test
    fun should_offer_the_spot_again_once_its_session_is_released() {
        // The promotion (a measured drive) or a witnessed departure clears the session; from that
        // moment the space really is free and nothing needs undoing.
        val mine = session("collision", "veh-A")
        val released = HomeState(
            activeSessions = emptyList(),
            nearbySpots = listOf(provisionalTwinOf(mine)),
        )

        assertEquals(listOf("collision"), released.filteredNearbySpots().map { it.id })
        assertTrue(released.toBrowseListSlice().hasAnySpots)
    }

    // ── Size filter (browse list + peek freeCount) ────────────────────────────

    @Test
    fun should_include_all_spots_when_no_size_filter() {
        val state = HomeState(nearbySpots = listOf(spot("a"), spot("b", VehicleSize.VAN_HIGH)))
        assertEquals(listOf("a", "b"), state.toBrowseListSlice().filteredSpots.map { it.id })
        assertEquals(2, state.toPeekSlice().freeCount)
    }

    @Test
    fun should_keep_matching_and_unknown_size_spots_when_filter_active() {
        val state = HomeState(
            nearbySpots = listOf(
                spot("match", VehicleSize.MEDIUM_SUV),
                spot("unknown", null),
                spot("other", VehicleSize.VAN_HIGH),
            ),
            sizeFilter = VehicleSize.MEDIUM_SUV,
        )
        assertEquals(listOf("match", "unknown"), state.toBrowseListSlice().filteredSpots.map { it.id })
        assertEquals(2, state.toPeekSlice().freeCount)
    }

    // ── Spot status: what is on offer, and in what order [DET-HANDOFF-NOT-MANUAL-001 §B.3] ──

    @Test
    fun should_drop_a_retracted_spot_from_the_list_the_map_and_the_counter() {
        val state = HomeState(nearbySpots = listOf(spot("live"), spot("withdrawn", status = SpotStatus.RETRACTED)))

        assertEquals(listOf("live"), state.toBrowseListSlice().filteredSpots.map { it.id })
        assertEquals(listOf("live"), state.toMapSlice().nearbySpots.map { it.id }, "no marker for a space we withdrew")
        assertEquals(1, state.toPeekSlice().freeCount)
    }

    @Test
    fun should_keep_a_retracted_spot_selectable_so_the_peek_can_explain_it() {
        // The user had it open when it was withdrawn. Dropping it from nearbySpots would close the
        // sheet under them with no explanation — the exact silence the retraction exists to avoid.
        val state = HomeState(
            nearbySpots = listOf(spot("withdrawn", status = SpotStatus.RETRACTED)),
            selection = HomeSelection.Spot("withdrawn"),
        )

        assertEquals("withdrawn", state.selectedSpot?.id)
        assertEquals("withdrawn", state.toPeekSlice().selectedSpot?.id)
        assertFalse(state.toBrowseListSlice().hasAnySpots, "…while nothing is actually on offer")
    }

    @Test
    fun should_rank_an_unconfirmed_spot_below_every_confirmed_one() {
        val state = HomeState(
            nearbySpots = listOf(
                spot("unconfirmed", status = SpotStatus.PROVISIONAL),
                spot("confirmed-a"),
                spot("confirmed-b"),
            ),
        )

        assertEquals(
            listOf("confirmed-a", "confirmed-b", "unconfirmed"),
            state.toBrowseListSlice().filteredSpots.map { it.id },
            "still offered — it is a real space — but last, and the confirmed order is untouched",
        )
    }

    // ── Vehicle-card join ─────────────────────────────────────────────────────

    @Test
    fun should_join_each_vehicle_to_its_session_by_vehicleId() {
        val state = HomeState(
            vehicles = listOf(vehicle("veh-A"), vehicle("veh-B")),
            activeSessions = listOf(session("s1", vehicleId = "veh-A")),
        )
        val cards = state.toBrowseListSlice().vehicleCards
        assertEquals(2, cards.size)
        assertEquals("s1", cards.first { it.vehicle.id == "veh-A" }.session?.id)
        assertNull(cards.first { it.vehicle.id == "veh-B" }.session)
    }

    @Test
    fun should_emit_empty_vehicleCards_when_no_vehicles() {
        assertEquals(emptyList(), HomeState().toBrowseListSlice().vehicleCards)
    }

    // ── Peek selection resolution ─────────────────────────────────────────────

    @Test
    fun should_resolve_selected_spot_when_selection_is_not_a_session() {
        val state = HomeState(
            nearbySpots = listOf(spot("spot-1")),
            activeSessions = listOf(session("s1", "veh-A")),
            selection = HomeSelection.Spot("spot-1"),
        )
        val peek = state.toPeekSlice()
        assertEquals("spot-1", peek.selectedSpot?.id)
        assertNull(peek.selectedSession)
        assertFalse(peek.isParkingSelected)
    }

    @Test
    fun should_resolve_selected_session_when_selection_is_a_session() {
        val state = HomeState(
            nearbySpots = listOf(spot("spot-1")),
            activeSessions = listOf(session("s1", "veh-A")),
            selection = HomeSelection.Parking("s1"),
        )
        val peek = state.toPeekSlice()
        assertEquals("s1", peek.selectedSession?.id)
        assertNull(peek.selectedSpot)
        assertTrue(peek.isParkingSelected)
        assertEquals("s1", peek.userParking?.id)
    }

    // ── Preferred session (userParking) — most recent park wins ──────────────
    // [UI-PREFERRED-SESSION-RECENCY-001]

    @Test
    fun should_prefer_the_most_recently_parked_session_over_the_bt_vehicles_older_one() {
        val state = HomeState(
            vehicles = listOf(
                vehicle("veh-active").copy(isActive = true),
                vehicle("veh-bt").copy(bluetoothDeviceId = "AA:BB"),
            ),
            // The BT car parked days ago; the active car is the one driven these days.
            activeSessions = listOf(
                session("s-bt", "veh-bt", parkedAt = 1_000L),
                session("s-active", "veh-active", parkedAt = 2_000L),
            ),
        )
        assertEquals("s-active", state.userParking?.id)
        assertEquals("s-active", state.toPeekSlice().userParking?.id)
    }

    @Test
    fun should_break_a_timestamp_tie_by_watch_rank_bt_first() {
        val state = HomeState(
            vehicles = listOf(
                vehicle("veh-inactive"),
                vehicle("veh-active").copy(isActive = true),
                vehicle("veh-bt").copy(bluetoothDeviceId = "AA:BB"),
            ),
            // Same park instant — the best-watched car's session wins the tie.
            activeSessions = listOf(
                session("s-inactive", "veh-inactive"),
                session("s-active", "veh-active"),
                session("s-bt", "veh-bt"),
            ),
        )
        assertEquals("s-bt", state.userParking?.id)
    }

    @Test
    fun should_fall_back_to_first_session_when_timestamps_and_ranks_tie() {
        val state = HomeState(
            vehicles = listOf(vehicle("veh-a"), vehicle("veh-b")),
            activeSessions = listOf(session("s-a", "veh-a"), session("s-b", "veh-b")),
        )
        assertEquals("s-a", state.userParking?.id)
    }

    // ── Vehicles row order — driving > parked (by recency) > unparked ─────────
    // [UI-BROWSE-DRIVING-OVER-PARKED-001]

    @Test
    fun should_float_the_driving_vehicle_first_even_when_another_car_is_parked() {
        val parked = VehicleCard(vehicle("veh-parked"), session("s1", "veh-parked"))
        val driving = VehicleCard(vehicle("veh-driving"), session = null)
        val order = vehiclesRowOrder(listOf(parked, driving), drivingVehicleId = "veh-driving")
        assertEquals(listOf("veh-driving", "veh-parked"), order.map { it.vehicle.id })
    }

    @Test
    fun should_order_parked_cards_by_most_recent_park_not_list_order() {
        // The OLD car is better watched (BT) and comes first in list order — recency must still win.
        val oldBt = VehicleCard(
            vehicle("veh-old", bluetoothDeviceId = "AA:BB"),
            session("s-old", "veh-old", parkedAt = 1_000L),
        )
        val recent = VehicleCard(vehicle("veh-recent"), session("s-recent", "veh-recent", parkedAt = 2_000L))
        val order = vehiclesRowOrder(listOf(oldBt, recent), drivingVehicleId = null)
        assertEquals(listOf("veh-recent", "veh-old"), order.map { it.vehicle.id })
    }

    @Test
    fun should_break_a_parked_timestamp_tie_by_watch_rank_matching_the_preferred_session() {
        val plain = VehicleCard(vehicle("veh-plain"), session("s-plain", "veh-plain"))
        val bt = VehicleCard(vehicle("veh-bt", bluetoothDeviceId = "AA:BB"), session("s-bt", "veh-bt"))
        val order = vehiclesRowOrder(listOf(plain, bt), drivingVehicleId = null)
        assertEquals(listOf("veh-bt", "veh-plain"), order.map { it.vehicle.id })
    }

    @Test
    fun should_rank_unparked_cards_by_watch_after_every_parked_one() {
        val unparkedBt = VehicleCard(vehicle("veh-bt", bluetoothDeviceId = "AA:BB"), session = null)
        val unparkedPlain = VehicleCard(vehicle("veh-plain"), session = null)
        val parked = VehicleCard(vehicle("veh-parked"), session("s1", "veh-parked"))
        val order = vehiclesRowOrder(listOf(unparkedPlain, unparkedBt, parked), drivingVehicleId = null)
        assertEquals(listOf("veh-parked", "veh-bt", "veh-plain"), order.map { it.vehicle.id })
    }

    // ── Map slice: AddingParking vehicle resolution ───────────────────────────

    @Test
    fun should_resolve_addParkingVehicle_from_editing_session_when_editing() {
        val state = HomeState(
            vehicles = listOf(vehicle("veh-A"), vehicle("veh-B")),
            activeSessions = listOf(session("s1", vehicleId = "veh-B")),
            mode = HomeMode.AddingParking,
            editingParkingId = "s1",
            // A stale create-id must lose to the editing session's vehicle.
            addingParkingVehicleId = "veh-A",
        )
        assertEquals("veh-B", state.toMapSlice().addParkingVehicle?.id)
    }

    @Test
    fun should_resolve_addParkingVehicle_from_target_id_when_creating() {
        val state = HomeState(
            vehicles = listOf(vehicle("veh-A")),
            mode = HomeMode.AddingParking,
            addingParkingVehicleId = "veh-A",
        )
        assertEquals("veh-A", state.toMapSlice().addParkingVehicle?.id)
    }

    @Test
    fun should_have_null_addParkingVehicle_in_browse() {
        val state = HomeState(vehicles = listOf(vehicle("veh-A")))
        assertNull(state.toMapSlice().addParkingVehicle)
    }

    // ── Fabs / header slices ──────────────────────────────────────────────────

    @Test
    fun should_project_fabs_booleans_from_sessions_and_gps() {
        val bare = HomeState().toFabsSlice()
        assertFalse(bare.hasActiveParking)
        assertFalse(bare.hasGpsFix)
        assertNull(bare.selectedParkingWatch)

        val full = HomeState(
            vehicles = listOf(vehicle("veh-A", isActive = true)),
            activeSessions = listOf(session("s1", "veh-A")),
            userGpsPoint = gps,
            selection = HomeSelection.Parking("s1"),
        ).toFabsSlice()
        assertTrue(full.hasActiveParking)
        assertTrue(full.hasGpsFix)
        assertEquals(VehicleMonitoringStatus.Active, full.selectedParkingWatch)
    }

    // The car FAB cycles between parked sessions, so its tint must name the SELECTED
    // vehicle's watch method, not a flat brand green. [UI-FAB-CAR-IDENTITY-001]

    @Test
    fun should_project_bluetooth_watch_when_selected_session_belongs_to_a_bt_vehicle() {
        val state = HomeState(
            vehicles = listOf(
                vehicle("veh-BT", bluetoothDeviceId = "AA:BB:CC:DD:EE:FF"),
                vehicle("veh-A", isActive = true),
            ),
            activeSessions = listOf(session("s1", "veh-A"), session("s2", "veh-BT")),
            selection = HomeSelection.Parking("s2"),
        )
        assertEquals(
            VehicleMonitoringStatus.Bluetooth("AA:BB:CC:DD:EE:FF"),
            state.toFabsSlice().selectedParkingWatch,
        )
    }

    @Test
    fun should_project_inactive_watch_when_selected_session_belongs_to_an_unwatched_vehicle() {
        val state = HomeState(
            vehicles = listOf(vehicle("veh-B")),
            activeSessions = listOf(session("s1", "veh-B")),
            selection = HomeSelection.Parking("s1"),
        )
        assertEquals(VehicleMonitoringStatus.Inactive, state.toFabsSlice().selectedParkingWatch)
    }

    @Test
    fun should_project_inactive_watch_when_selected_session_has_no_matching_vehicle() {
        val state = HomeState(
            activeSessions = listOf(session("s1", "gone")),
            selection = HomeSelection.Parking("s1"),
        )
        assertEquals(VehicleMonitoringStatus.Inactive, state.toFabsSlice().selectedParkingWatch)
    }

    @Test
    fun should_project_no_watch_when_selection_is_a_spot() {
        val state = HomeState(
            nearbySpots = listOf(spot("spot-1")),
            vehicles = listOf(vehicle("veh-A", isActive = true)),
            activeSessions = listOf(session("s1", "veh-A")),
            selection = HomeSelection.Spot("spot-1"),
        )
        assertNull(state.toFabsSlice().selectedParkingWatch)
    }

    @Test
    fun should_project_gps_accuracy_into_header_slice() {
        assertNull(HomeState().toHeaderSlice().gpsAccuracy)
        assertEquals(10f, HomeState(userGpsPoint = gps).toHeaderSlice().gpsAccuracy)
    }

    // ── Peek stepper: what ‹ / › can reach [UI-PEEK-STEPS-BETWEEN-PINS-001] ──────────────────

    @Test
    fun should_offer_both_neighbours_when_the_open_pin_is_mid_list() {
        assertEquals(PeekStep("a", "c"), PeekStep.of(listOf("a", "b", "c"), "b"))
    }

    @Test
    fun should_offer_only_the_next_one_when_the_open_pin_is_the_first() {
        assertEquals(PeekStep(null, "b"), PeekStep.of(listOf("a", "b", "c"), "a"))
    }

    @Test
    fun should_offer_only_the_previous_one_when_the_open_pin_is_the_last() {
        assertEquals(PeekStep("b", null), PeekStep.of(listOf("a", "b", "c"), "c"))
    }

    @Test
    fun should_offer_nothing_when_it_is_the_only_pin() {
        assertEquals(PeekStep.None, PeekStep.of(listOf("a"), "a"))
    }

    /** A withdrawn spot stays SELECTED (the peek has to explain itself) while dropping out of the
     *  browse order — stepping must not silently jump it to the list's edge. */
    @Test
    fun should_offer_nothing_when_the_open_pin_is_not_in_the_order() {
        assertEquals(PeekStep.None, PeekStep.of(listOf("a", "b"), "withdrawn"))
        assertEquals(PeekStep.None, PeekStep.of(listOf("a", "b"), null))
        assertEquals(PeekStep.None, PeekStep.of(emptyList(), "a"))
    }

    @Test
    fun should_step_spots_in_the_same_order_the_sheet_lists_them() {
        val state = HomeState(
            // Provisional sorts LAST in the browse order, so from "fresh" the next one is "other",
            // not the provisional that happens to sit first in the raw list.
            nearbySpots = listOf(
                spot("provisional", status = SpotStatus.PROVISIONAL),
                spot("fresh"),
                spot("other"),
            ),
            selection = HomeSelection.Spot("fresh"),
        )
        val slice = state.toPeekSlice()

        assertEquals(listOf("fresh", "other", "provisional"), slice.browsableSpotIds)
        assertEquals(PeekStep(null, "other"), slice.spotStep)
    }

    @Test
    fun should_not_step_to_a_withdrawn_spot_nor_to_my_own_parked_car() {
        val mine = session("mine", "veh-A")
        val state = HomeState(
            activeSessions = listOf(mine),
            nearbySpots = listOf(
                spot("first"),
                provisionalTwinOf(mine),
                spot("gone", status = SpotStatus.RETRACTED),
                spot("last"),
            ),
            selection = HomeSelection.Spot("first"),
        )

        assertEquals(listOf("first", "last"), state.toPeekSlice().browsableSpotIds)
        assertEquals(PeekStep(null, "last"), state.toPeekSlice().spotStep)
    }

    @Test
    fun should_offer_no_step_from_a_withdrawn_spot_that_is_still_open() {
        val state = HomeState(
            nearbySpots = listOf(spot("gone", status = SpotStatus.RETRACTED), spot("other")),
            selection = HomeSelection.Spot("gone"),
        )

        assertEquals("gone", state.toPeekSlice().selectedSpot?.id, "it stays selected on purpose")
        assertEquals(PeekStep.None, state.toPeekSlice().spotStep)
    }

    // ── Car lane: the stepper walks VEHICLES, each resolving to ITS modal (session peek or
    // add-parking peek). [UI-PEEK-STEPS-WALK-VEHICLES-NOT-SESSIONS-001] ──────────────────────

    @Test
    fun should_step_between_parked_cars_in_the_order_the_vehicle_strip_lists_them() {
        val state = HomeState(
            vehicles = listOf(vehicle("veh-A"), vehicle("veh-B")),
            // veh-B parked more recently → it leads the strip, and the ‹ / › agree.
            activeSessions = listOf(session("s1", "veh-A", parkedAt = 1_000L), session("s2", "veh-B", parkedAt = 2_000L)),
        )
        val slice = state.toPeekSlice()

        assertEquals(listOf("veh-B", "veh-A"), slice.steppableVehicleIds)
        assertEquals(PeekStep(null, "veh-A"), slice.vehicleStep("veh-B"))
        assertEquals(PeekStep("veh-B", null), slice.vehicleStep("veh-A"))
    }

    @Test
    fun should_offer_the_unparked_vehicle_as_the_parked_peeks_neighbour() {
        val state = HomeState(
            vehicles = listOf(vehicle("veh-parked"), vehicle("veh-bare")),
            activeSessions = listOf(session("s1", "veh-parked")),
        )
        val slice = state.toPeekSlice()

        // Parked floats first in the strip; the bare car is one › away instead of a dead end.
        assertEquals(listOf("veh-parked", "veh-bare"), slice.steppableVehicleIds)
        assertEquals(PeekStep(null, "veh-bare"), slice.vehicleStep("veh-parked"))
        assertEquals(PeekStep("veh-parked", null), slice.vehicleStep("veh-bare"))
    }

    @Test
    fun should_step_between_vehicles_when_nothing_is_parked_at_all() {
        val state = HomeState(vehicles = listOf(vehicle("veh-A"), vehicle("veh-B")))

        assertEquals(PeekStep(null, "veh-B"), state.toPeekSlice().vehicleStep("veh-A"))
    }

    @Test
    fun should_offer_no_step_with_a_single_vehicle() {
        val state = HomeState(
            vehicles = listOf(vehicle("veh-A")),
            activeSessions = listOf(session("s1", "veh-A")),
        )
        assertEquals(PeekStep.None, state.toPeekSlice().vehicleStep("veh-A"))
    }

    /** Delete race: a session whose vehicle is gone is not a stop of the lane — its peek simply
     *  offers no arrows, like the withdrawn spot that stays open. */
    @Test
    fun should_offer_no_step_from_a_session_whose_vehicle_was_deleted() {
        val state = HomeState(
            vehicles = listOf(vehicle("veh-A")),
            activeSessions = listOf(session("s1", "veh-A"), session("orphan", "veh-gone")),
        )
        assertEquals(PeekStep.None, state.toPeekSlice().vehicleStep("veh-gone"))
    }

    // ── The open question's place. [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] ────────────

    @Test
    fun should_give_the_map_the_asked_about_place_when_a_question_is_open() {
        val carStop = GpsPoint(36.6119, -6.2805, accuracy = 9f, timestamp = 1_000L, speed = 0f)
        val state = HomeState(
            promptWindow = PendingPromptWindow(shownAtMs = 1_000L, vehicleName = "Kamiq", candidate = carStop),
        )

        assertEquals(
            carStop,
            state.toMapSlice().unconfirmedParking,
            "the marker must stand on the witnessed CAR stop, never on wherever the phone is now",
        )
    }

    @Test
    fun should_give_the_map_no_place_when_the_open_question_has_none() {
        // A window persisted by a build older than this field. "No place" is an answer; guessing
        // one would put the marker on the pedestrian, which is the whole bug.
        val state = HomeState(promptWindow = PendingPromptWindow(shownAtMs = 1_000L, vehicleName = "Kamiq"))

        assertNull(state.toMapSlice().unconfirmedParking)
    }

    @Test
    fun should_give_the_map_no_place_when_no_question_is_open() {
        assertNull(HomeState().toMapSlice().unconfirmedParking)
    }

    // ── The guided checklist's ONE gate [ONBOARDING-FIRST-STEPS-MUST-BE-READABLE-AND-FOUND-001] ──
    // Three surfaces read it: the card, the cold-start row standing down, and the sheet opening
    // itself. These tests are what stops the third from ever springing open on a checklist the
    // first two are not rendering.

    private fun newUser(
        dismissed: Boolean = false,
        hasCorePermissions: Boolean = true,
        vehicles: List<Vehicle> = listOf(vehicle("veh-A")),
    ) = HomeState(
        vehicles = vehicles,
        hasCorePermissions = hasCorePermissions,
        firstStepsDismissed = dismissed,
    )

    @Test
    fun should_show_the_checklist_to_a_new_user_with_a_car_and_permissions() {
        val slice = newUser().toBrowseListSlice()

        assertTrue(slice.showsFirstSteps)
        assertEquals(FirstStep.MARK_PARKING, slice.firstStepAnchor, "the sheet opens on step one")
    }

    @Test
    fun should_hide_the_checklist_when_the_user_skipped_it() {
        val slice = newUser(dismissed = true).toBrowseListSlice()

        assertFalse(slice.showsFirstSteps)
        assertNull(slice.firstStepAnchor, "a skipped checklist must not open the sheet either")
    }

    @Test
    fun should_hide_the_checklist_when_no_vehicle_is_registered() {
        // `DetectionStory.NoVehicle` owns this ask with the right CTA — the checklist would be
        // telling the user to park a car they have not registered.
        val slice = newUser(vehicles = emptyList()).toBrowseListSlice()

        assertFalse(slice.showsFirstSteps)
        assertNull(slice.firstStepAnchor)
    }

    @Test
    fun should_hide_the_checklist_without_core_permissions() {
        val slice = newUser(hasCorePermissions = false).toBrowseListSlice()

        assertFalse(slice.showsFirstSteps)
        assertNull(slice.firstStepAnchor)
    }

    @Test
    fun should_stop_offering_an_anchor_once_every_step_is_banked() {
        // All three done and NOT dismissed: the closing card is still on screen (it does not vanish
        // under the finger that finished it), but there is no step left to open the sheet for.
        val slice = newUser()
            .copy(firstStepsDone = FirstStep.entries.toSet())
            .toBrowseListSlice()

        assertTrue(slice.showsFirstSteps, "the closing card still shows")
        assertNull(slice.firstStepAnchor, "…and asks for nothing, so the sheet stays put")
    }

    @Test
    fun should_advance_the_anchor_to_the_next_step_once_the_car_is_marked() {
        // With detection healthy, marking the car also settles the watch step, so the next thing to
        // teach is the community one. Either way the anchor MOVED, which is what opens the sheet
        // again. [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
        val slice = newUser()
            .copy(activeSessions = listOf(session("s-1", "veh-A")))
            .toBrowseListSlice()

        assertEquals(
            FirstStep.FIND_SPOT,
            slice.firstStepAnchor,
            "a new step is new to teach — that is exactly when the sheet opens again",
        )
    }
}
