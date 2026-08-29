package com.rndeveloper.paparcar.data.mapper

import com.rndeveloper.paparcar.data.datasource.remote.dto.SpotDto
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.SpotStatus
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.model.VehicleSize
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
            sizeCategory = "MICRO_SMALL",
            enRouteCount = 3,
            expiresAt = 1_000_000L,
        )

        val domain = dto.toDomain()

        assertEquals(SpotType.MANUAL_REPORT, domain.type)
        assertEquals(0.65f, domain.confidence)
        assertEquals(VehicleSize.MICRO_SMALL, domain.sizeCategory)
        assertEquals(3, domain.enRouteCount)
        assertEquals(1_000_000L, domain.expiresAt)
    }

    @Test
    fun `toDomain_should_notDecayConfidenceWithAge_when_spotIsNearlyExpired`() {
        // [SPOT-FRESHNESS-IS-AGE-NOT-A-COUNTDOWN-001] confidence used to be multiplied by a
        // timeFactor that fell to zero at expiry, so one Float answered both "do people believe
        // this report" and "how old is it". Age is SpotFreshness's job now; a spot one millisecond
        // from the sweep still carries the community's full opinion of it.
        val almostGone = buildDto(
            confidence = 0.8f,
            reportedAt = 1_000L,
            expiresAt = 1_001L,
        )

        assertEquals(0.8f, almostGone.toDomain().confidence)
    }

    @Test
    fun `toDomain_should_useVoteRatio_when_enoughSignalsExist`() {
        // Three votes is the threshold at which the community outranks the stored value.
        val voted = buildDto(confidence = 1f, acceptCount = 3, rejectCount = 0)

        // Laplace-smoothed: (3 + 1) / (3 + 2) = 0.8
        assertEquals(0.8f, voted.toDomain().confidence)
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
            sizeCategory = VehicleSize.VAN_HIGH,
            enRouteCount = 2,
            expiresAt = 9_999_999L,
        )

        val dto = spot.toDto()

        assertEquals("MANUAL_REPORT", dto.type)
        assertEquals(0.5f, dto.confidence)
        assertEquals("VAN_HIGH", dto.sizeCategory)
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

    // ── Status [DET-HANDOFF-NOT-MANUAL-001 §B.3] ──────────────────────────────

    @Test
    fun `status_should_surviveEveryHop_dto_domain_entity`() {
        // The parity that matters: a status that drops anywhere on this path silently turns a
        // withdrawn report back into an offer.
        val dto = buildDto(status = "RETRACTED")

        assertEquals(SpotStatus.RETRACTED, dto.toDomain().status, "wire → domain")
        assertEquals("RETRACTED", buildSpot(status = SpotStatus.RETRACTED).toDto().status, "domain → wire")
        assertEquals("RETRACTED", dto.toEntity().status, "wire → cache")
        assertEquals(SpotStatus.RETRACTED, dto.toEntity().toDomain().status, "cache → domain")
    }

    @Test
    fun `status_should_readAsConfirmed_for_everyDocumentWrittenBeforeTheFieldExisted`() {
        // Those spots were published on witnessed departures, which is exactly what CONFIRMED means.
        assertEquals(SpotStatus.CONFIRMED, buildDto().toDomain().status)
        assertEquals(SpotStatus.CONFIRMED, buildDto(status = "SOMETHING_ELSE").toDomain().status)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildDto(
        type: String = "AUTO_DETECTED",
        confidence: Float = 1f,
        sizeCategory: String? = null,
        enRouteCount: Int = 0,
        expiresAt: Long = 0L,
        status: String = "CONFIRMED",
        reportedAt: Long = 0L,
        acceptCount: Int = 0,
        rejectCount: Int = 0,
    ) = SpotDto(
        id = "spot-test",
        latitude = 40.416775,
        longitude = -3.703790,
        accuracy = 0f,
        reportedAt = reportedAt,
        reportedBy = "user-test",
        speed = 0f,
        type = type,
        confidence = confidence,
        sizeCategory = sizeCategory,
        enRouteCount = enRouteCount,
        expiresAt = expiresAt,
        status = status,
        acceptCount = acceptCount,
        rejectCount = rejectCount,
    )

    private fun buildSpot(
        type: SpotType = SpotType.AUTO_DETECTED,
        confidence: Float = 1f,
        sizeCategory: VehicleSize? = null,
        enRouteCount: Int = 0,
        expiresAt: Long = 0L,
        status: SpotStatus = SpotStatus.CONFIRMED,
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
        status = status,
    )
}
