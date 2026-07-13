package io.apptolast.paparcar.domain.vehicle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class VehicleActiveStatePolicyTest {

    private data class V(val id: String, val active: Boolean)

    private fun normalize(items: List<V>) =
        VehicleActiveStatePolicy.normalizeSingleActive(
            items = items,
            isActive = { it.active },
            deactivate = { it.copy(active = false) },
        )

    // ── normalizeSingleActive ────────────────────────────────────────────────

    @Test
    fun `empty list is returned unchanged`() {
        val items = emptyList<V>()
        assertSame(items, normalize(items))
    }

    @Test
    fun `no active item is returned unchanged`() {
        val items = listOf(V("a", false), V("b", false))
        assertSame(items, normalize(items))
    }

    @Test
    fun `single active item is returned unchanged`() {
        val items = listOf(V("a", false), V("b", true), V("c", false))
        assertSame(items, normalize(items))
    }

    @Test
    fun `multiple active keeps the first and deactivates the rest`() {
        val result = normalize(listOf(V("a", true), V("b", true), V("c", true)))
        assertEquals(listOf(V("a", true), V("b", false), V("c", false)), result)
        assertEquals(1, result.count { it.active })
    }

    @Test
    fun `multiple active preserves order and only flips later actives`() {
        val result = normalize(listOf(V("a", false), V("b", true), V("c", false), V("d", true)))
        assertEquals(
            listOf(V("a", false), V("b", true), V("c", false), V("d", false)),
            result,
        )
    }

    @Test
    fun `the kept active is the first by insertion order`() {
        val result = normalize(listOf(V("x", true), V("y", true)))
        assertEquals("x", result.single { it.active }.id)
    }

    // ── promotionTarget ──────────────────────────────────────────────────────

    @Test
    fun `promotionTarget is null when nothing remains`() {
        assertNull(VehicleActiveStatePolicy.promotionTarget(emptyList()))
    }

    @Test
    fun `promotionTarget is the first remaining id`() {
        assertEquals("v1", VehicleActiveStatePolicy.promotionTarget(listOf("v1", "v2", "v3")))
    }

    // ── shouldBeActiveOnSave [AUDIT-M11-001] ─────────────────────────────────

    @Test
    fun `new first vehicle becomes active`() {
        assertEquals(true, VehicleActiveStatePolicy.shouldBeActiveOnSave(isEditing = false, existingIsActive = false, userHasVehicles = false))
    }

    @Test
    fun `new vehicle when others exist does not steal active`() {
        assertEquals(false, VehicleActiveStatePolicy.shouldBeActiveOnSave(isEditing = false, existingIsActive = false, userHasVehicles = true))
    }

    @Test
    fun `editing preserves the current active flag`() {
        assertEquals(true, VehicleActiveStatePolicy.shouldBeActiveOnSave(isEditing = true, existingIsActive = true, userHasVehicles = true))
        assertEquals(false, VehicleActiveStatePolicy.shouldBeActiveOnSave(isEditing = true, existingIsActive = false, userHasVehicles = true))
    }
}
