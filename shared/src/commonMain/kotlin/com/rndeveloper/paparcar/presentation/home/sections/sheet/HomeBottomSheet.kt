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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.rndeveloper.paparcar.domain.nearby.NearbySubject
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.HomePeekHandle
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.HomeSheetRowsScroll
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.homeSheetItems
import com.rndeveloper.paparcar.presentation.home.model.HomeNearbyLens
import com.rndeveloper.paparcar.ui.components.PapDivider
import com.rndeveloper.paparcar.ui.theme.PapShapes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.nearbyLensIndexOrNull

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
    val nearbyHeaderHeightPx: Int,
    val onNearbyHeaderHeightChanged: (Int) -> Unit,
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
    var focusedNearbyList by rememberSaveable { mutableStateOf<HomeNearbyLens?>(null) }
    var hasScrolledBrowse by rememberSaveable { mutableStateOf(false) }
    var skipNextLaneAutoScroll by remember { mutableStateOf(false) }
    val onSheetAction: (HomeSheetAction) -> Unit = { action ->
        if (action is HomeSheetAction.ScrollToNearbyList) {
            val targetLens = when (action.subject) {
                NearbySubject.SPOT, NearbySubject.CAR_PARK -> HomeNearbyLens.SPOTS
                NearbySubject.FUEL -> HomeNearbyLens.FUEL
            }
            skipNextLaneAutoScroll = browse.nearbyLens != targetLens
        }
        onAction(action)
    }
    val latestSpotKeys = rememberUpdatedState(browse.filteredSpots.mapTo(mutableSetOf()) { it.id })
    LaunchedEffect(frame.lazyListState, browse.nearbyLens) {
        focusedNearbyList = null
        hasScrolledBrowse = false
        snapshotFlow {
            val layoutInfo = frame.lazyListState.layoutInfo
            val visibleArea = mutableMapOf(
                HomeNearbyLens.SPOTS to 0,
                HomeNearbyLens.PARKINGS to 0,
                HomeNearbyLens.FUEL to 0,
            )
            layoutInfo.visibleItemsInfo.forEach { item ->
                val pixels = (
                    minOf(item.offset + item.size, layoutInfo.viewportEndOffset) -
                        maxOf(item.offset, layoutInfo.viewportStartOffset)
                    ).coerceAtLeast(0)
                when {
                    item.key in latestSpotKeys.value || item.key in SPOT_FOCUS_ITEM_KEYS ->
                        visibleArea[HomeNearbyLens.SPOTS] = visibleArea.getValue(HomeNearbyLens.SPOTS) + pixels
                    item.key in PARKING_FOCUS_ITEM_KEYS ->
                        visibleArea[HomeNearbyLens.PARKINGS] = visibleArea.getValue(HomeNearbyLens.PARKINGS) + pixels
                    item.key == "fuel_lane" ->
                        visibleArea[HomeNearbyLens.FUEL] = visibleArea.getValue(HomeNearbyLens.FUEL) + pixels
                }
            }
            NearbyFocusSnapshot(
                visibleArea = visibleArea,
                viewportPixels = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(1),
                isScrollInProgress = frame.lazyListState.isScrollInProgress,
            )
        }.collect { snapshot ->
            if (snapshot.isScrollInProgress || frame.lazyListState.firstVisibleItemIndex > 0) {
                hasScrolledBrowse = true
            }
            if (!hasScrolledBrowse) return@collect

            val minFocusPixels = (snapshot.viewportPixels * MIN_FOCUS_VIEWPORT_FRACTION).roundToInt()
            val best = snapshot.visibleArea.maxByOrNull { it.value }
            if (best == null || best.value < minFocusPixels) {
                focusedNearbyList = null
                return@collect
            }
            val switchMarginPixels = (snapshot.viewportPixels * FOCUS_SWITCH_MARGIN_FRACTION).roundToInt()
            val currentFocus = focusedNearbyList
            if (currentFocus == null) {
                if (best.value >= minFocusPixels) focusedNearbyList = best.key
            } else {
                val currentPixels = snapshot.visibleArea[currentFocus] ?: 0
                if (
                    best.key != currentFocus &&
                    best.value >= minFocusPixels &&
                    best.value > currentPixels + switchMarginPixels
                ) {
                    focusedNearbyList = best.key
                }
            }
        }
    }
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
                val peekListLens = focusedNearbyList ?: peek.nearbyLens
                val peekListCount = when (peekListLens) {
                    HomeNearbyLens.SPOTS -> peek.freeCount
                    HomeNearbyLens.PARKINGS -> peek.publicParkings.size
                    HomeNearbyLens.FUEL -> peek.fuelStations.size
                }
                HomePeekHandle(
                    slice = peek.copy(
                        nearbyLens = peekListLens,
                        laneCount = peekListCount,
                    ),
                    browseShowsZoneHeader = browseShowsZoneHeader && focusedNearbyList != null,
                    browseLaneHeaderLatched = browseLaneHeaderLatched && focusedNearbyList != null,
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
            var hasPresentedBrowseList by remember { mutableStateOf(false) }
            var preserveScrollOnReturn by remember { mutableStateOf(false) }
            var wasBrowseExpanded by remember { mutableStateOf(false) }
            LaunchedEffect(showList, browseShowsZoneHeader, frame.lazyListState) {
                val expandedBrowse = showList && browseShowsZoneHeader
                if (!showList && hasPresentedBrowseList) {
                    preserveScrollOnReturn = true
                }
                if (expandedBrowse && !wasBrowseExpanded) {
                    if (!preserveScrollOnReturn && frame.lazyListState.layoutInfo.totalItemsCount > 0) {
                        frame.lazyListState.scrollToItem(0)
                    }
                    preserveScrollOnReturn = false
                }
                if (showList) hasPresentedBrowseList = true
                wasBrowseExpanded = expandedBrowse
            }
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
                //
                // Y la hoja SUBE hasta la mitad, porque desde el peek un cambio de carril mueve el
                // mapa y devuelve la lista a su sitio sin que se vea ninguna de las dos cosas: el
                // usuario pide gasolineras y lo único que cambia es una pestaña.
                //
                // ⛔ **Sube, nunca baja.** Si la hoja ya estaba por encima de la mitad (expandida o
                // entera) se queda donde el usuario la dejó: pedir otro carril no es pedir menos
                // sitio, y encogerle la lista al que la tenía abierta sería quitarle algo que no ha
                // soltado. Con el offset, "más arriba" es un valor MENOR.
                //
                // ⛔ **Y no en la primera composición**: Home abre en peek y ahí se queda hasta que
                // alguien toque una pestaña. Por eso el primer paso del efecto sólo arma.
                var laneMotionArmed by remember { mutableStateOf(false) }
                LaunchedEffect(browse.nearbyLens) {
                    // ⛔ A la CABECERA DEL CARRIL, no al índice 0
                    // [HOME-THE-CAMERA-FRAMES-WHAT-THE-LIST-SHOWS-001]. El 0 es el principio de toda
                    // la hoja —por encima de la tira de coches y de la superficie de detección—, así
                    // que «volver al principio del carril» dejaba al usuario por encima de todo eso
                    // y obligándole a bajar otra vez. Reportado en uso: «cada vez que cambie tiene
                    // que volver a scrollear para bajar al contenido».
                    //
                    // Sin cabecera compuesta no se mueve nada: quien está mirando sus coches no ha
                    // pedido que le desplacen la hoja.
                    if (skipNextLaneAutoScroll) {
                        skipNextLaneAutoScroll = false
                        laneMotionArmed = true
                        return@LaunchedEffect
                    }
                    if (!laneMotionArmed) {
                        laneMotionArmed = true
                        return@LaunchedEffect
                    }
                    frame.lazyListState.nearbyLensIndexOrNull()
                        ?.let { frame.lazyListState.animateScrollToItem(it) }
                    val half = frame.dragSnap.halfOffsetPx
                    if (frame.sheetOffsetPx.value > half) {
                        frame.sheetOffsetPx.animateTo(half, frame.dragSnap.snapSpec)
                    }
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
                        onAction = onSheetAction,
                        rowsScroll = frame.rowsScroll,
                        firstStepGuide = firstStepGuide,
                        focusedNearbyList = focusedNearbyList,
                        onNearbyHeaderHeightChanged = frame.onNearbyHeaderHeightChanged,
                    )
                }
            }
        }
    }
}

// HomeSheetSnap (the drag snap-point bundle) lives in HomeSheetPositioning.kt,
// next to the geometry it is built from. [HOME-ATOMIZE-001 F2]

private const val SHEET_SHADOW_ELEVATION_DP = 12
private data class NearbyFocusSnapshot(
    val visibleArea: Map<HomeNearbyLens, Int>,
    val viewportPixels: Int,
    val isScrollInProgress: Boolean,
)

private val SPOT_FOCUS_ITEM_KEYS = setOf(
    "community_spots_header",
    "skeleton",
    "empty_filtered",
    "empty",
    "report_spot_cta",
)
private val PARKING_FOCUS_ITEM_KEYS = setOf("parkings_header", "parkings_lane")
private const val MIN_FOCUS_VIEWPORT_FRACTION = 0.30f
private const val FOCUS_SWITCH_MARGIN_FRACTION = 0.10f
// Matches [AppBottomNavigation]'s top divider alpha so the two hairlines read
// as a single visual boundary when the sheet's list section sits above the nav bar.
