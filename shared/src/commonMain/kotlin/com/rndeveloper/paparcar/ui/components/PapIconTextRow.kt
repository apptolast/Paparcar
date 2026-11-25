package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset

/**
 * Centers the icon and text on one line. Wrapped text and title/subtitle blocks start at the top.
 * [text] supplies its baselines through Compose Text, including when it is wrapped in a Column.
 * Use [fillText] to keep a trailing chevron at the end of the row.
 */
@Composable
internal fun PapIconTextRow(
    gap: Dp,
    modifier: Modifier = Modifier,
    multilineIconTopPadding: Dp = 0.dp,
    trailing: (@Composable () -> Unit)? = null,
    fillText: Boolean = false,
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            Box { icon() }
            Box { text() }
            if (trailing != null) Box { trailing() }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gapPx = gap.roundToPx()
        val leading = measurables[0].measure(loose)
        val afterIcon = loose.offset(horizontal = -leading.width)
        val leadingGap = gapPx.coerceAtMost(afterIcon.maxWidth)
        val afterLeadingGap = afterIcon.offset(horizontal = -leadingGap)
        val end = measurables.getOrNull(2)?.measure(afterLeadingGap)
        val afterTrailing = afterLeadingGap.offset(horizontal = -(end?.width ?: 0))
        val trailingGap = if (end == null) 0 else gapPx.coerceAtMost(afterTrailing.maxWidth)
        val textConstraints = afterTrailing.offset(horizontal = -trailingGap)
        val label = measurables[1].measure(
            if (fillText && textConstraints.hasBoundedWidth) {
                textConstraints.copy(minWidth = textConstraints.maxWidth)
            } else {
                textConstraints
            },
        )

        val firstBaseline = label[FirstBaseline]
        val singleLine = firstBaseline != AlignmentLine.Unspecified &&
            firstBaseline == label[LastBaseline]
        val iconTop = if (singleLine) {
            0
        } else {
            multilineIconTopPadding.roundToPx().coerceAtMost(constraints.maxHeight - leading.height)
        }
        val height = constraints.constrainHeight(
            maxOf(leading.height + iconTop, label.height, end?.height ?: 0),
        )
        val textX = leading.width + leadingGap
        val width = constraints.constrainWidth(textX + label.width + trailingGap + (end?.width ?: 0))

        layout(width, height) {
            fun top(childHeight: Int): Int =
                if (singleLine) Alignment.CenterVertically.align(childHeight, height) else 0

            leading.placeRelative(0, top(leading.height) + iconTop)
            label.placeRelative(textX, top(label.height))
            end?.placeRelative(textX + label.width + trailingGap, top(end.height))
        }
    }
}
