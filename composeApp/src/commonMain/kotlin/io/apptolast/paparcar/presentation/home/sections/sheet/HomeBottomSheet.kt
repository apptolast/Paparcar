package io.apptolast.paparcar.presentation.home.sections.sheet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import io.apptolast.paparcar.ui.components.PapDivider
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.presentation.home.HomeIntent
import io.apptolast.paparcar.presentation.home.HomeMode
import io.apptolast.paparcar.presentation.home.HomeState
import io.apptolast.paparcar.presentation.home.model.DetectionUiState
import io.apptolast.paparcar.presentation.home.sections.sheet.components.HomePeekHandle
import io.apptolast.paparcar.presentation.home.sections.sheet.components.homeSheetItems
import io.apptolast.paparcar.ui.theme.PapShapes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * The bottom Surface that hosts the peek handle (drag affordance) and the
 * scrollable list of sheet items. Owns the drag gesture, the fling snap
 * logic, and the LazyColumn — but does NOT own the [sheetOffsetPx]
 * Animatable itself. That lives in the parent so the map can compute its
 * height from the same source of truth.
 *
 * @param dragSnap controls fling/soft-drag snapping; pass [HomeSheetSnap] from the parent.
 */
@Composable
internal fun HomeBottomSheet(
    state: HomeState,
    containerHeightPx: Float,
    sheetOffsetPx: Animatable<Float, AnimationVector1D>,
    dragSnap: HomeSheetSnap,
    lazyListState: LazyListState,
    sheetNestedScroll: NestedScrollConnection,
    bottomContentPadding: Dp,
    coroutineScope: CoroutineScope,
    onPeekHeightChanged: (Float) -> Unit,
    onIntent: (HomeIntent) -> Unit,
    /** Tap on a per-vehicle row that already has an active session — selects that session. */
    onParkingClick: (UserParking) -> Unit,
    /** Tap on the "Aparcar" pill of a per-vehicle row with no active session — enters AddingParking for that vehicle. */
    onParkVehicle: (vehicleId: String) -> Unit,
    /** Tap on the "Mover ubicación" button on the active-parking peek — opens the AddingParking edit flow. */
    onMoveParkingLocation: () -> Unit,
    spotListExpanded: Boolean,
    onToggleSpotList: () -> Unit,
    onSpotSelect: (lat: Double, lon: Double, spotId: String) -> Unit,
    onCameraMove: (lat: Double, lon: Double) -> Unit,
    onEnterReportMode: () -> Unit,
    onRelease: () -> Unit,
    onNavigateExternal: (lat: Double, lon: Double, walking: Boolean) -> Unit,
    onToggle: () -> Unit,
    /** Detection surface (DET-READY-001h) — add a vehicle. */
    onDetectionAddVehicle: () -> Unit = {},
    /** Detection surface — open the permissions flow (CORE or PRODUCER). */
    onDetectionOpenPermissions: () -> Unit = {},
    /** Detection surface — cold-start "mark my spot" (enters AddingParking for the active vehicle). */
    onDetectionMarkSpot: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                // Height is read in the layout phase so sheet drag never causes
                // recomposition of HomeContent — only a re-layout of this node.
                val heightPx = (containerHeightPx - sheetOffsetPx.value)
                    .coerceAtLeast(0f).roundToInt()
                val placeable = measurable.measure(
                    constraints.copy(minHeight = 0, maxHeight = heightPx)
                )
                layout(placeable.width, heightPx) { placeable.place(0, 0) }
            },
        shape = PapShapes.sheet,
        color = MaterialTheme.colorScheme.surfaceContainer,
        // Lift the top edge above the map tiles with the same depth
        // language as the floating search bar and circular FABs.
        // [HOME-DEPTH-001]
        shadowElevation = SHEET_SHADOW_ELEVATION_DP.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // The entire peek handle (drag pill + content row) is tappable to
            // toggle the sheet between peek and the appropriate snap (half for
            // Browse, minimized for non-Browse). Inner interactive children
            // (buttons, text fields, sliders) consume their own clicks and
            // don't propagate. Ripple is suppressed because the visible response
            // to the tap is the sheet animation itself — a flash on the chip /
            // helper text reads as a glitch over content. [SHEET-TAP-002]
            // CORE/GPS blocker: the sheet is a static full message — no drag, no toggle, no expand.
            // [DET-READY-001n]
            val isBlocked = state.detectionUiState == DetectionUiState.BlockedCore
            val toggleInteractionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    // Measure the peek handle with an UNBOUNDED max height so the
                    // reported intrinsic size is independent of the Surface clip.
                    // Without this, dragging the sheet below peek (non-Browse states)
                    // shrinks the Surface, the Box gets clipped, onSizeChanged reports
                    // the clipped height, peekOffsetPx chases minimizedOffsetPx, and
                    // the reset-to-peek effect yanks the sheet back — making drag
                    // feel static. The Box is laid out at min(intrinsic, parentMax)
                    // so siblings (LazyColumn) keep their position; visual overflow
                    // is clipped by the Surface shape. [SHEET-DRAG-003]
                    .layout { measurable, constraints ->
                        val unbounded = constraints.copy(
                            minHeight = 0,
                            maxHeight = androidx.compose.ui.unit.Constraints.Infinity,
                        )
                        val placeable = measurable.measure(unbounded)
                        // Report the exact natural height → peekOffset = container - peekHeight, so the
                        // header's bottom edge (and its divider) sits flush on the bottom-nav divider.
                        // [BUG-PEEK-DIVIDER-ALIGN]
                        if (placeable.height > 0) onPeekHeightChanged(placeable.height.toFloat())
                        val outHeight = placeable.height.coerceAtMost(constraints.maxHeight)
                        layout(placeable.width, outHeight) { placeable.place(0, 0) }
                    }
                    .clickable(
                        interactionSource = toggleInteractionSource,
                        indication = null,
                        enabled = !isBlocked,
                        onClick = onToggle,
                    )
                    .draggable(
                        orientation = Orientation.Vertical,
                        enabled = !isBlocked,
                        state = rememberDraggableState { delta ->
                            coroutineScope.launch {
                                sheetOffsetPx.snapTo(
                                    (sheetOffsetPx.value + delta)
                                        .coerceIn(dragSnap.fullSnapOffsetPx, dragSnap.minimizedOffsetPx),
                                )
                            }
                        },
                        onDragStopped = { velocity ->
                            coroutineScope.launch {
                                val target = dragSnap.snapTarget(sheetOffsetPx.value, velocity)
                                sheetOffsetPx.animateTo(target, dragSnap.snapSpec)
                            }
                        },
                    ),
            ) {
                HomePeekHandle(
                    state = state,
                    onToggle = onToggle,
                    spotListExpanded = spotListExpanded,
                    onToggleSpotList = onToggleSpotList,
                    onDismiss = { onIntent(HomeIntent.SelectItem(null)) },
                    onRelease = onRelease,
                    onRejectSpot = {
                        state.selectedSpot?.id?.let { id ->
                            onIntent(HomeIntent.SendSpotSignal(id, accepted = false))
                        }
                        onIntent(HomeIntent.SelectItem(null))
                    },
                    onNavigateExternal = onNavigateExternal,
                    onCancelReport = { onIntent(HomeIntent.ExitReportMode) },
                    onConfirmReport = { onIntent(HomeIntent.ConfirmReportSpot) },
                    onReportSizeSelected = { onIntent(HomeIntent.SetReportingSize(it)) },
                    onCancelAddZone = { onIntent(HomeIntent.ExitAddZoneMode) },
                    onConfirmAddZone = { onIntent(HomeIntent.ConfirmAddZone) },
                    onUpdateZoneName = { onIntent(HomeIntent.UpdateAddingZoneName(it)) },
                    onUpdateZoneIcon = { onIntent(HomeIntent.UpdateAddingZoneIcon(it)) },
                    onZoneRadiusChanged = { onIntent(HomeIntent.SetZoneRadius(it)) },
                    onZoneIsPrivateToggled = { onIntent(HomeIntent.SetZoneIsPrivate(it)) },
                    onCancelAddParking = { onIntent(HomeIntent.ExitAddParkingMode) },
                    onConfirmAddParking = { onIntent(HomeIntent.ConfirmAddParking) },
                    onMoveParkingLocation = onMoveParkingLocation,
                    onActivateLocation = onDetectionOpenPermissions,
                )
            }

            val isSpotSelected = state.selectedSpot != null
            // When location/GPS is missing the peek already shows the full blocker — suppress the
            // list (and its divider) so the sheet is just that one message. [DET-READY-001n]
            val showList = state.detectionUiState != DetectionUiState.BlockedCore &&
                state.mode is HomeMode.Browse &&
                !state.isParkingSelected &&
                (!isSpotSelected || spotListExpanded)
            // The peek→list divider sits at the bottom of the peek header. The header is now stretched
            // to the fixed peek slot, so this divider lands exactly on the bottom-nav top divider at
            // rest (one continuous hairline) and rides above the nav as the sheet is dragged up — no
            // hide-trick / hysteresis-reveal needed. [BUG-PEEK-DIVIDER-ALIGN]
            if (showList) {
                PapDivider()
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .nestedScroll(sheetNestedScroll),
                    contentPadding = PaddingValues(
                        top = 4.dp,
                        // Reserve the AppBottomNavigation height so the last
                        // list row stays visible above the global nav bar.
                        bottom = 16.dp + bottomContentPadding,
                    ),
                ) {
                    homeSheetItems(
                        state = state,
                        onIntent = onIntent,
                        onCameraMove = onCameraMove,
                        onParkingClick = onParkingClick,
                        onParkVehicle = onParkVehicle,
                        onSpotSelect = onSpotSelect,
                        onEnterReportMode = onEnterReportMode,
                        onDetectionAddVehicle = onDetectionAddVehicle,
                        onDetectionOpenPermissions = onDetectionOpenPermissions,
                        onDetectionMarkSpot = onDetectionMarkSpot,
                    )
                }
            }
        }
    }
}

/**
 * Snap-point bundle for the sheet drag. Centralises the offsets and the
 * fling/soft-drag selection so [HomeBottomSheet] can be called with a single
 * value instead of loose floats.
 *
 * Offset semantics: larger value = sheet top farther from screen top = sheet
 * smaller. So `minimizedOffsetPx >= peekOffsetPx >= halfOffsetPx >= expandedOffsetPx >= fullSnapOffsetPx`.
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
    val snapSpec: androidx.compose.animation.core.AnimationSpec<Float>,
) {
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

// Velocity (px/s) required to snap the sheet on fling; below this the sheet
// stays in place at its closest snap point.
private const val FLING_SNAP_VELOCITY = 1200f

private const val SHEET_SHADOW_ELEVATION_DP = 12
// Matches [AppBottomNavigation]'s top divider alpha so the two hairlines read
// as a single visual boundary when the sheet's list section sits above the nav bar.
