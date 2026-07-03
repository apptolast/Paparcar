package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.theme.PaparcarType

/**
 * The reusable list row — the recurring **leading + [overline] + title + subtitle + trailing**
 * anatomy that was hand-rolled across Settings, permissions, detection, selectors… Renders ONLY the
 * row (no container): wrap it in [PapOutlinedCard] for a bordered/clickable card, or drop it in a
 * plain `Surface` for a divided list row. Interaction lives on the container, layout lives here.
 *
 * - [leading] / [trailing] are slots (an icon [PapIconTile], a vehicle glyph, a spot puck, a switch,
 *   a chevron, a badge, a status pin — anything).
 * - [subtitle] is plain text; pass [subtitleSlot] instead for a structured meta row (chips/tokens).
 * - [overline] is a small uppercase eyebrow above the title (accent-coloured).
 * [UI-LIST-ITEM-001]
 */
@Composable
fun PapListItem(
    title: String,
    modifier: Modifier = Modifier,
    overline: String? = null,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    subtitleSlot: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    titleStyle: TextStyle = PaparcarType.current.body,
    titleWeight: FontWeight = FontWeight.SemiBold,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    subtitleStyle: TextStyle = PaparcarType.current.caption,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    overlineColor: Color = MaterialTheme.colorScheme.primary,
    contentPadding: PaddingValues = PaddingValues(horizontal = ROW_H_PAD_DP.dp, vertical = ROW_V_PAD_DP.dp),
    gap: Dp = ROW_GAP_DP.dp,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            if (overline != null) {
                Text(
                    text = overline.uppercase(),
                    style = PaparcarType.current.badge,
                    color = overlineColor,
                    maxLines = 1,
                )
                Spacer(Modifier.height(OVERLINE_GAP_DP.dp))
            }
            Text(
                text = title,
                style = titleStyle,
                fontWeight = titleWeight,
                color = titleColor,
            )
            when {
                subtitleSlot != null -> subtitleSlot()
                subtitle != null -> Text(
                    text = subtitle,
                    style = subtitleStyle,
                    color = subtitleColor,
                )
            }
        }
        trailing?.invoke()
    }
}

private const val ROW_H_PAD_DP = 16
private const val ROW_V_PAD_DP = 14
private const val ROW_GAP_DP = 14
private const val OVERLINE_GAP_DP = 2
