
package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

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
import com.rndeveloper.paparcar.ui.components.PapDivider
import com.rndeveloper.paparcar.ui.components.PapShimmerBox
import com.rndeveloper.paparcar.ui.components.PapShimmerBlockScale
import com.rndeveloper.paparcar.ui.components.chips.PaparcarFilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.detection.DetectionPhase
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.onboarding.FindSpotAsk
import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.domain.onboarding.FirstStepsOwnership
import com.rndeveloper.paparcar.domain.onboarding.WatchAsk
import com.rndeveloper.paparcar.domain.onboarding.WatchReinforcement
import com.rndeveloper.paparcar.presentation.onboarding.FirstStepsCard
import com.rndeveloper.paparcar.presentation.home.HomeBrowseListSlice
import com.rndeveloper.paparcar.presentation.home.HomeIntent
import com.rndeveloper.paparcar.presentation.home.HomeSelection
import com.rndeveloper.paparcar.presentation.home.VehicleCard
import com.rndeveloper.paparcar.presentation.home.isDriving
import com.rndeveloper.paparcar.presentation.home.vehiclesRowOrder
import com.rndeveloper.paparcar.presentation.home.model.DetectionStory
import com.rndeveloper.paparcar.presentation.home.model.resolveDetectionStory
import com.rndeveloper.paparcar.presentation.home.sections.sheet.HomeSheetAction
import com.rndeveloper.paparcar.ui.components.PapSectionHeader
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
fun LazyListScope.homeSheetItems(
    slice: HomeBrowseListSlice,
    onIntent: (HomeIntent) -> Unit,
    onAction: (HomeSheetAction) -> Unit,
) {
    val filteredSpots = slice.filteredSpots
    val vehicleCards = slice.vehicleCards
    val showPersonalBlocks = slice.hasCorePermissions && vehicleCards.isNotEmpty()
    val showFilterBar = slice.hasCorePermissions && slice.hasAnySpots

    // ── 0. Detection story surface — under the address header, above vehicles.
    // The ONE voice for "what is detection doing right now": loud action rows + discreet happy
    // lines, resolved by a single testable projection. [UX-DETECTION-STORY-001]
    // This whole list only exists in Browse with nothing selected — a selected pin gives the peek
    // the entire surface and the stepper walks the pins from there, so the old "hide the story
    // behind a tapped spot" branches (and the live-question exception to them) are gone with the
    // state that made them reachable. [UI-PEEK-STEPS-BETWEEN-PINS-001] [DET-ASK-STATE-001]
    // Both pending questions (prompt and nudge) are resolved by the projection, not arbitrated here.
    // The car both cold-start CTAs are about: the active vehicle, or the first if none is flagged.
    // Hoisted out of the detection item because the checklist's first step targets the SAME car —
    // two resolutions of "which car is this about" would be two chances to disagree.
    // [VEH-ACTIVE-FENCE-001]
    val coldStartVehicleId = vehicleCards.firstOrNull { it.vehicle.isActive }?.vehicle?.id
        ?: vehicleCards.firstOrNull()?.vehicle?.id

    // ── -1. Guided first steps — above the detection story, and only for a new user.
    // [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001]
    val firstSteps = slice.firstSteps
    // The gate lives on the slice: the sheet also OPENS ITSELF on this same question, and a second
    // copy of the rule here is how the two would come to disagree.
    // [ONBOARDING-FIRST-STEPS-MUST-BE-READABLE-AND-FOUND-001]
    val showFirstSteps = slice.showsFirstSteps
    if (showFirstSteps) {
        item("first_steps") {
            FirstStepsCard(
                progress = firstSteps,
                onStartStep = { step ->
                    // Pressing the CTA counts as much as reading it: it drops the user INTO the flow
                    // the step teaches, which is what the step is about.
                    // [ONBOARDING-THE-COMMUNITY-STEP-CANNOT-DEMAND-A-SPOT-001]
                    if (step.completesOnEngage) onIntent(HomeIntent.CompleteFirstStep(step))
                    when (step) {
                        // The REAL flow, with the same parameters the cold-start row passes — the
                        // step teaches the app's own control, not a rehearsal of it.
                        FirstStep.MARK_PARKING -> onIntent(
                            HomeIntent.EnterAddParkingMode(
                                initialGps = slice.userGpsPoint,
                                targetVehicleId = coldStartVehicleId,
                            ),
                        )
                        // [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001] Two faces here too.
                        // With detection healthy the step only DESCRIBES and renders no CTA, so this
                        // is unreachable and it completes by observing the honest watch line below.
                        // With detection stopped the step is the one asking for it, and it fires the
                        // very intent the row it took over fires — one action, one label, one voice.
                        // [DET-TOGGLE-001]
                        // [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] Each reinforcement
                        // fires the flow that grants it, and each is the SAME entry point the app
                        // already had: the exemption request behind the fragile-watch row, and the
                        // car-Bluetooth screen Settings deep-links to. NONE renders no CTA at all
                        // (the step is not even applicable), so it cannot be pressed.
                        FirstStep.FORTIFY_WATCH -> when (firstSteps.reinforcement) {
                            WatchReinforcement.BATTERY -> onIntent(HomeIntent.RequestBatteryExemption)
                            WatchReinforcement.BLUETOOTH -> coldStartVehicleId
                                ?.let { onAction(HomeSheetAction.LinkVehicleBluetooth(it)) }
                            WatchReinforcement.NONE -> Unit
                        }
                        FirstStep.UNDERSTAND_WATCH -> when (firstSteps.watchAsk) {
                            WatchAsk.TURN_IT_ON -> onIntent(HomeIntent.EnableAutoDetection)
                            WatchAsk.EXPLAIN_RELEASE -> Unit
                        }
                        // The community half, and it has TWO doors depending on whether the
                        // community has anything to offer yet. Which one is open was decided by
                        // the projection (`findSpotAsk`); here we only translate it into the app's
                        // real entry point — the same list the sheet already renders, or the same
                        // action the "Report a free spot" card at its bottom fires.
                        // [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001]
                        FirstStep.FIND_SPOT -> when (firstSteps.findSpotAsk) {
                            FindSpotAsk.SEE_NEARBY -> onAction(HomeSheetAction.RevealFreeSpots)
                            FindSpotAsk.REPORT_ONE -> onAction(HomeSheetAction.RequestReportMode)
                        }
                    }
                },
                onDismiss = { onIntent(HomeIntent.DismissFirstSteps) },
                // "Not yet" — the step stops being the ask without pretending it was done.
                // [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
                onDeferStep = { step -> onIntent(HomeIntent.DeferFirstStep(step)) },
                onResumeStep = { step -> onIntent(HomeIntent.ResumeFirstStep(step)) },
                // [ONBOARDING-A-SPOT-IS-BORN-TWO-WAYS-001] Every row opens its explainer, done ones
                // included — that is what makes replaying the checklist from Settings worth
                // something once the live state already ticks all three.
                onOpenStep = { step ->
                    onAction(HomeSheetAction.OpenFirstStepExplainer(step))
                    // [ONBOARDING-THE-COMMUNITY-STEP-CANNOT-DEMAND-A-SPOT-001] For a step of
                    // KNOWLEDGE, engaging with it IS doing it. WHICH steps those are is the model's
                    // answer (`completesOnEngage`), not an `if` written here.
                    if (step.completesOnEngage) onIntent(HomeIntent.CompleteFirstStep(step))
                },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            )
        }
    }

    val detectionStory = resolveDetectionStory(
        slice.detectionUiState, slice.drivingMeta, vehicleCards, slice.parkedWatchBadge,
        promptWindow = slice.promptWindow,
        showParkNudge = slice.showParkNudge,
        // One voice: whichever of this surface's asks the checklist is currently making, THAT row
        // stands down. Which one is derived by the projection (`owns`); all this line adds is that a
        // checklist nobody can see owns nothing.
        // [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001]
        firstStepsOwns = if (showFirstSteps) firstSteps.owns else FirstStepsOwnership.NOTHING,
    )
    if (detectionStory != DetectionStory.Hidden) {
        item("detection_surface") {
            HomeDetectionSurface(
                story = detectionStory,
                onAddVehicle = { onAction(HomeSheetAction.AddVehicle) },
                onOpenPermissions = { onAction(HomeSheetAction.OpenCorePermissions) },
                onMarkSpot = {
                    // Cold-start "mark my spot" — enters AddingParking for that vehicle. [DET-TOGGLE-002]
                    onIntent(
                        HomeIntent.EnterAddParkingMode(
                            initialGps = slice.userGpsPoint,
                            targetVehicleId = coldStartVehicleId,
                        ),
                    )
                },
                // [DET-G-01b] "I'm driving" declares THIS car and arms detection for it. [VEH-ACTIVE-FENCE-001]
                onStartDrivingDetection = { onIntent(HomeIntent.StartDrivingDetection(vehicleId = coldStartVehicleId)) },
                onActivateDetection = { onIntent(HomeIntent.EnableAutoDetection) }, // [DET-TOGGLE-001]
                // "Parar detección" on the live-trip row — ends THIS trip, leaves the feature on.
                // [DET-STOP-BUTTON-001]
                onStopDetection = { onIntent(HomeIntent.StopDetection) },
                // Fragile watch → fortify it with the battery exemption. [DET-WATCH-HONEST-001]
                onRequestBatteryExemption = { onIntent(HomeIntent.RequestBatteryExemption) },
                // Interrupted watch → rebuild the watcher itself. [DET-WATCH-REACTIVATE-001]
                onResumeWatch = { onIntent(HomeIntent.ResumeWatch) },
                allowDrivingDetection = true, // show both cold-start CTAs (mark spot + I'm driving)
                // [DET-ASK-STATE-001] The same two commands the notification's buttons send.
                onAnswerParked = { onIntent(HomeIntent.AnswerParkingPrompt(parked = true)) },
                onAnswerStillDriving = { onIntent(HomeIntent.AnswerParkingPrompt(parked = false)) },
                // [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] Tapping the card frames the place
                // the question is about. It is a CAMERA move and nothing else — the same action a
                // row tap already uses — so consulting the map can never be mistaken for answering.
                onFocusAsk = { at -> onAction(HomeSheetAction.MoveCamera(at.latitude, at.longitude)) },
                onMarkNudgeSpot = {
                    // Same promise as the notification's "Marcar mi plaza": straight into
                    // AddingParking for the nudged vehicle. [DET-NUDGE-PERSIST-001] The row only
                    // exists because DETECTION nominated a park it could not place — the confirmed
                    // pin keeps detection provenance. [DET-NUDGE-PIN-PROVENANCE-001]
                    onIntent(
                        HomeIntent.EnterAddParkingMode(
                            initialGps = slice.userGpsPoint,
                            targetVehicleId = slice.parkNudgeVehicleId ?: coldStartVehicleId,
                            fromDetectionNudge = true,
                        ),
                    )
                },
                onDismissNudge = { onIntent(HomeIntent.DismissParkNudge) },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            )
        }
    }

    // ── 1. "TUS VEHÍCULOS" header + per-vehicle rows
    if (showPersonalBlocks) {
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
        vehiclesSection(slice, vehicleCards, onIntent, onAction)
    }

    // ── 3. Spots (header + filter bar + list + report CTA) ─────────────────
    // Hidden entirely without CORE: the nearby feed is meaningless with no location, and the
    // red Blocked·CORE surface above is already the single "turn on location" prompt — no need
    // for the old HomePermissionsCard duplicating it. [DET-READY-001i]
    if (slice.hasCorePermissions) {
        spotsSection(
            slice = slice,
            onIntent = onIntent,
            onAction = onAction,
            filteredSpots = filteredSpots,
            showFilterBar = showFilterBar,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sections
// ─────────────────────────────────────────────────────────────────────────────

private fun LazyListScope.vehiclesSection(
    slice: HomeBrowseListSlice,
    vehicleCards: List<VehicleCard>,
    onIntent: (HomeIntent) -> Unit,
    onAction: (HomeSheetAction) -> Unit,
) {
    val userLocation = slice.userGpsPoint?.let { Pair(it.latitude, it.longitude) }
    // The vehicle whose trip is being detected RIGHT NOW (driving, not yet parked). [CHIP-DRIVING-001]
    val drivingVehicleId = slice.drivingMeta?.vehicleId
    fun VehicleCard.isDriving() = isDriving(drivingVehicleId)
    // The trip stopped and the user appears to be leaving the car — the chip flips to the candidate
    // ("Parking…") treatment. Only meaningful for the driving vehicle. [DET-PHASE-001]
    val isCandidatePhase = slice.drivingMeta?.phase == DetectionPhase.Candidate
    // Live state floats first: driving → parked (most recent park first, same preference as the
    // Browse peek subject) → monitoring config (BT, Active, Inactive). [UI-BROWSE-DRIVING-OVER-PARKED-001]
    val sorted = vehiclesRowOrder(vehicleCards, drivingVehicleId)

    fun cardClick(card: VehicleCard): () -> Unit = {
        val session = card.session
        if (session != null) {
            // Row with an active session — select it and fly the camera there.
            onIntent(HomeIntent.SelectItem(HomeSelection.Parking(session.id)))
            onAction(HomeSheetAction.MoveCamera(session.location.latitude, session.location.longitude))
        } else {
            // "Aparcar" pill — enter AddingParking pre-centred on the user's GPS and
            // tagged with this vehicleId so ConfirmParkingUseCase persists the session
            // for that specific car instead of the default. [MULTI-PARKING-001]
            onIntent(
                HomeIntent.EnterAddParkingMode(
                    initialGps = slice.userGpsPoint,
                    targetVehicleId = card.vehicle.id,
                ),
            )
        }
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
                    val onCardClick = remember(card.session?.id, card.vehicle.id, onIntent, onAction) {
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
    slice: HomeBrowseListSlice,
    onIntent: (HomeIntent) -> Unit,
    onAction: (HomeSheetAction) -> Unit,
    filteredSpots: List<Spot>,
    showFilterBar: Boolean,
) {
    item(FREE_SPOTS_SECTION_KEY) {
        PapSectionHeader(
            title = if (filteredSpots.isNotEmpty())
                pluralStringResource(Res.plurals.home_feed_nearby_with_count, filteredSpots.size, filteredSpots.size)
            else
                stringResource(Res.string.home_feed_nearby),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )
    }

    if (showFilterBar) {
        item(key = "filter_bar") {
            HomeSizeFilterBar(
                selectedSize = slice.sizeFilter,
                onFilterSelect = { size -> onIntent(HomeIntent.SetSizeFilter(size)) },
                // 16dp — same grid as headers/rows so the bar doesn't step out of the
                // left edge when the sheet expands. [HOME-VEH-REFINE-001]
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }

    when {
        slice.isLoading -> item("skeleton") { SpotsSkeletonList() }
        filteredSpots.isEmpty() && slice.sizeFilter != null && slice.hasAnySpots ->
            item("empty_filtered") {
                HomeEmptyFilteredSpots(
                    onClearFilter = { onIntent(HomeIntent.SetSizeFilter(null)) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        filteredSpots.isEmpty() -> item("empty") {
            HomeEmptySpots(
                onReport = { onAction(HomeSheetAction.RequestReportMode) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        else -> itemsIndexed(filteredSpots, key = { _, spot -> spot.id }) { index, spot ->
            Column {
                HomeSpotRow(
                    spot = spot,
                    userLocation = slice.userGpsPoint?.let { Pair(it.latitude, it.longitude) },
                    onSelect = {
                        onAction(HomeSheetAction.MoveCamera(spot.location.latitude, spot.location.longitude))
                        onIntent(HomeIntent.SelectItem(HomeSelection.Spot(spot.id)))
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
    val emptyShowsReportCta = !slice.isLoading && filteredSpots.isEmpty() &&
        !(slice.sizeFilter != null && slice.hasAnySpots)
    if (slice.hasCorePermissions && !emptyShowsReportCta) {
        item("report_spot_cta") {
            HomeReportSpotCard(
                onReport = { onAction(HomeSheetAction.RequestReportMode) },
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
    Column {
        repeat(itemCount) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PapShimmerBox(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    alphaScale = PapShimmerBlockScale,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PapShimmerBox(
                        modifier = Modifier.width(SKELETON_TITLE_WIDTH.dp).height(12.dp),
                        shape = RoundedCornerShape(4.dp),
                        alphaScale = PapShimmerBlockScale,
                    )
                    PapShimmerBox(
                        modifier = Modifier.width(SKELETON_SUBTITLE_WIDTH.dp).height(10.dp),
                        shape = RoundedCornerShape(4.dp),
                        alphaScale = PapShimmerBlockScale * SKELETON_SUBTITLE_ALPHA_FACTOR,
                    )
                }
            }
            if (index < itemCount - 1) {
                PapDivider(modifier = Modifier.padding(start = 70.dp, end = 16.dp))
            }
        }
    }
}

/**
 * Item key of the free-spots section header. Public because
 * [HomeSheetAction.RevealFreeSpots] scrolls the list to it — a second spelling of the
 * string would be a scroll that silently stops working.
 * [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001]
 */
internal const val FREE_SPOTS_SECTION_KEY = "spots_header"

private const val SKELETON_ITEM_COUNT = 4
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

