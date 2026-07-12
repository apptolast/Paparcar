
package io.apptolast.paparcar.presentation.home.sections.sheet.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import io.apptolast.paparcar.ui.components.PapDivider
import io.apptolast.paparcar.ui.components.chips.PaparcarFilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.detection.DetectionPhase
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.monitoringStatus
import io.apptolast.paparcar.domain.model.sortRank
import io.apptolast.paparcar.presentation.home.HomeIntent
import io.apptolast.paparcar.presentation.home.HomeState
import io.apptolast.paparcar.presentation.home.VehicleCard
import io.apptolast.paparcar.presentation.home.model.rendersActionSurface
import io.apptolast.paparcar.ui.components.PapSectionHeader
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_feed_nearby
import paparcar.composeapp.generated.resources.home_feed_nearby_with_count
import paparcar.composeapp.generated.resources.home_vehicles_section_header
import paparcar.composeapp.generated.resources.home_size_filter_all
import paparcar.composeapp.generated.resources.vehicle_size_large
import paparcar.composeapp.generated.resources.vehicle_size_medium
import paparcar.composeapp.generated.resources.vehicle_size_moto
import paparcar.composeapp.generated.resources.vehicle_size_small
import paparcar.composeapp.generated.resources.vehicle_size_van

/**
 * Emits the sheet content items into a [LazyListScope].
 *
 * Section order:
 *  1. **"TUS VEHÍCULOS" header + per-vehicle rows** — one row per registered
 *     vehicle. Vehicles with an active session show their park status; others
 *     show a "Park" pill that enters AddingParking for that specific vehicle.
 *     The section is hidden entirely when the user has no vehicles registered
 *     yet (onboarding edge — should not happen in steady state).
 *     [MULTI-PARKING-001]
 *  2. **"PLAZAS LIBRES CERCA · N" header + filter bar + spots list** — the
 *     community discovery feed, capped by a "Report a free spot" CTA after
 *     the list.
 *
 * Zone chips have moved to [HomeHeaderSection] (below the search bar).
 */
internal fun LazyListScope.homeSheetItems(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onCameraMove: (Double, Double) -> Unit,
    onParkingClick: (UserParking) -> Unit,
    onParkVehicle: (vehicleId: String) -> Unit,
    onSpotSelect: (lat: Double, lon: Double, spotId: String) -> Unit,
    onEnterReportMode: () -> Unit,
    onDetectionAddVehicle: () -> Unit = {},
    onDetectionOpenPermissions: () -> Unit = {},
    onDetectionMarkSpot: () -> Unit = {},
) {
    val selectedSpotId = state.selectedSpot?.id
    val isSpotSelected = selectedSpotId != null
    val filteredSpots = state.filteredNearbySpots
    val vehicleCards = state.vehicleCards
    val showPersonalBlocks = state.hasCorePermissions && vehicleCards.isNotEmpty()
    val showFilterBar = state.hasCorePermissions && state.nearbySpots.isNotEmpty()

    // ── 0. Detection action surface — under the address header, above vehicles.
    // Browse-only (hidden while a spot is selected) so it never shifts the spot-scroll index.
    if (state.detectionUiState.rendersActionSurface && !isSpotSelected) {
        item("detection_surface") {
            HomeDetectionSurface(
                state = state.detectionUiState,
                onAddVehicle = onDetectionAddVehicle,
                onOpenPermissions = onDetectionOpenPermissions,
                onMarkSpot = onDetectionMarkSpot,
                onStartDrivingDetection = { onIntent(HomeIntent.StartDrivingDetection) }, // [DET-G-01b]
                onActivateDetection = { onIntent(HomeIntent.EnableAutoDetection) }, // [DET-TOGGLE-001]
                allowDrivingDetection = true, // show both cold-start CTAs (mark spot + I'm driving)
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            )
        }
    }

    // ── 1. "TUS VEHÍCULOS" header + per-vehicle rows — hidden when a spot is selected
    if (showPersonalBlocks && !isSpotSelected) {
        item("vehicles_header") {
            PapSectionHeader(
                // Singular/plural header by vehicle count via plurals. [HOME-CARDS-001]
                title = pluralStringResource(
                    Res.plurals.home_vehicles_section_header,
                    vehicleCards.size,
                ),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            )
        }
        vehiclesSection(state, vehicleCards, onParkingClick, onParkVehicle)
    }

    // ── 3. Spots (header + filter bar + list + report CTA) ─────────────────
    // Hidden entirely without CORE: the nearby feed is meaningless with no location, and the
    // red Blocked·CORE surface above is already the single "turn on location" prompt — no need
    // for the old HomePermissionsCard duplicating it. [DET-READY-001i]
    if (state.hasCorePermissions) {
        spotsSection(
            state = state,
            onIntent = onIntent,
            onCameraMove = onCameraMove,
            onSpotSelect = onSpotSelect,
            selectedSpotId = selectedSpotId,
            filteredSpots = filteredSpots,
            showFilterBar = showFilterBar,
            isSpotSelected = isSpotSelected,
            onEnterReportMode = onEnterReportMode,
        )
    }
}

/**
 * Returns the absolute item index of a spot card in the LazyColumn emitted
 * by [homeSheetItems], or -1 if the spot is not part of the current list.
 */
internal fun homeSheetSpotItemIndex(state: HomeState, spotId: String): Int {
    val filteredSpots = state.filteredNearbySpots
    val spotIdx = filteredSpots.indexOfFirst { it.id == spotId }
    if (spotIdx < 0) return -1

    val vehicleCards = state.vehicleCards
    val showPersonalBlocks = state.hasCorePermissions && vehicleCards.isNotEmpty()
    val showFilterBar = state.hasCorePermissions && state.nearbySpots.isNotEmpty()

    val isSpotSelected = state.selectedSpot != null
    var base = 1 // offset carried from original layout
    if (!isSpotSelected) {
        if (showPersonalBlocks) {
            base += 1   // vehicles_header
            base += 1   // vehicles_row (single LazyRow item)
        }
        base += 1                       // spots_header (hidden when spot is selected)
    }
    if (showFilterBar) base += 1        // filter_bar
    // report_spot_cta sits AFTER the spot list, so it does not shift indices.
    return base + spotIdx
}

// ─────────────────────────────────────────────────────────────────────────────
// Sections
// ─────────────────────────────────────────────────────────────────────────────

private fun LazyListScope.vehiclesSection(
    state: HomeState,
    vehicleCards: List<VehicleCard>,
    onParkingClick: (UserParking) -> Unit,
    onParkVehicle: (vehicleId: String) -> Unit,
) {
    val userLocation = state.userGpsPoint?.let { Pair(it.latitude, it.longitude) }
    // The vehicle whose trip is being detected RIGHT NOW (driving, not yet parked). [CHIP-DRIVING-001]
    val drivingVehicleId = state.drivingPuck?.vehicleId
    fun VehicleCard.isDriving() = session == null && vehicle.id == drivingVehicleId
    // The trip stopped and the user appears to be leaving the car — the chip flips to the candidate
    // ("Parking…") treatment. Only meaningful for the driving vehicle. [DET-PHASE-001]
    val isCandidatePhase = state.drivingPuck?.phase == DetectionPhase.Candidate
    // Live state floats first: driving → parked → monitoring config (BT, Active, Inactive).
    val sorted = vehicleCards.sortedWith(
        compareByDescending<VehicleCard> { it.isDriving() }
            .thenByDescending { it.session != null }
            .thenBy { it.vehicle.monitoringStatus().sortRank() }
    )

    fun cardClick(card: VehicleCard): () -> Unit = {
        val session = card.session
        if (session != null) onParkingClick(session) else onParkVehicle(card.vehicle.id)
    }

    if (sorted.size == 1) {
        // Single vehicle → one full-width card (no horizontal strip), roomier layout. [HOME-CARDS-001]
        val card = sorted.first()
        item("vehicle_single") {
            HomeVehicleCard(
                card = card,
                isDriving = card.isDriving(),
                isCandidate = card.isDriving() && isCandidatePhase,
                userLocation = userLocation,
                onClick = cardClick(card),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    } else {
        item("vehicles_row") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(sorted, key = { it.vehicle.id }) { card ->
                    val onCardClick = remember(card.session?.id, card.vehicle.id, onParkingClick, onParkVehicle) {
                        cardClick(card)
                    }
                    HomeVehicleChip(
                        card = card,
                        isDriving = card.isDriving(),
                        isCandidate = card.isDriving() && isCandidatePhase,
                        onClick = onCardClick,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.spotsSection(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onCameraMove: (Double, Double) -> Unit,
    onSpotSelect: (lat: Double, lon: Double, spotId: String) -> Unit,
    selectedSpotId: String?,
    filteredSpots: List<Spot>,
    showFilterBar: Boolean,
    isSpotSelected: Boolean,
    onEnterReportMode: () -> Unit,
) {
    if (!isSpotSelected) {
        item("spots_header") {
            PapSectionHeader(
                title = if (filteredSpots.isNotEmpty())
                    pluralStringResource(Res.plurals.home_feed_nearby_with_count, filteredSpots.size, filteredSpots.size)
                else
                    stringResource(Res.string.home_feed_nearby),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            )
        }
    }

    if (showFilterBar) {
        item(key = "filter_bar") {
            HomeSizeFilterBar(
                selectedSize = state.sizeFilter,
                onFilterSelect = { size -> onIntent(HomeIntent.SetSizeFilter(size)) },
                // 16dp — same grid as headers/rows so the bar doesn't step out of the
                // left edge when the sheet expands. [HOME-VEH-REFINE-001]
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }

    when {
        state.isLoading -> item("skeleton") { SpotsSkeletonList() }
        filteredSpots.isEmpty() && state.sizeFilter != null && state.nearbySpots.isNotEmpty() ->
            item("empty_filtered") {
                HomeEmptyFilteredSpots(
                    onClearFilter = { onIntent(HomeIntent.SetSizeFilter(null)) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        filteredSpots.isEmpty() -> item("empty") {
            HomeEmptySpots(
                onReport = onEnterReportMode,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        else -> itemsIndexed(filteredSpots, key = { _, spot -> spot.id }) { index, spot ->
            Column {
                HomeSpotRow(
                    spot = spot,
                    userLocation = state.userGpsPoint?.let { Pair(it.latitude, it.longitude) },
                    isSelected = spot.id == selectedSpotId,
                    onSelect = {
                        onCameraMove(spot.location.latitude, spot.location.longitude)
                        onSpotSelect(spot.location.latitude, spot.location.longitude, spot.id)
                    },
                )
                if (index < filteredSpots.lastIndex) {
                    PapDivider(modifier = Modifier.padding(start = 70.dp, end = 16.dp))
                }
            }
        }
    }

    // The plain empty state already carries its own "Report a free spot" primary —
    // don't double the CTA below it. [UI-SHEET-001]
    val emptyShowsReportCta = !state.isLoading && filteredSpots.isEmpty() &&
        !(state.sizeFilter != null && state.nearbySpots.isNotEmpty())
    if (state.hasCorePermissions && !emptyShowsReportCta) {
        item("report_spot_cta") {
            HomeReportSpotCard(
                onReport = onEnterReportMode,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Loading skeleton
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SpotsSkeletonList(
    itemCount: Int = SKELETON_ITEM_COUNT,
) {
    val transition = rememberInfiniteTransition(label = "skeleton_shimmer")
    val alpha by transition.animateFloat(
        initialValue = SKELETON_ALPHA_MIN,
        targetValue = SKELETON_ALPHA_MAX,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SKELETON_ANIM_MS),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton_alpha",
    )
    val shimmerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

    Column {
        repeat(itemCount) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(shimmerColor),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .width(SKELETON_TITLE_WIDTH.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerColor),
                    )
                    Box(
                        modifier = Modifier
                            .width(SKELETON_SUBTITLE_WIDTH.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerColor.copy(alpha = shimmerColor.alpha * SKELETON_SUBTITLE_ALPHA_FACTOR)),
                    )
                }
            }
            if (index < itemCount - 1) {
                PapDivider(modifier = Modifier.padding(start = 70.dp, end = 16.dp))
            }
        }
    }
}

private const val SKELETON_ITEM_COUNT = 4
private const val SKELETON_ALPHA_MIN = 0.06f
private const val SKELETON_ALPHA_MAX = 0.16f
private const val SKELETON_ANIM_MS = 900
private const val SKELETON_TITLE_WIDTH = 140
private const val SKELETON_SUBTITLE_WIDTH = 100
private const val SKELETON_SUBTITLE_ALPHA_FACTOR = 0.7f

// Width of the right-edge scroll-hint fade on the size filter bar. [HOME-POLISH-001]
private const val FILTER_FADE_WIDTH_DP = 28

// ─────────────────────────────────────────────────────────────────────────────
// Size filter bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeSizeFilterBar(
    selectedSize: VehicleSize?,
    onFilterSelect: (VehicleSize?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val allLabel    = stringResource(Res.string.home_size_filter_all)
    val motoLabel   = stringResource(Res.string.vehicle_size_moto)
    val smallLabel  = stringResource(Res.string.vehicle_size_small)
    val mediumLabel = stringResource(Res.string.vehicle_size_medium)
    val largeLabel  = stringResource(Res.string.vehicle_size_large)
    val vanLabel    = stringResource(Res.string.vehicle_size_van)

    val scrollState = rememberScrollState()
    // Right-edge fade to the sheet background, signalling there are more filter chips off-screen.
    // Drawn BEFORE horizontalScroll so it stays fixed at the viewport edge (not scrolled with the
    // content) and, being a draw modifier, never intercepts chip taps. Only shown while there is
    // more to scroll. [HOME-POLISH-001]
    val fadeColor = MaterialTheme.colorScheme.surfaceContainer
    Row(
        modifier = modifier
            .drawWithContent {
                drawContent()
                if (scrollState.canScrollForward) {
                    val fadeW = FILTER_FADE_WIDTH_DP.dp.toPx()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, fadeColor),
                            startX = size.width - fadeW,
                            endX = size.width,
                        ),
                        topLeft = Offset(size.width - fadeW, 0f),
                        size = Size(fadeW, size.height),
                    )
                }
            }
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PaparcarFilterChip(
            label = allLabel,
            selected = selectedSize == null,
            onClick = { onFilterSelect(null) },
        )
        listOf(
            VehicleSize.MOTORCYCLE   to motoLabel,
            VehicleSize.MICRO_SMALL  to smallLabel,
            VehicleSize.MEDIUM_SUV to mediumLabel,
            VehicleSize.LARGE_SEDAN  to largeLabel,
            VehicleSize.VAN_HIGH    to vanLabel,
        ).forEach { (size, label) ->
            PaparcarFilterChip(
                label = label,
                selected = selectedSize == size,
                onClick = { onFilterSelect(if (selectedSize == size) null else size) },
            )
        }
    }
}

