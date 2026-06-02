package io.apptolast.paparcar.presentation.home.sections.sheet.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.ui.components.PapFooterButton
import io.apptolast.paparcar.ui.components.PapFooterButtonStyle
import io.apptolast.paparcar.ui.theme.PaparcarTheme

// ─────────────────────────────────────────────────────────────────────────────
// Shared mock building blocks
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MockPeekCard(
    label: String,
    title: String,
    subtitle: String? = null,
    accentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = CARD_H_PAD.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor,
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        maxLines = 1,
                    )
                }
            }
        }
        content()
        actions()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    accentColor: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            maxLines = 1,
        )
    }
}

@Composable
private fun CompatRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// CURRENT — Spot modal (SpotMetaRow: labelSmall inline)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CurrentSpotModal() {
    val accent = MaterialTheme.colorScheme.tertiary
    MockPeekCard(
        label = "High",
        title = "Calle Serrano, 41",
        accentColor = accent,
        leadingIcon = Icons.Outlined.LocalParking,
        content = {
            CompatRow("Fits medium car")
            Spacer(Modifier.height(8.dp))
            InfoRow(Icons.Outlined.Navigation, "320m  ·  4 min drive", accent)
            Spacer(Modifier.height(6.dp))
            // SpotMetaRow ACTUAL — todo inline, labelSmall
            val metaColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.Group, null, tint = metaColor, modifier = Modifier.size(18.dp))
                    Text("2 on the way", style = MaterialTheme.typography.labelSmall, color = metaColor)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.Schedule, null, tint = metaColor, modifier = Modifier.size(18.dp))
                    Text("Posted 5 min ago", style = MaterialTheme.typography.labelSmall, color = metaColor)
                }
            }
            Spacer(Modifier.height(12.dp))
            // Fiability stub
            FiabilityStub(accent)
            Spacer(Modifier.height(14.dp))
        },
        actions = {
            PapFooterButton("Navigate to spot", onClick = {}, leadingIcon = Icons.Outlined.Navigation, style = PapFooterButtonStyle.Filled, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            PapFooterButton("Mark as occupied", onClick = {}, leadingIcon = Icons.Outlined.Block, style = PapFooterButtonStyle.Outlined, modifier = Modifier.fillMaxWidth())
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// CURRENT — Parking modal (ParkingDurationRow: bodyMedium SemiBold)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CurrentParkingModal() {
    val accent = MaterialTheme.colorScheme.secondary
    MockPeekCard(
        label = "Car parked",
        title = "Avda. de América, 12",
        subtitle = "Cupra Born",
        accentColor = accent,
        leadingIcon = Icons.Filled.DirectionsCar,
        content = {
            // ParkingDurationRow ACTUAL — bodyMedium SemiBold, igual que DistanceRow
            InfoRow(Icons.AutoMirrored.Outlined.DirectionsWalk, "500m  ·  6 min walk", accent)
            Spacer(Modifier.height(8.dp))
            InfoRow(Icons.Outlined.Schedule, "Parked 25 min", accent)
            Spacer(Modifier.height(14.dp))
        },
        actions = {
            PapFooterButton("Walk to car", onClick = {}, leadingIcon = Icons.AutoMirrored.Outlined.DirectionsWalk, style = PapFooterButtonStyle.Filled, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            PapFooterButton("Move location", onClick = {}, leadingIcon = Icons.Outlined.EditLocationAlt, style = PapFooterButtonStyle.Outlined, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            PapFooterButton("Release parking", onClick = {}, leadingIcon = Icons.AutoMirrored.Outlined.Logout, style = PapFooterButtonStyle.Outlined, modifier = Modifier.fillMaxWidth())
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// PROPOSED — Spot modal (cada fila = bodyMedium SemiBold, igual que parking)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProposedSpotModal() {
    val accent = MaterialTheme.colorScheme.tertiary
    MockPeekCard(
        label = "High",
        title = "Calle Serrano, 41",
        accentColor = accent,
        leadingIcon = Icons.Outlined.LocalParking,
        content = {
            CompatRow("Fits medium car")
            Spacer(Modifier.height(8.dp))
            InfoRow(Icons.Outlined.Navigation, "320m  ·  4 min drive", accent)
            Spacer(Modifier.height(8.dp))
            // SpotMetaRow NUEVO — misma fila prominente que ParkingDurationRow
            InfoRow(Icons.Outlined.Schedule, "Posted 5 min ago", accent)
            Spacer(Modifier.height(8.dp))
            InfoRow(Icons.Outlined.Group, "2 on the way", accent)
            Spacer(Modifier.height(12.dp))
            FiabilityStub(accent)
            Spacer(Modifier.height(14.dp))
        },
        actions = {
            PapFooterButton("Navigate to spot", onClick = {}, leadingIcon = Icons.Outlined.Navigation, style = PapFooterButtonStyle.Filled, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            PapFooterButton("Mark as occupied", onClick = {}, leadingIcon = Icons.Outlined.Block, style = PapFooterButtonStyle.Outlined, modifier = Modifier.fillMaxWidth())
        },
    )
}

// Parking modal no cambia — ya es el target style
@Composable
private fun ProposedParkingModal() = CurrentParkingModal()

// ─────────────────────────────────────────────────────────────────────────────
// Fiability stub
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FiabilityStub(accent: androidx.compose.ui.graphics.Color) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "RELIABILITY",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = cs.onSurface.copy(alpha = 0.55f),
            letterSpacing = 0.8.sp,
        )
        Text(
            "Expires in 12 min",
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurface.copy(alpha = 0.55f),
        )
    }
    Spacer(Modifier.height(6.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(5) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i < 4) accent else cs.onSurface.copy(alpha = 0.25f)),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Divider between current and proposed
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(name = "SPOT — actual vs propuesta · claro", showBackground = true)
@Composable
private fun SpotCompareLight() {
    PaparcarTheme(darkTheme = false) {
        Surface {
            Column {
                SectionLabel("ACTUAL")
                CurrentSpotModal()
                SectionLabel("PROPUESTA — filas unificadas")
                ProposedSpotModal()
            }
        }
    }
}

@Preview(name = "SPOT — actual vs propuesta · oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SpotCompareDark() {
    PaparcarTheme(darkTheme = true) {
        Surface {
            Column {
                SectionLabel("ACTUAL")
                CurrentSpotModal()
                SectionLabel("PROPUESTA — filas unificadas")
                ProposedSpotModal()
            }
        }
    }
}

@Preview(name = "PARKING — referencia · claro", showBackground = true)
@Composable
private fun ParkingReferenceLight() {
    PaparcarTheme(darkTheme = false) {
        Surface { CurrentParkingModal() }
    }
}

@Preview(name = "PARKING — referencia · oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ParkingReferenceDark() {
    PaparcarTheme(darkTheme = true) {
        Surface { CurrentParkingModal() }
    }
}

private const val CARD_H_PAD = 20
