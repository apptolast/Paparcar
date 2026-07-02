package io.apptolast.paparcar.data.repository

import io.apptolast.paparcar.data.datasource.local.room.VehicleEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VehicleReconcileTest {

    private fun v(
        id: String,
        isActive: Boolean = false,
        updatedAt: Long = 0,
        pendingSync: Boolean = false,
        licensePlate: String? = null,
    ) = VehicleEntity(
        id = id,
        userId = "u1",
        sizeCategory = "MEDIUM_SUV",
        isActive = isActive,
        updatedAt = updatedAt,
        pendingSync = pendingSync,
        licensePlate = licensePlate,
    )

    @Test
    fun `pending local newer than remote is kept - offline edit not clobbered`() {
        // The core bug: change active vehicle offline (pending, newer) → restart → sync reads a stale
        // server snapshot. The local choice must survive.
        val local = listOf(v("a", isActive = true, updatedAt = 100, pendingSync = true))
        val remote = listOf(v("a", isActive = false, updatedAt = 50))

        val merged = reconcileVehicles(local, remote)

        assertEquals(1, merged.size)
        assertTrue(merged.single().isActive)
        assertTrue(merged.single().pendingSync)
    }

    @Test
    fun `pending local is dropped once remote catches up - self heal`() {
        val local = listOf(v("a", isActive = true, updatedAt = 100, pendingSync = true))
        val remote = listOf(v("a", isActive = true, updatedAt = 100)) // server now reflects the edit

        val merged = reconcileVehicles(local, remote)

        assertEquals(1, merged.size)
        assertTrue(merged.single().isActive)
        assertFalse(merged.single().pendingSync) // taken from remote → clean again
    }

    @Test
    fun `clean local always takes remote`() {
        val local = listOf(v("a", isActive = true, updatedAt = 100, pendingSync = false))
        val remote = listOf(v("a", isActive = false, updatedAt = 50))

        val merged = reconcileVehicles(local, remote)

        assertFalse(merged.single().isActive) // remote authoritative for clean rows
    }

    @Test
    fun `local-only pending row created offline is kept`() {
        val local = listOf(v("existing", updatedAt = 10), v("new", pendingSync = true, updatedAt = 100))
        val remote = listOf(v("existing", updatedAt = 10))

        val merged = reconcileVehicles(local, remote)

        assertEquals(setOf("existing", "new"), merged.map { it.id }.toSet())
    }

    @Test
    fun `clean local-only row is dropped - remote deletion`() {
        val local = listOf(v("existing", updatedAt = 10), v("deleted", pendingSync = false))
        val remote = listOf(v("existing", updatedAt = 10))

        val merged = reconcileVehicles(local, remote)

        assertEquals(listOf("existing"), merged.map { it.id })
    }

    @Test
    fun `on-device-only fields survive taking remote`() {
        val local = listOf(v("a", licensePlate = "1234-ABC", updatedAt = 10))
        val remote = listOf(v("a", licensePlate = null, updatedAt = 20)) // remote never carries the plate

        val merged = reconcileVehicles(local, remote)

        assertEquals("1234-ABC", merged.single().licensePlate)
    }
}
