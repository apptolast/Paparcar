package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.onboarding.NearbyComplement
import com.rndeveloper.paparcar.ui.components.PapDivider
import com.rndeveloper.paparcar.ui.components.PapShimmerBlockScale
import com.rndeveloper.paparcar.ui.components.PapShimmerBox
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_fuel_intro
import paparcar.composeapp.generated.resources.home_parkings_intro

// ─────────────────────────────────────────────────────────────────────────────
// The two pieces every nearby lane needs, in one place because all three lanes
// need them the same way. [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001]
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A lane's own sentence: the honest one-liner that qualifies (or replaces) its rows — "we could not
 * ask", "this country publishes no prices", "prices reported at 09:41".
 *
 * ⛔ Never a title. The lane's subject is named by the chip that selected it, once.
 */
@Composable
internal fun LaneNote(text: String) {
    Text(
        text = text,
        style = PaparcarType.current.caption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * The one-off line that PRESENTS a complement: where its data comes from, and what it does not
 * promise. [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001]
 *
 * Shown the first time its lane is entered and never again — the moment the user has asked the
 * question is the only moment this is information rather than a lecture. It is what replaces the
 * third onboarding page the app deliberately does not have
 * ([ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]).
 *
 * Same [LaneNote] as every other sentence a lane says: no card, no tile, no colour of its own. It is
 * not a promotion, it is the lane introducing itself. [UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001]
 *
 * ⛔ The pairing complement → sentence lives HERE, once. Both lanes render it through this, so the
 * car-park line cannot end up under the stations.
 */
@Composable
internal fun LaneIntroNote(complement: NearbyComplement) {
    LaneNote(
        stringResource(
            when (complement) {
                NearbyComplement.CAR_PARKS -> Res.string.home_parkings_intro
                NearbyComplement.FUEL -> Res.string.home_fuel_intro
            },
        ),
    )
}

/**
 * The wait, drawn where the rows will land — same rhythm for spots, car parks and stations, because
 * all three lanes are lists of "icon + two lines" in the same 16dp grid.
 *
 * This is the doctrine of [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001] applied to a lane:
 * the fuel and car-park waits used to be a spinner in a section header's chevron slot; that header no
 * longer exists, and a wait shown anywhere other than the place that is loading is a wait the user
 * has to interpret.
 */
@Composable
internal fun NearbyRowsSkeleton(itemCount: Int = SKELETON_ITEM_COUNT) {
    Column {
        repeat(itemCount) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PapShimmerBox(
                    modifier = Modifier.size(SKELETON_ICON_DP.dp),
                    shape = RoundedCornerShape(SKELETON_ICON_RADIUS_DP.dp),
                    alphaScale = PapShimmerBlockScale,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(SKELETON_LINE_GAP)) {
                    PapShimmerBox(
                        modifier = Modifier.width(SKELETON_TITLE_WIDTH.dp).height(SKELETON_TITLE_H_DP.dp),
                        shape = RoundedCornerShape(SKELETON_LINE_RADIUS_DP.dp),
                        alphaScale = PapShimmerBlockScale,
                    )
                    PapShimmerBox(
                        modifier = Modifier.width(SKELETON_SUBTITLE_WIDTH.dp).height(SKELETON_SUBTITLE_H_DP.dp),
                        shape = RoundedCornerShape(SKELETON_LINE_RADIUS_DP.dp),
                        alphaScale = PapShimmerBlockScale * SKELETON_SUBTITLE_ALPHA_FACTOR,
                    )
                }
            }
            if (index < itemCount - 1) {
                PapDivider(modifier = Modifier.padding(start = ROW_DIVIDER_INSET_DP.dp, end = 16.dp))
            }
        }
    }
}

private const val SKELETON_ITEM_COUNT = 4
private const val SKELETON_ICON_DP = 42
private const val SKELETON_ICON_RADIUS_DP = 12
private const val SKELETON_LINE_RADIUS_DP = 4
private const val SKELETON_TITLE_WIDTH = 140
private const val SKELETON_TITLE_H_DP = 12
private const val SKELETON_SUBTITLE_WIDTH = 100
private const val SKELETON_SUBTITLE_H_DP = 10
private const val SKELETON_SUBTITLE_ALPHA_FACTOR = 0.7f

/** Divider inset that clears a row's leading 42dp glyph plus its 16dp gutter and 12dp gap. */
internal const val ROW_DIVIDER_INSET_DP = 70

/** Las dos líneas de un esqueleto de fila: el hueco imita el interlineado del título y su meta, que
 *  es lo que la fila real pondrá ahí. [UI-A-PAGE-KEEPS-ONE-VERTICAL-RHYTHM-001] */
private val SKELETON_LINE_GAP = 6.dp
