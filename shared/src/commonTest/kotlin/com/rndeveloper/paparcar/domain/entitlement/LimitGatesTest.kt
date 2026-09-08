package com.rndeveloper.paparcar.domain.entitlement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two count gates of FREE-LIMITS-001. Same downgrade doctrine as the history gate: the gate
 * blocks what is NEW, never what exists.
 */
class LimitGatesTest {

    // ── Vehicles: free keeps ONE own car ─────────────────────────────────────

    @Test
    fun `should_allowAddingTheFirstCar_when_free`() {
        assertTrue(canAddOwnVehicle(ownedCount = 0, isPlus = false))
    }

    @Test
    fun `should_blockTheSecondCar_when_freeAtTheLimit`() {
        assertEquals(1, FREE_OWN_VEHICLE_LIMIT)
        assertFalse(canAddOwnVehicle(ownedCount = 1, isPlus = false))
    }

    @Test
    fun `should_allowAnyCar_when_plus`() {
        assertTrue(canAddOwnVehicle(ownedCount = 7, isPlus = true))
    }

    @Test
    fun `should_stillBlockAdding_when_freeAboveTheLimit`() {
        // Cars created under Plus survive a downgrade — but adding MORE stays gated.
        assertFalse(canAddOwnVehicle(ownedCount = 3, isPlus = false))
    }

    // ── Zones: free keeps TWO ────────────────────────────────────────────────

    @Test
    fun `should_allowCreatingBelowTheLimit_when_free`() {
        assertTrue(canCreateZone(zoneCount = 0, isPlus = false))
        assertTrue(canCreateZone(zoneCount = 1, isPlus = false))
    }

    @Test
    fun `should_blockTheThirdZone_when_freeAtTheLimit`() {
        assertEquals(2, FREE_ZONE_LIMIT)
        assertFalse(canCreateZone(zoneCount = 2, isPlus = false))
    }

    @Test
    fun `should_allowAnyZone_when_plus`() {
        assertTrue(canCreateZone(zoneCount = 9, isPlus = true))
    }
}
