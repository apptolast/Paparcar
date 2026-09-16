package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.theme.PapShapes
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.stats_locked_cta

/** What the blurred body is shaped like — the card's own layout, said without its data. */
enum class PapLockedShape {
    /** Rows of a list: last month, the conclusions, the places, the pace. */
    Lines,

    /** The day x band grid. */
    Grid,
}

/**
 * A card whose CONTENT is Plus, shown as a blurred silhouette with its real title and a way in.
 * [MONEY-A-BLUR-IS-ONLY-HONEST-WHEN-THERE-IS-SOMETHING-BEHIND-IT-001]
 *
 * ⛔ **The blur covers a FORM, never a figure.** There are no numbers under the glass: not the real
 * ones (they live beyond the entitlement gate and have no business crossing it to be smudged) and
 * certainly not invented ones, which would make the card lie about the reader's own data while
 * pretending to be their data. What the silhouette says is "there is a card of this shape here",
 * and the title above it says which one.
 *
 * ⛔ **And it is never a skeleton.** `PapShimmerBox` means "this is arriving"; these blocks are
 * static and carry a padlock, because a shimmering lock would promise that waiting is enough.
 *
 * ⚠️ On Android `Modifier.blur` is a no-op below API 31 and this project's `minSdk` is 26. That is
 * survivable BY CONSTRUCTION here and nowhere else: the silhouette is already unreadable without
 * the blur — it is blocks, not text — so the desaturation is a finish, not the privacy mechanism.
 *
 * The whole surface is clickable and leads to the paywall: a soft gate is one where the door is
 * visible and takes you to the till [MONEY-001].
 */
@Composable
fun PapLockedCard(
    title: String,
    shape: PapLockedShape,
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
            Spacer(Modifier.height(BODY_GAP_DP.dp))
            Silhouette(shape = shape)
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

/**
 * The body, drawn as the card's layout and nothing else.
 *
 * Widths vary per row on purpose: a stack of identical bars reads as a loading placeholder, and
 * this is not one — what a list of findings looks like is lines of different lengths.
 */
@Composable
private fun Silhouette(shape: PapLockedShape) {
    val blurred = Modifier.blur(BLUR_DP.dp)
    when (shape) {
        PapLockedShape.Lines -> Column(
            modifier = blurred.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(LINE_GAP_DP.dp),
        ) {
            LINE_WIDTHS.forEach { fraction ->
                Block(modifier = Modifier.fillMaxWidth(fraction).height(LINE_H_DP.dp))
            }
        }

        PapLockedShape.Grid -> Column(
            modifier = blurred.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CELL_GAP_DP.dp),
        ) {
            repeat(GRID_ROWS) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CELL_GAP_DP.dp),
                ) {
                    repeat(GRID_COLUMNS) { column ->
                        Block(
                            modifier = Modifier.weight(1f).height(CELL_H_DP.dp),
                            // A flat grid reads as a table that failed to load; the real one is
                            // dense in some cells and empty in others, and that is what shows
                            // through the glass.
                            alpha = CELL_ALPHAS[(row * GRID_COLUMNS + column) % CELL_ALPHAS.size],
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Block(modifier: Modifier, alpha: Float = BLOCK_ALPHA) {
    Spacer(
        modifier = modifier.background(
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            shape = PapShapes.chip,
        ),
    )
}

private const val CARD_PAD_DP = 16
private const val TITLE_GAP_DP = 8
private const val BODY_GAP_DP = 14
private const val CTA_GAP_DP = 8
private const val LOCK_DP = 18
private const val MARK_DP = 18
private const val BLUR_DP = 7
private const val LINE_GAP_DP = 10
private const val LINE_H_DP = 14
private const val CELL_GAP_DP = 6
private const val CELL_H_DP = 16
private const val GRID_ROWS = 3
private const val GRID_COLUMNS = 7
private const val BLOCK_ALPHA = 0.30f
private val LINE_WIDTHS = listOf(0.86f, 0.62f, 0.74f)
private val CELL_ALPHAS = listOf(0.12f, 0.34f, 0.20f, 0.42f, 0.16f, 0.28f, 0.10f)
