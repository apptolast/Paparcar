@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.map

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EditLocationAlt
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.util.collectAsStateLifecycleAware
import io.apptolast.paparcar.presentation.vehicles.MONTH_SHORT_RES
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.util.PolylineCodec
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.presentation.util.locationDisplayText
import io.apptolast.paparcar.presentation.util.rememberOpenExternalNavigation
import io.apptolast.paparcar.ui.components.PapFooterButton
import io.apptolast.paparcar.ui.components.PapFooterButtonStyle
import io.apptolast.paparcar.ui.components.PapSectionHeaderRow
import io.apptolast.paparcar.ui.components.PaparcarMapConfig
import io.apptolast.paparcar.ui.components.PaparcarMapView
import io.apptolast.paparcar.ui.components.VehicleGlyph
import io.apptolast.paparcar.ui.theme.PapMotion
import io.apptolast.paparcar.ui.theme.PapShapes
import io.apptolast.paparcar.ui.theme.PaparcarType
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.map_cd_back
import paparcar.composeapp.generated.resources.parking_detail_active_section_label
import paparcar.composeapp.generated.resources.parking_detail_detection_auto
import paparcar.composeapp.generated.resources.parking_detail_detection_home
import paparcar.composeapp.generated.resources.parking_detail_detection_manual
import paparcar.composeapp.generated.resources.parking_detail_navigate_action
import paparcar.composeapp.generated.resources.parking_detail_next
import paparcar.composeapp.generated.resources.parking_detail_no_address
import paparcar.composeapp.generated.resources.parking_detail_prev
import paparcar.composeapp.generated.resources.parking_detail_route_recalculating
import paparcar.composeapp.generated.resources.parking_detail_section_label
import kotlin.time.Instant

@Composable
fun HistoryParkingDetailScreen(
    onNavigateBack: () -> Unit = {},
    initialFocus: Pair<Double, Double>? = null,
    sessionId: String = "",
    viewModel: ParkingLocationViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateLifecycleAware()
    val openNavigation = rememberOpenExternalNavigation()

    LaunchedEffect(sessionId) {
        if (sessionId.isNotBlank()) {
            viewModel.handleIntent(ParkingLocationIntent.SetFocusedSession(sessionId))
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
        mutableStateOf(if (routeSnapped) PolylineCodec.decode(focusedSession?.routePolyline) else emptyList())
    }
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

    Box(modifier = Modifier.fillMaxSize()) {
        PaparcarMapView(
            config = PaparcarMapConfig(showFreeSpotOverlays = false),
            spots = emptyList(),
            userLocation = state.userLocation,
            tripTrail = routeTrail,
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
                    .statusBarsPadding()
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

        FloatingBackButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 12.dp, top = 8.dp),
        )

        HistoryDetailSheet(
            session = focusedSession,
            vehicle = state.focusedVehicle,
            isActive = focusedSession?.isActive == true,
            hasOlder = state.hasOlder,
            hasNewer = state.hasNewer,
            onOlder = { viewModel.handleIntent(ParkingLocationIntent.FocusOlder) },
            onNewer = { viewModel.handleIntent(ParkingLocationIntent.FocusNewer) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onGloballyPositioned { sheetHeightPx = it.size.height },
            onNavigate = { lat, lon -> openNavigation(lat, lon, false) },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Floating back button — pill-shaped surface with back arrow, over the map top-left
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FloatingBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = cs.surfaceContainer,
        shadowElevation = BACK_BUTTON_ELEVATION,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(Res.string.map_cd_back),
            tint = cs.onSurface,
            modifier = Modifier
                .padding(BACK_BUTTON_PADDING)
                .size(BACK_BUTTON_ICON_DP.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Detail sheet — non-draggable card anchored at the bottom
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The bottom detail card of the history map. Extracted as `internal` (map-free, pure inputs) so the
 * Dev Catalog gallery + previews can render the detection-label / vehicle-icon / stepper variants
 * without a live map. [HISTORY-DETAIL-001]
 */
@Composable
internal fun HistoryDetailSheet(
    session: UserParking?,
    vehicle: Vehicle?,
    isActive: Boolean,
    hasOlder: Boolean,
    hasNewer: Boolean,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
    onNavigate: (lat: Double, lon: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme

    // Swipe = the chevrons' gesture twin: dragging the card LEFT pulls the newer parking in from
    // the right (same as ›), dragging RIGHT the older one (same as ‹) — the directional page-turn
    // below plays the matching slide, so finger and motion agree. Trigger-on-release with a
    // distance threshold; taps (CTA, chevrons) pass through untouched. rememberUpdatedState keeps
    // the guards/callbacks fresh without restarting the gesture loop. [HISTORY-DETAIL-002]
    val latestHasOlder by rememberUpdatedState(hasOlder)
    val latestHasNewer by rememberUpdatedState(hasNewer)
    val latestOnOlder by rememberUpdatedState(onOlder)
    val latestOnNewer by rememberUpdatedState(onNewer)

    Surface(
        modifier = modifier.pointerInput(Unit) {
            var dragged = 0f
            detectHorizontalDragGestures(
                onDragStart = { dragged = 0f },
                onDragEnd = {
                    val threshold = SWIPE_TRIGGER_DP.dp.toPx()
                    when {
                        dragged <= -threshold && latestHasNewer -> latestOnNewer()
                        dragged >= threshold && latestHasOlder -> latestOnOlder()
                    }
                },
            ) { _, dragAmount -> dragged += dragAmount }
        },
        shape = PapShapes.sheet,
        color = cs.surfaceContainer,
        shadowElevation = SHEET_ELEVATION,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(
                    start = SHEET_HORIZ_PAD.dp,
                    end = SHEET_HORIZ_PAD.dp,
                    top = SHEET_TOP_PAD.dp,
                    bottom = SHEET_BOTTOM_PAD.dp,
                ),
        ) {
            val sectionLabel = if (isActive) {
                stringResource(Res.string.parking_detail_active_section_label)
            } else {
                stringResource(Res.string.parking_detail_section_label)
            }
            // Timeline stepper: the chevrons read as TIME, not list order — ‹ steps into the past
            // (older parking), › steps toward today (newer). Opening the most recent entry leaves
            // only ‹ active: "you can go back in time", which matches reality. Title truly centred
            // between the chevrons and demoted to outline — it is a pager eyebrow, not this card's
            // protagonist (the address hero below is). [HISTORY-DETAIL-002][ROUTE-QUALITY-001]
            PapSectionHeaderRow(
                title = sectionLabel,
                color = cs.outline,
                centerTitle = true,
                leading = {
                    StepperButton(
                        icon = Icons.Rounded.ChevronLeft,
                        contentDescription = stringResource(Res.string.parking_detail_prev),
                        enabled = hasOlder,
                        onClick = onOlder,
                    )
                },
                trailing = {
                    StepperButton(
                        icon = Icons.Rounded.ChevronRight,
                        contentDescription = stringResource(Res.string.parking_detail_next),
                        enabled = hasNewer,
                        onClick = onNewer,
                    )
                },
            )

            Spacer(Modifier.height(SECTION_GAP.dp))

            // Directional page-turn: stepping › (newer) slides the incoming parking in FROM THE
            // RIGHT, ‹ (older) from the left — the motion itself teaches the timeline mapping
            // (past ← → present) regardless of which convention the user expects. Transitions are
            // keyed on the session ID (not the object) so a Firestore re-emit of the same session
            // (late geocode, cosmetic drift) refreshes in place without re-triggering the slide
            // [BUG-PEEK-JITTER-001]; the direction falls out of the two timestamps. The default
            // SizeTransform clips the slide to the content box and animates height differences
            // between entries. [HISTORY-DETAIL-002]
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
                Column(modifier = Modifier.fillMaxWidth()) {
                    AddressHeroRow(session = shownSession, vehicle = shownVehicle)

                    if (shownSession != null) {
                        Spacer(Modifier.height(META_ROW_GAP.dp))
                        DateTimeRow(
                            timestampMs = shownSession.location.timestamp,
                            isActive = shownSession.isActive,
                        )
                        Spacer(Modifier.height(META_ROW_GAP.dp))
                        DetectionRow(spotType = shownSession.spotType, isActive = shownSession.isActive)
                    }
                }
            }

            Spacer(Modifier.height(ACTION_TOP_GAP.dp))

            val (lat, lon) = if (session != null) {
                session.location.latitude to session.location.longitude
            } else {
                0.0 to 0.0
            }
            PapFooterButton(
                label = stringResource(Res.string.parking_detail_navigate_action),
                leadingIcon = Icons.Rounded.Navigation,
                onClick = { onNavigate(lat, lon) },
                style = PapFooterButtonStyle.Filled,
                enabled = session != null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddressHeroRow(session: UserParking?, vehicle: Vehicle?) {
    val cs = MaterialTheme.colorScheme
    val noAddress = stringResource(Res.string.parking_detail_no_address)

    val primaryText = if (session != null) {
        locationDisplayText(
            placeInfo = session.placeInfo,
            address = session.address,
            lat = session.location.latitude,
            lon = session.location.longitude,
        )
    } else {
        noAddress
    }
    val secondaryText = session?.address?.city?.takeIf { it.isNotBlank() }
        ?.let { city ->
            session.address?.region?.takeIf { it.isNotBlank() }
                ?.let { "$city, $it" } ?: city
        }

    // Real vehicle: body shape from the session (captured at park time), falling back to the
    // registered vehicle; colour only lives on the vehicle. [HISTORY-DETAIL-001]
    val carbody = session?.carbodyType ?: vehicle?.carbodyType
    val size = session?.sizeCategory ?: vehicle?.sizeCategory

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HERO_GAP.dp),
    ) {
        // Home's lead-tile treatment: the full-colour vehicle pictogram (VehicleGlyph) on a quiet
        // rounded tile — same subject + sizing as the Home sheet so both surfaces read consistently.
        // Status reads through the meta-row accent, not the tile (INACTIVE-OPAQUE doctrine). [HISTORY-DETAIL-001]
        Box(
            modifier = Modifier
                .size(HERO_TILE_DP.dp)
                .clip(PapShapes.cardSmall)
                .background(cs.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            VehicleGlyph(
                carbody = carbody,
                size = size,
                glyphSize = HERO_GLYPH_DP.dp,
                color = vehicle?.color,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = primaryText,
                style = PaparcarType.current.cardTitle,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (secondaryText != null) {
                Text(
                    text = secondaryText,
                    style = PaparcarType.current.caption,
                    color = cs.onSurface.copy(alpha = SECONDARY_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DateTimeRow(timestampMs: Long, isActive: Boolean) {
    if (timestampMs <= 0L) return
    val cs = MaterialTheme.colorScheme
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

    MetaRow(
        icon = Icons.Rounded.Schedule,
        tint = if (isActive) cs.primary else cs.onSurfaceVariant,
        text = "$dateStr · $timeStr",
    )
}

@Composable
private fun DetectionRow(spotType: SpotType, isActive: Boolean) {
    val cs = MaterialTheme.colorScheme
    val (icon, label, tint) = when (spotType) {
        SpotType.AUTO_DETECTED -> Triple(
            Icons.Rounded.Bolt,
            stringResource(Res.string.parking_detail_detection_auto),
            if (isActive) cs.primary else cs.onSurfaceVariant,
        )
        SpotType.MANUAL_REPORT -> Triple(
            Icons.Rounded.EditLocationAlt,
            stringResource(Res.string.parking_detail_detection_manual),
            if (isActive) cs.secondary else cs.onSurfaceVariant,
        )
        SpotType.HOME_GEOFENCE -> Triple(
            Icons.Rounded.Home,
            stringResource(Res.string.parking_detail_detection_home),
            if (isActive) cs.primary else cs.onSurfaceVariant,
        )
    }
    MetaRow(icon = icon, tint = tint, text = label)
}

@Composable
private fun MetaRow(
    icon: ImageVector,
    tint: Color,
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(META_ICON_GAP.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(META_ICON_DP.dp),
        )
        Text(
            text = text,
            style = PaparcarType.current.body,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = META_TEXT_ALPHA),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stepper button — one circular chevron; a pair flanks the header title (prev ‹ / next ›)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepperButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = cs.surfaceVariant.copy(
            alpha = if (enabled) STEPPER_BG_ALPHA else STEPPER_BG_DISABLED_ALPHA,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = cs.onSurfaceVariant.copy(alpha = if (enabled) 1f else STEPPER_DISABLED_ALPHA),
            modifier = Modifier
                .padding(STEPPER_PADDING.dp)
                .size(STEPPER_ICON_DP.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tokens
// ─────────────────────────────────────────────────────────────────────────────

private const val BACK_BUTTON_ICON_DP = 22
private val BACK_BUTTON_PADDING = 10.dp
private val BACK_BUTTON_ELEVATION = 4.dp

private val SHEET_ELEVATION = 8.dp
private const val SHEET_HORIZ_PAD = 20
// No drag pill: the sheet is a fixed card, so a pill would promise a drag that doesn't exist.
// The header IS the top chrome — a slightly larger top inset gives it the air the pill used to
// occupy. The bottom inset (on top of the nav-bar padding) keeps the CTA off the gesture area
// so the card doesn't end flush against the screen edge. [HISTORY-DETAIL-002]
private const val SHEET_TOP_PAD = 16
private const val SHEET_BOTTOM_PAD = 20

// Eyebrow→content gap: enough air under the (compact, muted) header for the address block to read
// as the card's protagonist — the hierarchy lives in the CONTENT's breathing room, not in the
// chrome's height. Rhythm sits on the 4dp grid. [HISTORY-DETAIL-002][ROUTE-QUALITY-001]
private const val SECTION_GAP = 12
private const val META_ROW_GAP = 10
private const val ACTION_TOP_GAP = 24

private const val HERO_GAP = 12
// Home's lead-tile sizing (PapSheet LEAD_TILE_DP / LEAD_GLYPH_DP) so the vehicle reads the same on
// both surfaces. [HISTORY-DETAIL-001]
private const val HERO_TILE_DP = 46
private const val HERO_GLYPH_DP = 38

private const val META_ICON_DP = 18
private const val META_ICON_GAP = 8
private const val META_TEXT_ALPHA = 0.70f
private const val SECONDARY_ALPHA = 0.55f

// Swipe distance that commits a page step. Comfortably above tap slop so scroll-ish micro-drags
// never page, well below half the sheet width so the gesture stays effortless one-handed.
private const val SWIPE_TRIGGER_DP = 64

// Compact steppers (32 dp circle): they are pager chrome, not primary actions — a taller row here
// pushes the whole card's content down and hands the eyebrow the visual hierarchy.
private const val STEPPER_ICON_DP = 22
private const val STEPPER_PADDING = 5
private const val STEPPER_BG_ALPHA = 0.6f
private const val STEPPER_BG_DISABLED_ALPHA = 0.3f
private const val STEPPER_DISABLED_ALPHA = 0.35f

private val MAP_BOTTOM_BLEED = 20.dp
