package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.theme.PapShapes
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.stats_locked_cta

/**
 * The SHAPE of the card behind the glass — each one mirrors the real card's own layout.
 * [UI-A-BLUR-MUST-LOOK-LIKE-THE-CARD-IT-HIDES-001]
 */
enum class PapLockedPreview {
    /** Label on the left, figure on the right, three of them: the pace and the recap rows. */
    Rows,

    /** Icon tile + name + meta line, divided: what `PapListItem` draws for every place. */
    ListRows,

    /** Days x bands of small cells at varying intensity: the heat grid, legend included. */
    Grid,
}

/**
 * A card whose CONTENT is Plus: its real title, a line saying what it is, and its own shape under
 * frosted glass. [MONEY-A-BLUR-IS-ONLY-HONEST-WHEN-THERE-IS-SOMETHING-BEHIND-IT-001]
 *
 * ⛔ **The blur covers a FORM, never a figure.** There are no numbers under the glass: not the real
 * ones (they live beyond the entitlement gate and have no business crossing it to be smudged) and
 * certainly not invented ones, which would make the card lie about the reader's own data while
 * pretending to be their data.
 *
 * ⛔ **But a form has to be the form of THIS card.** The first version drew three grey bars for all
 * five and read, on device, as a list that had failed to load — «entrecortado», said the user, and
 * they were right: what makes frosted glass legible as content is that the blob behind it has the
 * geometry of the thing it hides. So [preview] mirrors each card: rows label/figure, list items
 * with their icon tile, the 7x6 grid with its legend.
 * [UI-A-BLUR-MUST-LOOK-LIKE-THE-CARD-IT-HIDES-001]
 *
 * ⛔ **And the words are NOT blurred.** A locked card whose subject can only be guessed from a
 * smudge sells nothing; [description] says in one line what this card would tell you. It describes
 * the FEATURE, never the reader's data, which is why it can be read out loud with the glass on.
 *
 * ⛔ **Never a skeleton.** `PapShimmerBox` means "this is arriving"; these blocks are static and
 * carry a padlock, because a shimmering lock would promise that waiting is enough.
 *
 * ⚠️ On Android `Modifier.blur` is a no-op below API 31 and this project's `minSdk` is 26. That is
 * survivable BY CONSTRUCTION here and nowhere else: the silhouette is already unreadable without
 * the blur — it is blocks, not text — so the blur is a finish, not the privacy mechanism.
 *
 * The whole surface is clickable and leads to the paywall: a soft gate is one where the door is
 * visible and takes you to the till [MONEY-001].
 */
@Composable
fun PapLockedCard(
    title: String,
    description: String,
    preview: PapLockedPreview,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PapOutlinedCard(modifier = modifier.fillMaxWidth(), shape = PapShapes.card, onClick = onClick) {
        Column(modifier = Modifier.padding(CARD_PAD_DP.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TITLE_GAP_DP.dp),
            ) {
                Text(
                    text = title,
                    style = PaparcarType.current.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(LOCK_DP.dp),
                )
            }
            Spacer(Modifier.height(DESC_GAP_DP.dp))
            Text(
                text = description,
                // The subtitle role every list row in this app uses under a title — `body` was the
                // size of the card's own prose and turned a caption into a paragraph.
                // [UI-TYPE-TWO-VOICES-ONE-ROW-001]
                style = PaparcarType.current.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(BODY_GAP_DP.dp))
            // ⛔ **Unbounded, and NOT clipped.** Clipping the blur to a shape is what put a visible
            // rounded plate inside the card: the smudge stopped dead on all four sides, so instead
            // of frosted glass you saw a grey panel with cut edges. Letting it bleed past its own
            // bounds is what makes the haze fade out into the card, which is the whole look.
            // The gaps above and below are what the bleed lands in.
            // [UI-A-BLUR-MUST-LOOK-LIKE-THE-CARD-IT-HIDES-001]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .blur(BLUR_DP.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
            ) {
                when (preview) {
                    PapLockedPreview.Rows -> RowsPreview()
                    PapLockedPreview.ListRows -> ListPreview()
                    PapLockedPreview.Grid -> GridPreview()
                }
            }
            Spacer(Modifier.height(BODY_GAP_DP.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CTA_GAP_DP.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.WorkspacePremium,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(MARK_DP.dp),
                )
                Text(
                    text = stringResource(Res.string.stats_locked_cta),
                    style = PaparcarType.current.cta,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                PapNavChevron()
            }
        }
    }
}

/** `StatsPaceCard` / `StatsMonthRecapCard`: a label that runs long, a figure that does not. */
@Composable
private fun RowsPreview() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ROW_GAP_DP.dp),
    ) {
        ROW_LABEL_WIDTHS.forEachIndexed { index, labelFraction ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Block(Modifier.fillMaxWidth(labelFraction).height(LINE_H_DP.dp))
                Spacer(Modifier.weight(1f))
                Block(
                    Modifier
                        .width(ROW_VALUE_WIDTHS[index].dp)
                        .height(VALUE_H_DP.dp),
                    alpha = STRONG_ALPHA,
                )
            }
        }
        Spacer(Modifier.height(CAPTION_GAP_DP.dp))
        Block(Modifier.fillMaxWidth(CAPTION_WIDTH).height(CAPTION_H_DP.dp), alpha = FAINT_ALPHA)
    }
}

/** `StatsPlacesCard` / `StatsInsightsCard`: icon tile, a name, a meta line under it. */
@Composable
private fun ListPreview() {
    Column(modifier = Modifier.fillMaxWidth()) {
        LIST_NAME_WIDTHS.forEachIndexed { index, nameFraction ->
            if (index > 0) {
                Spacer(Modifier.height(LIST_ROW_GAP_DP.dp))
                Block(Modifier.fillMaxWidth().height(HAIRLINE_DP.dp), alpha = FAINT_ALPHA)
                Spacer(Modifier.height(LIST_ROW_GAP_DP.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TILE_GAP_DP.dp),
            ) {
                Block(Modifier.size(TILE_DP.dp), shape = PapShapes.card, alpha = STRONG_ALPHA)
                Column(verticalArrangement = Arrangement.spacedBy(LINE_GAP_DP.dp)) {
                    Block(Modifier.fillMaxWidth(nameFraction).height(LINE_H_DP.dp), alpha = STRONG_ALPHA)
                    Block(Modifier.fillMaxWidth(LIST_META_WIDTHS[index]).height(META_H_DP.dp))
                }
            }
        }
    }
}

/** `StatsWhenCard`: the day x band grid at varying intensity, with its axis and its legend. */
@Composable
private fun GridPreview() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CELL_GAP_DP.dp),
    ) {
        // Hour axis, offset by the day-label column exactly as the real grid is.
        Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP_DP.dp)) {
            Spacer(Modifier.width(AXIS_W_DP.dp))
            repeat(BANDS) {
                Block(Modifier.weight(1f).height(AXIS_H_DP.dp), alpha = FAINT_ALPHA)
            }
        }
        repeat(DAYS) { day ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CELL_GAP_DP.dp),
            ) {
                Block(
                    Modifier.width(AXIS_W_DP.dp).height(AXIS_H_DP.dp),
                    alpha = FAINT_ALPHA,
                )
                repeat(BANDS) { band ->
                    // The real grid is dense in a couple of bands and empty in most: a flat one
                    // reads as a table that failed to load, which is the bug this preview fixes.
                    Block(
                        Modifier.weight(1f).height(CELL_H_DP.dp),
                        alpha = CELL_ALPHAS[(day * BANDS + band) % CELL_ALPHAS.size],
                        shape = PapShapes.chip,
                    )
                }
            }
        }
        Spacer(Modifier.height(CAPTION_GAP_DP.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP_DP.dp)) {
            Block(Modifier.width(LEGEND_W_DP.dp).height(AXIS_H_DP.dp), alpha = FAINT_ALPHA)
            repeat(LEGEND_STEPS) { step ->
                Block(
                    Modifier.width(LEGEND_CELL_DP.dp).height(AXIS_H_DP.dp),
                    alpha = FAINT_ALPHA + step * LEGEND_ALPHA_STEP,
                    shape = PapShapes.chip,
                )
            }
        }
    }
}

@Composable
private fun Block(
    modifier: Modifier,
    alpha: Float = BLOCK_ALPHA,
    shape: Shape = BLOCK_SHAPE,
) {
    Spacer(
        modifier = modifier.background(
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            shape = shape,
        ),
    )
}

private val BLOCK_SHAPE = RoundedCornerShape(6.dp)

private const val CARD_PAD_DP = 16
private const val TITLE_GAP_DP = 8
private const val DESC_GAP_DP = 6
private const val BODY_GAP_DP = 16
private const val CTA_GAP_DP = 8
private const val LOCK_DP = 18
private const val MARK_DP = 18

// Enough to make a word unreadable at this block size, measured against the first pass's 7 dp,
// which left the bars looking like bars rather than like something behind glass.
private const val BLUR_DP = 12

private const val LINE_H_DP = 15
private const val VALUE_H_DP = 19
private const val META_H_DP = 12
private const val CAPTION_H_DP = 11
private const val HAIRLINE_DP = 1
private const val ROW_GAP_DP = 14
private const val LINE_GAP_DP = 7
private const val LIST_ROW_GAP_DP = 12
private const val CAPTION_GAP_DP = 4
private const val TILE_DP = 40
private const val TILE_GAP_DP = 12
private const val CAPTION_WIDTH = 0.66f

private val ROW_LABEL_WIDTHS = listOf(0.44f, 0.30f, 0.38f)
private val ROW_VALUE_WIDTHS = listOf(84, 62, 70)
private val LIST_NAME_WIDTHS = listOf(0.52f, 0.38f, 0.46f)
private val LIST_META_WIDTHS = listOf(0.78f, 0.64f, 0.72f)

private const val DAYS = 7
private const val BANDS = 6
private const val AXIS_W_DP = 22
private const val AXIS_H_DP = 10
private const val CELL_H_DP = 14
private const val CELL_GAP_DP = 4
private const val LEGEND_W_DP = 34
private const val LEGEND_CELL_DP = 18
private const val LEGEND_STEPS = 4
private const val LEGEND_ALPHA_STEP = 0.09f

private const val BLOCK_ALPHA = 0.26f
private const val STRONG_ALPHA = 0.40f
private const val FAINT_ALPHA = 0.14f
private val CELL_ALPHAS = listOf(
    0.10f, 0.38f, 0.16f, 0.52f, 0.12f, 0.22f,
    0.44f, 0.14f, 0.30f, 0.10f, 0.34f, 0.18f,
    0.12f, 0.26f, 0.48f, 0.16f, 0.20f, 0.36f,
    0.28f, 0.10f, 0.22f, 0.40f, 0.14f, 0.12f,
)
