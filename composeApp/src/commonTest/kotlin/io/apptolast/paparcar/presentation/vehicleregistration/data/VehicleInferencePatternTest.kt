package io.apptolast.paparcar.presentation.vehicleregistration.data

import io.apptolast.paparcar.domain.model.CarbodyType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers the catalog fallback path: when the (brand, model) pair is not in the
 * curated map, the pattern matcher should still classify it via keyword cues.
 */
class VehicleInferencePatternTest {

    @Test
    fun should_inferPickup_when_modelMatchesHilux() {
        assertEquals(CarbodyType.PICKUP, VehicleCatalog.inferBodyType("Toyota", "Hilux Invincible"))
    }

    @Test
    fun should_inferPickup_when_modelMatchesRanger() {
        assertEquals(CarbodyType.PICKUP, VehicleCatalog.inferBodyType("Ford", "Ranger Raptor"))
    }

    @Test
    fun should_inferCommercialVan_when_modelMatchesVitoOrTransporter() {
        assertEquals(CarbodyType.VAN_COMMERCIAL, VehicleCatalog.inferBodyType("Mercedes", "Vito Tourer"))
        assertEquals(CarbodyType.VAN_COMMERCIAL, VehicleCatalog.inferBodyType("Volkswagen", "Transporter T6.1"))
    }

    @Test
    fun should_inferLightVan_when_modelMatchesKangooOrBerlingo() {
        assertEquals(CarbodyType.VAN_LIGHT, VehicleCatalog.inferBodyType("Renault", "Kangoo Combi"))
        // Berlingo is also in the exact catalog; both paths must agree.
        assertEquals(CarbodyType.VAN_LIGHT, VehicleCatalog.inferBodyType("Citroën", "Berlingo"))
    }

    @Test
    fun should_inferEstate_when_modelHasAvantOrTouring() {
        assertEquals(CarbodyType.FAMILY_LONG, VehicleCatalog.inferBodyType("Audi", "A4 Avant"))
        assertEquals(CarbodyType.FAMILY_LONG, VehicleCatalog.inferBodyType("BMW", "Serie 3 Touring"))
    }

    @Test
    fun should_returnNull_when_noPatternMatches() {
        assertNull(VehicleCatalog.inferBodyType("MarcaInexistente", "ModeloSinPista"))
    }

    @Test
    fun should_preferExactMatch_over_patternFallback() {
        // "Golf" matches the medium-hatchback pattern AND lives in the exact catalog.
        // The exact map must always win.
        assertEquals(CarbodyType.HATCHBACK_MEDIUM, VehicleCatalog.inferBodyType("Volkswagen", "Golf"))
    }
}
