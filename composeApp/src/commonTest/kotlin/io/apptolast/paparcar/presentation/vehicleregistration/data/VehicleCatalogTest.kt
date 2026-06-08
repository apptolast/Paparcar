package io.apptolast.paparcar.presentation.vehicleregistration.data

import io.apptolast.paparcar.domain.model.VehicleSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VehicleCatalogTest {

    @Test
    fun should_returnCorrectSize_when_knownBrandAndModel() {
        assertEquals(VehicleSize.MEDIUM_SUV, VehicleCatalog.inferSize("Dacia", "Sandero"))
        assertEquals(VehicleSize.MEDIUM_SUV, VehicleCatalog.inferSize("Dacia", "Logan"))
        assertEquals(VehicleSize.MICRO_SMALL, VehicleCatalog.inferSize("Dacia", "Spring"))
        assertEquals(VehicleSize.MEDIUM_SUV, VehicleCatalog.inferSize("Volkswagen", "Golf"))
        assertEquals(VehicleSize.MICRO_SMALL, VehicleCatalog.inferSize("Volkswagen", "Polo"))
        assertEquals(VehicleSize.LARGE_SEDAN, VehicleCatalog.inferSize("Volkswagen", "Tiguan"))
    }

    @Test
    fun should_returnNull_when_unknownModel() {
        assertNull(VehicleCatalog.inferSize("Dacia", "Otro"))
        assertNull(VehicleCatalog.inferSize("Volkswagen", "Otro"))
    }

    @Test
    fun should_fallBackToPatternMatch_when_unknownBrandButKnownModel() {
        // New bidimensional catalog falls back to keyword patterns when the brand is
        // unknown — "golf" still maps to a medium hatchback. [VEHICLE-CATEGORIZATION-001]
        assertEquals(VehicleSize.MEDIUM_SUV, VehicleCatalog.inferSize("MarcaInventada", "Golf"))
    }

    @Test
    fun should_returnNull_when_brandAndModelHaveNoPatternMatch() {
        assertNull(VehicleCatalog.inferSize("MarcaInventada", "ModeloSinPista"))
    }

    @Test
    fun should_notContainSmall_when_cSegmentCars() {
        // C-segment hatchbacks and compact crossovers should never be SMALL
        val cSegment = listOf(
            "Audi" to "A3",
            "BMW" to "Serie 1",
            "Cupra" to "Born",
            "Cupra" to "Leon",
            "Mercedes" to "Clase A",
            "Mercedes" to "GLA",
            "Mazda" to "CX-3",
            "Peugeot" to "2008",
            "Seat" to "Arona",
        )
        cSegment.forEach { (brand, model) ->
            val size = VehicleCatalog.inferSize(brand, model)
            assertNotNull(size, "$brand $model should have a mapped size")
            assertTrue(
                size != VehicleSize.MICRO_SMALL,
                "$brand $model ($size) should not be SMALL — it is a C-segment or crossover vehicle",
            )
        }
    }

    @Test
    fun should_returnSmall_when_cityCarOrMicroSegment() {
        val cityCars = listOf(
            "Fiat" to "500",
            "Fiat" to "Panda",
            "Dacia" to "Spring",
            "Toyota" to "Aygo X",
            "Toyota" to "Yaris",
            "Mazda" to "2",
            "Suzuki" to "Swift",
        )
        cityCars.forEach { (brand, model) ->
            assertEquals(
                VehicleSize.MICRO_SMALL,
                VehicleCatalog.inferSize(brand, model),
                "$brand $model should be SMALL",
            )
        }
    }

    @Test
    fun should_containAllModels_when_checkingCatalogCompleteness() {
        // Every model in the catalog must have a size mapping
        val brands = VehicleCatalog.brands()
        assertTrue(brands.isNotEmpty())
        brands.forEach { brand ->
            val models = VehicleCatalog.modelsFor(brand)
            assertTrue(models.isNotEmpty(), "$brand should have at least one model")
            models.forEach { model ->
                assertNotNull(
                    VehicleCatalog.inferSize(brand, model),
                    "$brand $model is in the catalog but has no size mapping",
                )
            }
        }
    }

    @Test
    fun should_returnVan_when_utilityVehicles() {
        assertEquals(VehicleSize.VAN_HIGH, VehicleCatalog.inferSize("Citroën", "Berlingo"))
        assertEquals(VehicleSize.VAN_HIGH, VehicleCatalog.inferSize("Ford", "Transit"))
        assertEquals(VehicleSize.VAN_HIGH, VehicleCatalog.inferSize("Mercedes", "Vito"))
    }
}
