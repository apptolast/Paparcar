package com.rndeveloper.paparcar.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.theme.PaparcarType

/**
 * One figure of a stats grid: the number in the CIFRA voice, its label under it, and — when the
 * period before this one had the same figure — how it moved. [STATS-UI-001]
 *
 * A tile is only ever built for a figure that EXISTS: the aggregator returns null below each
 * field's significance threshold, and a null field gets no tile at all. There is deliberately no
 * "—" state here, because a dash reads as "zero" to the person looking at it.
 * [VEH-STATS-SAY-SOMETHING-USEFUL-001]
 */
@Composable
fun PapStatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    /** Movement against the previous period, already resolved to copy ("12%"). Null = no comparison. */
    delta: PapStatDelta? = null,
    /** Level-2 glyph beside the label — what the figure is ABOUT, so a grid of numbers can be
     *  scanned without reading six captions. Tinted like the label it belongs to. */
    icon: ImageVector? = null,
    /** The lead figure of its grid. Larger, never coloured. */
    emphasized: Boolean = false,
    /**
     * The number behind [value], for the count-up. Null = no animation, and that is the honest
     * default: most tiles hold a day of the week or a street name, which do not count up to
     * anything. [STATS-PANEL-001]
     */
    animatedNumber: StatTileNumber? = null,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = modifier.padding(horizontal = TILE_H_PAD_DP.dp, vertical = TILE_V_PAD_DP.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VALUE_DELTA_GAP_DP.dp),
        ) {
            // A figure that is the subject of its own block is CIFRA — and the size is the
            // role's, never the call site's. [UI-TYPE-TWO-VOICES-ONE-ROW-001]
            val valueStyle = if (emphasized) {
                PaparcarType.current.statNumber
            } else {
                PaparcarType.current.counter
            }
            if (animatedNumber != null) {
                Text(
                    text = rememberCountUpText(animatedNumber),
                    style = valueStyle,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                // "Martes" cannot count up to "Jueves", but it still CHANGED when the period
                // did, and a figure that swaps in place without a word of transition reads as a
                // glitch. Words swap, numbers climb — every tile moves, each in the way its own
                // value can. (User, 08-09-2026.)
                //
                // The swap is SEQUENCED, not a `Crossfade`: a crossfade drew "Martes" and
                // "Jueves" on top of each other for the whole fade. It went unnoticed here
                // because two short centred words overlap into something that still looks like
                // a word — the same overlap on the Activity card's km sentence was read on
                // device as the text changing several times. [UI-THE-KM-LINE-CHANGES-ONCE-001]
                PapTextSwap(targetState = value, label = "stat_value") { shown ->
                    Text(
                        text = shown,
                        style = valueStyle,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            delta?.let { StatDelta(it) }
        }
        Spacer(Modifier.height(VALUE_LABEL_GAP_DP.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LABEL_ICON_GAP_DP.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(LABEL_ICON_DP.dp),
                )
            }
            Text(
                text = label.uppercase(),
                style = PaparcarType.current.statLabel,
                color = cs.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * How a figure moved against the equivalent previous period. [amountText] is pre-formatted
 * ("12%", "1,2 kg") — this type carries the DIRECTION, which is the part the UI reasons about.
 */
data class PapStatDelta(val amountText: String, val isUp: Boolean)

/**
 * A tile's figure as a NUMBER plus the way to write it, which is what makes a count-up possible:
 * "14,2 kg" is a string, and a string cannot be interpolated between two values.
 *
 * [format] must be pure and cheap — it runs once per frame while the number is moving.
 */
data class StatTileNumber(val value: Double, val format: (Double) -> String)

/**
 * The figure, counting up to its new value — but ONLY when it changes while the tile is on screen.
 * [STATS-PANEL-001]
 *
 * The first composition writes the number as it is. That is the whole rule: a counter that runs
 * every time a tile scrolls into view is decoration, and worse, it says "this just happened" about
 * a figure that has been sitting there for a week. Here the animation IS the news — the user closed
 * a parking and watched the month's spend climb.
 */
@Composable
private fun rememberCountUpText(number: StatTileNumber): String =
    number.format(rememberCountUp(number.value.toFloat()).toDouble())

/**
 * A number on its way to [target] — the shared count-up. [STATS-PANEL-001]
 *
 * Public because the Activity card's own headline ("15 aparcamientos") has to climb exactly like a
 * tile does: they change for the same reason (the period moved) and two different speeds on one
 * screen would read as two different events.
 *
 * The first composition returns the value as it is: a counter that runs every time something
 * scrolls into view is decoration, and worse, it claims "this just happened" about a figure that
 * has been sitting there all week. Here the movement IS the news.
 */
@Composable
fun rememberCountUp(target: Float): Float {
    val animated = remember { Animatable(target) }
    LaunchedEffect(target) {
        if (animated.value == target) return@LaunchedEffect
        animated.animateTo(target, animationSpec = tween(COUNT_UP_MS, easing = FastOutSlowInEasing))
    }
    return animated.value
}

@Composable
private fun StatDelta(delta: PapStatDelta) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DELTA_ICON_GAP_DP.dp),
    ) {
        // Direction is a SHAPE, not a colour. Green-up/red-down would also be a judgement the app
        // has no business making: more kilometres is not "bad" and less CO₂ is not "good" for
        // someone who simply travelled less. The arrow states the fact and stops there.
        // [UI-COLOR-DOCTRINE-001]
        Icon(
            imageVector = if (delta.isUp) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
            contentDescription = null,
            tint = cs.onSurfaceVariant,
            modifier = Modifier.size(DELTA_ICON_DP.dp),
        )
        Text(
            text = delta.amountText,
            style = PaparcarType.current.caption,
            color = cs.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private const val TILE_H_PAD_DP = 14
private const val TILE_V_PAD_DP = 12
private const val VALUE_DELTA_GAP_DP = 6
private const val VALUE_LABEL_GAP_DP = 4
private const val LABEL_ICON_DP = 13
private const val LABEL_ICON_GAP_DP = 5
private const val DELTA_ICON_DP = 12
private const val DELTA_ICON_GAP_DP = 2
/** Long enough to read as a climb, short enough not to delay the number anyone came for. */
private const val COUNT_UP_MS = 700
