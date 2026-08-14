package io.apptolast.paparcar.presentation.home.sections.sheet.components.peek

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.domain.model.ZoneIcon
import io.apptolast.paparcar.presentation.home.HomeIntent
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheet
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheetBanner
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheetEyebrowTone
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheetLead
import io.apptolast.paparcar.presentation.util.zoneIconFor
import io.apptolast.paparcar.ui.components.PapAlertDialog
import io.apptolast.paparcar.ui.components.PapClearIconButton
import io.apptolast.paparcar.ui.components.PapDialogAccent
import io.apptolast.paparcar.ui.components.PapFooterButton
import io.apptolast.paparcar.ui.components.PapFooterButtonStyle
import io.apptolast.paparcar.ui.components.PapSectionHeader
import io.apptolast.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_release_dialog_cancel
import paparcar.composeapp.generated.resources.home_zone_action_delete
import paparcar.composeapp.generated.resources.home_zone_delete_confirm_body
import paparcar.composeapp.generated.resources.home_zone_delete_confirm_title
import paparcar.composeapp.generated.resources.home_zone_edit_header_label
import paparcar.composeapp.generated.resources.home_zone_header_label
import paparcar.composeapp.generated.resources.home_zone_helper_primary_create
import paparcar.composeapp.generated.resources.home_zone_helper_primary_edit
import paparcar.composeapp.generated.resources.home_zone_helper_secondary
import paparcar.composeapp.generated.resources.home_zone_name_dialog_body
import paparcar.composeapp.generated.resources.home_zone_name_dialog_title
import paparcar.composeapp.generated.resources.home_zone_name_placeholder
import paparcar.composeapp.generated.resources.home_zone_private_hint
import paparcar.composeapp.generated.resources.home_zone_private_label
import paparcar.composeapp.generated.resources.home_zone_radius_meters
import paparcar.composeapp.generated.resources.home_zone_radius_section
import paparcar.composeapp.generated.resources.home_zone_save_action
import kotlin.math.roundToInt

// ═════════════════════════════════════════════════════════════════════════════
// AddingZonePeek — "Nueva zona habitual" (create + edit). [HOME-ATOMIZE-001 F3]
//
// Same anatomy as its siblings (report a spot / position a parking): a banner
// telling you what the map is for, ONE row of choices, the settings, one filled
// action — plus, in edit, the destructive escape behind a confirm. The name is
// NOT here: it is the last decision, and it is asked in the confirm dialog once
// the place and the radius are already set. [UI-ZONE-MANAGE-001]
// ═════════════════════════════════════════════════════════════════════════════

private const val ZONE_ICON_CHIP_DP = 40
private const val SECTION_LABEL_ALPHA = 0.55f

/** The zone form's live values, projected from the peek slice by the orchestrator. */
@Immutable
internal data class ZonePeekForm(
    val name: String,
    val iconKey: String,
    val radius: Float,
    val isPrivate: Boolean,
    /** Non-null while an existing zone is being edited — it is also what "delete" acts on. */
    val editingZoneId: String?,
    val isSaving: Boolean,
) {
    val isEditing: Boolean get() = editingZoneId != null
}

@Composable
internal fun AddingZonePeek(
    title: String,
    form: ZonePeekForm,
    isCameraMoving: Boolean,
    onIntent: (HomeIntent) -> Unit,
) {
    val headerLabel = if (form.isEditing) {
        stringResource(Res.string.home_zone_edit_header_label)
    } else {
        stringResource(Res.string.home_zone_header_label)
    }
    val helperPrimary = if (form.isEditing) {
        stringResource(Res.string.home_zone_helper_primary_edit)
    } else {
        stringResource(Res.string.home_zone_helper_primary_create)
    }

    // Naming survives a failed save: the VM stays in AddingZone with the form intact,
    // so the dialog is still up for the retry. [BUG-8]
    var naming by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    PapSheet(
        lead = PapSheetLead.GenericIcon(icon = zoneIconFor(form.iconKey)),
        eyebrow = headerLabel,
        eyebrowTone = PapSheetEyebrowTone.Neutral,
        title = title,
        onDismiss = { onIntent(HomeIntent.ExitAddZoneMode) },
        banner = {
            PapSheetBanner(
                title = helperPrimary,
                subtitle = stringResource(Res.string.home_zone_helper_secondary),
            )
        },
        // A row of icons IS the label — a "Icon" header above it only names what is
        // already visible, and the space is worth more to the form. [UI-ZONE-MANAGE-001]
        chips = {
            ZoneIconPickerRow(
                selectedKey = form.iconKey,
                onSelect = { onIntent(HomeIntent.UpdateAddingZoneIcon(it)) },
            )
        },
        content = {
            ZoneRadiusSlider(
                radius = form.radius,
                onRadiusChange = { onIntent(HomeIntent.SetZoneRadius(it)) },
            )
            Spacer(Modifier.height(14.dp))
            ZonePrivacyToggle(
                isPrivate = form.isPrivate,
                onToggle = { onIntent(HomeIntent.SetZoneIsPrivate(it)) },
            )
            Spacer(Modifier.height(14.dp))
        },
        actions = {
            PapFooterButton(
                label = stringResource(Res.string.home_zone_save_action),
                leadingIcon = Icons.Rounded.Bookmark,
                onClick = { naming = true },
                style = PapFooterButtonStyle.Filled,
                // The pin has to be settled before the name is asked — a zone saved
                // mid-fling lands wherever the camera happened to be.
                enabled = !form.isSaving && !isCameraMoving,
                isLoading = form.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            if (form.isEditing) {
                Spacer(Modifier.height(8.dp))
                // The one sanctioned destructive red, behind a confirm — same contract as
                // "delete record" in the parking modal. [UI-SHEET-004]
                PapFooterButton(
                    label = stringResource(Res.string.home_zone_action_delete),
                    leadingIcon = Icons.Rounded.Delete,
                    onClick = { confirmingDelete = true },
                    style = PapFooterButtonStyle.Outlined,
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.error,
                    enabled = !form.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )

    if (naming) {
        ZoneNameDialog(
            form = form,
            onNameChange = { onIntent(HomeIntent.UpdateAddingZoneName(it)) },
            onConfirm = { onIntent(HomeIntent.ConfirmAddZone) },
            onDismiss = { if (!form.isSaving) naming = false },
        )
    }

    if (confirmingDelete) {
        PapAlertDialog(
            onDismiss = { confirmingDelete = false },
            accent = PapDialogAccent.Destructive,
            icon = Icons.Rounded.Delete,
            title = stringResource(Res.string.home_zone_delete_confirm_title),
            body = stringResource(Res.string.home_zone_delete_confirm_body),
            primaryLabel = stringResource(Res.string.home_zone_action_delete),
            primaryLeadingIcon = Icons.Rounded.Delete,
            onPrimary = {
                confirmingDelete = false
                // The VM closes this modal when the zone it is editing goes away.
                form.editingZoneId?.let { onIntent(HomeIntent.DeleteZone(it)) }
            },
            cancelLabel = stringResource(Res.string.home_release_dialog_cancel),
        )
    }
}

/**
 * The confirm step of saving a zone, which doubles as where the zone is NAMED —
 * the last thing decided, once the place, the icon and the radius are set on the
 * map behind it. The dialog owns the keyboard, so the sheet never has to grow a
 * text field over the map. [UI-ZONE-MANAGE-001]
 */
@Composable
private fun ZoneNameDialog(
    form: ZonePeekForm,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    PapAlertDialog(
        onDismiss = onDismiss,
        icon = zoneIconFor(form.iconKey),
        title = stringResource(Res.string.home_zone_name_dialog_title),
        body = stringResource(Res.string.home_zone_name_dialog_body),
        primaryLabel = stringResource(Res.string.home_zone_save_action),
        primaryLeadingIcon = Icons.Rounded.Bookmark,
        onPrimary = onConfirm,
        primaryEnabled = form.name.isNotBlank(),
        isLoading = form.isSaving,
        cancelLabel = stringResource(Res.string.home_release_dialog_cancel),
        content = {
            OutlinedTextField(
                value = form.name,
                onValueChange = onNameChange,
                placeholder = { Text(stringResource(Res.string.home_zone_name_placeholder)) },
                singleLine = true,
                enabled = !form.isSaving,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (form.name.isNotBlank() && !form.isSaving) onConfirm() },
                ),
                leadingIcon = {
                    Icon(
                        imageVector = zoneIconFor(form.iconKey),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingIcon = if (form.name.isNotEmpty()) {
                    { PapClearIconButton(onClick = { onNameChange("") }) }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        },
    )
}

@Composable
private fun ZoneIconPickerRow(
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        // Aligned with the rest of the modal content (PapSheet already insets this
        // row). On scroll the icons clip at the content box. [ZONE-AREA-001]
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = ZoneIcon.PRESETS, key = { it }) { key ->
            val isSelected = key == selectedKey
            Box(
                modifier = Modifier
                    .size(ZONE_ICON_CHIP_DP.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = zoneIconFor(key),
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ZoneRadiusSlider(
    radius: Float,
    onRadiusChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PapSectionHeader(
            title = stringResource(Res.string.home_zone_radius_section),
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = stringResource(Res.string.home_zone_radius_meters, radius.roundToInt()),
            style = PaparcarType.current.label,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Slider(
        value = radius,
        onValueChange = onRadiusChange,
        valueRange = Zone.MIN_RADIUS_METERS..Zone.MAX_RADIUS_METERS,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ZonePrivacyToggle(
    isPrivate: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
            Column {
                Text(
                    text = stringResource(Res.string.home_zone_private_label),
                    style = PaparcarType.current.body,
                )
                Text(
                    text = stringResource(Res.string.home_zone_private_hint),
                    style = PaparcarType.current.caption,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECTION_LABEL_ALPHA),
                )
            }
        }
        Switch(
            checked = isPrivate,
            onCheckedChange = onToggle,
        )
    }
}
