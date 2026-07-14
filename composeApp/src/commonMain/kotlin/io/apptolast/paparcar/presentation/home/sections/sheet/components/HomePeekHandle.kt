package io.apptolast.paparcar.presentation.home.sections.sheet.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.home.HomeIntent
import io.apptolast.paparcar.presentation.home.HomeMode
import io.apptolast.paparcar.presentation.home.HomePeekSlice
import io.apptolast.paparcar.presentation.home.model.DetectionUiState
import io.apptolast.paparcar.presentation.home.sections.sheet.HomeSheetAction
import io.apptolast.paparcar.presentation.home.sections.sheet.components.peek.AddingParkingPeek
import io.apptolast.paparcar.presentation.home.sections.sheet.components.peek.AddingZonePeek
import io.apptolast.paparcar.presentation.home.sections.sheet.components.peek.BrowsePeek
import io.apptolast.paparcar.presentation.home.sections.sheet.components.peek.ParkingPeek
import io.apptolast.paparcar.presentation.home.sections.sheet.components.peek.ReportPeek
import io.apptolast.paparcar.presentation.home.sections.sheet.components.peek.SpotPeek
import io.apptolast.paparcar.presentation.home.sections.sheet.components.peek.ZonePeekForm
import io.apptolast.paparcar.presentation.home.sections.sheet.components.peek.cameraTitleWhileSettling
import io.apptolast.paparcar.ui.theme.PapMotion

// ─────────────────────────────────────────────────────────────────────────────
// HomePeekHandle — the peek ORCHESTRATOR: decides which PeekState to render,
// animates between them, and hands each variant its concrete data. The variants
// themselves live in components/peek/, one file each. [HOME-ATOMIZE-001 F3]
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun HomePeekHandle(
    slice: HomePeekSlice,
    /** True while the sheet sits beyond peek — expanded browse swaps to the zone header. [UI-SHEET-004] */
    browseShowsZoneHeader: Boolean = false,
    spotListExpanded: Boolean = false,
    onIntent: (HomeIntent) -> Unit = {},
    onAction: (HomeSheetAction) -> Unit = {},
) {
    val selectedSpot = slice.selectedSpot
    // Under multi-parking pick the *specific* selected session, not just the first active one,
    // so the peek's title, address and actions refer to the vehicle the user actually tapped.
    val selectedSession = slice.selectedSession
    val peekState: PeekState = when {
        slice.mode is HomeMode.AddingParking ->
            PeekState.AddingParking(isEditing = slice.editingParkingId != null)
        slice.mode is HomeMode.Reporting -> PeekState.Reporting
        slice.mode is HomeMode.AddingZone -> PeekState.AddingZone
        selectedSpot != null -> PeekState.SelectedSpot(selectedSpot.id)
        selectedSession != null -> PeekState.SelectedParking(selectedSession.id)
        else -> PeekState.Browse
    }

    Column(modifier = Modifier.fillMaxWidth()) {

        // Drag pill — hidden in the CORE/GPS blocker, where the sheet is static (no drag). [DET-READY-001n]
        if (slice.detectionUiState != DetectionUiState.BlockedCore) {
            Box(
                modifier = Modifier
                    // Glued to the header — no dead air between pill and eyebrow; the header's own
                    // top padding (12dp) is all the breathing room the peek needs. [UI-SHEET-003]
                    .padding(top = 8.dp, bottom = 2.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        CircleShape,
                    )
                    .align(Alignment.CenterHorizontally),
            )
        }

        if (slice.detectionUiState == DetectionUiState.BlockedCore) {
            // Consumer Home can't work without location/GPS — take over the sheet with the full
            // blocker instead of a peek + small surface + redundant header. [DET-READY-001n]
            HomeLocationBlockedState(onActivate = { onAction(HomeSheetAction.OpenCorePermissions) })
        } else AnimatedContent(
            targetState = peekState,
            transitionSpec = {
                // Explicit duration coordinated with the sheet snap (PapMotion.Emphasized)
                // so the peek content and the sheet move as one piece.
                val incomingEngaged = targetState !is PeekState.Browse
                if (incomingEngaged) {
                    (slideInVertically(PapMotion.emphasized()) { it / 2 } + fadeIn(PapMotion.emphasized())) togetherWith
                        (slideOutVertically(PapMotion.emphasized()) { -it / 2 } + fadeOut(PapMotion.emphasized()))
                } else {
                    (slideInVertically(PapMotion.emphasized()) { -it / 2 } + fadeIn(PapMotion.emphasized())) togetherWith
                        (slideOutVertically(PapMotion.emphasized()) { it / 2 } + fadeOut(PapMotion.emphasized()))
                }
            },
            label = "peek_content",
        ) { target ->
            // Pin-mode variants share the camera-anchored title; stale data or "…" while the
            // camera is moving/geocoding so the card never flashes "unknown address" mid-drag.
            when (target) {
                is PeekState.SelectedSpot -> {
                    // Resolve the live spot from the slice — PeekState only carries the id so
                    // AnimatedContent doesn't transition on Spot data refresh. [BUG-PEEK-JITTER-001]
                    val spot = slice.nearbySpots.firstOrNull { it.id == target.spotId }
                    if (spot != null) {
                        SpotPeek(
                            spot = spot,
                            userLocation = slice.userGpsPoint?.let { Pair(it.latitude, it.longitude) },
                            activeVehicle = slice.vehicles.firstOrNull { it.isActive },
                            spotListExpanded = spotListExpanded,
                            onIntent = onIntent,
                            onAction = onAction,
                        )
                    }
                }
                is PeekState.SelectedParking -> {
                    val parking = slice.activeSessions.firstOrNull { it.id == target.sessionId }
                    if (parking != null) {
                        ParkingPeek(
                            parking = parking,
                            vehicle = slice.vehicles.firstOrNull { it.id == parking.vehicleId },
                            userLocation = slice.userGpsPoint?.let { Pair(it.latitude, it.longitude) },
                            onIntent = onIntent,
                            onAction = onAction,
                        )
                    }
                }
                PeekState.Reporting -> ReportPeek(
                    title = slice.settlingCameraTitle(),
                    selectedSize = slice.reportingSize,
                    isReporting = slice.isReporting,
                    isCameraMoving = slice.isCameraMoving,
                    onIntent = onIntent,
                )
                PeekState.AddingZone -> AddingZonePeek(
                    title = slice.settlingCameraTitle(),
                    form = ZonePeekForm(
                        name = slice.addingZoneName,
                        iconKey = slice.addingZoneIconKey,
                        radius = slice.addingZoneRadius,
                        isPrivate = slice.addingZoneIsPrivate,
                        isEditing = slice.editingZoneId != null,
                        isSaving = slice.isSavingZone,
                    ),
                    isCameraMoving = slice.isCameraMoving,
                    onIntent = onIntent,
                )
                is PeekState.AddingParking -> AddingParkingPeek(
                    title = slice.settlingCameraTitle(),
                    // create: the tapped row's vehicle; edit: the moved session's vehicle. [MULTI-PARKING-001]
                    targetVehicle = run {
                        val vid = if (target.isEditing) {
                            slice.activeSessions.firstOrNull { it.id == slice.editingParkingId }?.vehicleId
                        } else {
                            slice.addingParkingVehicleId
                        }
                        vid?.let { id -> slice.vehicles.firstOrNull { it.id == id } }
                    },
                    isEditing = target.isEditing,
                    // "Delete record" acts on the session BEING EDITED (falls back to the
                    // selected session for safety). [UI-SHEET-004]
                    deleteTarget = slice.editingParkingId
                        ?.let { id -> slice.activeSessions.firstOrNull { it.id == id } }
                        ?: slice.selectedSession ?: slice.userParking,
                    isSaving = slice.isSavingParking,
                    isCameraMoving = slice.isCameraMoving,
                    onIntent = onIntent,
                )
                PeekState.Browse -> {
                    val parking = slice.userParking
                    BrowsePeek(
                        parking = parking,
                        parkingVehicle = parking?.let { p -> slice.vehicles.firstOrNull { it.id == p.vehicleId } },
                        drivingMeta = slice.drivingMeta,
                        drivingVehicle = slice.drivingMeta?.let { m -> slice.vehicles.firstOrNull { it.id == m.vehicleId } },
                        cameraInfo = slice.cameraAddressAndPlace,
                        userGpsPoint = slice.userGpsPoint,
                        freeCount = slice.freeCount,
                        showZoneHeader = browseShowsZoneHeader,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomePeekSlice.settlingCameraTitle(): String =
    cameraTitleWhileSettling(cameraAddressAndPlace, isSettling = isCameraMoving || isCameraGeocoding)

/**
 * Drives [AnimatedContent] in [HomePeekHandle]. Variants store **identity only**
 * (ids and other rarely-changing primitives) — never the underlying domain
 * object — so equality stays stable even when Firestore re-emits the same
 * spot/parking with cosmetic field changes (enRouteCount, expiresAt drift,
 * geocoded address arriving late, etc.). Without this discipline the
 * AnimatedContent would transition on every Spot/UserParking refresh and the
 * sheet would visibly thrash. The content lambda re-reads the live object from
 * the captured peek slice. [BUG-PEEK-JITTER-001]
 */
private sealed class PeekState {
    data class SelectedSpot(val spotId: String) : PeekState()
    data class SelectedParking(val sessionId: String) : PeekState()
    data object Reporting : PeekState()
    data object AddingZone : PeekState()
    data class AddingParking(val isEditing: Boolean) : PeekState()
    data object Browse : PeekState()
}
