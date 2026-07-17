package io.apptolast.paparcar.presentation.home.sections.sheet

import androidx.compose.animation.core.snap
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure geometry/physics tests for the sheet: snap-point derivation
 * ([computeSheetPositioning]) and post-drag snap selection
 * ([HomeSheetSnap.snapTarget]). [HOME-ATOMIZE-001 F2]
 */
class HomeSheetPositioningTest {

    // ── computeSheetPositioning ───────────────────────────────────────────────
    // Reference frame: 2000px container, 400px peek handle, 300px header band.

    private fun compute(
        peekHeightPx: Float = 400f,
        isPureBrowsePeek: Boolean = false,
        capExpandAtPeek: Boolean = false,
        allItemsFit: Boolean = false,
        listNaturalHeightPx: Float = 0f,
    ) = computeSheetPositioning(
        containerHeightPx = 2000f,
        peekHeightPx = peekHeightPx,
        headerBandPx = 300f,
        isPureBrowsePeek = isPureBrowsePeek,
        capExpandAtPeek = capExpandAtPeek,
        allItemsFit = allItemsFit,
        listNaturalHeightPx = listNaturalHeightPx,
    )

    @Test
    fun should_derive_the_five_anchors_in_order() {
        with(compute()) {
            assertEquals(1600f, peekOffsetPx)            // container - peekHeight
            assertEquals(1700f, minimizedOffsetPx)       // container - headerBand (header-only floor)
            assertEquals(0f, fullSnapOffsetPx)
            assertEquals(840f, expandedOffsetPx)         // container * 0.42
            assertEquals(1220f, halfOffsetPx)            // midpoint(expanded, peek)
            assertEquals(1030f, overlayHideThresholdPx)  // midpoint(half, expanded)
        }
    }

    @Test
    fun should_pin_minimized_to_peek_in_pure_browse() {
        val computed = compute(isPureBrowsePeek = true)
        assertEquals(computed.peekOffsetPx, computed.minimizedOffsetPx)
    }

    @Test
    fun should_floor_minimized_at_peek_when_header_band_is_taller_than_peek() {
        // 200px peek → peek offset 1800; container - band = 1700 would sit ABOVE
        // peek (a bigger sheet), so the floor coerces it back to peek.
        val computed = compute(peekHeightPx = 200f)
        assertEquals(1800f, computed.peekOffsetPx)
        assertEquals(1800f, computed.minimizedOffsetPx)
    }

    @Test
    fun should_cap_full_snap_at_peek_when_peek_owns_the_surface() {
        val computed = compute(capExpandAtPeek = true)
        assertEquals(1600f, computed.fullSnapOffsetPx)
        // With full pinned to peek, expanded and half collapse onto peek too.
        assertEquals(1600f, computed.expandedOffsetPx)
        assertEquals(1600f, computed.halfOffsetPx)
    }

    @Test
    fun should_stop_full_snap_at_content_height_when_all_items_fit() {
        val computed = compute(allItemsFit = true, listNaturalHeightPx = 600f)
        // container - peekHeight - listNaturalHeight
        assertEquals(1000f, computed.fullSnapOffsetPx)
        // Expanded (840) would overshoot the content-aware full snap → coerced onto it.
        assertEquals(1000f, computed.expandedOffsetPx)
    }

    // ── HomeSheetSnap.snapTarget ──────────────────────────────────────────────
    // Explicit anchors so the fling ladder reads directly: full 0 < expanded 400
    // < half 700 < peek 1000 < minimized 1400.

    private val dragSnap = HomeSheetSnap(
        peekOffsetPx = 1000f,
        halfOffsetPx = 700f,
        expandedOffsetPx = 400f,
        fullSnapOffsetPx = 0f,
        minimizedOffsetPx = 1400f,
        snapSpec = snap(),
    )

    @Test
    fun should_climb_one_anchor_on_fling_up() {
        assertEquals(1000f, dragSnap.snapTarget(1400f, -1300f)) // minimized → peek
        assertEquals(700f, dragSnap.snapTarget(1000f, -1300f))  // peek → half
        assertEquals(400f, dragSnap.snapTarget(700f, -1300f))   // half → expanded
        assertEquals(0f, dragSnap.snapTarget(400f, -1300f))     // expanded → full
    }

    @Test
    fun should_descend_one_anchor_on_fling_down() {
        assertEquals(400f, dragSnap.snapTarget(0f, 1300f))      // full → expanded
        assertEquals(700f, dragSnap.snapTarget(400f, 1300f))    // expanded → half
        assertEquals(1000f, dragSnap.snapTarget(700f, 1300f))   // half → peek
        assertEquals(1400f, dragSnap.snapTarget(1000f, 1300f))  // peek → minimized
    }

    @Test
    fun should_snap_to_nearest_anchor_below_fling_velocity() {
        // 1199 px/s is under the 1200 px/s fling threshold → soft-drag nearest.
        assertEquals(400f, dragSnap.snapTarget(480f, 1199f))
        assertEquals(1000f, dragSnap.snapTarget(920f, -1199f))
        assertEquals(0f, dragSnap.snapTarget(150f, 0f))
        assertEquals(1400f, dragSnap.snapTarget(1350f, 0f))
    }

    @Test
    fun should_coerce_target_into_the_valid_range() {
        // Overshoot below minimized on a downward fling still lands on minimized.
        assertEquals(1400f, dragSnap.snapTarget(1600f, 1300f))
        // Overshoot above full on an upward fling still lands on full.
        assertEquals(0f, dragSnap.snapTarget(-50f, -1300f))
    }

    // ── isIntentionallyAbovePeek ──────────────────────────────────────────────
    // The re-measure gate judges against the anchor the sheet last RESTED at —
    // not against the moving per-frame measure. [BUG-SHEET-TAP-BOUNCE-001]

    @Test
    fun should_gate_a_sheet_the_user_sent_above_its_resting_anchor() {
        // Tap-to-expand in flight (target 840) while resting anchor was 1819:
        // the expansion belongs to the user — the correction must not steal it.
        assertEquals(true, isIntentionallyAbovePeek(840f, 1819f, 168f, canExpandAbovePeek = true))
    }

    @Test
    fun should_follow_the_remeasure_when_the_sheet_was_resting_at_its_anchor() {
        // Deselecting a tall spot peek: the sheet rests exactly at its anchor
        // (1221) while the browse peek re-measures shorter — it must follow down.
        assertEquals(false, isIntentionallyAbovePeek(1221f, 1221f, 168f, canExpandAbovePeek = true))
        // Same when the sheet sits below the anchor (minimized).
        assertEquals(false, isIntentionallyAbovePeek(2400f, 2251f, 168f, canExpandAbovePeek = true))
    }

    @Test
    fun should_treat_within_tolerance_wobble_as_resting() {
        // Sub-tolerance offsets above the anchor are layout noise, not intent.
        assertEquals(false, isIntentionallyAbovePeek(2100f, 2251f, 168f, canExpandAbovePeek = true))
        assertEquals(true, isIntentionallyAbovePeek(2082f, 2251f, 168f, canExpandAbovePeek = true))
    }

    @Test
    fun should_never_gate_when_expansion_is_capped_at_peek() {
        // Pin modes (AddingParking/Reporting/AddingZone) cap expansion at peek: an
        // above-peek reading there is the residue of a follow animation cancelled by
        // the next AnimatedContent re-measure frame, never user intent. Gating on it
        // used to strand the sheet taller than its content. [BUG-SHEET-STRANDED-TALL-001]
        assertEquals(false, isIntentionallyAbovePeek(840f, 1819f, 168f, canExpandAbovePeek = false))
        assertEquals(false, isIntentionallyAbovePeek(2082f, 2251f, 168f, canExpandAbovePeek = false))
    }
}
