package io.apptolast.paparcar.data.mapper

import io.apptolast.paparcar.data.datasource.remote.dto.SpotDto
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.VehicleSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpotDtoMapperTest {

    // ── toDomain ──────────────────────────────────────────────────────────────

    @Test
    fun `toDomain_should_mapAllPhase4Fields`() {
        val dto = buildDto(
            type = "MANUAL_REPORT",
            confidence = 0.65f,
            sizeCategory = "SMALL",
            enRouteCount = 3,
            expiresAt = 1_000_000L,
        )

        val domain = dto.toDomain()

        assertEquals(SpotType.MANUAL_REPORT, domain.type)
        assertEquals(0.65f, domain.confidence)
        assertEquals(VehicleSize.SMALL, domain.sizeCategory)
        assertEquals(3, domain.enRouteCount)
        assertEquals(1_000_000L, domain.expiresAt)
    }

    @Test
    fun `toDomain_should_defaultToAutoDetected_for_unknownType`() {
        val domain = buildDto(type = "INVALID_TYPE").toDomain()

        assertEquals(SpotType.AUTO_DETECTED, domain.type)
    }

    @Test
    fun `toDomain_should_clampConfidence_when_above1`() {
        val domain = buildDto(confidence = 1.5f).toDomain()

        assertEquals(1f, domain.confidence)
    }

    @Test
    fun `toDomain_should_clampConfidence_when_negative`() {
        val domain = buildDto(confidence = -0.5f).toDomain()

        assertEquals(0f, domain.confidence)
    }

    @Test
    fun `toDomain_should_returnNullSizeCategory_for_unknownSize`() {
        val domain = buildDto(sizeCategory = "BICYCLE").toDomain()

        assertNull(domain.sizeCategory)
    }

    @Test
    fun `toDomain_should_returnNullSizeCategory_when_fieldAbsent`() {
        val domain = buildDto(sizeCategory = null).toDomain()

        assertNull(domain.sizeCategory)
    }

    @Test
    fun `toDomain_should_clampEnRouteCount_when_negative`() {
        val domain = buildDto(enRouteCount = -1).toDomain()

        assertEquals(0, domain.enRouteCount)
    }

    // ── toDto ─────────────────────────────────────────────────────────────────

    @Test
    fun `toDto_should_serializeAllPhase4Fields`() {
        val spot = buildSpot(
            type = SpotType.MANUAL_REPORT,
            confidence = 0.5f,
            sizeCategory = VehicleSize.VAN,
            enRouteCount = 2,
            expiresAt = 9_999_999L,
        )

        val dto = spot.toDto()

        assertEquals("MANUAL_REPORT", dto.type)
        assertEquals(0.5f, dto.confidence)
        assertEquals("VAN", dto.sizeCategory)
        assertEquals(2, dto.enRouteCount)
        assertEquals(9_999_999L, dto.expiresAt)
    }

    @Test
    fun `toDto_should_setNullSizeCategory_when_none`() {
        val dto = buildSpot(sizeCategory = null).toDto()

        assertNull(dto.sizeCategory)
    }

    @Test
    fun `toDto_should_serializeTypeAsName`() {
        assertEquals("AUTO_DETECTED", buildSpot(type = SpotType.AUTO_DETECTED).toDto().type)
        assertEquals("MANUAL_REPORT", buildSpot(type = SpotType.MANUAL_REPORT).toDto().type)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildDto(
        type: String = "AUTO_DETECTED",
        confidence: Float = 1f,
        sizeCategory: String? = null,
        enRouteCount: Int = 0,
        expiresAt: Long = 0L,
    ) = SpotDto(
        id = "spot-test",
        latitude = 40.416775,
        longitude = -3.703790,
        accuracy = 0f,
        reportedAt = 0L,
        reportedBy = "user-test",
        speed = 0f,
        type = type,
        confidence = confidence,
        sizeCategory = sizeCategory,
        enRouteCount = enRouteCount,
        expiresAt = expiresAt,
    )

    private fun buildSpot(
        type: SpotType = SpotType.AUTO_DETECTED,
        confidence: Float = 1f,
        sizeCategory: VehicleSize? = null,
        enRouteCount: Int = 0,
        expiresAt: Long = 0L,
    ) = Spot(
        id = "spot-test",
        location = GpsPoint(
            latitude = 40.416775,
            longitude = -3.703790,
            accuracy = 0f,
            timestamp = 0L,
            speed = 0f,
        ),
        reportedBy = "user-test",
        type = type,
        confidence = confidence,
        sizeCategory = sizeCategory,
        enRouteCount = enRouteCount,
        expiresAt = expiresAt,
    )
}
