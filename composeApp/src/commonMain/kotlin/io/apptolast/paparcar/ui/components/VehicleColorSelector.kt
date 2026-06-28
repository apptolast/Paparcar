package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.VehicleColor
import io.apptolast.paparcar.ui.icons.PaparcarIcons

private val SWATCH_SIZE = 40.dp
private val SWATCH_GAP = 10.dp
private val SELECTED_RING_WIDTH = 2.5.dp
private val REST_BORDER_WIDTH = 1.dp
private val CHECK_SIZE = 20.dp
private const val CHECK_DARK_LUMINANCE = 0.55f

/**
 * Inline swatch picker for a vehicle's paint colour, styled like [VehicleSizeSelector]
 * (a wrapping row of tappable circles). The first swatch is the "default" (brand-green)
 * option that maps to a `null` colour; the rest are the [VehicleColor] palette. The
 * currently selected colour's name is shown below the row. [VEH-COLOR-001]
 *
 * @param selected currently chosen colour, or null for the default green.
 * @param onSelect called with the chosen colour (null = reset to default).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VehicleColorSelector(
    selected: VehicleColor?,
    onSelect: (VehicleColor?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options: List<VehicleColor?> = listOf(null) + VehicleColor.entries
    Column(modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SWATCH_GAP),
            verticalArrangement = Arrangement.spacedBy(SWATCH_GAP),
        ) {
            options.forEach { option ->
                ColorSwatch(
                    color = option.swatchColor(),
                    isSelected = selected == option,
                    isDefault = option == null,
                    label = option.colorLabel(),
                    onClick = { onSelect(option) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = selected.colorLabel(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    isSelected: Boolean,
    isDefault: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    // Selected → a primary ring; otherwise a hairline outline so light swatches (white,
    // silver) stay visible on the surface.
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }
    val borderWidth = if (isSelected) SELECTED_RING_WIDTH else REST_BORDER_WIDTH
    val glyphTint = if (color.luminance() > CHECK_DARK_LUMINANCE) Color.Black else Color.White
    Box(
        modifier = Modifier
            .size(SWATCH_SIZE)
            .clip(CircleShape)
            .background(color)
            .border(borderWidth, borderColor, CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.RadioButton
                this.selected = isSelected
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            isSelected -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(CHECK_SIZE),
                tint = glyphTint,
            )
            // The default swatch carries no hue meaning of its own, so mark it with a small
            // car glyph to read as "keep the standard look".
            isDefault -> Icon(
                imageVector = PaparcarIcons.VehicleCar,
                contentDescription = null,
                modifier = Modifier.size(CHECK_SIZE),
                tint = glyphTint,
            )
        }
    }
}
