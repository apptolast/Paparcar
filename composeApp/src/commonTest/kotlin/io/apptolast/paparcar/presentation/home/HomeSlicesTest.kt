package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
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

    private fun spot(id: String, size: VehicleSize? = null) = Spot(
        id = id,
        location = gps,
        reportedBy = "user-1",
        sizeCategory = size,
    )

    private fun vehicle(id: String) = Vehicle(
        id = id,
        userId = "user-1",
        brand = "Toyota",
        model = "Corolla",
        sizeCategory = VehicleSize.MEDIUM_SUV,
    )

    private fun session(id: String, vehicleId: String) = UserParking(
        id = id,
        userId = "user-1",
        vehicleId = vehicleId,
        location = gps,
        isActive = true,
    )

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
            selectedItemId = "spot-1",
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
            selectedItemId = "s1",
        )
        val peek = state.toPeekSlice()
        assertEquals("s1", peek.selectedSession?.id)
        assertNull(peek.selectedSpot)
        assertTrue(peek.isParkingSelected)
        assertEquals("s1", peek.userParking?.id)
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

        val full = HomeState(
            activeSessions = listOf(session("s1", "veh-A")),
            userGpsPoint = gps,
            selectedItemId = "s1",
        ).toFabsSlice()
        assertTrue(full.hasActiveParking)
        assertTrue(full.hasGpsFix)
        assertTrue(full.isParkingSelected)
    }

    @Test
    fun should_project_gps_accuracy_into_header_slice() {
        assertNull(HomeState().toHeaderSlice().gpsAccuracy)
        assertEquals(10f, HomeState(userGpsPoint = gps).toHeaderSlice().gpsAccuracy)
    }
}
