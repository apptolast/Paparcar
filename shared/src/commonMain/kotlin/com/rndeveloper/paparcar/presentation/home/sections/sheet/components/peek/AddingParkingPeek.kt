package com.rndeveloper.paparcar.presentation.home.sections.sheet.components.peek

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditLocationAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.model.ParkingReleaseReason
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.displayName
import com.rndeveloper.paparcar.presentation.home.HomeIntent
import com.rndeveloper.paparcar.presentation.home.PeekStep
import com.rndeveloper.paparcar.presentation.home.sections.sheet.HomeSheetAction
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheet
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetBanner
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetStepper
import com.rndeveloper.paparcar.domain.model.monitoringStatus
import com.rndeveloper.paparcar.ui.theme.VehicleWatch
import com.rndeveloper.paparcar.ui.theme.vehicleIdentityColor
import com.rndeveloper.paparcar.ui.theme.watch
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetLead
import com.rndeveloper.paparcar.ui.components.PapAlertDialog
import com.rndeveloper.paparcar.ui.components.PapDialogAccent
import com.rndeveloper.paparcar.ui.components.PapFooterButton
import com.rndeveloper.paparcar.ui.components.PapFooterButtonStyle
import com.rndeveloper.paparcar.ui.icons.PaparcarIcons
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_add_parking_cancel_cd
import paparcar.composeapp.generated.resources.home_add_parking_confirm_create
import paparcar.composeapp.generated.resources.home_add_parking_header_label_create
import paparcar.composeapp.generated.resources.home_add_parking_header_label_edit
import paparcar.composeapp.generated.resources.home_add_parking_helper_primary_create
import paparcar.composeapp.generated.resources.home_add_parking_helper_primary_edit
import paparcar.composeapp.generated.resources.home_add_parking_helper_secondary_create
import paparcar.composeapp.generated.resources.home_add_parking_helper_secondary_edit
import paparcar.composeapp.generated.resources.home_parking_delete_confirm_body
import paparcar.composeapp.generated.resources.home_parking_delete_confirm_title
import paparcar.composeapp.generated.resources.home_parking_menu_correct
import paparcar.composeapp.generated.resources.home_peek_step_next_car
import paparcar.composeapp.generated.resources.home_peek_step_prev_car
import paparcar.composeapp.generated.resources.home_parking_menu_delete
import paparcar.composeapp.generated.resources.home_parking_menu_repark
import paparcar.composeapp.generated.resources.home_release_dialog_cancel
import paparcar.composeapp.generated.resources.home_vehicle_fallback_name

// ═════════════════════════════════════════════════════════════════════════════
// AddingParkingPeek — "Posicionar aparcamiento" (create + edit). [HOME-ATOMIZE-001 F3]
// ═════════════════════════════════════════════════════════════════════════════

/**
 * @param targetVehicle the vehicle this session is FOR (create: the tapped row's
 *   vehicle; edit: the moved session's vehicle) — the header shows its name so
 *   the user recognises the car when they hit confirm. [MULTI-PARKING-001]
 * @param deleteTarget the session the edit-mode "delete parking" acts on.
 * @param step neighbouring VEHICLES in the car lane — the header ‹ / ›, same chrome as
 *   [ParkingPeek], so an unparked car is a page of the same book instead of a dead end. The
 *   caller passes [PeekStep.None] in the flows where stepping away has a cost (edit, detection
 *   nudge). [UI-PEEK-STEPS-WALK-VEHICLES-NOT-SESSIONS-001]
 */
@Composable
internal fun AddingParkingPeek(
    title: String,
    targetVehicle: Vehicle?,
    isEditing: Boolean,
    deleteTarget: UserParking?,
    isSaving: Boolean,
    isCameraMoving: Boolean,
    step: PeekStep,
    onIntent: (HomeIntent) -> Unit,
    onAction: (HomeSheetAction) -> Unit,
) {
    val fallbackVehicleName = stringResource(Res.string.home_vehicle_fallback_name)
    val genericHeader = if (isEditing) {
        stringResource(Res.string.home_add_parking_header_label_edit)
    } else {
        stringResource(Res.string.home_add_parking_header_label_create)
    }
    val headerLabel = targetVehicle?.displayName(fallback = fallbackVehicleName) ?: genericHeader
    val helperPrimary = if (isEditing) {
        stringResource(Res.string.home_add_parking_helper_primary_edit)
    } else {
        stringResource(Res.string.home_add_parking_helper_primary_create)
    }
    // In EDIT the geofence line of CREATE says nothing new — the fence already exists. What the
    // user needs here is the one axis that separates the two confirms below: correcting keeps the
    // parked time this session has been counting, marking a new parking restarts it from zero.
    // [COPY-PARKING-EDIT-THREE-ANSWERS-ONE-QUESTION-001]
    val helperSecondary = if (isEditing) {
        stringResource(Res.string.home_add_parking_helper_secondary_edit)
    } else {
        stringResource(Res.string.home_add_parking_helper_secondary_create)
    }
    // In EDIT the primary confirm CORRECTS this session's pin (same session); "Mark new parking" is
    // the sibling that starts another one. In CREATE it just parks the car. [UX-PARKED-STATE-001]
    val ctaLabel = if (isEditing) {
        stringResource(Res.string.home_parking_menu_correct)
    } else {
        stringResource(Res.string.home_add_parking_confirm_create)
    }
    // Pre-load the cancel content-description so the IDE catches an
    // unreferenced-string regression early. PapSheet.onDismiss is the close
    // affordance — the CD is read by accessibility for that button.
    @Suppress("UnusedExpression") stringResource(Res.string.home_add_parking_cancel_cd)

    var confirmingDelete by remember { mutableStateOf(false) }

    // Show the actual car (carbody glyph) being parked, not a generic DirectionsCar so the user
    // recognises the vehicle. The car stays full-colour/opaque regardless of monitoring state — that
    // state reads on its on-map marker border, not by dimming the glyph here. [INACTIVE-OPAQUE-001]
    PapSheet(
        lead = PapSheetLead.Vehicle(
            carbody = targetVehicle?.carbodyType,
            size = targetVehicle?.sizeCategory,
            color = targetVehicle?.color,
            loading = targetVehicle == null,
        ),
        eyebrow = headerLabel,
        // The eyebrow IS the vehicle name → it wears the car's identity colour (watch method),
        // not the generic action green. [UI-COLOR-DOCTRINE-001]
        eyebrowColor = vehicleIdentityColor(targetVehicle?.monitoringStatus()?.watch() ?: VehicleWatch.Off),
        title = title,
        onDismiss = { onIntent(HomeIntent.ExitAddParkingMode) },
        // Same ‹ / › as ParkingPeek: the car lane walks vehicles, and this modal is just the
        // unparked vehicle's page of it. Arrows go quiet while the save is in flight, like the
        // CTA below. [UI-PEEK-STEPS-WALK-VEHICLES-NOT-SESSIONS-001]
        stepper = PapSheetStepper(
            prevContentDescription = stringResource(Res.string.home_peek_step_prev_car),
            nextContentDescription = stringResource(Res.string.home_peek_step_next_car),
            onPrev = step.prevId?.takeIf { !isSaving }
                ?.let { id -> { onAction(HomeSheetAction.StepToVehicle(id)) } },
            onNext = step.nextId?.takeIf { !isSaving }
                ?.let { id -> { onAction(HomeSheetAction.StepToVehicle(id)) } },
        ),
        banner = {
            PapSheetBanner(
                title = helperPrimary,
                subtitle = helperSecondary,
            )
        },
        actions = {
            // Primary confirm. CREATE → parks the car; EDIT → corrects THIS session's pin in place.
            PapFooterButton(
                label = ctaLabel,
                leadingIcon = if (isEditing) Icons.Rounded.EditLocationAlt
                              else PaparcarIcons.VehicleCar,
                onClick = { onIntent(HomeIntent.ConfirmAddParking(asNewSession = false)) },
                style = PapFooterButtonStyle.Filled,
                enabled = !isSaving && !isCameraMoving,
                isLoading = isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            if (isEditing) {
                Spacer(Modifier.height(8.dp))
                // Same tool, different meaning: the pin was fine but the CAR actually moved → a
                // NEW session for the same vehicle (the model supersedes the old one on save).
                // Decided HERE, with the pin already placed, instead of guessing up front. [UX-PARKED-STATE-001]
                PapFooterButton(
                    label = stringResource(Res.string.home_parking_menu_repark),
                    leadingIcon = Icons.Rounded.AddLocationAlt,
                    onClick = { onIntent(HomeIntent.ConfirmAddParking(asNewSession = true)) },
                    style = PapFooterButtonStyle.Outlined,
                    enabled = !isSaving && !isCameraMoving,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                // Delete parking — the one sanctioned destructive red, behind a confirm since it is
                // one tap away and irreversible. Acts on the session being edited. [UI-SHEET-004]
                PapFooterButton(
                    label = stringResource(Res.string.home_parking_menu_delete),
                    leadingIcon = Icons.Rounded.Delete,
                    onClick = { confirmingDelete = true },
                    style = PapFooterButtonStyle.Outlined,
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.error,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )

    if (confirmingDelete) {
        PapAlertDialog(
            onDismiss = { confirmingDelete = false },
            accent = PapDialogAccent.Destructive,
            icon = Icons.Rounded.Delete,
            title = stringResource(Res.string.home_parking_delete_confirm_title),
            body = stringResource(Res.string.home_parking_delete_confirm_body),
            primaryLabel = stringResource(Res.string.home_parking_menu_delete),
            primaryLeadingIcon = Icons.Rounded.Delete,
            onPrimary = {
                confirmingDelete = false
                deleteTarget?.let { p ->
                    // "This record was wrong", not "I'm leaving": it must not publish a plaza and
                    // must not make this the car you drive. [PARK-DELETE-NO-DECLARE-001]
                    onIntent(HomeIntent.ReleaseParking(sessionId = p.id, reason = ParkingReleaseReason.RECORD_DELETED))
                }
                onIntent(HomeIntent.ExitAddParkingMode)
            },
            cancelLabel = stringResource(Res.string.home_release_dialog_cancel),
        )
    }
}
