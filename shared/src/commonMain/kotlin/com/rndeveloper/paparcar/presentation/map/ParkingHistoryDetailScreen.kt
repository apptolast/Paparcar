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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.rndeveloper.paparcar.ui.theme.PapMotion
import com.rndeveloper.paparcar.ui.theme.PapShapes
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.VehicleWatch
import com.rndeveloper.paparcar.ui.theme.vehicleIdentityColor
import com.rndeveloper.paparcar.ui.theme.watch
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
import paparcar.composeapp.generated.resources.parking_detail_navigate_action
import paparcar.composeapp.generated.resources.parking_detail_next
import paparcar.composeapp.generated.resources.parking_detail_no_address
import paparcar.composeapp.generated.resources.parking_detail_prev
import paparcar.composeapp.generated.resources.parking_detail_route_inferred_no
import paparcar.composeapp.generated.resources.parking_detail_route_inferred_question
import paparcar.composeapp.generated.resources.parking_detail_route_inferred_yes
import paparcar.composeapp.generated.resources.parking_detail_route_recalculating
import kotlin.time.Instant

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
    val focusedSession = state.focusedSession
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
            config = PaparcarMapConfig(showFreeSpotOverlays = false),
            spots = emptyList(),
            userLocation = state.userLocation,
            tripTrail = routeTrail,
            tripSegments = routeSegments,
            departurePoint = routeStart,
            arrivalPoint = routeEnd,
            parkingLocation = focusedSession?.location ?: parkingGpsPoint ?: state.userParking?.location,
            parkingVehicleSize = focusedSession?.sizeCategory ?: state.focusedVehicle?.sizeCategory,
            parkingVehicleCarbody = focusedSession?.carbodyType ?: state.focusedVehicle?.carbodyType,
            parkingVehicleColor = state.focusedVehicle?.color,
            parkingIsActive = focusedSession?.isActive == true,
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
            session = focusedSession,
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
                        color = MaterialTheme.colorScheme.primary,
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
    session: UserParking?,
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
                targetState = session to vehicle,
                contentKey = { (target, _) -> target?.id },
                transitionSpec = {
                    val toNewer = (targetState.first?.location?.timestamp ?: 0L) >
                        (initialState.first?.location?.timestamp ?: 0L)
                    val from = if (toNewer) 1 else -1
                    ContentTransform(
                        targetContentEnter = slideInHorizontally(PapMotion.emphasized()) { it * from } +
                            fadeIn(PapMotion.emphasized()),
                        initialContentExit = slideOutHorizontally(PapMotion.emphasized()) { -it * from } +
                            fadeOut(PapMotion.emphasized()),
                    )
                },
                label = "history_step",
            ) { (shownSession, shownVehicle) ->
                HistoryParkingCard(
                    session = shownSession,
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

/** One parking as Home's sheet molde renders it — the page the timeline turns. */
@Composable
private fun HistoryParkingCard(
    session: UserParking?,
    vehicle: Vehicle?,
    hasOlder: Boolean,
    hasNewer: Boolean,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
    onNavigate: (lat: Double, lon: Double) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val title = if (session != null) {
        locationDisplayText(
            placeInfo = session.placeInfo,
            address = session.address,
        ) ?: stringResource(Res.string.location_fallback_parking)
    } else {
        stringResource(Res.string.parking_detail_no_address)
    }
    val subtitle = session?.address?.city?.takeIf { it.isNotBlank() }
        ?.let { city ->
            session.address.region?.takeIf { it.isNotBlank() }?.let { "$city, $it" } ?: city
        }

    // ONE accent for the whole card, and it is the vehicle's watch method — blue for a BT-watched
    // car, brand green for an assisted one, grey unwatched — through the single resolver, exactly
    // like Home and Vehículos. A closed session keeps its muted tone: the accent marks what is LIVE.
    // [UI-COLOR-DOCTRINE-001][UI-HISTORY-IDENTITY-AND-SOURCE-001]
    val identity = vehicleIdentityColor(vehicle?.monitoringStatus()?.watch() ?: VehicleWatch.Off)
    val isLive = session?.isActive == true
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

    val (lat, lon) = session?.let { it.location.latitude to it.location.longitude } ?: (0.0 to 0.0)

    PapSheet(
        // Body shape from the session (captured at park time), falling back to the registered
        // vehicle; colour only lives on the vehicle. [HISTORY-DETAIL-001]
        lead = PapSheetLead.Vehicle(
            carbody = session?.carbodyType ?: vehicle?.carbodyType,
            size = session?.sizeCategory ?: vehicle?.sizeCategory,
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
            if (session != null) {
                DateTimeRow(timestampMs = session.location.timestamp, tint = metaTint)
                DetectionRow(session = session, tint = metaTint)
            }
        },
        actions = {
            PapFooterButton(
                label = stringResource(Res.string.parking_detail_navigate_action),
                leadingIcon = Icons.Rounded.Navigation,
                onClick = { onNavigate(lat, lon) },
                style = PapFooterButtonStyle.Filled,
                enabled = session != null,
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
