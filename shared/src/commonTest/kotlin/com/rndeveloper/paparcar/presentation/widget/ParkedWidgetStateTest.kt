package com.rndeveloper.paparcar.presentation.widget

import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ParkedWidgetStateTest {

    private fun vehicle(id: String, name: String) = Vehicle(
        id = id,
        userId = "user-1",
        name = name,
        sizeCategory = VehicleSize.MEDIUM_SUV,
    )

    private fun session(
        id: String,
        vehicleId: String?,
        timestamp: Long,
        street: String? = "Calle Mayor",
        isActive: Boolean = true,
    ) = UserParking(
        id = id,
        userId = "user-1",
        vehicleId = vehicleId,
        location = GpsPoint(40.4, -3.7, 8f, timestamp, 0f),
        isActive = isActive,
        address = street?.let { AddressInfo(street = it, city = "Madrid", region = null, country = "ES") },
    )

    @Test
    fun should_resolve_empty_when_nothing_is_parked() {
        assertEquals(ParkedWidgetState.Empty, resolveParkedWidgetState(emptyList(), emptyList()))
    }

    @Test
    fun should_resolve_the_single_parked_vehicle_with_its_street_and_time() {
        val state = resolveParkedWidgetState(
            sessions = listOf(session("s1", "v1", timestamp = 1_000L)),
            vehicles = listOf(vehicle("v1", "Mi Golf")),
        )

        val parked = assertIs<ParkedWidgetState.Parked>(state)
        assertEquals("s1", parked.subject.sessionId, "the release ask needs to name the session [WIDGET-ACTIONS-002]")
        assertEquals("Mi Golf", parked.subject.vehicleName)
        assertEquals("Calle Mayor", parked.subject.street)
        assertEquals(1_000L, parked.subject.parkedAtMs)
        assertEquals(0, parked.othersCount)
    }

    @Test
    fun should_show_null_street_while_enrichment_has_not_landed() {
        val state = resolveParkedWidgetState(
            sessions = listOf(session("s1", "v1", timestamp = 1_000L, street = null)),
            vehicles = listOf(vehicle("v1", "Mi Golf")),
        )

        assertNull(assertIs<ParkedWidgetState.Parked>(state).subject.street)
    }

    @Test
    fun should_pick_the_most_recent_session_and_count_the_rest() {
        val state = resolveParkedWidgetState(
            sessions = listOf(
                session("s-old", "v1", timestamp = 1_000L),
                session("s-new", "v2", timestamp = 9_000L),
            ),
            vehicles = listOf(vehicle("v1", "Golf"), vehicle("v2", "Kamiq")),
        )

        val parked = assertIs<ParkedWidgetState.Parked>(state)
        assertEquals("Kamiq", parked.subject.vehicleName)
        assertEquals(1, parked.othersCount)
    }

    @Test
    fun should_skip_a_session_whose_vehicle_was_deleted() {
        val state = resolveParkedWidgetState(
            sessions = listOf(
                session("s-ghost", "v-deleted", timestamp = 9_000L),
                session("s-real", "v1", timestamp = 1_000L),
            ),
            vehicles = listOf(vehicle("v1", "Golf")),
        )

        val parked = assertIs<ParkedWidgetState.Parked>(state)
        assertEquals("Golf", parked.subject.vehicleName)
        assertEquals(0, parked.othersCount)
    }

    @Test
    fun should_ignore_inactive_sessions() {
        val state = resolveParkedWidgetState(
            sessions = listOf(session("s1", "v1", timestamp = 1_000L, isActive = false)),
            vehicles = listOf(vehicle("v1", "Golf")),
        )

        assertEquals(ParkedWidgetState.Empty, state)
    }

    // ── A trip is not an empty widget ─────────────────────────────────────────
    // [WIDGET-A-TRIP-IS-NOT-AN-EMPTY-WIDGET-001]

    @Test
    fun should_say_the_car_is_on_the_way_instead_of_asking_to_mark_it() {
        // The bug this closes: a trip STARTS by releasing the previous session, so with no third
        // state the widget fell to Empty — "park and this widget will show where" — during the whole
        // drive, which is the one moment the user cannot act on it.
        val state = resolveParkedWidgetState(
            sessions = emptyList(),
            vehicles = listOf(vehicle("v1", "Kamiq")),
            isDriving = true,
            drivingVehicleId = "v1",
        )

        val driving = assertIs<ParkedWidgetState.Driving>(state)
        assertEquals("Kamiq", driving.vehicleName)
        assertEquals(false, driving.isCandidate)
    }

    @Test
    fun should_let_a_live_trip_outrank_a_car_parked_elsewhere() {
        // Same hierarchy as the Browse peek and the vehicles strip: what is happening now wins.
        // [UI-BROWSE-DRIVING-OVER-PARKED-001]
        val state = resolveParkedWidgetState(
            sessions = listOf(session("s1", "v1", timestamp = 5_000L)),
            vehicles = listOf(vehicle("v1", "Golf"), vehicle("v2", "Kamiq")),
            isDriving = true,
            drivingVehicleId = "v2",
        )

        assertEquals("Kamiq", assertIs<ParkedWidgetState.Driving>(state).vehicleName)
    }

    @Test
    fun should_name_no_car_when_the_trip_was_armed_without_a_known_origin() {
        // A manual arm has no departing vehicle. The widget says the trip, not a guessed car.
        val state = resolveParkedWidgetState(
            sessions = emptyList(),
            vehicles = listOf(vehicle("v1", "Golf")),
            isDriving = true,
            drivingVehicleId = null,
        )

        assertNull(assertIs<ParkedWidgetState.Driving>(state).vehicleName)
    }

    @Test
    fun should_carry_the_candidate_phase_so_the_widget_stops_claiming_movement() {
        val state = resolveParkedWidgetState(
            sessions = emptyList(),
            vehicles = listOf(vehicle("v1", "Golf")),
            isDriving = true,
            drivingVehicleId = "v1",
            isCandidate = true,
        )

        assertEquals(true, assertIs<ParkedWidgetState.Driving>(state).isCandidate)
    }

    @Test
    fun should_go_back_to_the_parked_car_once_the_trip_ends() {
        val state = resolveParkedWidgetState(
            sessions = listOf(session("s1", "v1", timestamp = 1_000L)),
            vehicles = listOf(vehicle("v1", "Golf")),
            isDriving = false,
            drivingVehicleId = "v1",
        )

        assertEquals("Golf", assertIs<ParkedWidgetState.Parked>(state).subject.vehicleName)
    }

    @Test
    fun should_pick_the_same_car_home_would_when_two_are_parked_at_the_same_instant() {
        // The reason the local `maxByOrNull { timestamp }` had to go: on an exact tie it returned
        // whichever came first in the list, while Home breaks the tie by watch rank. Two surfaces
        // answering "which car is the subject" on their own is two chances to name different cars.
        // [UI-PREFERRED-SESSION-RECENCY-001]
        val watched = vehicle("v-bt", "Kamiq").copy(bluetoothDeviceId = "AA:BB:CC:DD:EE:FF")
        val unwatched = vehicle("v-off", "Golf").copy(isActive = false)
        val sessions = listOf(
            session("s-off", "v-off", timestamp = 1_000L),
            session("s-bt", "v-bt", timestamp = 1_000L),
        )

        val state = resolveParkedWidgetState(sessions, listOf(unwatched, watched))

        val parked = assertIs<ParkedWidgetState.Parked>(state)
        assertEquals(
            "Kamiq",
            parked.subject.vehicleName,
            "the shared preference breaks a timestamp tie by watch rank — the widget must not re-derive it",
        )
        assertEquals(1, parked.othersCount)
    }

    // ── D5 · un widget alto lista los coches ──────────────────────────────────

    @Test
    fun should_list_every_parked_car_in_the_same_order_home_uses() {
        // El widget declara resizeMode vertical desde el dia uno: estirado, la lista es lo que
        // rellena ese alto. El ORDEN no se re-deriva aqui — sale del mismo comparador que la tira
        // de Home, o la primera fila del widget y el primer chip de Home serian coches distintos.
        val state = resolveParkedWidgetState(
            sessions = listOf(
                session("s-old", "v1", timestamp = 1_000L),
                session("s-new", "v2", timestamp = 9_000L),
            ),
            vehicles = listOf(vehicle("v1", "Golf"), vehicle("v2", "Kamiq")),
        )

        val parked = assertIs<ParkedWidgetState.Parked>(state)
        assertEquals(listOf("Kamiq", "Golf"), parked.cars.map { it.vehicleName })
        assertEquals(parked.cars.first(), parked.subject, "el sujeto ES la primera fila, no otra eleccion")
    }

    @Test
    fun should_keep_showing_the_other_parked_car_while_one_drives() {
        // Solo posible con mas de un coche, que es Plus [FREE-LIMITS-001]. Conducir uno no es razon
        // para esconder donde esta el otro: el alto da sitio para las dos cosas.
        val state = resolveParkedWidgetState(
            sessions = listOf(session("s1", "v1", timestamp = 5_000L)),
            vehicles = listOf(vehicle("v1", "Golf"), vehicle("v2", "Kamiq")),
            isDriving = true,
            drivingVehicleId = "v2",
        )

        val driving = assertIs<ParkedWidgetState.Driving>(state)
        assertEquals("Kamiq", driving.vehicleName)
        assertEquals(listOf("Golf"), driving.alsoParked.map { it.vehicleName })
    }

    @Test
    fun should_carry_each_cars_own_coordinates_so_a_row_never_navigates_to_another_car() {
        // Cada fila lleva SUS acciones. Si las coordenadas fueran las del sujeto, la segunda fila
        // te llevaria al primer coche -- el fallo silencioso de una lista con una sola accion.
        val state = resolveParkedWidgetState(
            sessions = listOf(
                session("s1", "v1", timestamp = 9_000L),
                session("s2", "v2", timestamp = 1_000L),
            ),
            vehicles = listOf(vehicle("v1", "Golf"), vehicle("v2", "Kamiq")),
        )

        val parked = assertIs<ParkedWidgetState.Parked>(state)
        assertEquals(listOf("s1", "s2"), parked.cars.map { it.sessionId })
    }
}
