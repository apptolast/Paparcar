package io.apptolast.paparcar.presentation.home.sections.sheet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.clickable
import androidx.compose.runtime.remember
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.presentation.home.HomeIntent
import io.apptolast.paparcar.presentation.home.HomeMode
import io.apptolast.paparcar.presentation.home.HomeState
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
    onHeaderHeightChanged: (Float) -> Unit,
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
    onZoneDismiss: () -> Unit = {},
    onEditZone: (zoneId: String) -> Unit = {},
    onDeleteZone: (zoneId: String) -> Unit = {},
    onToggle: () -> Unit,
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
            // In Browse-with-no-selection mode the entire peek handle (drag pill
            // + content row) is tappable to toggle the sheet. The clickable sits
            // alongside draggable on the same Box so pointer events reach ONE
            // composable, avoiding the press-state race that prevents the ripple
            // from showing when clickable is nested below draggable.
            val isBrowseTappable = state.mode is HomeMode.Browse && state.selectedItemId == null
            Box(
                modifier = Modifier
                    .onSizeChanged { size ->
                        // Guard 0-height transients during the first compose
                        // pass so peekHeightPx never collapses the offset math.
                        if (size.height > 0) onPeekHeightChanged(size.height.toFloat())
                    }
                    .then(if (isBrowseTappable) Modifier.clickable(onClick = onToggle) else Modifier)
                    .draggable(
                        orientation = Orientation.Vertical,
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
                    onHeaderHeightChanged = onHeaderHeightChanged,
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
                    onZoneDismiss = onZoneDismiss,
                    onEditZone = onEditZone,
                    onDeleteZone = onDeleteZone,
                )
            }

            val isSpotSelected = state.selectedSpot != null
            val showList = state.mode is HomeMode.Browse &&
                !state.isParkingSelected &&
                state.selectedZoneId == null &&
                (!isSpotSelected || spotListExpanded)
            if (showList) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = PEEK_LIST_DIVIDER_ALPHA),
                )
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
 * smaller. So `minimizedOffsetPx >= peekOffsetPx >= halfOffsetPx >= fullSnapOffsetPx`.
 *
 * [minimizedOffsetPx] is the extra "drag-down to header-only" snap point used
 * in non-Browse states. In Browse it equals [peekOffsetPx] so the snap logic
 * collapses to the original three-point behaviour. [SHEET-DRAG-001]
 */
internal data class HomeSheetSnap(
    val peekOffsetPx: Float,
    val halfOffsetPx: Float,
    val fullSnapOffsetPx: Float,
    val minimizedOffsetPx: Float,
    val snapSpec: androidx.compose.animation.core.AnimationSpec<Float>,
) {
    /** Returns the snap target the sheet should animate to after the user releases the drag. */
    fun snapTarget(current: Float, velocityYPxPerSec: Float): Float = when {
        velocityYPxPerSec < -FLING_SNAP_VELOCITY -> {
            // Fling up: minimized → peek, peek → half, half → full.
            when {
                current > peekOffsetPx -> peekOffsetPx
                current > halfOffsetPx -> halfOffsetPx
                else -> fullSnapOffsetPx
            }
        }
        velocityYPxPerSec > FLING_SNAP_VELOCITY -> {
            // Fling down: full → half, half → peek, peek → minimized.
            when {
                current < halfOffsetPx -> halfOffsetPx
                current < peekOffsetPx -> peekOffsetPx
                else -> minimizedOffsetPx
            }
        }
        else -> {
            // Soft drag: snap to the nearest of minimized / peek / half / full.
            val distMin = (current - minimizedOffsetPx).absoluteValue
            val distPeek = (current - peekOffsetPx).absoluteValue
            val distHalf = (current - halfOffsetPx).absoluteValue
            val distFull = (current - fullSnapOffsetPx).absoluteValue
            val (offset, _) = listOf(
                fullSnapOffsetPx to distFull,
                halfOffsetPx to distHalf,
                peekOffsetPx to distPeek,
                minimizedOffsetPx to distMin,
            ).minBy { it.second }
            offset
        }
    }.coerceIn(fullSnapOffsetPx, minimizedOffsetPx)
}

// Velocity (px/s) required to snap the sheet on fling; below this the sheet
// stays in place at its closest snap point.
private const val FLING_SNAP_VELOCITY = 1200f

private const val SHEET_SHADOW_ELEVATION_DP = 12
private const val PEEK_LIST_DIVIDER_ALPHA = 0.08f
