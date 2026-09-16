package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.rndeveloper.paparcar.ui.theme.PapBorders
import com.rndeveloper.paparcar.ui.theme.PaparcarType

/**
 * A choice between options that are a CLOSED SET, shown all at once.
 * [UI-STATS-ONE-QUESTION-ONE-COMPONENT-001]
 *
 * 🔑 **Which control a choice gets is decided by its TYPE, not by how many options it happens to
 * have today.** Kilometres or miles is two by definition, and so is what the day × band grid
 * measures (`HeatMeasure`) — those are segmented. The chart's metrics are three by definition and
 * sometimes render as two because a period has no measured distance; those stay CHIPS. If the
 * control followed the count, the same place on the same screen would be two different things
 * depending on the month, which is worse than either choice.
 *
 * ⚠️ This was a private function of `SettingsScreen` and the stats panel could not reach it, so the
 * one pair-choice outside Settings was drawn as chips instead. The recipe lives here now and the
 * settings row composes it, rather than the other way round.
 *
 * The selected segment follows the house chip recipe ([chips.PaparcarFilterChip]):
 * primaryContainer fill + primary border/text — never the M3 default secondaryContainer.
 * [UI-COLOR-DOCTRINE-001]
 */
@Composable
fun <K> PapSegmentedChoice(
    options: List<Pair<K, String>>,
    selected: K,
    onSelect: (K) -> Unit,
    modifier: Modifier = Modifier,
    /** Drawn in M3's selection-check slot. Null keeps the check — see `SettingsScreen`'s theme row,
     *  which paints what each option LOOKS like instead. [UI-THEME-OPTION-SHOWS-ITS-THEME-001] */
    optionIcon: @Composable ((K) -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val segmentColors = SegmentedButtonDefaults.colors(
        activeContainerColor = cs.primaryContainer,
        activeContentColor = cs.primary,
        activeBorderColor = cs.primary,
        inactiveContainerColor = Color.Transparent,
        inactiveContentColor = cs.onSurface,
        inactiveBorderColor = cs.outline.copy(alpha = PapBorders.DEFAULT_OUTLINE_ALPHA),
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (key, label) ->
            SegmentedButton(
                selected = key == selected,
                onClick = { onSelect(key) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = segmentColors,
                icon = {
                    if (optionIcon != null) optionIcon(key)
                    else SegmentedButtonDefaults.Icon(active = key == selected)
                },
            ) {
                Text(label, style = PaparcarType.current.label)
            }
        }
    }
}
