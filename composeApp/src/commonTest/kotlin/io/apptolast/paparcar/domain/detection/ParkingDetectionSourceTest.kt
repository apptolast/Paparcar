package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.UserParking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The history detail must be able to say WHICH strategy put a pin — `spotType` alone collapses the
 * two into "auto-detected". [UI-HISTORY-IDENTITY-AND-SOURCE-001]
 */
class ParkingDetectionSourceTest {

    @Test
    fun should_readBluetooth_when_pathIsTheWalkCorroboratedBtPark() {
        assertEquals(
            ParkingDetectionSource.Bluetooth,
            parkingDetectionSourceOf(SpotType.AUTO_DETECTED, "bt"),
        )
    }

    @Test
    fun should_readBluetooth_when_theWalkAwayWatchTimedOut() {
        // "bt_timeout" is still the BT strategy — the difference is how much corroboration it got.
        assertEquals(
            ParkingDetectionSource.Bluetooth,
            parkingDetectionSourceOf(SpotType.AUTO_DETECTED, "bt_timeout"),
        )
    }

    @Test
    fun should_readAssisted_when_theCoordinatorConfirmedTheDrive() {
        // The coordinator's real labels are diagnostic strings, not an enum — they must all land in
        // Assisted without the screen ever printing them.
        listOf(
            "steps=3 kinematicFixes=7",
            "motorBand=41000ms ≥8.0mps",
            "vehicle-exit",
            "unattended_timeout",
            "unattended_zone_prompt_ignored",
            "safety_net_backfill",
        ).forEach { path ->
            assertEquals(
                ParkingDetectionSource.Assisted,
                parkingDetectionSourceOf(SpotType.AUTO_DETECTED, path),
                "path=$path",
            )
        }
    }

    @Test
    fun should_readManual_when_theUserPlacedOrConfirmedThePin() {
        listOf("manual", "user").forEach { path ->
            assertEquals(
                ParkingDetectionSource.Manual,
                parkingDetectionSourceOf(SpotType.MANUAL_REPORT, path),
                "path=$path",
            )
        }
    }

    @Test
    fun should_readManual_when_aNudgePinKeptAutoDetectedProvenance() {
        // A nudge pin stays AUTO_DETECTED on purpose (auto TTL on release), but the hand was the
        // user's — classifying it Assisted would credit the coordinator with a manual park.
        // [DET-NUDGE-PIN-PROVENANCE-001]
        assertEquals(
            ParkingDetectionSource.Manual,
            parkingDetectionSourceOf(SpotType.AUTO_DETECTED, "nudge"),
        )
    }

    @Test
    fun should_readPrivateZone_when_theParkClosedInsideAHomeGeofence() {
        assertEquals(
            ParkingDetectionSource.PrivateZone,
            parkingDetectionSourceOf(SpotType.HOME_GEOFENCE, "steps=2 kinematicFixes=4"),
        )
    }

    @Test
    fun should_claimNothing_when_theRowPredatesProvenance() {
        // Legacy rows carry no path. We do not know who detected them, so we do not say.
        assertEquals(
            ParkingDetectionSource.Unknown,
            parkingDetectionSourceOf(SpotType.AUTO_DETECTED, null),
        )
        assertEquals(
            ParkingDetectionSource.Unknown,
            parkingDetectionSourceOf(SpotType.AUTO_DETECTED, "   "),
        )
    }

    @Test
    fun should_readTheSessionsOwnPath_when_askedThroughTheSessionExtension() {
        val session = UserParking(
            id = "s1",
            location = GpsPoint(40.4168, -3.7038, accuracy = 8f, timestamp = 1_700_000_000_000L, speed = 0f),
            spotType = SpotType.AUTO_DETECTED,
            detectionPath = "bt",
        )
        assertEquals(ParkingDetectionSource.Bluetooth, session.detectionSource())
    }
}
