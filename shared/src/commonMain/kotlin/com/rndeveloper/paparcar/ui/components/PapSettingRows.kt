package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.theme.PapAlpha
import com.rndeveloper.paparcar.ui.theme.PaparcarType

/**
 * The three canonical setting-row flavours, built on [PapListItem]. They were born as private
 * composables inside SettingsScreen; being locked away there is what made
 * VehicleRegistrationScreen re-implement the switch row (and its icon tile) by hand.
 * [UI-LIST-ITEM-001] [SETTINGS-AUDIT-REMEDIATION-001]
 *
 * No own container — stack them inside a [PapOutlinedCard] with [PapDivider]s between rows.
 */

/**
 * Row with a trailing [Switch]. The WHOLE row is the toggle (`Modifier.toggleable` with
 * `Role.Switch`): one big touch target, and TalkBack announces the label as the switch's
 * name instead of an anonymous "switch, on". The [Switch] itself is purely visual
 * (`onCheckedChange = null`). When [enabled] is false the row is locked and dimmed but still
 * shows the REAL persisted value — never a fake "off".
 */
@Composable
fun PapSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    description: String? = null,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val contentAlpha = if (enabled) 1f else PapAlpha.disabled
    PapListItem(
        modifier = modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
        leading = icon?.let { { PapIconTile(icon = it) } },
        title = label,
        titleColor = cs.onSurface.copy(alpha = contentAlpha),
        subtitle = description,
        subtitleColor = subtitleColor.copy(alpha = subtitleColor.alpha * contentAlpha),
        trailing = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

/** Row that navigates somewhere — trailing chevron, whole row clickable. */
@Composable
fun PapNavRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    description: String? = null,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: (@Composable () -> Unit)? = null,
) {
    PapListItem(
        modifier = modifier.clickable(onClick = onClick),
        leading = icon?.let { { PapIconTile(icon = it) } },
        title = label,
        subtitle = description,
        subtitleColor = subtitleColor,
        trailing = trailing ?: { PapNavChevron() },
    )
}

/** Static info row — trailing value as a data token (Barlow `metadata`). */
@Composable
fun PapInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    PapListItem(
        modifier = modifier,
        leading = icon?.let { { PapIconTile(icon = it) } },
        title = label,
        trailing = { Text(value, style = PaparcarType.current.meta, color = valueColor) },
    )
}

/** The shared trailing chevron of navigation rows — one size, one dimness, everywhere. */
@Composable
fun PapNavChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = PapAlpha.dim),
        modifier = Modifier.size(NAV_CHEVRON_DP.dp),
    )
}

private const val NAV_CHEVRON_DP = 20
