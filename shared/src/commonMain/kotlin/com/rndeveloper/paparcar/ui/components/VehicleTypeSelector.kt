package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.model.VehicleType
import com.rndeveloper.paparcar.ui.icons.PaparcarIcons
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PaparcarSpacing
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.vehicle_type_bike
import paparcar.composeapp.generated.resources.vehicle_type_bike_examples
import paparcar.composeapp.generated.resources.vehicle_type_car
import paparcar.composeapp.generated.resources.vehicle_type_car_examples
import paparcar.composeapp.generated.resources.vehicle_type_motorcycle
import paparcar.composeapp.generated.resources.vehicle_type_motorcycle_examples
import paparcar.composeapp.generated.resources.vehicle_type_scooter
import paparcar.composeapp.generated.resources.vehicle_type_scooter_examples

private val IconSize = 32.dp
private val BorderWidth = 1.5.dp

private data class TypeOption(
    val type: VehicleType,
    val icon: ImageVector,
    val label: StringResource,
    val examples: StringResource,
)

/**
 * [VEH-A-NEW-VEHICLE-TYPE-MUST-NOT-BE-A-CAR-BY-OMISSION-001] How each type shows itself in the
 * picker — an exhaustive `when`, so a type added tomorrow does not compile until it has an icon and
 * its two strings. The list below is then derived from [VehicleType.entries] rather than written by
 * hand: a hand-written list omits silently, and a type absent from this picker is a type the user
 * can never choose while every downstream lane still handles it.
 */
private fun VehicleType.toOption(): TypeOption = when (this) {
    VehicleType.CAR -> TypeOption(
        this, PaparcarIcons.VehicleCar, Res.string.vehicle_type_car, Res.string.vehicle_type_car_examples,
    )
    VehicleType.MOTORCYCLE -> TypeOption(
        this, PaparcarIcons.VehicleMotorcycle, Res.string.vehicle_type_motorcycle, Res.string.vehicle_type_motorcycle_examples,
    )
    VehicleType.SCOOTER -> TypeOption(
        this, PaparcarIcons.VehicleScooter, Res.string.vehicle_type_scooter, Res.string.vehicle_type_scooter_examples,
    )
    VehicleType.BIKE -> TypeOption(
        this, PaparcarIcons.VehicleBike, Res.string.vehicle_type_bike, Res.string.vehicle_type_bike_examples,
    )
}

/**
 * Visual vehicle type selector (CAR / MOTORCYCLE / SCOOTER / BIKE).
 *
 * Mirrors [VehicleSizeSelector] structurally — vertical list of tappable tiles
 * with icon + label + example use-case. The selected type drives downstream
 * detection logic: SCOOTER / BIKE bypass the Coordinator algorithm entirely
 * (they're typically dismounted on the sidewalk, never confirm a parking
 * spot). [BUG-SCOOTER-001]
 */
@Composable
fun VehicleTypeSelector(
    selected: VehicleType?,
    onSelect: (VehicleType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = VehicleType.entries.map { it.toOption() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PaparcarSpacing.sm),
    ) {
        options.forEach { option ->
            TypeTile(
                option = option,
                isSelected = selected == option.type,
                onClick = { onSelect(option.type) },
            )
        }
    }
}

@Composable
private fun TypeTile(
    option: TypeOption,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected)
        PapColor.selected
    else
        MaterialTheme.colorScheme.outlineVariant

    val bgColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    else
        MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(bgColor)
            .border(BorderWidth, borderColor, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.RadioButton
                this.selected = isSelected
            },
    ) {
        PapListItem(
            title = stringResource(option.label),
            subtitle = stringResource(option.examples),
            titleColor = if (isSelected) PapColor.selected
                         else MaterialTheme.colorScheme.onSurface,
            contentPadding = PaddingValues(horizontal = PaparcarSpacing.lg, vertical = PaparcarSpacing.md),
            gap = PaparcarSpacing.lg,
            leading = {
                Icon(
                    imageVector = option.icon,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize),
                    tint = if (isSelected) PapColor.selected
                           else MaterialTheme.colorScheme.onSurface,
                )
            },
            trailing = if (isSelected) {
                { PapStatusBadge(label = "✓") }
            } else null,
        )
    }
}
