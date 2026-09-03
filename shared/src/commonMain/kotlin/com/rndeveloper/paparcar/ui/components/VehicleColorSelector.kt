package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
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
import com.rndeveloper.paparcar.domain.model.VehicleColor
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PaparcarType

private val SWATCH_SIZE = 40.dp
private val SWATCH_GAP = 10.dp
private val SELECTED_RING_WIDTH = 2.5.dp
private val REST_BORDER_WIDTH = 1.dp

/** Surface-coloured breathing room between the selection ring and the fill, so the ring reads
 *  against ANY swatch — including the brand-green default, which is the same green as the ring
 *  itself. [UI-COLOR-THE-DEFAULT-SWATCH-MUST-SHOW-ITS-PAINT-001] */
private val SELECTED_RING_GAP = 2.dp
private val CHECK_SIZE = 20.dp
private const val CHECK_DARK_LUMINANCE = 0.55f

/**
 * Inline swatch picker for a vehicle's paint colour, styled like [VehicleSizeSelector]
 * (a wrapping row of tappable circles). The first swatch is the "default" (brand-green)
 * option that maps to a `null` colour; the rest are the [VehicleColor] palette. The
 * currently selected colour's name is shown below the row. [VEH-COLOR-001]
 *
 * Every swatch — the default one included — is painted with the fill it will apply, resolved by
 * `swatchColor()`. It carries no glyph: a bubble in a paint chart is said by its colour and by the
 * name underneath. [UI-COLOR-THE-DEFAULT-SWATCH-MUST-SHOW-ITS-PAINT-001]
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
                    label = option.colorLabel(),
                    onClick = { onSelect(option) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = selected.colorLabel(),
            style = PaparcarType.current.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    isSelected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    // Selected → a primary ring; otherwise a hairline outline so light swatches (white,
    // silver) stay visible on the surface.
    val borderColor = if (isSelected) {
        PapColor.selected
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }
    val borderWidth = if (isSelected) SELECTED_RING_WIDTH else REST_BORDER_WIDTH
    // The ring is drawn on the outer box and the fill is inset inside it, so the surface shows
    // through in between. At rest the inset is just the hairline, which keeps today's look (the
    // outline hugging the fill); when selected the extra gap detaches the ring from the fill, which
    // is what lets a green ring be seen on the green default — and stops the black swatch from
    // swallowing its own ring.
    val fillInset = if (isSelected) SELECTED_RING_WIDTH + SELECTED_RING_GAP else REST_BORDER_WIDTH
    val glyphTint = if (color.luminance() > CHECK_DARK_LUMINANCE) Color.Black else Color.White
    Box(
        modifier = Modifier
            .size(SWATCH_SIZE)
            // Before .clickable, so the ripple is bounded by the circle instead of the layout box.
            // The fill is clipped on the inner Box, so without this the outer node has no shape and
            // the press indication squares off over a round swatch.
            .clip(CircleShape)
            .border(borderWidth, borderColor, CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.RadioButton
                this.selected = isSelected
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(fillInset)
                .fillMaxSize()
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(CHECK_SIZE),
                    tint = glyphTint,
                )
            }
        }
    }
}
