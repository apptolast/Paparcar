package com.rndeveloper.paparcar.data.datasource.local.room

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rndeveloper.paparcar.data.mapper.toDomain
import com.rndeveloper.paparcar.domain.model.AddressInfo
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * An address survives the enrichment path WHOLE, or this fails.
 * [FUEL-A-PARKING-WITHOUT-A-COUNTRY-HAS-NO-PRICE-001]
 *
 * The bug this pins was not "the country is missing". It was **somebody enumerated a subset of the
 * address fields by hand**, in a partial `UPDATE` that looked like the complete list — and the
 * mapper everyone actually reads was correct the whole time, with its own passing test, which is
 * precisely why nobody looked at the `UPDATE`. Measured on the Oppo: 20 parkings with an address,
 * zero with a country, so `ResolveFuelPriceUseCase` had no country to price and the spend tile
 * never rendered, in any country, for anyone.
 *
 * ⛔ **So a test that only checks the country would be worthless** — it would pass forever while the
 * next field added to [AddressInfo] leaked out the same hole. Two things make this exhaustive
 * instead:
 *
 *  1. [assertFixtureSetsEveryField] uses reflection to refuse a fixture that leaves any property
 *     null. Add a field to [AddressInfo] and this test stops compiling-in-spirit: it fails until
 *     the fixture names the new field.
 *  2. [AddressInfo] is a data class, so `assertEquals` compares EVERY property. Once the fixture is
 *     forced to be complete, equality does the rest of the work for free.
 *
 * ⛔ **There is no exemption list any more.** `province` used to be the one field allowed to be
 * dropped, and this test carried a `copy(province = null)` to tolerate it. That single tolerated
 * hole is exactly the shape the country bug came in through, so the field is persisted now
 * (Room v6) and the assertion compares the address WHOLE. Nothing is excused; nothing has to be
 * remembered.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ParkingAddressSurvivesEnrichmentTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun should_persistEveryAddressField_when_enrichmentStampsAParking() = runTest {
        assertFixtureSetsEveryField(FULL_ADDRESS)

        val db = buildAppDatabase(context, "addr-enrichment-write.db")
        val dao = db.parkingSessionDao()
        dao.insert(parkingRow())

        dao.updateAddressAndPlace(
            id = SESSION_ID,
            street = FULL_ADDRESS.street,
            city = FULL_ADDRESS.city,
            region = FULL_ADDRESS.region,
            country = FULL_ADDRESS.country,
            countryCode = FULL_ADDRESS.countryCode,
            province = FULL_ADDRESS.province,
            postalCode = FULL_ADDRESS.postalCode,
            placeInfoName = null,
            placeInfoCategory = null,
            now = 1L,
        )

        val stored = assertNotNull(dao.getByUser(USER_ID).single().toDomain().address)
        db.close()

        // Whole. Not "whole except the one we agreed to lose".
        assertEquals(FULL_ADDRESS, stored)
    }

    /**
     * ⛔ Dragging the pin must clear the country WITH the rest of the address.
     *
     * This was latent: `updateLocation` nulls four address columns and used to leave
     * `addressCountryCode` standing. It could not be observed while the country was never written
     * in the first place — so fixing the write path is exactly what would have armed it. A pin
     * dragged across a border would then price the drive in the previous country's currency: not a
     * gap, a WRONG number, which is the worse of the two.
     */
    @Test
    fun should_clearTheCountryToo_when_thePinIsMovedByHand() = runTest {
        val db = buildAppDatabase(context, "addr-enrichment-clear.db")
        val dao = db.parkingSessionDao()
        dao.insert(parkingRow())
        dao.updateAddressAndPlace(
            id = SESSION_ID,
            street = FULL_ADDRESS.street,
            city = FULL_ADDRESS.city,
            region = FULL_ADDRESS.region,
            country = FULL_ADDRESS.country,
            countryCode = FULL_ADDRESS.countryCode,
            province = FULL_ADDRESS.province,
            postalCode = FULL_ADDRESS.postalCode,
            placeInfoName = null,
            placeInfoCategory = null,
            now = 1L,
        )

        dao.updateLocation(
            id = SESSION_ID,
            lat = 48.8566,
            lon = 2.3522,
            accuracy = 5f,
            timestamp = 2L,
            detectionPath = "manual",
            detectionReliability = 1f,
            now = 2L,
        )

        val row = dao.getByUser(USER_ID).single()
        db.close()

        assertNull(row.addressCountryCode, "the old country survived a manual move")
        assertNull(row.addressProvince, "the old province survived a manual move")
        // The whole address goes, not just the parts someone remembered.
        assertNull(row.toDomain().address, "a moved pin kept part of its old address")
    }

    /**
     * Refuses a fixture that does not exercise every field of [AddressInfo].
     *
     * Java reflection rather than `kotlin-reflect`: no extra dependency, and `declaredFields` is
     * exactly the list that grows when someone adds a property.
     */
    private fun assertFixtureSetsEveryField(fixture: AddressInfo) {
        val unset = AddressInfo::class.java.declaredFields
            .filterNot { it.isSynthetic }
            .mapNotNull { field ->
                field.isAccessible = true
                field.name.takeIf { field.get(fixture) == null }
            }
        assertTrue(
            unset.isEmpty(),
            "The fixture leaves $unset null, so this test cannot prove those fields survive. " +
                "Set them — and then make sure the enrichment path actually persists them.",
        )
    }

    private fun parkingRow() = UserParkingEntity(
        id = SESSION_ID,
        userId = USER_ID,
        vehicleId = "vehicle-1",
        latitude = 40.4168,
        longitude = -3.7038,
        accuracy = 8f,
        timestamp = 1L,
        isActive = false,
    )

    private companion object {
        const val SESSION_ID = "session-1"
        const val USER_ID = "user-1"

        /**
         * Every field populated on purpose. A fixture with holes in it proves nothing about the
         * holes — which is why [assertFixtureSetsEveryField] refuses one.
         */
        val FULL_ADDRESS = AddressInfo(
            street = "Calle Mayor 1",
            city = "Madrid",
            region = "Comunidad de Madrid",
            country = "España",
            countryCode = "es",
            province = "Madrid",
            postalCode = "28013",
        )
    }
}
