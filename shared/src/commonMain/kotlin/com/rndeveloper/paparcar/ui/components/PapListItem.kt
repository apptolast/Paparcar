package com.rndeveloper.paparcar.ui.components

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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.theme.PaparcarType

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
    titleMaxLines: Int = Int.MAX_VALUE,
    subtitleStyle: TextStyle = PaparcarType.current.caption,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    subtitleMaxLines: Int = Int.MAX_VALUE,
    overlineColor: Color = MaterialTheme.colorScheme.primary,
    overlineStyle: TextStyle = PaparcarType.current.badge,
    /** Substring of [overline] to tint with [overlineHighlightColor] — the vehicle NAME wears its
     *  identity colour while the state words stay in [overlineColor]. [UI-COLOR-DOCTRINE-001] */
    overlineHighlight: String? = null,
    overlineHighlightColor: Color = overlineColor,
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
                    text = annotatedWithHighlight(
                        text = overline.uppercase(),
                        highlight = overlineHighlight?.uppercase(),
                        highlightColor = overlineHighlightColor,
                    ),
                    style = overlineStyle,
                    color = overlineColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(OVERLINE_GAP_DP.dp))
            }
            Text(
                text = title,
                style = titleStyle,
                fontWeight = titleWeight,
                color = titleColor,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitleSlot != null || subtitle != null) {
                Spacer(Modifier.height(TITLE_SUBTITLE_GAP_DP.dp))
            }
            when {
                subtitleSlot != null -> subtitleSlot()
                subtitle != null -> Text(
                    text = subtitle,
                    style = subtitleStyle,
                    color = subtitleColor,
                    maxLines = subtitleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/** Builds the text with [highlight] tinted in [highlightColor]; the rest inherits the Text colour.
 *  First occurrence only — an eyebrow names one vehicle. [UI-COLOR-DOCTRINE-001] */
internal fun annotatedWithHighlight(
    text: String,
    highlight: String?,
    highlightColor: Color,
): AnnotatedString {
    if (highlight.isNullOrBlank()) return AnnotatedString(text)
    val start = text.indexOf(highlight)
    if (start < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        addStyle(SpanStyle(color = highlightColor), start, start + highlight.length)
    }
}

private const val ROW_H_PAD_DP = 16
private const val ROW_V_PAD_DP = 14
private const val ROW_GAP_DP = 14
private const val OVERLINE_GAP_DP = 2
/** One breathing gap between title and its subtitle/meta row, uniform for every consumer. */
private const val TITLE_SUBTITLE_GAP_DP = 4
