package io.apptolast.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.theme.PapShapes
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_permissions_button
import paparcar.composeapp.generated.resources.home_permissions_message
import paparcar.composeapp.generated.resources.home_permissions_title

/**
 * Permissions card (v1 redesign) — hero icon box + title + body + full-width
 * secondary button. Gives the affordance more visual weight than the prior
 * centred-Column variant. [HOME-UX-006]
 */
@Composable
internal fun HomePermissionsCard(
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PapShapes.cardLarge,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = CARD_BG_ALPHA),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(ICON_BOX_CORNER_DP.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(ICON_BOX_DP.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(Res.string.home_permissions_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                stringResource(Res.string.home_permissions_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = BODY_ALPHA),
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BUTTON_HEIGHT_DP.dp),
                shape = RoundedCornerShape(BUTTON_CORNER_DP.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
            ) {
                Text(
                    stringResource(Res.string.home_permissions_button),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private const val ICON_BOX_DP = 44
private const val ICON_BOX_CORNER_DP = 12
private const val BUTTON_HEIGHT_DP = 44
private const val BUTTON_CORNER_DP = 12
private const val CARD_BG_ALPHA = 0.6f
private const val BODY_ALPHA = 0.75f
