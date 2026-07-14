package io.apptolast.paparcar.presentation.home.sections.sheet.components.peek

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.presentation.home.HomeIntent
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheet
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheetBanner
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheetEyebrowTone
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheetLead
import io.apptolast.paparcar.ui.components.PapFooterButton
import io.apptolast.paparcar.ui.components.PapFooterButtonStyle
import io.apptolast.paparcar.ui.components.PapSectionHeader
import io.apptolast.paparcar.ui.components.chips.PaparcarFilterChip
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_report_confirm_here
import paparcar.composeapp.generated.resources.home_report_header_label
import paparcar.composeapp.generated.resources.home_report_helper_primary
import paparcar.composeapp.generated.resources.home_report_helper_secondary
import paparcar.composeapp.generated.resources.home_report_size_section
import paparcar.composeapp.generated.resources.vehicle_size_large
import paparcar.composeapp.generated.resources.vehicle_size_medium
import paparcar.composeapp.generated.resources.vehicle_size_moto
import paparcar.composeapp.generated.resources.vehicle_size_small
import paparcar.composeapp.generated.resources.vehicle_size_unknown
import paparcar.composeapp.generated.resources.vehicle_size_van

// ═════════════════════════════════════════════════════════════════════════════
// ReportPeek — "Avisar plaza libre". [HOME-ATOMIZE-001 F3]
// ═════════════════════════════════════════════════════════════════════════════

@Composable
internal fun ReportPeek(
    title: String,
    selectedSize: VehicleSize?,
    isReporting: Boolean,
    isCameraMoving: Boolean,
    onIntent: (HomeIntent) -> Unit,
) {
    PapSheet(
        lead = PapSheetLead.Announce,
        eyebrow = stringResource(Res.string.home_report_header_label),
        // Manual report = blue, mirroring the manual-spot tint on the map. [UI-SHEET-001]
        eyebrowTone = PapSheetEyebrowTone.Manual,
        title = title,
        onDismiss = { onIntent(HomeIntent.ExitReportMode) },
        banner = {
            PapSheetBanner(
                title = stringResource(Res.string.home_report_helper_primary),
                subtitle = stringResource(Res.string.home_report_helper_secondary),
            )
        },
        chips = {
            PapSectionHeader(title = stringResource(Res.string.home_report_size_section))
            Spacer(Modifier.height(6.dp))
            SizeChipRow(
                selected = selectedSize,
                onSelect = { onIntent(HomeIntent.SetReportingSize(it)) },
            )
        },
        actions = {
            PapFooterButton(
                label = stringResource(Res.string.home_report_confirm_here),
                leadingIcon = Icons.Rounded.Campaign,
                onClick = { onIntent(HomeIntent.ConfirmReportSpot) },
                style = PapFooterButtonStyle.Filled,
                enabled = !isCameraMoving && !isReporting,
                isLoading = isReporting,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun SizeChipRow(selected: VehicleSize?, onSelect: (VehicleSize?) -> Unit) {
    val sizes = VehicleSize.entries

    // One canonical chip for filter bars AND this size selector — selection reads
    // as tinted container + primary accents, never a solid green fill. [UI-SHEET-001]
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "unknown") {
            PaparcarFilterChip(
                label = stringResource(Res.string.vehicle_size_unknown),
                selected = selected == null,
                onClick = { onSelect(null) },
            )
        }
        items(items = sizes, key = { it.name }) { size ->
            val label = stringResource(
                when (size) {
                    VehicleSize.MOTORCYCLE   -> Res.string.vehicle_size_moto
                    VehicleSize.MICRO_SMALL  -> Res.string.vehicle_size_small
                    VehicleSize.MEDIUM_SUV -> Res.string.vehicle_size_medium
                    VehicleSize.LARGE_SEDAN  -> Res.string.vehicle_size_large
                    VehicleSize.VAN_HIGH    -> Res.string.vehicle_size_van
                }
            )
            PaparcarFilterChip(
                label = label,
                selected = size == selected,
                onClick = { onSelect(size) },
            )
        }
    }
}
