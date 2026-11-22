package com.rndeveloper.paparcar.presentation.home.sections.sheet.components.peek

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.WrongLocation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.rndeveloper.paparcar.ui.components.papHorizontalFadingEdges
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.model.SpotReportGate
import com.rndeveloper.paparcar.domain.model.SpotReportPolicy
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.presentation.home.HomeIntent
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheet
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetBanner
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetEyebrowTone
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetLead
import com.rndeveloper.paparcar.ui.components.PapAlertDialog
import com.rndeveloper.paparcar.ui.components.PapDialogAccent
import com.rndeveloper.paparcar.ui.components.PapDialogAction
import com.rndeveloper.paparcar.ui.components.PapFooterButton
import com.rndeveloper.paparcar.ui.components.PapFooterButtonStyle
import com.rndeveloper.paparcar.ui.components.PapSectionHeader
import com.rndeveloper.paparcar.presentation.util.LocalDistanceUnit
import com.rndeveloper.paparcar.presentation.util.formatDistance
import com.rndeveloper.paparcar.ui.components.chips.PaparcarFilterChip
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_release_dialog_cancel
import paparcar.composeapp.generated.resources.home_report_confirm_dialog_body
import paparcar.composeapp.generated.resources.home_report_confirm_dialog_title
import paparcar.composeapp.generated.resources.home_report_confirm_here
import paparcar.composeapp.generated.resources.home_report_header_label
import paparcar.composeapp.generated.resources.home_report_helper_primary
import paparcar.composeapp.generated.resources.home_report_helper_secondary
import paparcar.composeapp.generated.resources.home_report_needs_location
import paparcar.composeapp.generated.resources.home_report_no_fix_title
import paparcar.composeapp.generated.resources.home_report_size_section
import paparcar.composeapp.generated.resources.home_report_too_far_sub
import paparcar.composeapp.generated.resources.home_report_too_far_title
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
    /** Whether the pin may be published at all. [SPOT-A-REPORT-YOU-CANNOT-SEE-IS-NOT-A-REPORT-001] */
    gate: SpotReportGate,
    onIntent: (HomeIntent) -> Unit,
) {
    // ⛔ El banner es UNA pregunta: «¿qué tengo que hacer ahora?». Mientras el pin es publicable eso
    // es centrarlo; cuando no lo es, es volver a donde estás — y entonces la instrucción vieja
    // sobraría, porque centrar un pin que no se puede confirmar no lleva a ninguna parte. Por eso la
    // razón SUSTITUYE al banner en lugar de apilarse debajo. [UI-SHEET-001]
    val blocked = gate != SpotReportGate.ALLOWED
    val maxDistance = formatDistance(
        meters = SpotReportPolicy.MAX_REPORT_DISTANCE_METERS.toFloat(),
        unit = LocalDistanceUnit.current,
    )

    // Un toque publica una plaza para todo el vecindario — el molde de confirmación, con la
    // dirección ya resuelta ([title]) en el cuerpo, antes de disparar el intent real.
    // [UI-A-PLACED-PIN-ASKS-BEFORE-IT-STICKS-001]
    var confirmingReport by remember { mutableStateOf(false) }

    PapSheet(
        lead = PapSheetLead.Announce,
        eyebrow = stringResource(Res.string.home_report_header_label).uppercase(),
        // Manual report = blue, mirroring the manual-spot tint on the map. [UI-SHEET-001]
        eyebrowTone = PapSheetEyebrowTone.Action,
        title = title,
        onDismiss = { onIntent(HomeIntent.ExitReportMode) },
        banner = {
            when (gate) {
                SpotReportGate.ALLOWED -> PapSheetBanner(
                    title = stringResource(Res.string.home_report_helper_primary),
                    subtitle = stringResource(Res.string.home_report_helper_secondary),
                )
                // Causa + consecuencia + remedio, y la distancia NUNCA escrita a mano: va formateada
                // para que quien eligió millas no lea kilómetros.
                // [I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001]
                SpotReportGate.TOO_FAR -> PapSheetBanner(
                    title = stringResource(Res.string.home_report_too_far_title),
                    subtitle = stringResource(Res.string.home_report_too_far_sub, maxDistance),
                    icon = Icons.Rounded.WrongLocation,
                    maxLines = Int.MAX_VALUE,
                )
                // Sin fix no sabemos dónde estás: decir «estás lejos» sería adivinar.
                SpotReportGate.NO_FIX -> PapSheetBanner(
                    title = stringResource(Res.string.home_report_no_fix_title),
                    subtitle = stringResource(Res.string.home_report_needs_location),
                    icon = Icons.Rounded.LocationOff,
                    maxLines = Int.MAX_VALUE,
                )
            }
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
                onClick = { confirmingReport = true },
                style = PapFooterButtonStyle.Filled,
                enabled = !blocked && !isCameraMoving && !isReporting,
                isLoading = isReporting,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )

    if (confirmingReport) {
        PapAlertDialog(
            onDismiss = { if (!isReporting) confirmingReport = false },
            accent = PapDialogAccent.Primary,
            icon = Icons.Rounded.Campaign,
            title = stringResource(Res.string.home_report_confirm_dialog_title),
            body = stringResource(Res.string.home_report_confirm_dialog_body, title),
            primaryLabel = stringResource(Res.string.home_report_confirm_here),
            onPrimary = { onIntent(HomeIntent.ConfirmReportSpot) },
            loadingAction = PapDialogAction.Primary.takeIf { isReporting },
            cancelLabel = stringResource(Res.string.home_release_dialog_cancel),
        )
    }
}

@Composable
private fun SizeChipRow(selected: VehicleSize?, onSelect: (VehicleSize?) -> Unit) {
    val sizes = VehicleSize.entries

    // One canonical chip for filter bars AND this size selector — selection reads
    // as tinted container + primary accents, never a solid green fill. [UI-SHEET-001]
    // Cinco tamaños no caben en 360 dp: la fila se desplaza, y el desvanecido del borde lo dice.
    // [UI-A-SCROLLING-CHIP-ROW-SAYS-SO-001]
    val sizeChipsScroll = rememberLazyListState()
    LazyRow(
        state = sizeChipsScroll,
        modifier = Modifier.fillMaxWidth().papHorizontalFadingEdges(sizeChipsScroll),
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
