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
        assertEquals("Mi Golf", parked.vehicleName)
        assertEquals("Calle Mayor", parked.street)
        assertEquals(1_000L, parked.parkedAtMs)
        assertEquals(0, parked.othersCount)
    }

    @Test
    fun should_show_null_street_while_enrichment_has_not_landed() {
        val state = resolveParkedWidgetState(
            sessions = listOf(session("s1", "v1", timestamp = 1_000L, street = null)),
            vehicles = listOf(vehicle("v1", "Mi Golf")),
        )

        assertNull(assertIs<ParkedWidgetState.Parked>(state).street)
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
        assertEquals("Kamiq", parked.vehicleName)
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
        assertEquals("Golf", parked.vehicleName)
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
}
