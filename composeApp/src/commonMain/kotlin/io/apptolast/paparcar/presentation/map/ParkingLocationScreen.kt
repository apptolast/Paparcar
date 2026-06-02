@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.map

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.util.collectAsStateLifecycleAware
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.presentation.util.locationDisplayText
import io.apptolast.paparcar.presentation.util.rememberOpenExternalNavigation
import io.apptolast.paparcar.ui.components.PapFooterButton
import io.apptolast.paparcar.ui.components.PapFooterButtonStyle
import io.apptolast.paparcar.ui.components.PapSectionHeader
import io.apptolast.paparcar.ui.components.PaparcarMapConfig
import io.apptolast.paparcar.ui.components.PaparcarMapView
import io.apptolast.paparcar.ui.theme.PapShapes
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.map_cd_back
import paparcar.composeapp.generated.resources.parking_detail_active_section_label
import paparcar.composeapp.generated.resources.parking_detail_detection_auto
import paparcar.composeapp.generated.resources.parking_detail_detection_manual
import paparcar.composeapp.generated.resources.parking_detail_navigate_action
import paparcar.composeapp.generated.resources.parking_detail_no_address
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

    val cameraTarget = remember(initialFocus) {
        mutableStateOf(
            initialFocus?.let { (lat, lon) -> CameraTarget(lat = lat, lon = lon, zoom = 16f) }
        )
    }

    val parkingGpsPoint = remember(initialFocus) {
        initialFocus?.let { (lat, lon) ->
            GpsPoint(lat, lon, accuracy = 0f, timestamp = 0L, speed = 0f)
        }
    }

    val density = LocalDensity.current
    var sheetHeightPx by remember { mutableIntStateOf(0) }
    val mapBleedPx = with(density) { MAP_BOTTOM_BLEED.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        PaparcarMapView(
            config = PaparcarMapConfig(showFreeSpotOverlays = false),
            spots = emptyList(),
            userLocation = state.userLocation,
            parkingLocation = parkingGpsPoint ?: state.userParking?.location,
            onSpotClick = {},
            cameraTarget = cameraTarget.value,
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

        FloatingBackButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 12.dp, top = 8.dp),
        )

        HistoryDetailSheet(
            session = state.focusedSession,
            isActive = state.focusedSession?.isActive == true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onGloballyPositioned { sheetHeightPx = it.size.height },
            onNavigate = { lat, lon -> openNavigation(lat, lon, false) },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Floating back button — pill-shaped surface with back arrow
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
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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

@Composable
private fun HistoryDetailSheet(
    session: UserParking?,
    isActive: Boolean,
    onNavigate: (lat: Double, lon: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme

    Surface(
        modifier = modifier,
        shape = PapShapes.sheet,
        color = cs.surfaceContainer,
        shadowElevation = SHEET_ELEVATION,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = SHEET_HORIZ_PAD.dp, vertical = SHEET_VERT_PAD.dp),
        ) {
            DragPill(modifier = Modifier.align(Alignment.CenterHorizontally))

            Spacer(Modifier.height(PILL_BOTTOM_GAP.dp))

            val sectionLabel = if (isActive) {
                stringResource(Res.string.parking_detail_active_section_label)
            } else {
                stringResource(Res.string.parking_detail_section_label)
            }
            PapSectionHeader(title = sectionLabel)

            Spacer(Modifier.height(SECTION_GAP.dp))

            AddressHeroRow(session = session)

            if (session != null) {
                Spacer(Modifier.height(META_ROW_GAP.dp))
                DateTimeRow(timestampMs = session.location.timestamp)
                Spacer(Modifier.height(META_ROW_GAP.dp))
                DetectionRow(spotType = session.spotType)
            }

            Spacer(Modifier.height(ACTION_TOP_GAP.dp))

            val (lat, lon) = if (session != null) {
                session.location.latitude to session.location.longitude
            } else {
                0.0 to 0.0
            }
            PapFooterButton(
                label = stringResource(Res.string.parking_detail_navigate_action),
                leadingIcon = Icons.Outlined.Navigation,
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
private fun DragPill(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = PILL_WIDTH.dp, height = PILL_HEIGHT.dp)
            .background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = PILL_ALPHA),
                CircleShape,
            ),
    )
}

@Composable
private fun AddressHeroRow(session: UserParking?) {
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

    val iconBg = cs.primaryContainer
    val iconTint = cs.primary

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HERO_GAP.dp),
    ) {
        Box(
            modifier = Modifier
                .size(HERO_ICON_BOX_DP.dp)
                .clip(RoundedCornerShape(HERO_ICON_CORNER_DP.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.DirectionsCar,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(HERO_ICON_DP.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = primaryText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (secondaryText != null) {
                Text(
                    text = secondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurface.copy(alpha = SECONDARY_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DateTimeRow(timestampMs: Long) {
    if (timestampMs <= 0L) return
    val cs = MaterialTheme.colorScheme
    val dateTime = remember(timestampMs) {
        Instant.fromEpochMilliseconds(timestampMs)
            .toLocalDateTime(TimeZone.currentSystemDefault())
    }
    val timeStr = "${dateTime.hour.toString().padStart(2, '0')}:" +
        dateTime.minute.toString().padStart(2, '0')
    val dateStr = "${dateTime.dayOfMonth} ${dateTime.month.name.lowercase().replaceFirstChar { it.uppercase() }}" +
        " ${dateTime.year}"

    MetaRow(
        icon = Icons.Outlined.Schedule,
        tint = cs.primary,
        text = "$dateStr · $timeStr",
    )
}

@Composable
private fun DetectionRow(spotType: SpotType) {
    val cs = MaterialTheme.colorScheme
    val (icon, label, tint) = when (spotType) {
        SpotType.AUTO_DETECTED -> Triple(
            Icons.Outlined.Bolt,
            stringResource(Res.string.parking_detail_detection_auto),
            cs.primary,
        )
        SpotType.MANUAL_REPORT -> Triple(
            Icons.Outlined.EditLocationAlt,
            stringResource(Res.string.parking_detail_detection_manual),
            cs.secondary,
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
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = META_TEXT_ALPHA),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
private const val SHEET_VERT_PAD = 16

private const val PILL_WIDTH = 32
private const val PILL_HEIGHT = 4
private const val PILL_ALPHA = 0.12f
private const val PILL_BOTTOM_GAP = 12

private const val SECTION_GAP = 14
private const val META_ROW_GAP = 10
private const val ACTION_TOP_GAP = 20

private const val HERO_GAP = 12
private const val HERO_ICON_BOX_DP = 44
private const val HERO_ICON_CORNER_DP = 10
private const val HERO_ICON_DP = 22

private const val META_ICON_DP = 18
private const val META_ICON_GAP = 8
private const val META_TEXT_ALPHA = 0.70f
private const val SECONDARY_ALPHA = 0.55f

private val MAP_BOTTOM_BLEED = 20.dp
