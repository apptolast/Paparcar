package com.rndeveloper.paparcar.domain.sharedcar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

private const val MAC = "AA:BB:CC:DD:EE:FF"

/** The MAC never leaves the owner's device — only a token the group can compare. [VEH-SHARED-001] */
class SharedCarBtTokenTest {

    @Test
    fun should_notContainTheAddress_when_buildingTheToken() {
        val token = sharedCarBtToken("car-1", MAC)!!

        assertFalse(token.contains("AA"), "the token must not carry the MAC")
        assertFalse(token.contains("EE:FF"))
    }

    @Test
    fun should_matchItself_when_theSameCarAndDeviceAreHashedAgain() {
        // A member's phone answers "is this the same car?" by comparing tokens — so the same pair
        // must produce the same value on every platform and every run.
        assertEquals(sharedCarBtToken("car-1", MAC), sharedCarBtToken("car-1", "aa:bb:cc:dd:ee:ff"))
    }

    @Test
    fun should_differPerCar_when_theSameDeviceIsSharedTwice() {
        // Salted with the car id, so a token says nothing when read next to another car's.
        assertNotEquals(sharedCarBtToken("car-1", MAC), sharedCarBtToken("car-2", MAC))
    }

    @Test
    fun should_beNull_when_theCarHasNoPairing() {
        assertNull(sharedCarBtToken("car-1", null))
        assertNull(sharedCarBtToken("car-1", "   "))
    }
}
