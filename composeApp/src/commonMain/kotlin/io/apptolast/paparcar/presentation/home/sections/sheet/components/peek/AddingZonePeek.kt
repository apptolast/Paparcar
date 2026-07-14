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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.domain.model.ZoneIcon
import io.apptolast.paparcar.presentation.home.HomeIntent
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheet
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheetEyebrowTone
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheetLead
import io.apptolast.paparcar.presentation.util.zoneIconFor
import io.apptolast.paparcar.ui.components.PapClearIconButton
import io.apptolast.paparcar.ui.components.PapFooterButton
import io.apptolast.paparcar.ui.components.PapFooterButtonStyle
import io.apptolast.paparcar.ui.components.PapSectionHeader
import io.apptolast.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_zone_edit_header_label
import paparcar.composeapp.generated.resources.home_zone_header_label
import paparcar.composeapp.generated.resources.home_zone_icon_section
import paparcar.composeapp.generated.resources.home_zone_name_placeholder
import paparcar.composeapp.generated.resources.home_zone_private_hint
import paparcar.composeapp.generated.resources.home_zone_private_label
import paparcar.composeapp.generated.resources.home_zone_radius_meters
import paparcar.composeapp.generated.resources.home_zone_radius_section
import paparcar.composeapp.generated.resources.home_zone_save_action
import kotlin.math.roundToInt

// ═════════════════════════════════════════════════════════════════════════════
// AddingZonePeek — "Nueva zona habitual" (create + edit). [HOME-ATOMIZE-001 F3]
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
    val isEditing: Boolean,
    val isSaving: Boolean,
)

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
    PapSheet(
        lead = PapSheetLead.GenericIcon(icon = zoneIconFor(form.iconKey)),
        eyebrow = headerLabel,
        eyebrowTone = PapSheetEyebrowTone.Neutral,
        title = title,
        onDismiss = { onIntent(HomeIntent.ExitAddZoneMode) },
        content = {
            ZoneNameField(
                name = form.name,
                iconKey = form.iconKey,
                onNameChange = { onIntent(HomeIntent.UpdateAddingZoneName(it)) },
            )
            Spacer(Modifier.height(12.dp))
            PapSectionHeader(title = stringResource(Res.string.home_zone_icon_section))
            Spacer(Modifier.height(6.dp))
            ZoneIconPickerRow(
                selectedKey = form.iconKey,
                onSelect = { onIntent(HomeIntent.UpdateAddingZoneIcon(it)) },
            )
            Spacer(Modifier.height(14.dp))
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
                onClick = { onIntent(HomeIntent.ConfirmAddZone) },
                style = PapFooterButtonStyle.Filled,
                enabled = form.name.isNotBlank() && !form.isSaving && !isCameraMoving,
                isLoading = form.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun ZoneNameField(
    name: String,
    iconKey: String,
    onNameChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        placeholder = { Text(stringResource(Res.string.home_zone_name_placeholder)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        leadingIcon = {
            Icon(
                imageVector = zoneIconFor(iconKey),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailingIcon = if (name.isNotEmpty()) {
            { PapClearIconButton(onClick = { onNameChange("") }) }
        } else null,
        modifier = Modifier.fillMaxWidth(),
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
