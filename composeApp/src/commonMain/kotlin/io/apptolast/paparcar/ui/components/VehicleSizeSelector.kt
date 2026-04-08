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
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.ui.theme.PaparcarSpacing
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.vehicle_size_large
import paparcar.composeapp.generated.resources.vehicle_size_large_examples
import paparcar.composeapp.generated.resources.vehicle_size_medium
import paparcar.composeapp.generated.resources.vehicle_size_medium_examples
import paparcar.composeapp.generated.resources.vehicle_size_moto
import paparcar.composeapp.generated.resources.vehicle_size_moto_examples
import paparcar.composeapp.generated.resources.vehicle_size_small
import paparcar.composeapp.generated.resources.vehicle_size_small_examples
import paparcar.composeapp.generated.resources.vehicle_size_van
import paparcar.composeapp.generated.resources.vehicle_size_van_examples

private val EmojiSize = 28.sp
private val BorderWidth = 1.5.dp

private data class SizeOption(
    val size: VehicleSize,
    val emoji: String,
    val label: @Composable () -> String,
    val examples: @Composable () -> String,
)

/**
 * Visual vehicle size selector.
 *
 * Replaces the radio-button list in [VehicleRegistrationScreen] with tappable
 * cards arranged in a vertical list. Each card shows an emoji, a size label,
 * and example model names.
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
        SizeOption(VehicleSize.MOTO,   "🏍️", { stringResource(Res.string.vehicle_size_moto) },   { stringResource(Res.string.vehicle_size_moto_examples) }),
        SizeOption(VehicleSize.SMALL,  "🚗", { stringResource(Res.string.vehicle_size_small) },  { stringResource(Res.string.vehicle_size_small_examples) }),
        SizeOption(VehicleSize.MEDIUM, "🚙", { stringResource(Res.string.vehicle_size_medium) }, { stringResource(Res.string.vehicle_size_medium_examples) }),
        SizeOption(VehicleSize.LARGE,  "🛻", { stringResource(Res.string.vehicle_size_large) },  { stringResource(Res.string.vehicle_size_large_examples) }),
        SizeOption(VehicleSize.VAN,    "🚐", { stringResource(Res.string.vehicle_size_van) },    { stringResource(Res.string.vehicle_size_van_examples) }),
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PaparcarSpacing.sm),
    ) {
        options.forEach { option ->
            SizeTile(
                option = option,
                isSelected = selected == option.size,
                onClick = { onSelect(option.size) },
            )
        }
    }
}

@Composable
private fun SizeTile(
    option: SizeOption,
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
        Text(text = option.emoji, fontSize = EmojiSize)
        Spacer(Modifier.width(PaparcarSpacing.lg))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.label(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = option.examples(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isSelected) {
            Spacer(Modifier.width(PaparcarSpacing.sm))
            PapStatusBadge(label = "✓")
        }
    }
}
