package com.rndeveloper.paparcar.presentation.home

import com.rndeveloper.paparcar.domain.detection.DetectionPhase
import com.rndeveloper.paparcar.domain.detection.PendingPromptWindow
import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.DetectionReadiness
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Money
import com.rndeveloper.paparcar.domain.model.PlaceCategory
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.SpotStatus
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.presentation.home.model.HomeParkingsSlice
import com.rndeveloper.paparcar.domain.usecase.parkings.NearbyPublicParking
import com.rndeveloper.paparcar.domain.parkings.PublicParkingKind
import com.rndeveloper.paparcar.domain.parkings.PublicParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleMonitoringStatus
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.domain.permissions.RequiredPermission
import com.rndeveloper.paparcar.presentation.home.model.HomeNearbyLens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.rndeveloper.paparcar.presentation.home.model.PublicParkingFilter

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
        fuelType: FuelType? = null,
    ) = Vehicle(
        id = id,
        userId = "user-1",
        brand = "Toyota",
        model = "Corolla",
        sizeCategory = VehicleSize.MEDIUM_SUV,
        isActive = isActive,
        bluetoothDeviceId = bluetoothDeviceId,
        fuelType = fuelType,
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

    /** Minimal car park: its EXISTENCE for the lane counts, its distance for the stepper order. */
    private fun nearbyParking(id: String, dist: Double = 100.0) = NearbyPublicParking(
        parking = PublicParking(
            id = id,
            name = null,
            operator = null,
            kind = PublicParkingKind.SURFACE,
            latitude = 1.0,
            longitude = 1.0,
        ),
        distanceMeters = dist,
    )

    // ── The expanded header speaks for the LANE ───────────────────────────────
    // [HOME-THE-WATCH-COMES-FIRST-AND-THE-HEADER-FOLLOWS-THE-LANE-001]

    @Test
    fun should_countTheSpots_when_theLaneIsSpots() {
        val state = HomeState(nearbySpots = listOf(spot("a"), spot("b")))
        assertEquals(2, state.toPeekSlice().laneCount, "free spots are a subscription, never unasked")
    }

    @Test
    fun should_countTheCarParks_when_theirLaneWasAsked() {
        val state = HomeState(
            // The spots are still there underneath. If the header followed THEM the user would read
            // "3" over a list of two car parks — the very drift this ticket exists to remove.
            nearbySpots = listOf(spot("a"), spot("b"), spot("c")),
            nearbyLens = HomeNearbyLens.PARKINGS,
            parkings = HomeParkingsSlice(
                parkings = listOf(nearbyParking("p1"), nearbyParking("p2")),
                anchorLat = 1.0,
                anchorLon = 1.0,
            ),
        )
        assertEquals(2, state.toPeekSlice().laneCount)
    }

    @Test
    fun should_countNothing_when_theComplementsLaneWasNeverAsked() {
        // ⛔ THE assertion of this block: null, not 0. A complement has no count until its lane has
        // been entered, and a zero nobody measured is a verdict about the world.
        // [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
        val state = HomeState(nearbySpots = listOf(spot("a")), nearbyLens = HomeNearbyLens.FUEL)
        assertNull(state.toPeekSlice().laneCount)
    }

    @Test
    fun should_countZero_when_theLaneWasAskedAndFoundNothing() {
        // The other half of the same rule: once asked, an honest 0 is exactly what to show.
        val state = HomeState(
            nearbyLens = HomeNearbyLens.PARKINGS,
            parkings = HomeParkingsSlice(parkings = emptyList(), anchorLat = 1.0, anchorLon = 1.0),
        )
        assertEquals(0, state.toPeekSlice().laneCount)
    }

    @Test
    fun should_paintTheSameCarParksThatTheLaneLists_when_returningToHome() {
        // One store, one projection: the rows and the pins cannot be about two areas.
        // [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
        val state = HomeState(
            nearbyLens = HomeNearbyLens.PARKINGS,
            parkings = HomeParkingsSlice(parkings = listOf(nearbyParking("lane"), nearbyParking("camera"))),
        )

        assertEquals(
            state.toBrowseListSlice().parkings.parkings.map { it.parking.id },
            state.toMapSlice().publicParkings.map { it.parking.id },
        )
    }

    // ── Qué encuadra la cámara al cambiar de carril [HOME-THE-CAMERA-FRAMES-WHAT-THE-LIST-SHOWS-001] ──

    @Test
    fun should_frameTheFilteredFirstRow_when_aFilterHidesTheNearestCarPark() {
        // ⛔ EL TESTIGO. `laneFirstPoint` leía la lista CRUDA mientras la hoja pintaba la filtrada,
        // así que con «Covered» puesto la cámara volaba a un parking que la lista no enseñaba — y al
        // quitar el filtro, aparecía. Reportado en device (issue #87).
        // ⛔ Coordenadas DISTINTAS a propósito: con las mismas, el assert de abajo pasaría también
        // con la regla vieja y el test no probaría nada.
        val surface = nearbyParking("surface", dist = 50.0)
        val covered = nearbyParking("covered", dist = 900.0)
            .let {
                it.copy(
                    parking = it.parking.copy(
                        kind = PublicParkingKind.UNDERGROUND,
                        latitude = 2.0,
                        longitude = 2.0,
                    ),
                )
            }
        val state = HomeState(
            nearbyLens = HomeNearbyLens.PARKINGS,
            parkingFilter = PublicParkingFilter.COVERED,
            parkings = HomeParkingsSlice(
                parkings = listOf(surface, covered),
                anchorLat = 1.0,
                anchorLon = 1.0,
            ),
        )

        val framed = state.laneFirstPoint(cameraLat = 1.0, cameraLon = 1.0)
        val shown = state.toBrowseListSlice().parkings.parkings.first()

        assertEquals(shown.parking.latitude to shown.parking.longitude, framed)
        assertEquals(2.0 to 2.0, framed, "encuadra la cubierta (2.0), no la de superficie (1.0)")
        assertEquals(
            "covered",
            state.publicParkingsShown.first().parking.id,
            "la primera fila que la hoja pinta es la cubierta, no la más cercana",
        )
    }

    @Test
    fun should_frameNothing_when_theAnswerIsAboutSomewhereElse() {
        // ⛔ Buscas Madrid estando en Italia: la lista todavía contesta de Italia, y encuadrar su
        // primer elemento devolvía la cámara de donde el usuario acababa de sacarla. Se espera a la
        // respuesta de aquí, que el carril ya está pidiendo.
        val state = HomeState(
            nearbyLens = HomeNearbyLens.PARKINGS,
            parkings = HomeParkingsSlice(
                parkings = listOf(nearbyParking("in-italy")),
                anchorLat = 41.9028,
                anchorLon = 12.4964,
            ),
        )

        assertNull(
            state.laneFirstPoint(cameraLat = 40.4168, cameraLon = -3.7038),
            "una respuesta sobre Roma no encuadra una cámara puesta en Madrid",
        )
        assertNotNull(
            state.laneFirstPoint(cameraLat = 41.9028, cameraLon = 12.4964),
            "…y la misma respuesta sí encuadra cuando la cámara sigue en Roma",
        )
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

    // The accuracy rides in the FABS slice, not the header's: the pill is drawn in the map's FAB
    // band, and the band is the visibility it has to follow — the header hides itself for every pin
    // mode, which is when a bad fix matters most. [UI-A-WARNING-COSTS-NOTHING-WHEN-SILENT-001]
    @Test
    fun should_project_gps_accuracy_into_fabs_slice() {
        assertNull(HomeState().toFabsSlice().gpsAccuracy)
        assertEquals(10f, HomeState(userGpsPoint = gps).toFabsSlice().gpsAccuracy)
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
        assertEquals(FirstStep.MARK_PARKING.name, slice.firstStepsCue, "the sheet opens on step one")
    }

    @Test
    fun should_hide_the_checklist_when_the_user_skipped_it() {
        val slice = newUser(dismissed = true).toBrowseListSlice()

        assertFalse(slice.showsFirstSteps)
        assertNull(slice.firstStepsCue, "a skipped checklist must not open the sheet either")
    }

    @Test
    fun should_hide_the_checklist_when_no_vehicle_is_registered() {
        // `DetectionStory.NoVehicle` owns this ask with the right CTA — the checklist would be
        // telling the user to park a car they have not registered.
        val slice = newUser(vehicles = emptyList()).toBrowseListSlice()

        assertFalse(slice.showsFirstSteps)
        assertNull(slice.firstStepsCue)
    }

    @Test
    fun should_keep_the_checklist_without_core_permissions() {
        // ⛔ Se escondía, sobre la premisa de que sin ubicación la app no puede hacer ninguna de las
        // tres cosas que el checklist enseña. Falso en las tres: marcar el aparcamiento arranca en
        // la cámara, el carril responde por el sitio que miras, y los dos pasos que abrían un flujo
        // ahora solo SEÑALAN dónde está el control. Quedaba escondido justo para quien no lo ha
        // visto nunca. [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001]
        val slice = newUser(hasCorePermissions = false).toBrowseListSlice()

        assertTrue(slice.showsFirstSteps)
        assertNotNull(slice.firstStepsCue)
    }

    @Test
    fun should_stop_offering_an_anchor_once_every_step_is_banked() {
        // All three done and NOT dismissed: the closing card is still on screen (it does not vanish
        // under the finger that finished it), but there is no step left to open the sheet for.
        val slice = newUser()
            .copy(firstStepsDone = FirstStep.entries.toSet())
            .toBrowseListSlice()

        assertTrue(slice.showsFirstSteps, "the closing card still shows")
        // [ONBOARDING-THE-COMMUNITY-STEP-CANNOT-DEMAND-A-SPOT-001] A finished checklist still has
        // something to show — its closing card — and coming back from a step's explanation that
        // FINISHED it used to land on a closed sheet, hiding the tutorial at the very moment the
        // user completed it.
        assertEquals(FIRST_STEPS_CLOSING_CUE, slice.firstStepsCue)
    }

    @Test
    fun should_advance_the_anchor_to_the_next_step_once_the_car_is_marked() {
        // Marking the car makes the private-garage step the next thing to teach (it comes before the
        // watch step). Either way the anchor MOVED, which is what opens the sheet again.
        // [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] [FIRST-STEPS-A-PRIVATE-GARAGE-IS-A-PRIVATE-ZONE-001]
        val slice = newUser()
            .copy(activeSessions = listOf(session("s-1", "veh-A")))
            .toBrowseListSlice()

        assertEquals(
            FirstStep.PRIVATE_ZONE.name,
            slice.firstStepsCue,
            "a new step is new to teach — that is exactly when the sheet opens again",
        )
    }

    // ── A live trip names its place ───────────────────────────────────────────
    // [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001]

    private fun address(street: String?, city: String? = "Madrid", place: String? = null) =
        AddressAndPlace(
            address = AddressInfo(street = street, city = city, region = "Madrid", country = "España"),
            placeInfo = place?.let { PlaceInfo(name = it, category = PlaceCategory.OTHER) },
        )

    private fun driving(state: HomeState) = state.copy(
        drivingMeta = DrivingMeta(vehicleId = "veh-A", phase = DetectionPhase.Driving),
    )

    @Test
    fun should_not_name_a_place_when_no_trip_is_live() {
        // The field costs nothing outside a drive — which is what keeps the browse list from
        // re-emitting every time the user's own geocode lands.
        val state = HomeState(userAddressAndPlace = address(street = "Gran Vía 28"))

        assertNull(state.toBrowseListSlice().drivingPlace)
        assertNull(state.toPeekSlice().drivingPlace)
        assertNull(state.toPeekSlice().drivingSecondaryLine)
    }

    @Test
    fun should_name_the_trip_from_the_cars_own_geocode_and_never_from_the_camera() {
        // The bug this closes: with the camera panned away, the peek used to name the CAMERA's
        // street and put the car's name next to it. Subject decides. [UI-SHEET-004]
        val state = driving(
            HomeState(
                userAddressAndPlace = address(street = "Gran Vía 28"),
                cameraAddressAndPlace = address(street = "Calle de Alcalá 100", city = "Barcelona"),
            ),
        )

        assertEquals("Gran Vía 28", state.toBrowseListSlice().drivingPlace?.line)
        assertEquals("Gran Vía 28", state.toPeekSlice().drivingPlace?.line)
    }

    @Test
    fun should_prefer_the_place_name_over_the_street_and_keep_the_street_underneath() {
        val state = driving(HomeState(userAddressAndPlace = address(street = "Gran Vía 28", place = "El Corte Inglés")))
        val peek = state.toPeekSlice()

        assertEquals("El Corte Inglés", peek.drivingPlace?.line)
        assertEquals("Gran Vía 28", peek.drivingSecondaryLine, "the street explains the POI, it does not repeat it")
    }

    @Test
    fun should_stay_silent_when_the_geocode_says_nothing_usable() {
        // No place → the chip and the card say just "En ruta". A footer that spends its only line
        // announcing that we do not know is worse than one that says nothing. Note every field is
        // null: [AddressInfo.displayLine] falls back street → city → region → country, so a geocode
        // that only resolved the province still HAS a line, and saying "Madrid" while driving
        // through it is true.
        val empty = AddressAndPlace(
            address = AddressInfo(street = null, city = null, region = null, country = null),
            placeInfo = null,
        )
        val state = driving(HomeState(userAddressAndPlace = empty))

        assertNull(state.toBrowseListSlice().drivingPlace)
        assertNull(state.toPeekSlice().drivingPlace)
    }

    @Test
    fun should_not_re_emit_the_list_slice_while_the_street_stays_the_same() {
        // The whole reason the slice carries a RESOLVED line instead of the AddressAndPlace: driving
        // down one avenue re-geocodes every ~11 m, and every one of those would otherwise recompose
        // the vehicles strip, the spots feed and the detection surface.
        val first = driving(HomeState(userAddressAndPlace = address(street = "Gran Vía 28", city = "Madrid")))
        val second = driving(HomeState(userAddressAndPlace = address(street = "Gran Vía 28", city = "MADRID")))

        assertEquals(first.toBrowseListSlice(), second.toBrowseListSlice())
    }

    // ── La sección de plazas se pliega sola sobre un vacío de verdad ─────────
    // [FUEL-STATIONS-MAP-AND-PEEK-001]

    @Test
    fun should_openOnSpots_andPaintNoComplementOnTheMap_when_nobodyHasChosenALens() {
        // The lane a cold start opens on, whatever the data says. The rule that used to live here
        // folded the spots section on a true empty so the refuel section would sit one row below it —
        // a rule that only existed because the subjects were stacked. Nothing is stacked now.
        val fresh = HomeState(isLoading = false)
        assertEquals(HomeNearbyLens.SPOTS, fresh.nearbyLens)
        assertEquals(false, fresh.showsFuelOnMap)
        assertEquals(false, fresh.showsParkingsOnMap)
    }

    @Test
    fun should_paintOnlyTheLanesOwnComplement_when_theLensMoves() {
        // ⛔ Never both: two label layers over the spot pins is what the single lens prevents.
        val onFuel = HomeState(nearbyLens = HomeNearbyLens.FUEL)
        assertEquals(true, onFuel.showsFuelOnMap)
        assertEquals(false, onFuel.showsParkingsOnMap)

        val onParkings = HomeState(nearbyLens = HomeNearbyLens.PARKINGS)
        assertEquals(false, onParkings.showsFuelOnMap)
        assertEquals(true, onParkings.showsParkingsOnMap)
    }

    // ── El stepper del peek de gasolinera camina el orden de la lista ────────
    // [FUEL-PEEK-STEPS-BETWEEN-STATIONS-001]

    private fun fuelStation(id: String, dist: Double) =
        com.rndeveloper.paparcar.domain.usecase.fuel.NearbyFuelStation(
            station = com.rndeveloper.paparcar.domain.fuel.FuelStation(
                id = id, name = id, address = null, town = null,
                latitude = 40.0, longitude = -3.0, prices = emptyMap(),
            ),
            distanceMeters = dist,
            stationKey = "test|$id",
            source = "test",
            price = null,
        )

    @Test
    fun should_paintWhatTheLaneLists_when_theLaneIsOnFuel_andNothingWhenItIsNot() {
        // One store, one projection: the capsules are the rows, in the rows' order.
        // [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001] [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001]
        val state = HomeState(
            nearbyLens = HomeNearbyLens.FUEL,
            fuel = com.rndeveloper.paparcar.presentation.home.model.HomeFuelSlice(
                stations = listOf(fuelStation("c", 300.0), fuelStation("a", 100.0), fuelStation("b", 200.0)),
            ),
        )
        assertEquals(listOf("a", "b", "c"), state.toMapSlice().fuelStations.map { it.station.id })
        assertEquals(
            state.toBrowseListSlice().fuel.stations.map { it.station.id },
            state.toMapSlice().fuelStations.map { it.station.id },
        )

        // ⛔ And nothing at all once the lane is elsewhere, however full the stores are: the map shows
        // the complement you are LOOKING AT, never two label layers stacked over the spot pins.
        assertEquals(
            emptyList(),
            state.copy(nearbyLens = HomeNearbyLens.SPOTS).toMapSlice().fuelStations,
        )
    }

    // ── El precio de una cápsula es el del coche que conduces ────────────────
    // [MAP-A-PIN-IS-PRICED-FOR-THE-CAR-YOU-DRIVE-001]

    /** A forecourt that charges for BOTH fuels, which is the normal case in every feed. */
    private fun pricedStation(id: String, dist: Double, gasoline: Double, diesel: Double) =
        com.rndeveloper.paparcar.domain.usecase.fuel.NearbyFuelStation(
            station = com.rndeveloper.paparcar.domain.fuel.FuelStation(
                id = id, name = id, address = null, town = null,
                latitude = 40.0, longitude = -3.0,
                prices = mapOf(
                    FuelType.GASOLINE to com.rndeveloper.paparcar.domain.fuel.StationPrice(
                        Money(gasoline, "EUR"),
                    ),
                    FuelType.DIESEL to com.rndeveloper.paparcar.domain.fuel.StationPrice(
                        Money(diesel, "EUR"),
                    ),
                ),
            ),
            distanceMeters = dist,
            stationKey = "test|$id",
            source = "test",
            // ⛔ Null on purpose: this is the copy the LAYER holds, and the layer is asked fuel-less.
            price = null,
        )

    private fun carBurning(fuel: FuelType) = vehicle("v1", isActive = true).copy(fuelType = fuel)

    @Test
    fun should_priceTheCapsule_when_theStoreWasAskedFuelLess() {
        // The device report: rows with a price, capsules without one — the copy on the map had been
        // asked with no fuel. The price is resolved from the forecourt at projection time, whatever
        // the request that fetched it. [MAP-A-PIN-IS-PRICED-FOR-THE-CAR-YOU-DRIVE-001]
        val state = HomeState(
            nearbyLens = HomeNearbyLens.FUEL,
            vehicles = listOf(carBurning(FuelType.GASOLINE)),
            fuel = com.rndeveloper.paparcar.presentation.home.model.HomeFuelSlice(
                stations = listOf(pricedStation("a", 100.0, gasoline = 1.529, diesel = 1.419)),
            ),
        )

        val map = state.toMapSlice()
        assertEquals(listOf(1.529), map.fuelStations.map { it.price?.perUnit?.amount })
        assertEquals(FuelType.GASOLINE, map.fuelPricesFor, "the figure and its subject share a source")
        // The peek and the selection read the same repriced list, not a fourth opinion.
        assertEquals(
            1.529,
            state.copy(selection = HomeSelection.FuelStation("a")).selectedFuelStation?.price?.perUnit?.amount,
        )
    }

    @Test
    fun should_quoteTheNewCarsFuel_when_theActiveVehicleChanges_withoutAskingAgain() {
        // ⛔ Changing car does not refetch anything, so the map must requote from the forecourt's own
        // prices or it would go on quoting the old fuel.
        val stores = HomeState(
            nearbyLens = HomeNearbyLens.FUEL,
            fuel = com.rndeveloper.paparcar.presentation.home.model.HomeFuelSlice(
                stations = listOf(pricedStation("a", 100.0, gasoline = 1.529, diesel = 1.419)),
            ),
        )

        assertEquals(
            listOf(1.529),
            stores.copy(vehicles = listOf(carBurning(FuelType.GASOLINE))).toMapSlice()
                .fuelStations.map { it.price?.perUnit?.amount },
        )
        assertEquals(
            listOf(1.419),
            stores.copy(vehicles = listOf(carBurning(FuelType.DIESEL))).toMapSlice()
                .fuelStations.map { it.price?.perUnit?.amount },
        )
    }

    @Test
    fun should_quotePetrolAndSayPetrol_when_theActiveCarIsAHybrid() {
        // Measured on device in Berlin: the capsule and the row went blank while the peek quoted
        // both pumps, because `prices[HYBRID]` matches nothing any feed emits. The label must move
        // with the figure — «Híbrido» over a petrol price is the pairing the capsule exists to
        // prevent. [FUEL-A-HYBRID-FILLS-UP-WITH-PETROL-001]
        val state = HomeState(
            nearbyLens = HomeNearbyLens.FUEL,
            vehicles = listOf(carBurning(FuelType.HYBRID)),
            fuel = com.rndeveloper.paparcar.presentation.home.model.HomeFuelSlice(
                stations = listOf(pricedStation("a", 100.0, gasoline = 2.309, diesel = 2.399)),
            ),
        )

        val map = state.toMapSlice()
        assertEquals(listOf(2.309), map.fuelStations.map { it.price?.perUnit?.amount })
        assertEquals(FuelType.GASOLINE, map.fuelPricesFor, "the figure and its subject share a source")
        // The list reads the same store, so the row cannot disagree with the capsule.
        assertEquals(listOf(2.309), state.fuelStationsShown.map { it.price?.perUnit?.amount })
    }

    @Test
    fun should_showTheGlyphAlone_when_nobodyDeclaredWhatTheCarBurns() {
        // No fuel, no figure — and no invented zero. The stations still show: where to refuel is a
        // fair question whatever the car burns.
        val state = HomeState(
            nearbyLens = HomeNearbyLens.FUEL,
            vehicles = listOf(vehicle("v1", isActive = true)),
            fuel = com.rndeveloper.paparcar.presentation.home.model.HomeFuelSlice(
                stations = listOf(pricedStation("a", 100.0, gasoline = 1.529, diesel = 1.419)),
            ),
        )

        val map = state.toMapSlice()
        assertEquals(1, map.fuelStations.size)
        assertNull(map.fuelStations.single().price)
        assertNull(map.fuelPricesFor)
    }

    @Test
    fun should_leaveAnUnpricedStationUnpriced_when_theSourceCarriesNoPrices() {
        // OSM answers stations and never prices. Repricing must not invent one for them.
        val state = HomeState(
            nearbyLens = HomeNearbyLens.FUEL,
            vehicles = listOf(carBurning(FuelType.DIESEL)),
            fuel = com.rndeveloper.paparcar.presentation.home.model.HomeFuelSlice(
                stations = listOf(fuelStation("osm-1", 100.0)),
            ),
        )

        assertNull(state.toMapSlice().fuelStations.single().price)
    }

    @Test
    fun should_stepBetweenStations_inTheNearestFirstOrder() {
        val state = HomeState(
            fuel = com.rndeveloper.paparcar.presentation.home.model.HomeFuelSlice(
                stations = listOf(fuelStation("a", 100.0), fuelStation("b", 200.0), fuelStation("c", 300.0)),
            ),
            selection = HomeSelection.FuelStation("b"),
        )
        assertEquals(PeekStep(prevId = "a", nextId = "c"), state.toPeekSlice().fuelStationStep)
    }

    @Test
    fun should_offerNoStep_when_theStationLeftTheList_orSitsAtAnEdge() {
        val two = com.rndeveloper.paparcar.presentation.home.model.HomeFuelSlice(
            stations = listOf(fuelStation("a", 100.0), fuelStation("b", 200.0)),
        )
        // First station: nothing before it. A refreshed-away id yields None, never invented edges.
        assertEquals(
            PeekStep(prevId = null, nextId = "b"),
            HomeState(fuel = two, selection = HomeSelection.FuelStation("a")).toPeekSlice().fuelStationStep,
        )
        assertEquals(
            PeekStep.None,
            HomeState(fuel = two, selection = HomeSelection.FuelStation("gone")).toPeekSlice().fuelStationStep,
        )
    }

    // ── El stepper del peek de parking camina el orden de la lista ──────────
    // [CARPARK-DETAIL-HAS-PREV-NEXT-CLOSE-001]

    @Test
    fun should_stepBetweenCarParks_inTheNearestFirstOrder() {
        // Stored out of order on purpose: the stepper walks the LIST's order, not the store's.
        val state = HomeState(
            parkings = HomeParkingsSlice(
                parkings = listOf(nearbyParking("c", 300.0), nearbyParking("a", 100.0), nearbyParking("b", 200.0)),
            ),
            selection = HomeSelection.PublicParking("b"),
        )
        assertEquals(PeekStep(prevId = "a", nextId = "c"), state.toPeekSlice().publicParkingStep)
    }

    @Test
    fun should_offerNoCarParkStep_when_itLeftTheList_orSitsAtAnEdge() {
        val two = HomeParkingsSlice(parkings = listOf(nearbyParking("a", 100.0), nearbyParking("b", 200.0)))
        assertEquals(
            PeekStep(prevId = "a", nextId = null),
            HomeState(parkings = two, selection = HomeSelection.PublicParking("b")).toPeekSlice().publicParkingStep,
        )
        assertEquals(
            PeekStep.None,
            HomeState(parkings = two, selection = HomeSelection.PublicParking("gone")).toPeekSlice().publicParkingStep,
        )
    }

    @Test
    fun should_carryTheLens_intoTheSheetSlice() {
        // The sheet renders the lane the state names; it does not re-decide it.
        assertEquals(
            HomeNearbyLens.PARKINGS,
            HomeState(nearbyLens = HomeNearbyLens.PARKINGS).toBrowseListSlice().nearbyLens,
        )
    }

    // ── Sin ubicación: aviso, nunca bloqueo ──────────────────────────────────
    // [HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001]
    // El aviso viaja ahora en el slice de los FABs, que es donde se dibuja: la píldora del mapa.
    // [UI-NO-LOCATION-WEARS-THE-ACCURACY-PILL-001]

    @Test
    fun should_showTheLocationWarning_when_locationIsMissing() {
        val blocked = HomeState(
            hasCorePermissions = true,
            detectionReadiness = DetectionReadiness.Blocked(setOf(RequiredPermission.FOREGROUND_LOCATION)),
        )
        // Permiso concedido y los servicios apagados: el bloqueo de la vigilancia es la ÚNICA vía
        // por la que la app se entera de que el GPS está off.
        assertTrue(blocked.toFabsSlice().locationMissing)
    }

    @Test
    fun should_showTheLocationWarning_when_thePermissionIsMissingAndThereIsNoCar() {
        // ⛔ La píldora leía el bloqueo de la VIGILANCIA, que no existe sin coche registrado — así
        // que el usuario que acababa de pulsar «No permitir» y aún no tenía coche veía un Home que
        // nunca decía por qué no sabía dónde estaba. Es justo la persona a la que va la frase.
        // [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001]
        //
        // «Acababa de pulsar No permitir» ahora se DECLARA (`locationEverAsked`): la frase es para
        // quien ya contestó, no para quien tiene el diálogo delante.
        // [HOME-A-PENDING-ASK-IS-NOT-A-REFUSAL-001]
        assertTrue(HomeState(locationEverAsked = true).toFabsSlice().locationMissing)
    }

    @Test
    fun should_notShowTheLocationWarning_when_theFirstEntryAskIsStillUnanswered() {
        // El diálogo del sistema está encima pidiendo justo eso, y el remedio que ofrecería la
        // píldora —abrir los permisos— es ese mismo diálogo. [HOME-A-PENDING-ASK-IS-NOT-A-REFUSAL-001]
        assertFalse(
            HomeState(locationEverAsked = true, locationAnswerInFlight = true)
                .toFabsSlice().locationMissing,
        )
        // Y antes incluso de que el diálogo salga: la pregunta se dispara con el mapa, y hasta
        // entonces la pref todavía no está estampada.
        assertFalse(HomeState().toFabsSlice().locationMissing)
    }

    @Test
    fun should_showTheLocationWarning_when_servicesAreOffAndNoAskIsComing() {
        // ⛔ Testigo de que el silencio no se come al usuario que CONCEDIÓ el permiso y luego apagó
        // los servicios: a ese no se le preguntó nunca, así que su `locationEverAsked` es false para
        // siempre. [HOME-A-PENDING-ASK-IS-NOT-A-REFUSAL-001]
        val servicesOff = HomeState(
            hasCorePermissions = true,
            locationEverAsked = false,
            detectionReadiness = DetectionReadiness.Blocked(setOf(RequiredPermission.FOREGROUND_LOCATION)),
        )
        assertTrue(servicesOff.toFabsSlice().locationMissing)
    }

    @Test
    fun should_notShowTheLocationWarning_when_onlyProducerPermissionsAreMissing() {
        // Background location / activity recognition is the detection upsell, not "no location":
        // the map stays clean and the list's calm row sells it.
        val producerOnly = HomeState(
            hasCorePermissions = true,
            detectionReadiness = DetectionReadiness.Blocked(setOf(RequiredPermission.BACKGROUND_LOCATION)),
        )
        assertFalse(producerOnly.toFabsSlice().locationMissing)
        // Ni con todo concedido y nada bloqueado.
        assertFalse(HomeState(hasCorePermissions = true).toFabsSlice().locationMissing)
    }

    // ── Qué combustible cotiza el carril [FUEL-THE-LANE-IS-FILTERED-BY-WHAT-YOU-BURN-001] ──
    // El default era la bandera `isActive`, y marcar no es conducir: quien tiene un diésel de
    // diario y un GLP por Bluetooth veía el carril cotizado por el que dejó marcado hace un mes.

    @Test
    fun should_quote_the_fuel_of_the_most_recently_used_vehicle_not_the_flagged_one() {
        val flagged = vehicle("veh-flagged", isActive = true, fuelType = FuelType.LPG_CNG)
        val recent = vehicle("veh-recent", fuelType = FuelType.DIESEL)
        val state = HomeState(
            vehicles = listOf(flagged, recent),
            // La sesión más reciente es la del segundo coche: ése es el que se ha usado.
            activeSessions = listOf(session("s-recent", "veh-recent", parkedAt = 9_000L)),
        )

        assertEquals(
            FuelType.DIESEL,
            state.pricedFuelType,
            "el carril cotiza por el coche usado, no por el que lleva la bandera",
        )
    }

    @Test
    fun should_skip_a_vehicle_that_burns_nothing_when_quoting_the_lane() {
        // El eléctrico es el más reciente, pero ninguna gasolinera vende electricidad: quedarse en
        // él dejaría el carril cotizando un producto que nadie sirve.
        val ev = vehicle("veh-ev", fuelType = FuelType.ELECTRIC)
        val petrol = vehicle("veh-petrol", fuelType = FuelType.GASOLINE)
        val state = HomeState(
            vehicles = listOf(ev, petrol),
            activeSessions = listOf(session("s-ev", "veh-ev", parkedAt = 9_000L)),
        )

        assertEquals(FuelType.GASOLINE, state.pricedFuelType)
    }

    @Test
    fun should_keep_quoting_something_when_every_vehicle_is_electric() {
        // Sin nada mejor que ofrecer se conserva el comportamiento de siempre en vez de dejar la
        // cifra sin sujeto.
        val state = HomeState(vehicles = listOf(vehicle("veh-ev", fuelType = FuelType.ELECTRIC)))

        assertEquals(FuelType.ELECTRIC, state.pricedFuelType)
    }

    @Test
    fun should_quote_the_pump_product_of_a_hybrid_not_its_powertrain() {
        // Lo que un híbrido echa es gasolina, y los feeds se indexan por lo que vende el surtidor.
        val state = HomeState(vehicles = listOf(vehicle("veh-hev", fuelType = FuelType.HYBRID)))

        assertEquals(FuelType.GASOLINE, state.pricedFuelType)
    }

    @Test
    fun should_let_the_chosen_product_outrank_every_vehicle() {
        // El chip de producto es justo el caso de uso de la issue #42: mirar el GLP sin cambiar de
        // coche activo.
        val state = HomeState(
            vehicles = listOf(vehicle("veh-diesel", fuelType = FuelType.DIESEL)),
            fuelProduct = FuelType.LPG_CNG,
        )

        assertEquals(FuelType.LPG_CNG, state.pricedFuelType)
    }
}
