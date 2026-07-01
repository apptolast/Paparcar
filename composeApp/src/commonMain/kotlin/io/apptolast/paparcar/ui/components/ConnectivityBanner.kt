package io.apptolast.paparcar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.domain.connectivity.ConnectivityBannerPhase
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.connectivity_offline_banner
import paparcar.composeapp.generated.resources.connectivity_restored_banner

/**
 * Single connectivity banner anchored at the very top of the app. It is a LAYOUT element (placed as
 * the first child of the root Column), never an overlay — when visible it takes real height and
 * pushes the rest of the app content down, so it never covers the search bar or a screen header.
 * When [ConnectivityBannerPhase.Hidden] it collapses to zero height. [CONN-BANNER-001]
 *
 * Two visible treatments, same position:
 * - [ConnectivityBannerPhase.Offline]: red [errorContainer] + WifiOff, persistent (per the colour
 *   rule: red is reserved for real alerts).
 * - [ConnectivityBannerPhase.Restored]: brand-green [primaryContainer] + Wifi, transient — the
 *   caller flips it back to Hidden after a short delay.
 */
@Composable
fun ConnectivityBanner(
    phase: ConnectivityBannerPhase,
    modifier: Modifier = Modifier,
) {
    // Keep rendering the last visible treatment during the collapse animation: `phase` is already
    // Hidden by the time the exit runs, so read from the retained value instead.
    var lastVisible by remember { mutableStateOf(ConnectivityBannerPhase.Offline) }
    if (phase != ConnectivityBannerPhase.Hidden) {
        lastVisible = phase
    }

    AnimatedVisibility(
        visible = phase != ConnectivityBannerPhase.Hidden,
        // Expand/shrink so the banner grows from zero height, reflowing siblings (the push-down).
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        val restored = lastVisible == ConnectivityBannerPhase.Restored
        val container = if (restored) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }
        val onContainer = if (restored) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        }
        val icon: ImageVector = if (restored) Icons.Rounded.Wifi else Icons.Rounded.WifiOff
        val label: StringResource =
            if (restored) Res.string.connectivity_restored_banner else Res.string.connectivity_offline_banner

        Surface(
            color = container,
            contentColor = onContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = BANNER_PADDING_H_DP.dp, vertical = BANNER_PADDING_V_DP.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BANNER_GAP_DP.dp, Alignment.CenterHorizontally),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(BANNER_ICON_DP.dp),
                )
                Text(
                    stringResource(label),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = BANNER_TEXT_SP.sp,
                )
            }
        }
    }
}

private const val BANNER_PADDING_H_DP = 16
private const val BANNER_PADDING_V_DP = 8
private const val BANNER_GAP_DP = 8
private const val BANNER_ICON_DP = 16
private const val BANNER_TEXT_SP = 13
