package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.sp

/** Leading marker of a [VehicleStatusPill]: none, a solid dot, or a Bluetooth glyph. */
internal enum class PillLeading { None, Dot, BtIcon }

/**
 * Filled eyebrow pill for a vehicle's monitoring state (Active / Bluetooth / Activate…). Shared by
 * the Vehicles hero card and the Home vehicle chip so both read identically. Same height, padding
 * and typography across states so they align visually above the vehicle name. [fill] paints the
 * background, [content] the uppercase overline text, [marker] the leading dot / BT icon / spinner.
 * The action variant ([onClick] non-null) keeps its marker as an activation hint; when [isLoading]
 * is true the leading marker is replaced by a spinner and the click is suppressed. [HOME-CARDS-001]
 */
@Composable
internal fun VehicleStatusPill(
    text: String,
    fill: Color,
    content: Color,
    marker: Color,
    leading: PillLeading,
    onClick: (() -> Unit)?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(PILL_RADIUS_DP.dp)
    // Fixed pill height so every state is exactly the same size and the gap down to the name is
    // identical regardless of the label or marker. [CHIP-DRIVING-001]
    val pillModifier = modifier.height(EYEBROW_PILL_H_DP.dp)
    val inner: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = PILL_H_PAD.dp),
        ) {
            when {
                isLoading -> CircularProgressIndicator(
                    modifier = Modifier.size(BADGE_ICON_DP.dp),
                    strokeWidth = 2.dp,
                    color = marker,
                )
                leading == PillLeading.Dot -> Box(
                    modifier = Modifier
                        .size(ACTIVE_DOT_DP.dp)
                        .clip(CircleShape)
                        .background(marker),
                )
                leading == PillLeading.BtIcon -> Icon(
                    imageVector = Icons.Rounded.Bluetooth,
                    contentDescription = null,
                    tint = marker,
                    modifier = Modifier.size(BADGE_ICON_DP.dp),
                )
                else -> Unit
            }
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontSize = EYEBROW_TEXT_SP.sp,
                lineHeight = EYEBROW_TEXT_SP.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = EYEBROW_TRACKING_SP.sp,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    val safeOnClick: (() -> Unit)? = onClick?.takeIf { !isLoading }
    if (safeOnClick != null) {
        Surface(onClick = safeOnClick, modifier = pillModifier, shape = shape, color = fill) { inner() }
    } else {
        Surface(modifier = pillModifier, shape = shape, color = fill) { inner() }
    }
}

private const val PILL_RADIUS_DP = 999
private const val PILL_H_PAD = 8
private const val EYEBROW_PILL_H_DP = 19
private const val EYEBROW_TRACKING_SP = 0.4f
private const val EYEBROW_TEXT_SP = 9f
private const val ACTIVE_DOT_DP = 5
private const val BADGE_ICON_DP = 11
