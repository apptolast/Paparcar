package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.presentation.home.model.HomeParkingsSlice
import com.rndeveloper.paparcar.ui.components.PapSectionHeader
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.presentation.util.relativeTimeText
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_parkings_empty
import paparcar.composeapp.generated.resources.home_parkings_error
import paparcar.composeapp.generated.resources.home_parkings_remembered
import paparcar.composeapp.generated.resources.home_parkings_title

/**
 * "Car parks nearby", folded until asked for. [PARKINGS-ON-THE-MAP-001]
 *
 * Third section of the sheet, under "where to refuel", and built on the same fold: collapsed by
 * default and **fetched only on the first unfold**, because most sessions never ask this question
 * and a city of car parks downloaded for a list nobody opened is the user's data spent on our
 * convenience.
 *
 * ⛔ The ORDER of the three sections never changes — free spots, fuel, car parks — whatever each one
 * holds. What collapses is the body; a list that reorders itself under the finger was the shape
 * rejected in [FUEL-STATIONS-MAP-AND-PEEK-001].
 */
@Composable
fun HomeParkingsSection(
    slice: HomeParkingsSlice,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    /** A row opens the car park's peek — the same one its map capsule opens. */
    onParkingClick: ((String) -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // ⛔ `weight(1f)`: `PapSectionHeader` applies `fillMaxWidth()` to its own Text, so
            // without it the chevron lands off-screen and a foldable section shows nothing that
            // says it folds. Learnt on device, not in the gallery. [FUEL-STATIONS-LIST-001]
            PapSectionHeader(
                title = stringResource(Res.string.home_parkings_title),
                modifier = Modifier.weight(1f),
            )

            if (slice.isLoading) {
                // The spinner belongs to the row that was pressed.
                // [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001]
                CircularProgressIndicator(
                    modifier = Modifier.size(SPINNER_DP.dp),
                    strokeWidth = SPINNER_STROKE_DP.dp,
                )
            } else {
                Icon(
                    imageVector = if (slice.isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AnimatedVisibility(visible = slice.isExpanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                when {
                    // ⛔ A failure is NOT an empty list: saying "none nearby" when we could not ask
                    // sends the user driving on a claim we never checked.
                    // [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
                    slice.failed -> SectionNote(stringResource(Res.string.home_parkings_error))

                    slice.isLoading -> Unit

                    slice.parkings.isEmpty() ->
                        SectionNote(stringResource(Res.string.home_parkings_empty))

                    else -> HomePublicParkingRows(
                        parkings = slice.parkings,
                        onParkingClick = onParkingClick,
                    )
                }

                // ⛔ A remembered list must SAY it is remembered.
                // [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
                //
                // This is the third sentence, under the rows rather than instead of them: the app
                // could not ask just now, but it has been here before and this is what was there.
                // Without it the section shows month-old car parks with exactly the face it uses for
                // a list fetched a second ago — the same defect the fuel lane fixed one layer up,
                // where an average travelled with a date nobody read.
                // [FUEL-AN-AVERAGE-MUST-SAY-WHEN-IT-WAS-MEASURED-001]
                //
                // The AGE, not the date: `relativeTimeText` already answers "how old is this" in all
                // nine locales, so this costs one key instead of a month formatter times nine.
                slice.rememberedAtMs?.takeUnless { slice.isLoading }?.let { rememberedAt ->
                    SectionNote(
                        stringResource(
                            Res.string.home_parkings_remembered,
                            relativeTimeText(rememberedAt),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionNote(text: String) {
    Text(
        text = text,
        style = PaparcarType.current.caption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private const val SPINNER_DP = 18
private const val SPINNER_STROKE_DP = 2
