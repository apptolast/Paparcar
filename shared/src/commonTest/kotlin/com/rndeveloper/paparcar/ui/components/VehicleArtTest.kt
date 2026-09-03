package com.rndeveloper.paparcar.ui.components

import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.VehicleSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards the one fact the three vehicle renderers used to each invent for themselves: *a motorcycle
 * is not a car with a missing carbody.*
 * [UI-A-MOTORCYCLE-IS-DRAWN-LIKE-EVERY-OTHER-VEHICLE-001]
 */
class VehicleArtTest {

    @Test
    fun `should_resolveTheTwoWheelerArt_when_sizeIsMotorcycle`() {
        assertEquals(VehicleArt.TwoWheeler, vehicleArtOf(carbody = null, size = VehicleSize.MOTORCYCLE))
    }

    /**
     * The registration hero passes `defaultCarbody = HATCHBACK_MEDIUM`, and the old resolution order
     * let that car default win over a size that already said MOTORCYCLE — so the screen drew a car
     * while the user was registering a motorbike. Every carbody is tried, not just that one: a
     * default is a default whatever the call site picked.
     */
    @Test
    fun `should_keepTheTwoWheelerArt_when_aCallSiteOffersAnyCarDefault`() {
        CarbodyType.entries.forEach { default ->
            assertEquals(
                VehicleArt.TwoWheeler,
                vehicleArtOf(carbody = null, size = VehicleSize.MOTORCYCLE, defaultCarbody = default),
                "a $default default must not dress a two-wheeler as a car",
            )
        }
    }

    /** Population witness: nobody adds a carbody without drawing both of its pictograms. */
    @Test
    fun `should_drawEveryCarbody_when_walkingTheWholeTaxonomy`() {
        assertTrue(CarbodyType.entries.isNotEmpty(), "the taxonomy is empty — this test proves nothing")
        CarbodyType.entries.forEach { body ->
            val art = assertNotNull(vehicleArtOf(carbody = body, size = null), "$body resolves no art")
            assertEquals(VehicleArt.Car(body), art)
            // getValue throws on a missing spec, which is the assertion here.
            art.isoSpec()
            art.topdownSpec()
        }
    }

    /** Every four-wheeled tier still resolves a car, so the two-wheeler branch is not swallowing them. */
    @Test
    fun `should_resolveACarbody_when_sizeIsAnyFourWheeledTier`() {
        VehicleSize.entries.filter { it != VehicleSize.MOTORCYCLE }.forEach { size ->
            val art = assertNotNull(vehicleArtOf(carbody = null, size = size), "$size resolves no art")
            assertTrue(art is VehicleArt.Car, "$size resolved $art instead of a car")
        }
    }

    @Test
    fun `should_resolveNoArt_when_nothingIsKnownAboutTheVehicle`() {
        assertNull(vehicleArtOf(carbody = null, size = null))
    }

    /** The two-wheeler artwork is its own geometry, not a car spec wearing a different name. */
    @Test
    fun `should_notReuseACarSpec_when_drawingTheTwoWheeler`() {
        val motoIso = VehicleArt.TwoWheeler.isoSpec()
        val motoTopdown = VehicleArt.TwoWheeler.topdownSpec()
        CarbodyType.entries.forEach { body ->
            assertTrue(motoIso !== VehicleArt.Car(body).isoSpec(), "the two-wheeler reuses $body's side profile")
            assertTrue(motoTopdown !== VehicleArt.Car(body).topdownSpec(), "the two-wheeler reuses $body's top-down")
        }
    }
}
