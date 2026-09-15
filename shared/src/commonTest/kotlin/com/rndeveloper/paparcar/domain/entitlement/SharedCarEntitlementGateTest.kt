package com.rndeveloper.paparcar.domain.entitlement

import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Who opens a group, how big it gets, and what it costs the member. [VEH-SHARED-001] */
class SharedCarEntitlementGateTest {

    @Test
    fun should_requirePlusOfTheOwner_when_openingAGroup() {
        assertTrue(canShareVehicle(isPlus = true))
        assertFalse(canShareVehicle(isPlus = false))
    }

    @Test
    fun should_capTheGroupAtFourBesidesTheOwner_when_invitingSomebody() {
        assertTrue(canAddSharedCarMember(currentMembers = 3))
        assertFalse(canAddSharedCarMember(currentMembers = SHARED_CAR_MAX_MEMBERS))
    }

    @Test
    fun should_notCountACarSomebodySharedWithMe_when_checkingTheFreeLimit() {
        // ⛔ The whole deal: the OWNER's Plus pays for the group, so accepting an invitation must
        // not spend the free user's only slot.
        val mine = vehicle(id = "mine")
        val joined = vehicle(id = "theirs", ownerUid = "marta")

        assertEquals(1, countOwnVehicles(listOf(mine, joined)))
        assertTrue(canAddOwnVehicle(countOwnVehicles(listOf(joined)), isPlus = false))
        assertFalse(canAddOwnVehicle(countOwnVehicles(listOf(mine, joined)), isPlus = false))
    }

    @Test
    fun should_stillCountMyOwnCar_when_iAmTheOneSharingIt() {
        // Opening a group does not make the car stop being yours.
        val sharedByMe = vehicle(id = "mine", sharedCarId = "mine")

        assertEquals(1, countOwnVehicles(listOf(sharedByMe)))
    }

    private fun vehicle(id: String, ownerUid: String? = null, sharedCarId: String? = null) = Vehicle(
        id = id,
        userId = "nacho",
        name = id,
        sizeCategory = VehicleSize.MEDIUM_SUV,
        ownerUid = ownerUid,
        sharedCarId = sharedCarId,
    )
}
