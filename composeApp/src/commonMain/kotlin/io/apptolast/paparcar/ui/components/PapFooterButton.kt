package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Variant of the universal footer button.
 *
 *  - [Filled]: primary-coloured fill, used for the dominant action in a row
 *    of equally-weighted actions (e.g. "Publish spot").
 *  - [Outlined]: primary-coloured border with transparent fill, used as the
 *    sibling action with the same visual weight as a Filled (e.g. "Walk to
 *    my car" alongside "Release spot").
 */
enum class PapFooterButtonStyle { Filled, Outlined }

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
 * [PEEK-ACTIONS-001]
 */
@Composable
fun PapFooterButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    style: PapFooterButtonStyle = PapFooterButtonStyle.Filled,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    containerColor: Color? = null,
    contentColor: Color? = null,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(FOOTER_BUTTON_RADIUS)
    val combinedModifier = modifier
        .fillMaxWidth()
        .height(FOOTER_BUTTON_HEIGHT)
    val safeOnClick: () -> Unit = { if (!isLoading) onClick() }
    val effectiveContainer = containerColor ?: cs.primary

    when (style) {
        PapFooterButtonStyle.Filled -> Button(
            onClick = safeOnClick,
            modifier = combinedModifier,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = effectiveContainer,
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
            border = BorderStroke(OUTLINED_BORDER_WIDTH, effectiveContainer),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = contentColor ?: effectiveContainer,
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
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
    )
}

private val FOOTER_BUTTON_HEIGHT    = 48.dp
private val FOOTER_BUTTON_RADIUS    = 14.dp
private val FOOTER_BUTTON_ICON_SIZE = 20.dp
private val OUTLINED_BORDER_WIDTH   = 1.5.dp
