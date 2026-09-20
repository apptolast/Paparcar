package com.rndeveloper.paparcar.presentation.home.sections.sheet

import com.rndeveloper.paparcar.presentation.home.model.FirstStepGuide
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.presentation.home.HomeBrowseListSlice
import com.rndeveloper.paparcar.presentation.home.HomeIntent
import com.rndeveloper.paparcar.presentation.home.HomeMode
import com.rndeveloper.paparcar.presentation.home.HomePeekSlice
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.HomePeekHandle
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.HomeSheetRowsScroll
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.homeSheetItems
import com.rndeveloper.paparcar.ui.components.PapDivider
import com.rndeveloper.paparcar.ui.theme.PapShapes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The drag/layout plumbing of the sheet, bundled so [HomeBottomSheet]'s signature
 * stays at the action/data level. Owned by HomeContent (the [sheetOffsetPx]
 * Animatable is shared with the map, which computes its height from the same
 * source of truth). [HOME-ATOMIZE-001 F3]
 */
@Stable
internal data class HomeSheetFrame(
    val containerHeightPx: Float,
    val sheetOffsetPx: Animatable<Float, AnimationVector1D>,
    val dragSnap: HomeSheetSnap,
    val lazyListState: LazyListState,
    /** [HOME-BROWSE-SURVIVES-THE-DETAIL-SHEET-001] The horizontal rows' scroll, hoisted like the list's. */
    val rowsScroll: HomeSheetRowsScroll,
    val nestedScroll: NestedScrollConnection,
    val bottomContentPadding: Dp,
    val coroutineScope: CoroutineScope,
    val onPeekHeightChanged: (Float) -> Unit,
)

/**
 * The bottom Surface that hosts the peek handle (drag affordance) and the
 * scrollable list of sheet items. Owns the drag gesture and the LazyColumn.
 *
 * Two outbound channels [HOME-ATOMIZE-001 F3]:
 *  - [onIntent] — plain ViewModel intents, emitted directly by the peek variants
 *    and list rows.
 *  - [onAction] — [HomeSheetAction]s that need UI orchestration (sheet motion,
 *    dialogs, camera, navigation), translated in one place by HomeSheetSection.
 */
@Composable
internal fun HomeBottomSheet(
    peek: HomePeekSlice,
    browse: HomeBrowseListSlice,
    frame: HomeSheetFrame,
    /** The control a checklist step is pointing at, if any. [FIRST-STEPS-SHOW-WHERE-NOT-OPEN-001] */
    firstStepGuide: FirstStepGuide? = null,
    /** Browse header subject swap: true while the sheet sits beyond peek (expanded browse
     *  shows the zone counter header instead of the parked car). [UI-SHEET-004] */
    browseShowsZoneHeader: Boolean,
    /** The user has opened Browse once: the collapsed header stays on the lane, not the parked car.
     *  [HOME-PEEK-FOLLOWS-THE-LANE-ONCE-OPENED-001] */
    browseLaneHeaderLatched: Boolean = false,
    onIntent: (HomeIntent) -> Unit,
    onAction: (HomeSheetAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetOffsetPx = frame.sheetOffsetPx
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                // Height is read in the layout phase so sheet drag never causes
                // recomposition of HomeContent — only a re-layout of this node.
                val heightPx = (frame.containerHeightPx - sheetOffsetPx.value)
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
            // ⛔ No CORE/GPS lock here any more: missing location is a WARNING badge inside the peek,
            // never a sheet that stops dragging. [HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001]
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
                        if (placeable.height > 0) frame.onPeekHeightChanged(placeable.height.toFloat())
                        val outHeight = placeable.height.coerceAtMost(constraints.maxHeight)
                        layout(placeable.width, outHeight) { placeable.place(0, 0) }
                    }
                    .clickable(
                        interactionSource = toggleInteractionSource,
                        indication = null,
                        onClick = { onAction(HomeSheetAction.ToggleSheet) },
                    )
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            frame.coroutineScope.launch {
                                sheetOffsetPx.snapTo(
                                    (sheetOffsetPx.value + delta)
                                        .coerceIn(frame.dragSnap.fullSnapOffsetPx, frame.dragSnap.minimizedOffsetPx),
                                )
                            }
                        },
                        onDragStopped = { velocity ->
                            frame.coroutineScope.launch {
                                val target = frame.dragSnap.snapTarget(sheetOffsetPx.value, velocity)
                                sheetOffsetPx.animateTo(target, frame.dragSnap.snapSpec)
                            }
                        },
                    ),
            ) {
                HomePeekHandle(
                    slice = peek,
                    browseShowsZoneHeader = browseShowsZoneHeader,
                    browseLaneHeaderLatched = browseLaneHeaderLatched,
                    onIntent = onIntent,
                    onAction = onAction,
                )
            }

            // Missing location/GPS no longer hides the list: the sheet keeps working and the peek
            // carries the warning badge. [HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001]
            // A selected pin owns the whole surface: its peek now steps to the neighbouring pins on
            // its own, so there is no state left where the list shows underneath one.
            // [UI-PEEK-STEPS-BETWEEN-PINS-001]
            val showList = peek.mode is HomeMode.Browse &&
                !peek.isParkingSelected &&
                peek.selectedSpot == null &&
                peek.selectedFuelStation == null &&
                peek.selectedPublicParking == null
            // The peek→list divider sits at the bottom of the peek header. The header is now stretched
            // to the fixed peek slot, so this divider lands exactly on the bottom-nav top divider at
            // rest (one continuous hairline) and rides above the nav as the sheet is dragged up — no
            // hide-trick / hysteresis-reveal needed. [BUG-PEEK-DIVIDER-ALIGN]
            if (showList) {
                // [HOME-A-LANE-CHANGE-TAKES-THE-CAMERA-WITH-IT-001] Cambiar de carril devuelve la
                // lista a su principio, que es donde está la primera fila del carril nuevo. Sin
                // esto, quien estaba diez filas abajo mirando plazas aterriza en mitad de las
                // gasolineras, y las que se ven no son las primeras — ni las que la cámara acaba de
                // encuadrar. Mapa y lista tienen que estar mirando lo mismo.
                //
                // Ya en el principio es un no-op, así que no interfiere con la primera composición.
                LaunchedEffect(browse.nearbyLens) {
                    frame.lazyListState.animateScrollToItem(0)
                }
                PapDivider()
                LazyColumn(
                    state = frame.lazyListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .nestedScroll(frame.nestedScroll),
                    contentPadding = PaddingValues(
                        top = 4.dp,
                        // Reserve the AppBottomNavigation height so the last
                        // list row stays visible above the global nav bar.
                        bottom = 16.dp + frame.bottomContentPadding,
                    ),
                ) {
                    homeSheetItems(
                        slice = browse,
                        onIntent = onIntent,
                        onAction = onAction,
                        rowsScroll = frame.rowsScroll,
                        firstStepGuide = firstStepGuide,
                    )
                }
            }
        }
    }
}

// HomeSheetSnap (the drag snap-point bundle) lives in HomeSheetPositioning.kt,
// next to the geometry it is built from. [HOME-ATOMIZE-001 F2]

private const val SHEET_SHADOW_ELEVATION_DP = 12
// Matches [AppBottomNavigation]'s top divider alpha so the two hairlines read
// as a single visual boundary when the sheet's list section sits above the nav bar.
