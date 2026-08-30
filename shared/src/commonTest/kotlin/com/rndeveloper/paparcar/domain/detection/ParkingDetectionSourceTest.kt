package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.model.UserParking
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
        // [DET-DETECTION-PATH-IS-A-TYPE-001] These are now the coordinator's DECLARED paths, taken
        // from the type rather than retyped here — a label that drifts cannot pass this test by
        // being spelled the same in two places.
        listOf(
            DetectionPath.StepsEgress,
            DetectionPath.KinematicEgress,
            DetectionPath.VehicleExitWindow,
            DetectionPath.UnattendedTimeout,
            DetectionPath.UnattendedZone("prompt_ignored"),
            DetectionPath.SafetyNetBackfill,
            DetectionPath.ClosedApproximatePin,
        ).forEach { path ->
            assertEquals(
                ParkingDetectionSource.Assisted,
                parkingDetectionSourceOf(SpotType.AUTO_DETECTED, path.label),
                "path=${path.label}",
            )
        }
    }

    @Test
    fun should_readUnknown_when_theLabelIsNotOneWeWrite() {
        // ⚠️ BEHAVIOUR CHANGE, and the reason is measured. This list used to include
        // "steps=3 kinematicFixes=7" and "motorBand=41000ms ≥8.0mps", on the premise that the
        // coordinator persisted diagnostic jargon as a detectionPath. It does not: that jargon is a
        // TRACE note (`FastConfirmStage`'s "▶ steps+egress (steps=8 kinematicFixes=0)"), and three
        // real accounts of parking history read on 2026-08-30 carry only declared labels —
        // steps+egress, kinematic+egress, unattended_zone_gap_anchor, user, manual, nudge, bt,
        // safety_net_backfill, closed_approximate_pin — and nulls. Not one jargon path exists.
        //
        // "vehicle-exit" belongs here too, and that is the finding: production writes
        // "vehicleExit+window+egress". The other spelling lived only in UserParking's KDoc, the
        // repository fake and the preview data — a provenance the app has never once written.
        //
        // So the old `else -> Assisted` was not covering a real case; it was ATTRIBUTING an
        // unrecognised pin to the Coordinator, in the exact field a user opens to ask who placed a
        // pin they did not expect. Unknown claims nothing, which is the honest answer.
        listOf(
            "steps=3 kinematicFixes=7",
            "motorBand=41000ms ≥8.0mps",
            "vehicle-exit",
            "btle_beacon",
            "a_path_invented_after_this_ticket",
        ).forEach { path ->
            assertEquals(
                ParkingDetectionSource.Unknown,
                parkingDetectionSourceOf(SpotType.AUTO_DETECTED, path),
                "path=$path",
            )
        }
    }

    @Test
    fun should_notMistakeANewBtPrefixedPathForTheBluetoothStrategy() {
        // The prefix guard, made explicit: classification used to be `startsWith("bt")`, so any
        // future path beginning with those two letters would have been filed as the deterministic
        // Bluetooth strategy on the strength of two characters.
        assertEquals(ParkingDetectionSource.Bluetooth, parkingDetectionSourceOf(SpotType.AUTO_DETECTED, "bt"))
        assertEquals(ParkingDetectionSource.Bluetooth, parkingDetectionSourceOf(SpotType.AUTO_DETECTED, "bt_timeout"))
        assertEquals(ParkingDetectionSource.Unknown, parkingDetectionSourceOf(SpotType.AUTO_DETECTED, "bt_something_new"))
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
