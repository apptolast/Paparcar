package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.theme.PapBorders
import io.apptolast.paparcar.ui.theme.PapShapes
import io.apptolast.paparcar.ui.theme.PaparcarType
import io.apptolast.paparcar.ui.theme.greenOutline

/**
 * Variant of the universal footer button.
 *
 *  - [Filled]: primary-coloured fill — the ONE action that advances the community
 *    loop in this context (max 1 per sheet/screen).
 *  - [Outlined]: green-outline border with `primary` text — the sibling action
 *    that stays relevant but doesn't advance the loop (e.g. "Navigate to my car"
 *    alongside "I'm leaving").
 *  - [Tonal]: `surfaceContainerHigh` fill with `onSurface` text — low-emphasis
 *    companion actions (e.g. the "Still there? / It's gone" signal pair).
 */
enum class PapFooterButtonStyle { Filled, Outlined, Tonal }

/**
 * Universal full-width "footer" action button used inside peek modals,
 * registration sheets, and other places where a primary call-to-action lives
 * at the bottom of a content block. Single height across the app so multiple
 * footer buttons stacked together read as a coherent group.
 *
 * Heights:
 *   - Standard: 48dp (intermediate between Material's default 40dp Button and
 *     a hero 56dp; tall enough to be the visual anchor of a peek, short
 *     enough that two stacked buttons don't tower over the map below).
 *
 * [PEEK-ACTIONS-001] [UI-SHEET-001]
 */
@Composable
fun PapFooterButton(
    label: String,
    onClick: () -> Unit,
    // DEFAULT: every Paparcar button that names a concrete action carries a leading icon.
    // Pass `null` only for generic flow-control (cancel/dismiss/withdraw) whose meaning the
    // surrounding sheet already fixes — a glyph there is redundant noise. [UI-SHEET-002]
    leadingIcon: ImageVector? = null,
    modifier: Modifier = Modifier,
    style: PapFooterButtonStyle = PapFooterButtonStyle.Filled,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    containerColor: Color? = null,
    contentColor: Color? = null,
) {
    val cs = MaterialTheme.colorScheme
    val shape = PapShapes.chip
    val combinedModifier = modifier
        .fillMaxWidth()
        .height(FOOTER_BUTTON_HEIGHT)
    val safeOnClick: () -> Unit = { if (!isLoading) onClick() }

    when (style) {
        PapFooterButtonStyle.Filled -> Button(
            onClick = safeOnClick,
            modifier = combinedModifier,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor ?: cs.primary,
                contentColor = contentColor ?: cs.onPrimary,
            ),
        ) {
            FooterButtonContent(label = label, leadingIcon = leadingIcon, isLoading = isLoading)
        }

        PapFooterButtonStyle.Outlined -> OutlinedButton(
            onClick = safeOnClick,
            modifier = combinedModifier,
            enabled = enabled,
            shape = shape,
            border = BorderStroke(PapBorders.medium, containerColor ?: greenOutline),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = contentColor ?: cs.primary,
            ),
        ) {
            FooterButtonContent(label = label, leadingIcon = leadingIcon, isLoading = isLoading)
        }

        PapFooterButtonStyle.Tonal -> Button(
            onClick = safeOnClick,
            modifier = combinedModifier,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor ?: cs.surfaceContainerHigh,
                contentColor = contentColor ?: cs.onSurface,
            ),
        ) {
            FooterButtonContent(label = label, leadingIcon = leadingIcon, isLoading = isLoading)
        }
    }
}

@Composable
private fun FooterButtonContent(label: String, leadingIcon: ImageVector?, isLoading: Boolean) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(FOOTER_BUTTON_ICON_SIZE),
            strokeWidth = 2.dp,
            color = LocalContentColor.current,
        )
        Spacer(Modifier.width(10.dp))
    } else if (leadingIcon != null) {
        Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(FOOTER_BUTTON_ICON_SIZE))
        Spacer(Modifier.width(10.dp))
    }
    Text(
        text = label,
        style = PaparcarType.current.cta,
    )
}

private val FOOTER_BUTTON_HEIGHT    = 48.dp
private val FOOTER_BUTTON_ICON_SIZE = 18.dp
