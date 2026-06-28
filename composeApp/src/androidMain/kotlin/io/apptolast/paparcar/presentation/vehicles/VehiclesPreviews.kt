package io.apptolast.paparcar.presentation.vehicles

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.presentation.preview.FakeData
import io.apptolast.paparcar.ui.components.PapSecondaryButton
import io.apptolast.paparcar.ui.icons.icon
import io.apptolast.paparcar.ui.theme.PaparcarSpacing
import io.apptolast.paparcar.ui.theme.PaparcarTheme

// ─── Production previews (Option A — uniform Surface) ─────────────────────────

@Preview(name = "Vehicles — lista · Claro", showBackground = true)
@Composable
private fun VehiclesListLightPreview() {
    PaparcarTheme(darkTheme = false) {
        VehiclesContent(
            state = VehiclesState(vehicles = FakeData.vehiclesWithStats, isLoading = false),
        )
    }
}

@Preview(name = "Vehicles — lista · Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VehiclesListDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        VehiclesContent(
            state = VehiclesState(vehicles = FakeData.vehiclesWithStats, isLoading = false),
        )
    }
}

@Preview(name = "Vehicles — vacío · Claro", showBackground = true)
@Composable
private fun VehiclesEmptyLightPreview() {
    PaparcarTheme(darkTheme = false) {
        VehiclesContent(
            state = VehiclesState(vehicles = emptyList(), isLoading = false),
        )
    }
}

@Preview(name = "Vehicles — vacío · Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VehiclesEmptyDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        VehiclesContent(
            state = VehiclesState(vehicles = emptyList(), isLoading = false),
        )
    }
}

@Preview(name = "Vehicles — cargando", showBackground = true)
@Composable
private fun VehiclesLoadingPreview() {
    PaparcarTheme(darkTheme = false) {
        VehiclesContent(
            state = VehiclesState(isLoading = true),
        )
    }
}

// ─── Option A vs B header comparison (UI-002) ────────────────────────────────
//
// Option A is what ships. These previews exist so the user can compare visually
// against Option B (surfaceContainerHigh + primary left accent bar) without
// running the app. To switch the production header to Option B, remove the
// "Activo" SpanStyle in VehiclePageContent.VehicleIdentityCard and wrap the
// Surface body in a Row with a 3dp leading Box in primary color.

@Preview(name = "Header A (uniform) · Claro", showBackground = true)
@Composable
private fun HeaderOptionALightPreview() {
    PaparcarTheme(darkTheme = false) {
        HeaderOptionA(vehicle = FakeData.vehicleSedan)
    }
}

@Preview(name = "Header A (uniform) · Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HeaderOptionADarkPreview() {
    PaparcarTheme(darkTheme = true) {
        HeaderOptionA(vehicle = FakeData.vehicleSedan)
    }
}

@Preview(name = "Header B (accent bar) · Claro", showBackground = true)
@Composable
private fun HeaderOptionBLightPreview() {
    PaparcarTheme(darkTheme = false) {
        HeaderOptionB(vehicle = FakeData.vehicleSedan)
    }
}

@Preview(name = "Header B (accent bar) · Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HeaderOptionBDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        HeaderOptionB(vehicle = FakeData.vehicleSedan)
    }
}

@Composable
private fun HeaderOptionA(vehicle: Vehicle) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        HeaderBody(vehicle = vehicle)
    }
}

@Composable
private fun HeaderOptionB(vehicle: Vehicle) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            if (vehicle.isActive) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            HeaderBody(vehicle = vehicle, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeaderBody(vehicle: Vehicle, modifier: Modifier = Modifier) {
    val sizeLabel = vehicleSizeLabelPreview(vehicle.sizeCategory)
    val activeLabel = "Activo"  // preview only
    val primaryColor = MaterialTheme.colorScheme.primary
    val subtitle = if (vehicle.isActive) {
        buildAnnotatedString {
            append("$sizeLabel · ")
            withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Medium)) {
                append(activeLabel)
            }
        }
    } else {
        buildAnnotatedString { append(sizeLabel) }
    }
    val displayName = listOfNotNull(vehicle.brand, vehicle.model)
        .joinToString(" ")
        .ifBlank { sizeLabel }

    Column(
        modifier = modifier.padding(
            horizontal = PaparcarSpacing.lg,
            vertical = PaparcarSpacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(PaparcarSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            io.apptolast.paparcar.ui.components.VehicleIcon(
                carbody = vehicle.carbodyType,
                size = vehicle.sizeCategory,
                tint = Color.Unspecified, // native multi-colour silhouette [BOLT-MARKERS-001]
                modifier = Modifier.size(60.dp),
            )
            Spacer(Modifier.width(PaparcarSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = {}) {
                Icon(Icons.Outlined.MoreVert, contentDescription = null)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.sm),
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = PaparcarSpacing.sm, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Outlined.DirectionsCar, contentDescription = null,
                         modifier = Modifier.width(14.dp))
                    Text("Auto-detect", style = MaterialTheme.typography.labelSmall)
                }
            }
            PapSecondaryButton(label = "Configurar Bluetooth", onClick = {})
        }
    }
}

private fun vehicleSizeLabelPreview(size: VehicleSize): String = when (size) {
    VehicleSize.MOTORCYCLE   -> "MOTO"
    VehicleSize.MICRO_SMALL  -> "SMALL"
    VehicleSize.MEDIUM_SUV -> "MEDIUM"
    VehicleSize.LARGE_SEDAN  -> "LARGE"
    VehicleSize.VAN_HIGH    -> "VAN"
}
