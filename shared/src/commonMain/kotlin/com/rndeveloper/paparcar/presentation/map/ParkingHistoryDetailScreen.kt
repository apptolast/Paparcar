@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.map

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EditLocationAlt
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.presentation.util.collectAsStateLifecycleAware
import com.rndeveloper.paparcar.presentation.vehicles.MONTH_SHORT_RES
import com.rndeveloper.paparcar.domain.matching.InferredRoute
import com.rndeveloper.paparcar.domain.detection.ParkingDetectionSource
import com.rndeveloper.paparcar.domain.detection.detectionSource
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.util.PolylineCodec
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.monitoringStatus
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheet
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetLead
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetStepper
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.peek.PeekMetaRow
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.peek.vehicleSummary
import com.rndeveloper.paparcar.presentation.util.locationDisplayText
import com.rndeveloper.paparcar.presentation.util.rememberOpenExternalNavigation
import com.rndeveloper.paparcar.ui.components.GlassSurface
import com.rndeveloper.paparcar.ui.components.PapCollapsingTopBarScaffold
import com.rndeveloper.paparcar.ui.components.PapFooterButton
import com.rndeveloper.paparcar.ui.components.PapFooterButtonStyle
import com.rndeveloper.paparcar.ui.components.PaparcarMapConfig
import com.rndeveloper.paparcar.ui.components.PaparcarMapView
import com.rndeveloper.paparcar.ui.components.PapShimmerBox
import com.rndeveloper.paparcar.presentation.map.components.MapControlButtons
import com.rndeveloper.paparcar.presentation.home.sections.header.components.MapTypeToggle
import com.swmansion.kmpmaps.core.MapType
import com.rndeveloper.paparcar.ui.theme.PapMotion
import com.rndeveloper.paparcar.ui.theme.PapShapes
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.VehicleWatch
import com.rndeveloper.paparcar.ui.theme.vehicleIdentityColor
import com.rndeveloper.paparcar.ui.theme.watch
import com.rndeveloper.paparcar.ui.theme.PapColor
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.history_section_label
import paparcar.composeapp.generated.resources.home_peek_car_parked_label
import paparcar.composeapp.generated.resources.home_peek_vehicle_parked_label
import paparcar.composeapp.generated.resources.map_cd_back
import paparcar.composeapp.generated.resources.parking_detail_detection_assisted
import paparcar.composeapp.generated.resources.parking_detail_detection_auto
import paparcar.composeapp.generated.resources.parking_detail_detection_bluetooth
import paparcar.composeapp.generated.resources.parking_detail_detection_home
import paparcar.composeapp.generated.resources.parking_detail_detection_manual
import paparcar.composeapp.generated.resources.location_fallback_parking
import paparcar.composeapp.generated.resources.common_directions
import paparcar.composeapp.generated.resources.parking_detail_next
import paparcar.composeapp.generated.resources.parking_detail_not_in_history
import paparcar.composeapp.generated.resources.parking_detail_prev
import paparcar.composeapp.generated.resources.parking_detail_route_inferred_no
import paparcar.composeapp.generated.resources.parking_detail_route_inferred_question
import paparcar.composeapp.generated.resources.parking_detail_route_inferred_yes
import paparcar.composeapp.generated.resources.parking_detail_route_recalculating
import kotlin.time.Instant
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.peek.ApproximateZoneRow
import paparcar.composeapp.generated.resources.parking_detail_navigate_area_action

@Composable
fun ParkingHistoryDetailScreen(
    onNavigateBack: () -> Unit = {},
    initialFocus: Pair<Double, Double>? = null,
    sessionId: String = "",
    viewModel: ParkingHistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateLifecycleAware()
    val openNavigation = rememberOpenExternalNavigation()

    LaunchedEffect(sessionId) {
        if (sessionId.isNotBlank()) {
            viewModel.handleIntent(ParkingHistoryIntent.SetFocusedSession(sessionId))
        }
    }

    val parkingGpsPoint = remember(initialFocus) {
        initialFocus?.let { (lat, lon) ->
            GpsPoint(lat, lon, accuracy = 0f, timestamp = 0L, speed = 0f)
        }
    }

    // Camera + marker follow the focused session so the prev/next stepper recenters the map on each
    // step. Seeded from the nav-arg coords for the first frame, before the session resolves. [HISTORY-DETAIL-001]
    var cameraTarget by remember {
        mutableStateOf(initialFocus?.let { (lat, lon) -> CameraTarget(lat = lat, lon = lon, zoom = 16f) })
    }
    var cameraToken by remember { mutableIntStateOf(0) }
    // Tipo de mapa: estado LOCAL de la pantalla. En Home vive en `HomeState.mapType`, también en
    // memoria — no hay persistencia de este ajuste en ningún sitio, así que no se inventa una.
    // [UI-HISTORY-DETAIL-HAS-THE-MAP-CONTROLS-001]
    var mapType by remember { mutableStateOf(MapType.TERRAIN) }
    // ONE unwrap, in the open: everything below reads a session that is either resolved or absent,
    // and the sheet gets the full answer (unresolved / not found / resolved) so it can say which.
    // [UI-HISTORY-DETAIL-MUST-NOT-SPEAK-BEFORE-IT-KNOWS-001]
    val focusedParking = state.focusedParking
    val focusedSession = (focusedParking as? FocusedParking.Resolved)?.session
    LaunchedEffect(focusedSession?.id) {
        val loc = focusedSession?.location ?: return@LaunchedEffect
        cameraToken += 1
        cameraTarget = CameraTarget(lat = loc.latitude, lon = loc.longitude, zoom = 16f, token = cameraToken)
    }

    val density = LocalDensity.current
    var sheetHeightPx by remember { mutableIntStateOf(0) }
    val mapBleedPx = with(density) { MAP_BOTTOM_BLEED.toPx() }

    // The trip that led to this parking, drawn ONLY once it is the final on-road line (routeSnapped).
    // While the post-park worker is still snapping the raw fixes, the line is withheld and a
    // "recalculating" chip is shown instead — we never draw the jagged raw trace. Empty for legacy /
    // BT parks (no route). [DET-ROUTE-TRACK-001][DET-ROUTE-SNAP-STORE-001]
    val routeSnapped = focusedSession?.routeSnapped == true
    val routeRecalculating = !routeSnapped && !focusedSession?.routePolyline.isNullOrEmpty()
    val routeTrail = remember(focusedSession?.routePolyline, routeSnapped) {
        mutableStateOf(if (routeSnapped) PolylineCodec.decode(focusedSession.routePolyline) else emptyList())
    }
    // Provenance-aware segments: measured stretches solid, road-inferred stretches (reconstructed
    // data holes) dimmed until confirmed, dropped once rejected. [ROUTE-GAP-HONEST-001]
    val routeSegments = remember(
        focusedSession?.routePolyline,
        focusedSession?.routeInferredSpans,
        focusedSession?.routeInferredResolution,
        routeSnapped,
    ) {
        mutableStateOf(
            if (routeSnapped) {
                InferredRoute.split(
                    points = routeTrail.value,
                    encoded = focusedSession.routeInferredSpans,
                    resolution = focusedSession.routeInferredResolution,
                )
            } else emptyList()
        )
    }
    val askInferredRoute = focusedSession?.hasPendingInferredRoute == true
    // Origin vertex — the same departure dot Home draws on a live trip, here on the stored route's
    // first point, so the line visibly STARTS somewhere instead of reading as cut off. Null when
    // there is no drawable route (no marker). [ROUTE-QUALITY-001]
    val routeStart = remember(focusedSession?.routePolyline, routeSnapped) {
        mutableStateOf(routeTrail.value.firstOrNull())
    }
    // End vertex — the origin dot's mirror on the stored route's LAST point, tight against the
    // parked-car marker, so the line terminates cleanly instead of an abrupt cut. [ROUTE-END-AT-CAR-001]
    val routeEnd = remember(focusedSession?.routePolyline, routeSnapped) {
        mutableStateOf(routeTrail.value.lastOrNull())
    }

    // The screen says WHERE you are in the app's own voice — "Historial", with the standard back —
    // instead of a bare arrow floating on a map, and the card below is freed from having to label
    // itself. Same top bar as Ajustes / Vehículos / Bluetooth. [UI-PEEK-STEPS-BETWEEN-PINS-001]
    PapCollapsingTopBarScaffold(
        title = stringResource(Res.string.history_section_label),
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(Res.string.map_cd_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    ) { contentPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        PaparcarMapView(
            config = PaparcarMapConfig(showFreeSpotOverlays = false, mapType = mapType),
            spots = emptyList(),
            userLocation = state.userLocation,
            tripTrail = routeTrail,
            tripSegments = routeSegments,
            departurePoint = routeStart,
            arrivalPoint = routeEnd,
            // The caller's coords seed the first frame (they ARE this parking's), but there is no
            // third fallback: painting the ACTIVE session here put today's pin on a historic map.
            // No resolved session and no seed ⇒ no pin. [UI-HISTORY-DETAIL-MUST-NOT-SPEAK-BEFORE-IT-KNOWS-001]
            parkingLocation = focusedSession?.location ?: parkingGpsPoint,
            parkingVehicleSize = focusedSession?.sizeCategory ?: state.focusedVehicle?.sizeCategory,
            parkingVehicleCarbody = focusedSession?.carbodyType ?: state.focusedVehicle?.carbodyType,
            parkingVehicleColor = state.focusedVehicle?.color,
            parkingIsActive = focusedSession?.isActive == true,
            // [DET-DOUBT-MUST-REACH-THE-SCREEN-001] La duda que la sesión midió y guardó.
            parkingZoneRadiusMeters = focusedSession?.zoneRadiusMeters,
            onSpotClick = {},
            cameraTarget = cameraTarget,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .layout { measurable, constraints ->
                    val heightPx = (constraints.maxHeight - sheetHeightPx + mapBleedPx.toInt())
                        .coerceIn(0, constraints.maxHeight)
                    val placeable = measurable.measure(
                        constraints.copy(minHeight = 0, maxHeight = heightPx)
                    )
                    layout(placeable.width, heightPx) { placeable.place(0, 0) }
                },
        )

        // While the driven route is still being snapped onto streets by the post-park worker, show a
        // small "recalculating" chip instead of the raw line. [DET-ROUTE-SNAP-STORE-001]
        if (routeRecalculating) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = CircleShape,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = stringResource(Res.string.parking_detail_route_recalculating),
                        style = PaparcarType.current.label,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        // A stretch of this route was reconstructed over a GPS silence — ask the user to vouch for
        // it (Sí = draw it like the measured line; No = cut it). Floating over the map → glass.
        // [ROUTE-GAP-HONEST-001]
        if (askInferredRoute) {
            InferredRouteQuestionCard(
                onAnswer = { confirmed ->
                    viewModel.handleIntent(
                        ParkingHistoryIntent.ResolveInferredRoute(focusedSession.id, confirmed)
                    )
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp, start = 24.dp, end = 24.dp),
            )
        }

        HistoryDetailSheet(
            focused = focusedParking,
            vehicle = state.focusedVehicle,
            hasOlder = state.hasOlder,
            hasNewer = state.hasNewer,
            onOlder = { viewModel.handleIntent(ParkingHistoryIntent.FocusOlder) },
            onNewer = { viewModel.handleIntent(ParkingHistoryIntent.FocusNewer) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onGloballyPositioned { sheetHeightPx = it.size.height },
            onNavigate = { lat, lon -> openNavigation(lat, lon, false) },
        )

        // Los mandos del mapa, los mismos de Home: centrar en mí · centrar en ESTE aparcamiento ·
        // encuadrar ambos. Empujan por el mismo carril de cámara que el stepper.
        // [UI-HISTORY-DETAIL-HAS-THE-MAP-CONTROLS-001]
        val pinLocation = focusedSession?.location ?: parkingGpsPoint
        MapControlButtons(
            userLocation = state.userLocation,
            parkingLocation = pinLocation,
            sheetBottomPadding = with(density) { sheetHeightPx.toDp() },
            onMyLocation = {
                state.userLocation?.let { me ->
                    cameraToken += 1
                    cameraTarget = CameraTarget(
                        lat = me.latitude, lon = me.longitude, zoom = 16f, token = cameraToken,
                    )
                }
            },
            onParking = {
                pinLocation?.let { pin ->
                    cameraToken += 1
                    cameraTarget = CameraTarget(
                        lat = pin.latitude, lon = pin.longitude, zoom = 16f, token = cameraToken,
                    )
                }
            },
            onMidpoint = {
                val me = state.userLocation
                val pin = pinLocation
                if (me != null && pin != null) {
                    cameraToken += 1
                    // Sin zoom: el encuadre lo decide el par de puntos, no una cifra fija.
                    cameraTarget = CameraTarget(
                        lat = pin.latitude,
                        lon = pin.longitude,
                        token = cameraToken,
                        boundsLat2 = me.latitude,
                        boundsLon2 = me.longitude,
                    )
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd),
        )

        // El tipo de mapa se cambia arriba a la derecha, fuera del camino de la ficha — en Home vive
        // en la cabecera, que aquí la ocupa la top bar de "Historial".
        MapTypeToggle(
            currentType = mapType,
            onTypeSelected = { mapType = it },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 12.dp, top = 12.dp),
        )
    }
    }
}

@Composable
private fun InferredRouteQuestionCard(
    onAnswer: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Floating over the map → glass, per the map-chrome rule. The dimmed stretch on the map is the
    // subject; the card only asks. [ROUTE-GAP-HONEST-001]
    GlassSurface(modifier = modifier) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                text = stringResource(Res.string.parking_detail_route_inferred_question),
                style = PaparcarType.current.body,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = { onAnswer(false) }) {
                    Text(
                        text = stringResource(Res.string.parking_detail_route_inferred_no),
                        style = PaparcarType.current.cta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onAnswer(true) }) {
                    Text(
                        text = stringResource(Res.string.parking_detail_route_inferred_yes),
                        style = PaparcarType.current.cta,
                        color = PapColor.actionText,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Detail sheet — non-draggable card anchored at the bottom
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The bottom detail card of the history map. Extracted as `internal` (map-free, pure inputs) so the
 * Dev Catalog gallery + previews can render the detection-label / vehicle-icon / stepper variants
 * without a live map. [HISTORY-DETAIL-001]
 *
 * It is the SAME card as Home's: `PapSheet`, the one bottom-sheet molde, with the vehicle as lead,
 * the address as title and the chevrons in the header's trailing cluster. It used to be a
 * hand-rolled copy — same intent, its own paddings — and the copy drifted: its header sat lower
 * than Home's because it lacked the molde's reserved 3-line height. What it does NOT take from the
 * molde is the dismiss ×: this is a whole screen, and its way out is the top bar's back arrow.
 * [UI-PEEK-STEPS-BETWEEN-PINS-001] [UI-SHEET-001] [UI-SHEET-006]
 *
 * Directional page-turn: stepping › (newer) slides the incoming parking in FROM THE RIGHT, ‹ (older)
 * from the left — the motion itself teaches the timeline mapping (past ← → present) regardless of
 * which convention the user expects. Transitions are keyed on the session ID (not the object) so a
 * Firestore re-emit of the same session (late geocode, cosmetic drift) refreshes in place without
 * re-triggering the slide [BUG-PEEK-JITTER-001]; the direction falls out of the two timestamps.
 * [HISTORY-DETAIL-002]
 *
 * The swipe-to-step gesture is the molde's too (`PapSheet` mounts it whenever it carries a stepper),
 * so finger, chevrons and slide can't drift apart across the two surfaces.
 */
@Composable
fun HistoryDetailSheet(
    focused: FocusedParking,
    vehicle: Vehicle?,
    hasOlder: Boolean,
    hasNewer: Boolean,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
    onNavigate: (lat: Double, lon: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = PapShapes.sheet,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = SHEET_ELEVATION,
    ) {
        Box(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(top = SHEET_TOP_PAD.dp, bottom = SHEET_BOTTOM_PAD.dp),
        ) {
            AnimatedContent(
                targetState = focused to vehicle,
                // Keyed on WHICH face is showing: the two data-less faces are one page each, so the
                // skeleton doesn't re-enter on every recomposition and the arrival of the real card
                // is a single page turn. [UI-HISTORY-DETAIL-MUST-NOT-SPEAK-BEFORE-IT-KNOWS-001]
                contentKey = { (target, _) ->
                    when (target) {
                        is FocusedParking.Resolved -> target.session.id
                        FocusedParking.Unresolved -> KEY_UNRESOLVED
                        FocusedParking.NotFound -> KEY_NOT_FOUND
                    }
                },
                transitionSpec = {
                    val toNewer = (targetState.first.timestampOrZero()) >
                        (initialState.first.timestampOrZero())
                    val from = if (toNewer) 1 else -1
                    ContentTransform(
                        targetContentEnter = slideInHorizontally(PapMotion.emphasized()) { it * from } +
                            fadeIn(PapMotion.emphasized()),
                        initialContentExit = slideOutHorizontally(PapMotion.emphasized()) { -it * from } +
                            fadeOut(PapMotion.emphasized()),
                    )
                },
                label = "history_step",
            ) { (shownFocused, shownVehicle) ->
                when (shownFocused) {
                    FocusedParking.Unresolved -> HistoryParkingSkeleton()
                    FocusedParking.NotFound -> HistoryParkingMissing()
                    is FocusedParking.Resolved -> HistoryParkingCard(
                        session = shownFocused.session,
                        vehicle = shownVehicle,
                        hasOlder = hasOlder,
                        hasNewer = hasNewer,
                        onOlder = onOlder,
                        onNewer = onNewer,
                        onNavigate = onNavigate,
                    )
                }
            }
        }
    }
}

/** Page-turn direction only: a face without a session has no time to compare. */
private fun FocusedParking.timestampOrZero(): Long =
    (this as? FocusedParking.Resolved)?.session?.location?.timestamp ?: 0L

/**
 * The sheet while the history is still being read. Mirrors the card's anatomy (lead glyph, eyebrow,
 * title, two meta rows, footer button) so nothing jumps when the real data lands, and reuses
 * [PapShimmerBox] like `PeekLocationSkeleton` does in the same sheet molde.
 * [UI-HISTORY-DETAIL-MUST-NOT-SPEAK-BEFORE-IT-KNOWS-001]
 */
@Composable
private fun HistoryParkingSkeleton() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            PapShimmerBox(modifier = Modifier.size(SKELETON_LEAD_DP.dp), shape = CircleShape)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PapShimmerBox(
                    modifier = Modifier.fillMaxWidth(SKELETON_EYEBROW_FRACTION).height(10.dp),
                    shape = RoundedCornerShape(5.dp),
                    alphaScale = SKELETON_SECONDARY_ALPHA,
                )
                PapShimmerBox(
                    modifier = Modifier.fillMaxWidth(SKELETON_TITLE_FRACTION).height(16.dp),
                    shape = RoundedCornerShape(8.dp),
                )
            }
        }
        Spacer(Modifier.height(SKELETON_META_TOP_GAP.dp))
        repeat(SKELETON_META_ROWS) {
            PapShimmerBox(
                modifier = Modifier
                    .padding(bottom = SKELETON_META_ROW_GAP.dp)
                    .fillMaxWidth(SKELETON_META_FRACTION)
                    .height(12.dp),
                shape = RoundedCornerShape(6.dp),
                alphaScale = SKELETON_SECONDARY_ALPHA,
            )
        }
        Spacer(Modifier.height(SKELETON_ACTION_TOP_GAP.dp))
        PapShimmerBox(
            modifier = Modifier.fillMaxWidth().height(SKELETON_ACTION_HEIGHT_DP.dp),
            shape = RoundedCornerShape(SKELETON_ACTION_CORNER_DP.dp),
            alphaScale = SKELETON_SECONDARY_ALPHA,
        )
    }
}

/**
 * The history arrived and this parking is not in it — retracted, deleted, or a dead deep link. It
 * says so instead of rendering an empty card that reads as "this parking has no data".
 * [UI-HISTORY-DETAIL-MUST-NOT-SPEAK-BEFORE-IT-KNOWS-001]
 */
@Composable
private fun HistoryParkingMissing() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = MISSING_V_PAD.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.parking_detail_not_in_history),
            style = PaparcarType.current.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One parking as Home's sheet molde renders it — the page the timeline turns. */
@Composable
private fun HistoryParkingCard(
    // Non-null by construction: the card is only reached through [FocusedParking.Resolved], so it can
    // no longer be asked to render a parking it does not have.
    // [UI-HISTORY-DETAIL-MUST-NOT-SPEAK-BEFORE-IT-KNOWS-001]
    session: UserParking,
    vehicle: Vehicle?,
    hasOlder: Boolean,
    hasNewer: Boolean,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
    onNavigate: (lat: Double, lon: Double) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val title = locationDisplayText(
        placeInfo = session.placeInfo,
        address = session.address,
    ) ?: stringResource(Res.string.location_fallback_parking)
    val subtitle = session.address?.city?.takeIf { it.isNotBlank() }
        ?.let { city ->
            session.address.region?.takeIf { it.isNotBlank() }?.let { "$city, $it" } ?: city
        }

    // ONE accent for the whole card, and it is the vehicle's watch method — blue for a BT-watched
    // car, brand green for an assisted one, grey unwatched — through the single resolver, exactly
    // like Home and Vehículos. A closed session keeps its muted tone: the accent marks what is LIVE.
    // [UI-COLOR-DOCTRINE-001][UI-HISTORY-IDENTITY-AND-SOURCE-001]
    val identity = vehicleIdentityColor(vehicle?.monitoringStatus()?.watch() ?: VehicleWatch.Off)
    val isLive = session.isActive
    val metaTint = if (isLive) identity else cs.onSurfaceVariant

    // The eyebrow carries the car, the same way Home's parking peek does: the NAME wears the watch
    // colour, any state word around it stays neutral. A live session says so ("… · APARCADO"); a
    // closed one is just the car it belonged to — which is also what the removed section label used
    // to convey, now without a screen-wide title competing with the top bar. [UI-COLOR-DOCTRINE-001]
    val vehicleName = vehicleSummary(vehicle)
    val eyebrow = when {
        vehicleName == null -> stringResource(Res.string.home_peek_car_parked_label)
        isLive -> stringResource(Res.string.home_peek_vehicle_parked_label, vehicleName)
        else -> vehicleName
    }

    val (lat, lon) = session.location.latitude to session.location.longitude

    PapSheet(
        // Body shape from the session (captured at park time), falling back to the registered
        // vehicle; colour only lives on the vehicle. [HISTORY-DETAIL-001]
        lead = PapSheetLead.Vehicle(
            carbody = session.carbodyType ?: vehicle?.carbodyType,
            size = session.sizeCategory ?: vehicle?.sizeCategory,
            color = vehicle?.color,
        ),
        eyebrow = eyebrow,
        eyebrowColor = cs.onSurfaceVariant,
        eyebrowHighlight = vehicleName,
        eyebrowHighlightColor = identity,
        title = title,
        subtitle = subtitle,
        // No dismiss: this is a screen, and the top bar's back arrow is its way out.
        trailing = null,
        stepper = PapSheetStepper(
            prevContentDescription = stringResource(Res.string.parking_detail_prev),
            nextContentDescription = stringResource(Res.string.parking_detail_next),
            onPrev = onOlder.takeIf { hasOlder },
            onNext = onNewer.takeIf { hasNewer },
        ),
        meta = {
            run {
                DateTimeRow(timestampMs = session.location.timestamp, tint = metaTint)
                DetectionRow(session = session, tint = metaTint)
                // [DET-DOUBT-MUST-REACH-THE-SCREEN-001] El mismo componente que ya usa el peek de
                // Home — no se re-implementa una fila «icono + texto» [UI-LIST-ITEM-001]. Sin él,
                // esta pantalla presentaba una zona de 250 m como un punto exacto, con un botón
                // «Navegar a esta ubicación» debajo.
                ApproximateZoneRow(zoneRadiusMeters = session.zoneRadiusMeters, accentColor = metaTint)
            }
        },
        actions = {
            PapFooterButton(
                // [DET-DOUBT-MUST-REACH-THE-SCREEN-001] Un pin aproximado no es «esta ubicación»:
                // navegar te lleva al CENTRO de una zona, y decir lo contrario es prometer una
                // precisión que la propia app ya se ha negado a afirmar.
                // ⚠️ La clave del caso exacto es `common_directions` desde master — este ticket se
                // rebasó sobre ese renombrado, no lo revierte.
                label = if (session.isApproximate) {
                    stringResource(Res.string.parking_detail_navigate_area_action)
                } else {
                    stringResource(Res.string.common_directions)
                },
                leadingIcon = Icons.Rounded.Navigation,
                onClick = { onNavigate(lat, lon) },
                style = PapFooterButtonStyle.Filled,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DateTimeRow(timestampMs: Long, tint: Color) {
    if (timestampMs <= 0L) return
    val dateTime = remember(timestampMs) {
        Instant.fromEpochMilliseconds(timestampMs)
            .toLocalDateTime(TimeZone.currentSystemDefault())
    }
    val timeStr = "${dateTime.hour.toString().padStart(2, '0')}:" +
        dateTime.minute.toString().padStart(2, '0')
    // Localized month via the shared history resources — month.name is the English enum name and
    // bypassed i18n. [UI-REGRESSION]
    val monthStr = stringResource(MONTH_SHORT_RES[dateTime.month.ordinal])
    val dateStr = "${dateTime.day} $monthStr ${dateTime.year}"

    PeekMetaRow(icon = Icons.Rounded.Schedule, tint = tint, text = "$dateStr · $timeStr")
}

/**
 * Who put this pin. The icon carries the STRATEGY (the same Bluetooth / Radar pair Home uses for a
 * vehicle's watch tier) and the text carries the TIER NAME the user already reads on the Permissions
 * card — so "auto-detected" stops hiding which of the two independent strategies actually fired.
 * A legacy row without provenance keeps saying just "auto-detected": we don't invent a tier we can't
 * prove. [UI-HISTORY-IDENTITY-AND-SOURCE-001][DET-PIN-PROVENANCE-001]
 */
@Composable
private fun DetectionRow(session: UserParking, tint: Color) {
    val (icon, label) = when (session.detectionSource()) {
        ParkingDetectionSource.Bluetooth ->
            Icons.Rounded.Bluetooth to stringResource(Res.string.parking_detail_detection_bluetooth)
        ParkingDetectionSource.Assisted ->
            Icons.Rounded.Radar to stringResource(Res.string.parking_detail_detection_assisted)
        ParkingDetectionSource.Unknown ->
            Icons.Rounded.Bolt to stringResource(Res.string.parking_detail_detection_auto)
        ParkingDetectionSource.Manual ->
            Icons.Rounded.EditLocationAlt to stringResource(Res.string.parking_detail_detection_manual)
        ParkingDetectionSource.PrivateZone ->
            Icons.Rounded.Home to stringResource(Res.string.parking_detail_detection_home)
    }
    PeekMetaRow(icon = icon, tint = tint, text = label)
}

// ─────────────────────────────────────────────────────────────────────────────
// Tokens
// ─────────────────────────────────────────────────────────────────────────────

private val SHEET_ELEVATION = 8.dp
// No drag pill: the sheet is a fixed card, so a pill would promise a drag that doesn't exist. The
// top inset stands in for the pill block Home's peek has above the same header, so both cards give
// their header the same air. The bottom inset (on top of the nav-bar padding, and of the molde's
// own closing gap) keeps the CTA off the gesture area. [HISTORY-DETAIL-002]
private const val SHEET_TOP_PAD = 14
private const val SHEET_BOTTOM_PAD = 4

private val MAP_BOTTOM_BLEED = 20.dp

// ── Skeleton / missing faces ─────────────────────────────────────────────────
// Page keys for the two data-less faces, so AnimatedContent treats each as ONE page.
private const val KEY_UNRESOLVED = "unresolved"
private const val KEY_NOT_FOUND = "not_found"
// Widths as fractions of the card, sized off the real card's anatomy so nothing jumps on arrival.
private const val SKELETON_LEAD_DP = 40
private const val SKELETON_EYEBROW_FRACTION = 0.34f
private const val SKELETON_TITLE_FRACTION = 0.72f
private const val SKELETON_META_FRACTION = 0.55f
private const val SKELETON_META_ROWS = 2
private const val SKELETON_META_TOP_GAP = 16
private const val SKELETON_META_ROW_GAP = 8
private const val SKELETON_ACTION_TOP_GAP = 12
private const val SKELETON_ACTION_HEIGHT_DP = 48
private const val SKELETON_ACTION_CORNER_DP = 14
private const val SKELETON_SECONDARY_ALPHA = 0.7f
private const val MISSING_V_PAD = 28

