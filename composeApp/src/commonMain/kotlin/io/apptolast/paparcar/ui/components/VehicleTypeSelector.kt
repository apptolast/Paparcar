package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.VehicleType
import io.apptolast.paparcar.ui.icons.PaparcarIcons
import io.apptolast.paparcar.ui.theme.PaparcarSpacing
import io.apptolast.paparcar.ui.theme.PaparcarType
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
    val label: @Composable () -> String,
    val examples: @Composable () -> String,
)

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
    val options = listOf(
        TypeOption(VehicleType.CAR,        PaparcarIcons.VehicleCar,        { stringResource(Res.string.vehicle_type_car) },        { stringResource(Res.string.vehicle_type_car_examples) }),
        TypeOption(VehicleType.MOTORCYCLE, PaparcarIcons.VehicleMotorcycle, { stringResource(Res.string.vehicle_type_motorcycle) }, { stringResource(Res.string.vehicle_type_motorcycle_examples) }),
        TypeOption(VehicleType.SCOOTER,    PaparcarIcons.VehicleScooter,    { stringResource(Res.string.vehicle_type_scooter) },    { stringResource(Res.string.vehicle_type_scooter_examples) }),
        TypeOption(VehicleType.BIKE,       PaparcarIcons.VehicleBike,       { stringResource(Res.string.vehicle_type_bike) },       { stringResource(Res.string.vehicle_type_bike_examples) }),
    )

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
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.outlineVariant

    val bgColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    else
        MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(bgColor)
            .border(BorderWidth, borderColor, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.RadioButton
                this.selected = isSelected
            }
            .padding(horizontal = PaparcarSpacing.lg, vertical = PaparcarSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = option.icon,
            contentDescription = null,
            modifier = Modifier.size(IconSize),
            tint = if (isSelected) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(PaparcarSpacing.lg))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.label(),
                style = PaparcarType.current.body,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = option.examples(),
                style = PaparcarType.current.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isSelected) {
            Spacer(Modifier.width(PaparcarSpacing.sm))
            PapStatusBadge(label = "✓")
        }
    }
}
