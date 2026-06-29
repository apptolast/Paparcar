package io.apptolast.paparcar.ui.components

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
import androidx.compose.material3.HorizontalDivider
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
import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.VehicleSize
import org.jetbrains.compose.resources.painterResource
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
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
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

                Spacer(Modifier.height(8.dp))

                CarbodyType.entries
                    .groupBy { it.sizeCategory }
                    .toSortedMap(compareBy { it.ordinal })
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
                        Spacer(Modifier.height(8.dp))
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
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = cs.primary,
            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
        )
        HorizontalDivider(color = cs.outline.copy(alpha = 0.2f))
    }
}

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
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, border),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(cs.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(body.icon),
                    contentDescription = null,
                    tint = Color.Unspecified, // native multi-colour silhouette [BOLT-MARKERS-001]
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = body.label(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = cs.onSurface,
            )
        }
    }
}

private const val DIALOG_CORNER_DP = 20
private const val DIALOG_PADDING_DP = 16
