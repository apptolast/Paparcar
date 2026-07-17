package io.apptolast.paparcar.presentation.home.sections.sheet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.home.HomeMode
import io.apptolast.paparcar.presentation.home.sections.sheet.components.papSheetHeaderBandHeight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

// ─────────────────────────────────────────────────────────────────────────────
// HomeSheetPositioning — the sheet's snap-point geometry and transition physics,
// extracted from HomeContent so the orchestrator reads like a table of contents.
// [HOME-ATOMIZE-001 F2]
//
// Offset semantics everywhere in this file: larger value = sheet top farther
// from screen top = sheet smaller. So
// `minimized >= peek >= half >= expanded >= full`.
// ─────────────────────────────────────────────────────────────────────────────

/** Animation used by every sheet snap/toggle so drag release, taps and resets move as one. */
internal val SheetSnapSpec: AnimationSpec<Float> = tween(durationMillis = 300, easing = FastOutSlowInEasing)

// Sheet top position when fully expanded ("sheet top at screen top").
private const val FULL_SNAP_OFFSET_PX = 0f

// Primary "desplegado" auto-snap: sheet top sits this fraction of the container
// down from the top, so the sheet occupies ~58% and a slice of map + the car
// marker stay visible above it. Full-screen is reachable only by manual drag.
// [HOME-SNAP-001]
private const val EXPANDED_MAP_VISIBLE_FRACTION = 0.42f

// Fraction of peek offset at/above which the global bottom nav starts hiding.
// Remaps raw sheet progress so the nav disappears well before the sheet is
// fully expanded — responsive feel instead of a linear fade across the drag.
private const val NAV_HIDE_START = 0.65f

// When peekOffsetPx changes by less than this amount and the sheet is at rest,
// snap directly instead of animating — avoids the visible 300ms glide that
// occurs when the peek handle is first measured and peekHeightPx is corrected
// from the initial estimate to the real measured height.
private val PEEK_LAYOUT_SNAP_TOLERANCE = 64.dp

// Velocity (px/s) required to snap the sheet on fling; below this the sheet
// stays in place at its closest snap point.
private const val FLING_SNAP_VELOCITY = 1200f

/**
 * The five snap points of the sheet plus the derived chrome threshold, for one
 * frame of layout inputs. Pure data — see [computeSheetPositioning] for the
 * derivation and [rememberSheetPositioning] for the Compose wiring.
 */
@Immutable
internal data class SheetPositioning(
    val peekOffsetPx: Float,
    val halfOffsetPx: Float,
    val expandedOffsetPx: Float,
    val fullSnapOffsetPx: Float,
    val minimizedOffsetPx: Float,
    /** Floating map chrome (FABs/search) fades once the sheet top rises above this. [HOME-SNAP-001] */
    val overlayHideThresholdPx: Float,
)

/**
 * Derives the snap points from the frame's inputs. Pure and Compose-free so the
 * geometry is unit-testable.
 *
 * @param containerHeightPx screen height minus the active bottom bar — snap points stay
 *   stable while bars animate.
 * @param peekHeightPx the peek handle's real measured height (bootstrapped estimate on first frame).
 * @param headerBandPx collapsed "header-only" band height (drag pill + reserved 3-line header).
 * @param isPureBrowsePeek Browse with no selection: the header IS the whole peek, so the
 *   minimized floor pins exactly to peek. [SHEET-MIN-001]
 * @param capExpandAtPeek pin modes / parking selected / spot selected with the list hidden:
 *   the peek handle owns the whole surface, so the sheet must not expand above peek. [SHEET-DRAG-001]
 * @param allItemsFit every list item already visible — the sheet stops at content height
 *   instead of exposing empty space above the last row.
 * @param listNaturalHeightPx total content height of the list when [allItemsFit] (else ignored).
 */
internal fun computeSheetPositioning(
    containerHeightPx: Float,
    peekHeightPx: Float,
    headerBandPx: Float,
    isPureBrowsePeek: Boolean,
    capExpandAtPeek: Boolean,
    allItemsFit: Boolean,
    listNaturalHeightPx: Float,
): SheetPositioning {
    val peekOffsetPx = (containerHeightPx - peekHeightPx).coerceAtLeast(0f)

    // Minimized snap point — the drag-down "header-only" floor for the tall non-Browse peeks.
    // A design DERIVATION (font-scale aware, no measurement feedback), so the band seats
    // deterministically and the cut always lands under the header. [SHEET-MIN-001] [UI-SHEET-006]
    val minimizedOffsetPx = if (isPureBrowsePeek) {
        peekOffsetPx
    } else {
        (containerHeightPx - headerBandPx).coerceAtLeast(peekOffsetPx)
    }

    // Content-aware full snap: cap at peek when the peek owns the surface, stop at content
    // height when the whole list already fits, else the screen top.
    val fullSnapOffsetPx = when {
        capExpandAtPeek -> peekOffsetPx
        allItemsFit && peekHeightPx > 0f ->
            (containerHeightPx - peekHeightPx - listNaturalHeightPx).coerceAtLeast(0f)
        else -> FULL_SNAP_OFFSET_PX
    }

    // Primary "desplegado" auto-snap — capped below full so map + car marker stay visible,
    // coerced into the valid range. [HOME-SNAP-001]
    val expandedOffsetPx = (containerHeightPx * EXPANDED_MAP_VISIBLE_FRACTION)
        .coerceIn(fullSnapOffsetPx, peekOffsetPx)
    // Intermediate anchor — a genuine half-sheet between peek and expanded.
    val halfOffsetPx = (expandedOffsetPx + peekOffsetPx) / 2f
    // Chrome stays visible through peek + half and fades toward the deeper "expanded" anchor;
    // threshold = midpoint between the two. [HOME-SNAP-001]
    val overlayHideThresholdPx = (halfOffsetPx + expandedOffsetPx) / 2f

    return SheetPositioning(
        peekOffsetPx = peekOffsetPx,
        halfOffsetPx = halfOffsetPx,
        expandedOffsetPx = expandedOffsetPx,
        fullSnapOffsetPx = fullSnapOffsetPx,
        minimizedOffsetPx = minimizedOffsetPx,
        overlayHideThresholdPx = overlayHideThresholdPx,
    )
}

/**
 * Compose wiring for [computeSheetPositioning]: contributes the density-derived
 * header band and the content-aware reads of [lazyListState]. The layoutInfo
 * reads live inside derivedStateOf so scroll frames don't retrigger composition
 * — only the flips of the derived values do.
 */
@Composable
internal fun rememberSheetPositioning(
    containerHeightPx: Float,
    peekHeightPx: Float,
    lazyListState: LazyListState,
    isPureBrowsePeek: Boolean,
    capExpandAtPeek: Boolean,
): SheetPositioning {
    val headerBandPx = with(LocalDensity.current) { papSheetHeaderBandHeight().toPx() }
    val allItemsFit by remember(lazyListState) {
        derivedStateOf {
            lazyListState.layoutInfo.let { info ->
                info.totalItemsCount > 0 &&
                    info.visibleItemsInfo.size >= info.totalItemsCount
            }
        }
    }
    val listNaturalHeightPx by remember(lazyListState) {
        derivedStateOf {
            if (!allItemsFit) 0f
            else lazyListState.layoutInfo.let { info ->
                info.visibleItemsInfo.sumOf { it.size }.toFloat() +
                    info.beforeContentPadding + info.afterContentPadding
            }
        }
    }
    return computeSheetPositioning(
        containerHeightPx = containerHeightPx,
        peekHeightPx = peekHeightPx,
        headerBandPx = headerBandPx,
        isPureBrowsePeek = isPureBrowsePeek,
        capExpandAtPeek = capExpandAtPeek,
        allItemsFit = allItemsFit,
        listNaturalHeightPx = listNaturalHeightPx,
    )
}

/**
 * True when the sheet's position (or in-flight target) was sent above the peek
 * anchor it last rested at AND the current geometry allows a user position above
 * peek at all. Such a position belongs to the user — a layout correction must
 * not steal it. [BUG-SHEET-TAP-BOUNCE-001]
 *
 * When expansion is capped at peek ([canExpandAbovePeek] false: pin modes,
 * parking selected, spot selected with the list hidden) an above-peek reading
 * can only be the residue of a follow animation that the next re-measure frame
 * cancelled mid-flight — AnimatedContent re-measures every frame of a peek
 * transition — never user intent. Gating on it would strand the sheet taller
 * than its content, with dead space under the peek. [BUG-SHEET-STRANDED-TALL-001]
 */
internal fun isIntentionallyAbovePeek(
    referenceOffsetPx: Float,
    restingPeekAnchorPx: Float,
    tolerancePx: Float,
    canExpandAbovePeek: Boolean,
): Boolean = canExpandAbovePeek && referenceOffsetPx < restingPeekAnchorPx - tolerancePx

/**
 * The sheet's state-driven transitions, extracted from HomeContent as a
 * no-UI composable: reset-to-peek on selection/mode changes, auto-expand on
 * list toggle, and nav-progress hoisting for the global bottom nav.
 */
@Composable
internal fun SheetTransitionEffects(
    positioning: SheetPositioning,
    sheetOffsetPx: Animatable<Float, AnimationVector1D>,
    mode: HomeMode,
    selectedItemId: String?,
    isParkingSelected: Boolean,
    spotListExpanded: Boolean,
    navProgressState: MutableFloatState,
) {
    val peekOffsetPx = positioning.peekOffsetPx
    val peekSnapTolerancePx = with(LocalDensity.current) { PEEK_LAYOUT_SNAP_TOLERANCE.toPx() }

    val isPinning = mode is HomeMode.Reporting ||
        mode is HomeMode.AddingZone ||
        mode is HomeMode.AddingParking
    val resetToPeek = isPinning || isParkingSelected
    // Read by the layout-correction effect at fire time — a peek re-measure must
    // decide with the CURRENT selection/mode/geometry, not the ones captured at launch.
    val currentPeekOffset = rememberUpdatedState(peekOffsetPx)
    val currentResetToPeek = rememberUpdatedState(resetToPeek)
    val currentSelectedItemId = rememberUpdatedState(selectedItemId)
    val currentPositioning = rememberUpdatedState(positioning)

    // The peek anchor the sheet last RESTED at. Only the effects below advance it,
    // and only when their move COMPLETES — an AnimatedContent re-measure restarts
    // the correction effect every frame (cancelling the follow mid-flight), and a
    // cancelled follow skips the assignment, so one gate decision spans the whole
    // multi-frame re-measure instead of re-judging against each intermediate value.
    var restingPeekAnchor by remember { mutableFloatStateOf(peekOffsetPx) }

    // Intentional reset — a selection or mode CHANGE returns the sheet to peek
    // (full peek content visible). The user can then drag DOWN to minimized for
    // a header-only view. [SHEET-DRAG-001]
    LaunchedEffect(selectedItemId, mode) {
        val peek = currentPeekOffset.value
        if (selectedItemId == null || resetToPeek) {
            val correction = (sheetOffsetPx.value - peek).absoluteValue
            // Snap (not animate) when: small layout correction OR sheet is already at
            // or below the new peek. The latter covers selection events where the peek
            // handle grows (Browse→SelectedParking/Spot), shifting peekOffsetPx upward.
            // Without this guard the LaunchedEffect would fire animateTo and the sheet
            // would visibly slide up in slow motion to follow the handle.
            val sheetBelowNewPeek = sheetOffsetPx.value >= peek
            if (!sheetOffsetPx.isRunning && (correction < peekSnapTolerancePx || sheetBelowNewPeek)) {
                sheetOffsetPx.snapTo(peek)
            } else {
                sheetOffsetPx.animateTo(peek, SheetSnapSpec)
            }
            restingPeekAnchor = peek
        } else if (!sheetOffsetPx.isRunning && sheetOffsetPx.value >= peek) {
            // Guard isRunning so the expand animation launched on spot/car tap is not
            // cancelled by the peekOffsetPx change that occurs when the peek handle
            // grows to show the selected-item content.
            sheetOffsetPx.snapTo(peek)
            restingPeekAnchor = peek
        }
    }

    // Layout correction — a peek RE-MEASURE (Browse subject swap [UI-SHEET-004],
    // address line count, deselection shrinking the handle) moves the resting
    // position with the handle, but must never steal the sheet from a position or
    // animation the user sent above peek: without the gate, tapping the sheet open
    // re-measures the swapped header mid-flight and this effect used to cancel the
    // expansion and slide the sheet straight back down. [BUG-SHEET-TAP-BOUNCE-001]
    // The gate judges against [restingPeekAnchor] — the anchor the sheet last
    // rested at — NOT the previous frame's measure: during an animated shrink the
    // sheet lags behind the moving peek, and a per-frame anchor would misread that
    // lag as user intent and strand the sheet above the new peek.
    LaunchedEffect(peekOffsetPx) {
        val reference = if (sheetOffsetPx.isRunning) sheetOffsetPx.targetValue else sheetOffsetPx.value
        // fullSnap == peek exactly when capExpandAtPeek (the peek owns the surface) —
        // in that geometry there IS no user position above peek to protect, so the
        // correction always follows the measure. [BUG-SHEET-STRANDED-TALL-001]
        val snap = currentPositioning.value
        val canExpandAbovePeek = snap.fullSnapOffsetPx < snap.peekOffsetPx
        if (isIntentionallyAbovePeek(reference, restingPeekAnchor, peekSnapTolerancePx, canExpandAbovePeek)) {
            return@LaunchedEffect
        }
        if (currentSelectedItemId.value == null || currentResetToPeek.value) {
            val correction = (sheetOffsetPx.value - peekOffsetPx).absoluteValue
            val sheetBelowNewPeek = sheetOffsetPx.value >= peekOffsetPx
            if (!sheetOffsetPx.isRunning && (correction < peekSnapTolerancePx || sheetBelowNewPeek)) {
                sheetOffsetPx.snapTo(peekOffsetPx)
            } else {
                sheetOffsetPx.animateTo(peekOffsetPx, SheetSnapSpec)
            }
            restingPeekAnchor = peekOffsetPx
        } else if (!sheetOffsetPx.isRunning && sheetOffsetPx.value >= peekOffsetPx) {
            sheetOffsetPx.snapTo(peekOffsetPx)
            restingPeekAnchor = peekOffsetPx
        }
    }

    // Keep sheet position in sync with spot list expand/collapse.
    LaunchedEffect(spotListExpanded) {
        if (spotListExpanded) {
            // Auto-open the sheet to the capped "expanded" anchor so the list is visible
            // immediately while a slice of map stays in view (not full-screen). [HOME-SNAP-001]
            sheetOffsetPx.animateTo(positioning.expandedOffsetPx, SheetSnapSpec)
        } else if (sheetOffsetPx.value < peekOffsetPx) {
            // List collapsed while sheet was expanded — snap back to peek.
            sheetOffsetPx.animateTo(peekOffsetPx, SheetSnapSpec)
        }
    }

    // Hoist sheet progress up to the root so the global bottom nav can
    // fade + slide with the drag via graphicsLayer. snapshotFlow keeps
    // this off the composition path — only the layer phase reacts.
    LaunchedEffect(peekOffsetPx) {
        snapshotFlow {
            val raw = if (peekOffsetPx > 0f) sheetOffsetPx.value / peekOffsetPx else 1f
            ((raw - NAV_HIDE_START) / (1f - NAV_HIDE_START)).coerceIn(0f, 1f)
        }.collect { progress -> navProgressState.floatValue = progress }
    }
}

/**
 * The user-driven motions of the sheet — tap-toggle, programmatic expand and the
 * Instagram-style nested scroll — bound to the [Animatable] offset the layout
 * reads. Lambdas are identity-stable (the instance is remembered) while reading
 * the LATEST [SheetPositioning] at call-time via the [positioning] holder.
 */
@Stable
internal class SheetMotion(
    private val scope: CoroutineScope,
    private val sheetOffsetPx: Animatable<Float, AnimationVector1D>,
    private val positioning: State<SheetPositioning>,
    private val lazyListState: LazyListState,
) {
    /** Auto-snaps to the capped "expanded" anchor (not full) so map + car marker
     *  stay visible. [HOME-SNAP-001] */
    val animateToExpanded: () -> Unit = {
        scope.launch {
            val snap = positioning.value
            sheetOffsetPx.animateTo(
                snap.expandedOffsetPx.coerceIn(snap.fullSnapOffsetPx, snap.peekOffsetPx),
                SheetSnapSpec,
            )
        }
    }

    /**
     * Toggles the sheet between peek and the adjacent snap. The "adjacent" snap
     * depends on the current state:
     *  - Browse with a list (expanded < peek):  peek ↔ expanded
     *  - Non-Browse (expanded == peek, no expansion above):  peek ↔ minimized
     * Tap-from-peek opens straight to the deeper "expanded" anchor — a tap is an
     * explicit "show me more", so it lands on the detent that reveals the list while
     * a slice of map stays visible. Half remains a drag-only stop. [SHEET-TAP-001] [HOME-SNAP-001]
     */
    val toggle: () -> Unit = {
        scope.launch {
            val snap = positioning.value
            val current = sheetOffsetPx.value
            val peek = snap.peekOffsetPx
            val minimized = snap.minimizedOffsetPx
            val expanded = snap.expandedOffsetPx.coerceIn(snap.fullSnapOffsetPx, peek)
            val canExpandAbovePeek = expanded < peek - SNAP_EPSILON_PX
            val canCollapseBelowPeek = minimized > peek + SNAP_EPSILON_PX
            val target = when {
                // Above peek → collapse to peek.
                current < peek - SNAP_EPSILON_PX -> peek
                // Below peek → expand to peek.
                current > peek + SNAP_EPSILON_PX -> peek
                // At peek with an expanded snap available → open it.
                canExpandAbovePeek -> expanded
                // At peek with no expansion but a minimized snap → collapse.
                canCollapseBelowPeek -> minimized
                else -> peek
            }
            sheetOffsetPx.animateTo(target, SheetSnapSpec)
        }
    }

    /**
     * Instagram-style nested scroll: when the list is scrolled to the very top and
     * the user drags down, collapse the sheet instead of letting the gesture be
     * wasted. Upward gestures are never intercepted — they always scroll the list.
     */
    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val listAtTop = lazyListState.firstVisibleItemIndex == 0 &&
                lazyListState.firstVisibleItemScrollOffset == 0
            val peek = positioning.value.peekOffsetPx
            val sheetCanCollapse = sheetOffsetPx.value < peek
            if (available.y > 0f && listAtTop && sheetCanCollapse) {
                val newOffset = (sheetOffsetPx.value + available.y)
                    .coerceIn(positioning.value.fullSnapOffsetPx, peek)
                val consumed = newOffset - sheetOffsetPx.value
                scope.launch { sheetOffsetPx.snapTo(newOffset) }
                return Offset(0f, consumed)
            }
            return Offset.Zero
        }
    }

    private companion object {
        // "Effectively at an anchor" slack — sub-pixel float drift must not read
        // as a distinct position when picking the toggle target.
        const val SNAP_EPSILON_PX = 1f
    }
}

@Composable
internal fun rememberSheetMotion(
    sheetOffsetPx: Animatable<Float, AnimationVector1D>,
    positioning: SheetPositioning,
    lazyListState: LazyListState,
): SheetMotion {
    val scope = rememberCoroutineScope()
    val currentPositioning = rememberUpdatedState(positioning)
    return remember(sheetOffsetPx, lazyListState) {
        SheetMotion(scope, sheetOffsetPx, currentPositioning, lazyListState)
    }
}

/**
 * Snap-point bundle for the sheet drag. Centralises the offsets and the
 * fling/soft-drag selection so [HomeBottomSheet] can be called with a single
 * value instead of loose floats.
 *
 * [minimizedOffsetPx] is the extra "drag-down to header-only" snap point used
 * in non-Browse states. In Browse it equals [peekOffsetPx] so the snap logic
 * collapses to the original three-point behaviour. [SHEET-DRAG-001]
 *
 * [expandedOffsetPx] is the primary "desplegado" auto-snap — capped below full
 * so a slice of map + the car marker stay visible. `full` remains a valid snap
 * so a hard manual drag to the very top still rests at full-screen. [HOME-SNAP-001]
 */
internal data class HomeSheetSnap(
    val peekOffsetPx: Float,
    val halfOffsetPx: Float,
    val expandedOffsetPx: Float,
    val fullSnapOffsetPx: Float,
    val minimizedOffsetPx: Float,
    val snapSpec: AnimationSpec<Float>,
) {
    constructor(positioning: SheetPositioning, snapSpec: AnimationSpec<Float> = SheetSnapSpec) : this(
        peekOffsetPx = positioning.peekOffsetPx,
        halfOffsetPx = positioning.halfOffsetPx,
        expandedOffsetPx = positioning.expandedOffsetPx,
        fullSnapOffsetPx = positioning.fullSnapOffsetPx,
        minimizedOffsetPx = positioning.minimizedOffsetPx,
        snapSpec = snapSpec,
    )

    /** Returns the snap target the sheet should animate to after the user releases the drag. */
    fun snapTarget(current: Float, velocityYPxPerSec: Float): Float = when {
        velocityYPxPerSec < -FLING_SNAP_VELOCITY -> {
            // Fling up: minimized → peek, peek → half, half → expanded, expanded → full.
            when {
                current > peekOffsetPx -> peekOffsetPx
                current > halfOffsetPx -> halfOffsetPx
                current > expandedOffsetPx -> expandedOffsetPx
                else -> fullSnapOffsetPx
            }
        }
        velocityYPxPerSec > FLING_SNAP_VELOCITY -> {
            // Fling down: full → expanded, expanded → half, half → peek, peek → minimized.
            when {
                current < expandedOffsetPx -> expandedOffsetPx
                current < halfOffsetPx -> halfOffsetPx
                current < peekOffsetPx -> peekOffsetPx
                else -> minimizedOffsetPx
            }
        }
        else -> {
            // Soft drag: snap to the nearest of minimized / peek / half / expanded / full.
            val (offset, _) = listOf(
                fullSnapOffsetPx to (current - fullSnapOffsetPx).absoluteValue,
                expandedOffsetPx to (current - expandedOffsetPx).absoluteValue,
                halfOffsetPx to (current - halfOffsetPx).absoluteValue,
                peekOffsetPx to (current - peekOffsetPx).absoluteValue,
                minimizedOffsetPx to (current - minimizedOffsetPx).absoluteValue,
            ).minBy { it.second }
            offset
        }
    }.coerceIn(fullSnapOffsetPx, minimizedOffsetPx)
}
