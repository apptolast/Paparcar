package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DET-A-SAVED-CARD-MUST-NOT-OUTLIVE-ITS-TRIP-001] The rule both lanes ask when a trip starts.
 *
 * It lived as an inline expression inside the Coordinator's session start, which is why the
 * Bluetooth lane — which never enters that function — ran whole trips with the previous parking's
 * card still in the tray.
 */
class SavedCardIsStaleTest {

    private val config = ParkingDetectionConfig()
    private val window = config.confirmationResponseTimeoutMs
    private val now = 1_700_000_000_000L

    @Test
    fun should_beStale_when_noCardWasEverPosted() {
        // Nothing of ours is in the slot worth protecting — same answer the original inline
        // expression gave, and the reason a fresh process may clean up after itself.
        assertTrue(savedCardIsStale(postedAtMs = null, nowMs = now, config = config))
    }

    @Test
    fun should_beStale_when_theUndoWindowHasPassed() {
        assertTrue(savedCardIsStale(postedAtMs = now - window - 1, nowMs = now, config = config))
    }

    @Test
    fun should_keepTheCard_when_theUndoWindowIsStillOpen() {
        // The protection [REFACTOR-300-FIX] exists for a spurious IN_VEHICLE_ENTER fired while the
        // user walks away from the car: dismissing here would wipe their chance to tap «Cancelar».
        assertFalse(savedCardIsStale(postedAtMs = now - 1, nowMs = now, config = config))
        assertFalse(savedCardIsStale(postedAtMs = now - window, nowMs = now, config = config))
    }

    @Test
    fun should_keepTheCard_when_theClockCannotDateIt() {
        // A negative age (wall-clock skew, a restored backup) means we cannot date the card. Keeping
        // it costs a notification the user swipes away; dropping it costs them the undo of a wrong
        // pin — asymmetric failure keeps the card.
        assertFalse(savedCardIsStale(postedAtMs = now + 60_000L, nowMs = now, config = config))
    }
}
