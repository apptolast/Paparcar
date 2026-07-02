package io.apptolast.paparcar.data.repository

import io.apptolast.paparcar.data.datasource.local.room.ZoneEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZoneReconcileTest {

    private fun z(
        id: String,
        name: String = "Home",
        updatedAt: Long = 0,
        pendingSync: Boolean = false,
    ) = ZoneEntity(
        id = id,
        userId = "u1",
        name = name,
        lat = 40.0,
        lon = -3.0,
        iconKey = "home",
        createdAt = 0,
        updatedAt = updatedAt,
        pendingSync = pendingSync,
    )

    @Test
    fun `pending local newer than remote is kept - offline edit not clobbered`() {
        val local = listOf(z("a", name = "Gym", updatedAt = 100, pendingSync = true))
        val remote = listOf(z("a", name = "Home", updatedAt = 50))

        val merged = reconcileZones(local, remote)

        assertEquals("Gym", merged.single().name)
        assertTrue(merged.single().pendingSync)
    }

    @Test
    fun `pending local is dropped once remote catches up - self heal`() {
        val local = listOf(z("a", name = "Gym", updatedAt = 100, pendingSync = true))
        val remote = listOf(z("a", name = "Gym", updatedAt = 100))

        val merged = reconcileZones(local, remote)

        assertFalse(merged.single().pendingSync)
    }

    @Test
    fun `clean local always takes remote`() {
        val local = listOf(z("a", name = "Gym", updatedAt = 100, pendingSync = false))
        val remote = listOf(z("a", name = "Home", updatedAt = 50))

        val merged = reconcileZones(local, remote)

        assertEquals("Home", merged.single().name)
    }

    @Test
    fun `local-only pending zone created offline is kept`() {
        val local = listOf(z("existing", updatedAt = 10), z("new", pendingSync = true, updatedAt = 100))
        val remote = listOf(z("existing", updatedAt = 10))

        val merged = reconcileZones(local, remote)

        assertEquals(setOf("existing", "new"), merged.map { it.id }.toSet())
    }

    @Test
    fun `clean local-only zone is dropped - remote deletion`() {
        val local = listOf(z("existing", updatedAt = 10), z("deleted", pendingSync = false))
        val remote = listOf(z("existing", updatedAt = 10))

        val merged = reconcileZones(local, remote)

        assertEquals(listOf("existing"), merged.map { it.id })
    }
}
