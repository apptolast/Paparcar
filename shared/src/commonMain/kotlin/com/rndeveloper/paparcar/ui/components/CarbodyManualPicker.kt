package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.ui.theme.PapShapes
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PapBorders
import com.rndeveloper.paparcar.ui.theme.PaparcarSpacing
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.vehicle_registration_carbody_picker_dismiss
import paparcar.composeapp.generated.resources.vehicle_registration_carbody_picker_title

/**
 * Dialog that lets the user override the inferred [CarbodyType]. The ten body
 * options are grouped under their [VehicleSize] header so the size dimension
 * stays visually anchored — picking a body always implies a size.
 *
 * Tapping any tile dispatches [onSelect] and immediately closes the dialog
 * via [onDismiss] — callers do not need to chain the two.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarbodyManualPicker(
    selected: CarbodyType?,
    onSelect: (CarbodyType) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicAlertDialog(onDismissRequest = onDismiss, modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(DIALOG_CORNER_DP.dp),
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(modifier = Modifier.padding(DIALOG_PADDING_DP.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.vehicle_registration_carbody_picker_title),
                        style = PaparcarType.current.cardTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(Res.string.vehicle_registration_carbody_picker_dismiss),
                        )
                    }
                }

                Spacer(Modifier.height(PaparcarSpacing.sm))

                CarbodyType.entries
                    .groupBy { it.sizeCategory }
                    .entries
                    .sortedBy { it.key.ordinal }
                    .forEach { (size, bodies) ->
                        if (size == VehicleSize.MOTORCYCLE) return@forEach
                        SizeHeader(size = size)
                        bodies.forEach { body ->
                            CarbodyRow(
                                body = body,
                                isSelected = body == selected,
                                onClick = {
                                    onSelect(body)
                                    onDismiss()
                                },
                            )
                        }
                        Spacer(Modifier.height(PaparcarSpacing.sm))
                    }
            }
        }
    }
}

@Composable
private fun SizeHeader(size: VehicleSize) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = size.label().uppercase(),
            // Condensed data slot — size tokens share the sizeBadge recipe app-wide.
            // [HOME-VEH-REFINE-001]
            style = PaparcarType.current.badge,
            color = PapColor.actionText,
            modifier = Modifier.padding(top = HEADER_TOP_PAD_DP, bottom = PaparcarSpacing.xs),
        )
        PapDivider()
    }
}

private val HEADER_TOP_PAD_DP = 6.dp
private val ROW_PAD_V_DP = 10.dp
private val ICON_CIRCLE_DP = 36.dp
private val ICON_GLYPH_DP = 22.dp

@Composable
private fun CarbodyRow(
    body: CarbodyType,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val bg = if (isSelected) cs.primary.copy(alpha = 0.12f) else cs.surface
    val border = if (isSelected) cs.primary else cs.outline.copy(alpha = 0.3f)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = PaparcarSpacing.xs),
        shape = PapShapes.row,
        color = bg,
        border = BorderStroke(if (isSelected) PapBorders.medium else PapBorders.thin, border),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = PaparcarSpacing.md, vertical = ROW_PAD_V_DP),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(ICON_CIRCLE_DP)
                    .clip(CircleShape)
                    .background(cs.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = vehicleIconPainter(carbody = body, size = null),
                    contentDescription = null,
                    tint = Color.Unspecified, // native multi-colour silhouette [BOLT-MARKERS-001]
                    modifier = Modifier.size(ICON_GLYPH_DP),
                )
            }
            Text(
                text = body.label(),
                style = PaparcarType.current.body,
                color = cs.onSurface,
            )
        }
    }
}

private const val DIALOG_CORNER_DP = 20
private const val DIALOG_PADDING_DP = 16
