package io.apptolast.paparcar.data.mapper

import io.apptolast.paparcar.data.datasource.local.room.VehicleEntity
import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleColor
import io.apptolast.paparcar.domain.model.VehicleSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Guards the [VehicleColor] persistence parity across Room and Firestore mappers — the
 * exact pitfall the DTO-field-parity rule warns about: a new field silently lost on read.
 * [VEH-COLOR-001]
 */
class VehicleColorMapperTest {

    private fun vehicle(color: VehicleColor?) = Vehicle(
        id = "veh-1",
        userId = "user-1",
        brand = "Toyota",
        model = "Corolla",
        sizeCategory = VehicleSize.MEDIUM_SUV,
        carbodyType = CarbodyType.HATCHBACK_MEDIUM,
        color = color,
    )

    @Test
    fun should_preserveColor_when_roundTrippedThroughRoom() {
        val original = vehicle(VehicleColor.RED)
        val restored = original.toEntity().toDomain()
        assertEquals(VehicleColor.RED, restored.color)
    }

    @Test
    fun should_preserveColor_when_roundTrippedThroughFirestoreDto() {
        val original = vehicle(VehicleColor.NAVY)
        // Domain → Dto (write) → Entity (sync) → Domain (read)
        val restored = original.toDto().toEntity().toDomain()
        assertEquals(VehicleColor.NAVY, restored.color)
    }

    @Test
    fun should_serializeNullColorAsBlank_inDto() {
        assertEquals("", vehicle(null).toDto().color)
    }

    @Test
    fun should_resolveNullColor_when_dtoColorIsBlank() {
        val entity = vehicle(null).toDto().toEntity()
        assertNull(entity.color)
        assertNull(entity.toDomain().color)
    }

    @Test
    fun should_fallBackToNull_when_storedColorNameIsUnknown() {
        val entity = VehicleEntity(
            id = "veh-1",
            userId = "user-1",
            sizeCategory = VehicleSize.MEDIUM_SUV.name,
            color = "TEAL_THAT_NEVER_EXISTED",
        )
        assertNull(entity.toDomain().color)
    }

    @Test
    fun should_parseKnownNames_andRejectBlankOrUnknown() {
        assertEquals(VehicleColor.GOLD, VehicleColor.fromNameOrNull("GOLD"))
        assertNull(VehicleColor.fromNameOrNull(""))
        assertNull(VehicleColor.fromNameOrNull(null))
        assertNull(VehicleColor.fromNameOrNull("not-a-colour"))
    }
}
