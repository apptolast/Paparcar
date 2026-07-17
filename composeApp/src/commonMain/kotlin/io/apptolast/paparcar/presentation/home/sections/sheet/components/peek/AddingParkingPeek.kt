package io.apptolast.paparcar.presentation.home.sections.sheet.components.peek

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditLocationAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.displayName
import io.apptolast.paparcar.presentation.home.HomeIntent
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheet
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheetBanner
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheetEyebrowTone
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheetLead
import io.apptolast.paparcar.ui.components.PapFooterButton
import io.apptolast.paparcar.ui.components.PapFooterButtonStyle
import io.apptolast.paparcar.ui.icons.PaparcarIcons
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_add_parking_cancel_cd
import paparcar.composeapp.generated.resources.home_add_parking_confirm_create
import paparcar.composeapp.generated.resources.home_add_parking_confirm_edit
import paparcar.composeapp.generated.resources.home_add_parking_header_label_create
import paparcar.composeapp.generated.resources.home_add_parking_header_label_edit
import paparcar.composeapp.generated.resources.home_add_parking_helper_primary_create
import paparcar.composeapp.generated.resources.home_add_parking_helper_primary_edit
import paparcar.composeapp.generated.resources.home_add_parking_helper_secondary
import paparcar.composeapp.generated.resources.home_parking_menu_delete
import paparcar.composeapp.generated.resources.home_vehicle_fallback_name

// ═════════════════════════════════════════════════════════════════════════════
// AddingParkingPeek — "Posicionar aparcamiento" (create + edit). [HOME-ATOMIZE-001 F3]
// ═════════════════════════════════════════════════════════════════════════════

/**
 * @param targetVehicle the vehicle this session is FOR (create: the tapped row's
 *   vehicle; edit: the moved session's vehicle) — the header shows its name so
 *   the user recognises the car when they hit confirm. [MULTI-PARKING-001]
 * @param deleteTarget the session the edit-mode "delete record" acts on.
 */
@Composable
internal fun AddingParkingPeek(
    title: String,
    targetVehicle: Vehicle?,
    isEditing: Boolean,
    deleteTarget: UserParking?,
    isSaving: Boolean,
    isCameraMoving: Boolean,
    onIntent: (HomeIntent) -> Unit,
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
    val ctaLabel = if (isEditing) {
        stringResource(Res.string.home_add_parking_confirm_edit)
    } else {
        stringResource(Res.string.home_add_parking_confirm_create)
    }
    // Pre-load the cancel content-description so the IDE catches an
    // unreferenced-string regression early. PapSheet.onDismiss is the close
    // affordance — the CD is read by accessibility for that button.
    @Suppress("UnusedExpression") stringResource(Res.string.home_add_parking_cancel_cd)

    // Show the actual car (carbody glyph) being parked, not a generic DirectionsCar so the user
    // recognises the vehicle. The car stays full-colour/opaque regardless of monitoring state — that
    // state reads on its on-map marker border, not by dimming the glyph here. [INACTIVE-OPAQUE-001]
    PapSheet(
        lead = PapSheetLead.Vehicle(
            carbody = targetVehicle?.carbodyType,
            size = targetVehicle?.sizeCategory,
            color = targetVehicle?.color,
        ),
        eyebrow = headerLabel,
        eyebrowTone = PapSheetEyebrowTone.Action,
        title = title,
        onDismiss = { onIntent(HomeIntent.ExitAddParkingMode) },
        banner = {
            PapSheetBanner(
                title = helperPrimary,
                subtitle = stringResource(Res.string.home_add_parking_helper_secondary),
            )
        },
        actions = {
            PapFooterButton(
                label = ctaLabel,
                leadingIcon = if (isEditing) Icons.Rounded.EditLocationAlt
                              else PaparcarIcons.VehicleCar,
                onClick = { onIntent(HomeIntent.ConfirmAddParking) },
                style = PapFooterButtonStyle.Filled,
                enabled = !isSaving && !isCameraMoving,
                isLoading = isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            if (isEditing) {
                Spacer(Modifier.height(8.dp))
                // Editing an existing record also offers deleting it — the one
                // sanctioned destructive red. Aimed at the session BEING EDITED;
                // exits edit mode after. [UI-SHEET-004]
                PapFooterButton(
                    label = stringResource(Res.string.home_parking_menu_delete),
                    leadingIcon = Icons.Rounded.Delete,
                    onClick = {
                        deleteTarget?.let { p ->
                            onIntent(
                                HomeIntent.ReleaseParking(
                                    sessionId = p.id,
                                    publishSpot = false,
                                ),
                            )
                        }
                        onIntent(HomeIntent.ExitAddParkingMode)
                    },
                    style = PapFooterButtonStyle.Outlined,
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.error,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}
