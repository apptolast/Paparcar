package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.pap_field_clear_cd

private val CLEAR_BUTTON_SIZE = 22.dp
private val CLEAR_ICON_SIZE = 12.dp
private const val CLEAR_BG_ALPHA = 0.10f
private const val CLEAR_TINT_ALPHA = 0.75f

/**
 * Compact circular clear (X) button used inside text-field trailing slots.
 * Smaller than a standard [androidx.compose.material3.IconButton] so it reads
 * as a discreet affordance rather than a primary control.
 */
@Composable
fun PapClearIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = stringResource(Res.string.pap_field_clear_cd),
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(CLEAR_BUTTON_SIZE)
            .clip(CircleShape)
            .background(cs.onSurface.copy(alpha = CLEAR_BG_ALPHA))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = contentDescription,
            modifier = Modifier.size(CLEAR_ICON_SIZE),
            tint = cs.onSurface.copy(alpha = CLEAR_TINT_ALPHA),
        )
    }
}
