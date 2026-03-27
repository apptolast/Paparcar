package io.apptolast.paparcar.data.mapper

import io.apptolast.paparcar.data.datasource.local.room.UserProfileEntity
import io.apptolast.paparcar.data.datasource.remote.dto.UserProfileDto
import io.apptolast.paparcar.domain.model.UserProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserProfileMapperTest {

    private val dto = UserProfileDto(
        userId = "u1",
        email = "a@b.com",
        displayName = "Alice",
        photoUrl = "https://photo.url",
        createdAt = 1000L,
        updatedAt = 2000L,
    )

    private val entity = UserProfileEntity(
        userId = "u1",
        email = "a@b.com",
        displayName = "Alice",
        photoUrl = "https://photo.url",
        createdAt = 1000L,
        updatedAt = 2000L,
    )

    private val domain = UserProfile(
        userId = "u1",
        email = "a@b.com",
        displayName = "Alice",
        photoUrl = "https://photo.url",
        createdAt = 1000L,
        updatedAt = 2000L,
    )

    // ── Dto → Entity ──────────────────────────────────────────────────────────

    @Test
    fun `dto toEntity maps all fields`() {
        val result = dto.toEntity()
        assertEquals(dto.userId, result.userId)
        assertEquals(dto.email, result.email)
        assertEquals(dto.displayName, result.displayName)
        assertEquals(dto.photoUrl, result.photoUrl)
        assertEquals(dto.createdAt, result.createdAt)
        assertEquals(dto.updatedAt, result.updatedAt)
    }

    @Test
    fun `dto toEntity preserves null nullable fields`() {
        val sparse = dto.copy(email = null, photoUrl = null)
        val result = sparse.toEntity()
        assertNull(result.email)
        assertNull(result.photoUrl)
    }

    // ── Dto → Domain ──────────────────────────────────────────────────────────

    @Test
    fun `dto toDomain maps all fields`() {
        val result = dto.toDomain()
        assertEquals(dto.userId, result.userId)
        assertEquals(dto.displayName, result.displayName)
        assertEquals(dto.createdAt, result.createdAt)
    }

    // ── Entity → Domain ───────────────────────────────────────────────────────

    @Test
    fun `entity toDomain maps all fields`() {
        val result = entity.toDomain()
        assertEquals(entity.userId, result.userId)
        assertEquals(entity.email, result.email)
        assertEquals(entity.displayName, result.displayName)
        assertEquals(entity.updatedAt, result.updatedAt)
    }

    @Test
    fun `entity toDomain preserves null nullable fields`() {
        val sparse = entity.copy(email = null, displayName = null, photoUrl = null)
        val result = sparse.toDomain()
        assertNull(result.email)
        assertNull(result.displayName)
        assertNull(result.photoUrl)
    }

    // ── Domain → Dto ──────────────────────────────────────────────────────────

    @Test
    fun `domain toDto round-trips correctly`() {
        val result = domain.toDto()
        assertEquals(domain.userId, result.userId)
        assertEquals(domain.email, result.email)
        assertEquals(domain.createdAt, result.createdAt)
        assertEquals(domain.updatedAt, result.updatedAt)
    }
}
