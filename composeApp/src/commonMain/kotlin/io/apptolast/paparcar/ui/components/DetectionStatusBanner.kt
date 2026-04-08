package io.apptolast.paparcar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.theme.PapBlue
import io.apptolast.paparcar.ui.theme.PapBlueMuted
import io.apptolast.paparcar.ui.theme.PapGreen
import io.apptolast.paparcar.ui.theme.PapGreenMuted
import io.apptolast.paparcar.ui.theme.PaparcarSpacing
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.detection_banner_ar_active
import paparcar.composeapp.generated.resources.detection_banner_bt_active
import paparcar.composeapp.generated.resources.detection_banner_pair_bt

private val BannerIconSize = 16.dp

// ─────────────────────────────────────────────────────────────────────────────
// State model
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Current parking-detection method displayed in [DetectionStatusBanner].
 *
 *  - [BluetoothActive]: paired BT device is in use; [deviceLabel] = short id/name.
 *  - [ActivityRecognitionActive]: AR coordinator strategy is running (no BT paired).
 *  - [Inactive]: detection is disabled — callers should hide the banner entirely.
 */
sealed class DetectionBannerState {
    data class BluetoothActive(val deviceLabel: String) : DetectionBannerState()
    data object ActivityRecognitionActive : DetectionBannerState()
    data object Inactive : DetectionBannerState()
}

// ─────────────────────────────────────────────────────────────────────────────
// DetectionStatusBanner
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Compact banner that shows the active parking-detection strategy.
 *
 * - BT active  → blue pill, BT icon, device label.
 * - AR active  → green pill, car icon, optional "Pair Bluetooth" CTA that calls [onConfigureBluetooth].
 * - Inactive   → banner is hidden (AnimatedVisibility collapse).
 *
 * Place at the top of the map overlay column.
 */
@Composable
fun DetectionStatusBanner(
    state: DetectionBannerState,
    modifier: Modifier = Modifier,
    onConfigureBluetooth: (() -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = state !is DetectionBannerState.Inactive,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier,
    ) {
        when (state) {
            is DetectionBannerState.BluetoothActive -> BtActiveBanner(
                deviceLabel = state.deviceLabel,
            )
            DetectionBannerState.ActivityRecognitionActive -> ArActiveBanner(
                onConfigureBluetooth = onConfigureBluetooth,
            )
            DetectionBannerState.Inactive -> Unit
        }
    }
}

@Composable
private fun BtActiveBanner(deviceLabel: String) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = PapBlueMuted,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PaparcarSpacing.md, vertical = PaparcarSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.xs),
        ) {
            Icon(
                imageVector = Icons.Outlined.Bluetooth,
                contentDescription = null,
                tint = PapBlue,
                modifier = Modifier.size(BannerIconSize),
            )
            Text(
                text = stringResource(Res.string.detection_banner_bt_active, deviceLabel),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = PapBlue,
            )
        }
    }
}

@Composable
private fun ArActiveBanner(onConfigureBluetooth: (() -> Unit)?) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = PapGreenMuted,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = PaparcarSpacing.md, vertical = PaparcarSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.xs),
        ) {
            Icon(
                imageVector = Icons.Outlined.DirectionsCar,
                contentDescription = null,
                tint = PapGreen,
                modifier = Modifier.size(BannerIconSize),
            )
            Text(
                text = stringResource(Res.string.detection_banner_ar_active),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = PapGreen,
            )
            if (onConfigureBluetooth != null) {
                Spacer(Modifier.width(PaparcarSpacing.sm))
                PapTextButton(
                    label = stringResource(Res.string.detection_banner_pair_bt),
                    onClick = onConfigureBluetooth,
                )
            }
        }
    }
}
