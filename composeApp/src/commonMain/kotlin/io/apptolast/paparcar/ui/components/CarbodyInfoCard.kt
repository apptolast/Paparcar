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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.getParkingRules
import io.apptolast.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.vehicle_registration_carbody_auto_label
import paparcar.composeapp.generated.resources.vehicle_registration_carbody_change
import paparcar.composeapp.generated.resources.vehicle_registration_carbody_manual_label

/**
 * Feedback card surfaced once the registration form has enough signal (brand +
 * model) to infer a body type. Shows:
 *
 *  - the [CarbodyType.icon] painter
 *  - the size group title (e.g. "Mediano · SUV compacto")
 *  - the body subtitle (e.g. "SUV Mediano")
 *  - a parking advisory chip — tinted red when the body requires high ceiling
 *
 * The card surfaces an `Edit` affordance the user can tap to override the
 * inference manually via [onChange]. When [isManualOverride] is true the auto
 * badge flips to "Elegido manualmente" so the user always knows whether the
 * displayed body came from the catalog or their own pick.
 */
@Composable
fun CarbodyInfoCard(
    carbody: CarbodyType,
    sizeLabel: String,
    isManualOverride: Boolean,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val rules = carbody.getParkingRules()
    val alertCopy = rules.alertKey.label()
    val alertIcon = if (rules.requiresHighCeiling) Icons.Rounded.WarningAmber else Icons.Rounded.Info
    // Amber, not red: the ceiling note is an informational advisory — red is reserved for
    // destructive / blocking states. [UI-REGRESSION]
    val alertTint = if (rules.requiresHighCeiling) cs.secondary else cs.primary

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CARD_CORNER_DP.dp),
        color = cs.surfaceContainerHigh,
        border = BorderStroke(1.dp, cs.outline.copy(alpha = CARD_BORDER_ALPHA)),
    ) {
        Column(modifier = Modifier.padding(CARD_PADDING_DP.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(ICON_BOX_DP.dp)
                        .clip(CircleShape)
                        .background(cs.primary.copy(alpha = ICON_BG_ALPHA)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = vehicleIconPainter(carbody = carbody, size = null),
                        contentDescription = null,
                        tint = Color.Unspecified, // native multi-colour silhouette [BOLT-MARKERS-001]
                        modifier = Modifier.size(ICON_SIZE_DP.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sizeLabel,
                        style = PaparcarType.current.rowTitle,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = carbody.label(),
                        style = PaparcarType.current.body,
                        color = cs.onSurface.copy(alpha = SUBTITLE_ALPHA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    onClick = onChange,
                    shape = RoundedCornerShape(CHANGE_PILL_RADIUS_DP.dp),
                    color = cs.surfaceContainerHighest,
                    border = BorderStroke(1.dp, cs.outline.copy(alpha = CARD_BORDER_ALPHA)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            tint = cs.onSurface.copy(alpha = SUBTITLE_ALPHA),
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = stringResource(Res.string.vehicle_registration_carbody_change),
                            style = PaparcarType.current.label,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurface.copy(alpha = SUBTITLE_ALPHA),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            // Inference origin badge — flips between "auto" (sparkle) and "manual" (edit).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = if (isManualOverride) Icons.Rounded.Edit else Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(
                        if (isManualOverride) Res.string.vehicle_registration_carbody_manual_label
                        else Res.string.vehicle_registration_carbody_auto_label
                    ),
                    style = PaparcarType.current.label,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.primary,
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(ALERT_CORNER_DP.dp))
                    .background(alertTint.copy(alpha = ALERT_BG_ALPHA))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = alertIcon,
                    contentDescription = null,
                    tint = alertTint,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = alertCopy,
                    style = PaparcarType.current.caption,
                    color = alertTint,
                )
            }
        }
    }
}

/**
 * Read-only chip surfaced when the user picks a non-CAR [VehicleSize] (moto,
 * scooter, bike). Shows just the size label since carbody doesn't apply.
 */
@Composable
fun NonCarSizeBadge(
    sizeLabel: String,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CARD_CORNER_DP.dp),
        color = cs.surfaceContainerHigh,
        border = BorderStroke(1.dp, cs.outline.copy(alpha = CARD_BORDER_ALPHA)),
    ) {
        Row(
            modifier = Modifier.padding(CARD_PADDING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(ICON_BOX_DP.dp)
                    .clip(CircleShape)
                    .background(cs.primary.copy(alpha = ICON_BG_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = io.apptolast.paparcar.ui.icons.PaparcarIcons.VehicleMotorcycle,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(ICON_SIZE_DP.dp),
                )
            }
            Text(
                text = sizeLabel,
                style = PaparcarType.current.rowTitle,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
            )
        }
    }
}

@Suppress("UnusedReceiverParameter")
private fun VehicleSize.unused() = Unit  // anchors the import so build doesn't strip it

private const val CARD_CORNER_DP = 16
private const val CARD_PADDING_DP = 14
private const val CARD_BORDER_ALPHA = 0.4f
private const val ICON_BOX_DP = 48
private const val ICON_SIZE_DP = 28
private const val ICON_BG_ALPHA = 0.14f
private const val SUBTITLE_ALPHA = 0.65f
private const val CHANGE_PILL_RADIUS_DP = 999
private const val ALERT_CORNER_DP = 10
private const val ALERT_BG_ALPHA = 0.12f
