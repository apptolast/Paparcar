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
 *
 * ⛔ **And without M3's check.** If the fill and the border already say which option is on — the very
 * recipe every chip in the app is marked with — the tick is a SECOND mark for the same thing: the one
 * house control that pointed at itself twice, and it shifted the selected segment's label relative to
 * the others, so choosing moved the words. What is left in that slot is [optionIcon], for an option
 * with something to SHOW that its name does not say.
 */
@Composable
fun <K> PapSegmentedChoice(
    options: List<Pair<K, String>>,
    selected: K,
    onSelect: (K) -> Unit,
    modifier: Modifier = Modifier,
    /** What each option shows where M3 puts its check. Null draws NOTHING — see the doctrine note
     *  above. `SettingsScreen`'s theme row fills the slot with what each option LOOKS like.
     *  [UI-THEME-OPTION-SHOWS-ITS-THEME-001] */
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
                icon = { optionIcon?.invoke(key) },
            ) {
                Text(label, style = PaparcarType.current.label)
            }
        }
    }
}
