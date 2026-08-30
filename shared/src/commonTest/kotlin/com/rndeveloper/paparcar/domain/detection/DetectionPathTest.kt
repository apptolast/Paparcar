package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** [DET-DETECTION-PATH-IS-A-TYPE-001] */
class DetectionPathTest {

    private val config = ParkingDetectionConfig()

    @Test
    fun should_roundTripEveryFixedLabel_through_ofLabel() {
        // The guard against the drift that started this ticket: production wrote
        // "vehicleExit+window+egress" while UserParking's KDoc, the repository fake and the preview
        // data all said "vehicle-exit" — a provenance the app has never once written, and nothing
        // could catch it because the set of paths was stated nowhere.
        DetectionPath.fixedLabelPaths.forEach { path ->
            assertSame(path, DetectionPath.ofLabel(path.label), "label=${path.label}")
        }
    }

    @Test
    fun should_haveUniqueLabels() {
        // Two paths sharing a label would make `ofLabel` answer by list order.
        val labels = DetectionPath.fixedLabelPaths.map { it.label }
        assertEquals(labels.size, labels.toSet().size, "duplicated labels in $labels")
    }

    @Test
    fun should_roundTripTheComposedZoneFamily() {
        val path = DetectionPath.UnattendedZone("gap_anchor")
        assertEquals("unattended_zone_gap_anchor", path.label)
        assertEquals(path, DetectionPath.ofLabel(path.label))
    }

    @Test
    fun should_failClosed_when_theLabelIsUnknown() {
        // ⛔ The whole point. The predicate this replaces ended in `else -> Assisted`.
        assertNull(DetectionPath.ofLabel("a_path_invented_after_this_ticket"))
        assertNull(DetectionPath.ofLabel("bt_something_new"))
        assertNull(DetectionPath.ofLabel(null))
        assertNull(DetectionPath.ofLabel("  "))
    }

    @Test
    fun should_giveEveryLiveConfirmPathItsOwnReliability_and_noneToTheOthers() {
        // Failure #12: reliability was chosen by string equality, with everything unmatched falling
        // to the MAXIMUM — a path added tomorrow was born stamped 0.90 by nobody's decision.
        assertEquals(config.reliabilityVehicleExit, DetectionPath.StepsEgress.confirmReliability(config))
        assertEquals(config.reliabilityKinematicEgress, DetectionPath.KinematicEgress.confirmReliability(config))
        assertEquals(config.reliabilityVehicleExit, DetectionPath.VehicleExitWindow.confirmReliability(config))

        // The paths that do NOT confirm live answer null rather than a number someone might read as
        // a decision: their reliability is decided at their own site.
        listOf(
            DetectionPath.UnattendedTimeout, DetectionPath.SafetyNetBackfill,
            DetectionPath.ClosedApproximatePin, DetectionPath.Bt, DetectionPath.BtTimeout,
            DetectionPath.UserAnswered, DetectionPath.ManualPin, DetectionPath.Nudge,
        ).forEach { assertNull(it.confirmReliability(config), "path=${it.label}") }
        assertNull(DetectionPath.UnattendedZone("gap_anchor").confirmReliability(config))
    }

    @Test
    fun should_declareTheStrategyOfEveryPath() {
        assertTrue(
            DetectionPath.fixedLabelPaths.all { it.source != ParkingDetectionSource.Unknown },
            "no declared path may present itself as Unknown — that value is for labels we do not write",
        )
        assertEquals(ParkingDetectionSource.Bluetooth, DetectionPath.Bt.source)
        assertEquals(ParkingDetectionSource.Manual, DetectionPath.Nudge.source)
        assertNotNull(DetectionPath.ofLabel("unattended_zone_walk_entered_anchor"))
    }
}
