package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.ui.theme.PaparcarType

private val IconSize = 24.dp
private val TilePadding = 8.dp

private data class SizeOption(
    val size: VehicleSize,
    val label: String,
)

/**
 * Visual vehicle size selector.
 *
 * Horizontal row of tappable tiles. Each tile shows a Paparcar vehicle
 * icon and a short label.
 *
 * @param selected  Currently selected [VehicleSize], or null for nothing selected.
 * @param onSelect  Called when the user taps a size option.
 */
@Composable
fun VehicleSizeSelector(
    selected: VehicleSize?,
    onSelect: (VehicleSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        SizeOption(VehicleSize.MOTORCYCLE,   "Mini"),
        SizeOption(VehicleSize.MICRO_SMALL,  "Pequeño"),
        SizeOption(VehicleSize.MEDIUM_SUV,   "Mediano"),
        SizeOption(VehicleSize.LARGE_SEDAN,  "Grande"),
        SizeOption(VehicleSize.VAN_HIGH,     "Furgo"),
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        options.forEach { option ->
            SizeTile(
                option = option,
                isSelected = selected == option.size,
                onClick = { onSelect(option.size) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SizeTile(
    option: SizeOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    val contentColor = if (isSelected)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .clip(MaterialTheme.shapes.small)
            .background(bgColor)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.RadioButton
                this.selected = isSelected
            }
            .padding(vertical = TilePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Isometric pictogram, flattened to the tile's status colour via tint. Every tier resolves
        // one, two-wheelers included. [UI-A-MOTORCYCLE-IS-DRAWN-LIKE-EVERY-OTHER-VEHICLE-001]
        VehicleIcon(
            carbody = null,
            size = option.size,
            modifier = Modifier.size(IconSize),
            tint = contentColor,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = option.label,
            style = PaparcarType.current.label,
            color = contentColor,
        )
    }
}

