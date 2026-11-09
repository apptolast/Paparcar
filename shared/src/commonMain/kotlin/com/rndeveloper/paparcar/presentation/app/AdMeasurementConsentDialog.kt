package com.rndeveloper.paparcar.presentation.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.components.PapAlertDialog
import com.rndeveloper.paparcar.ui.theme.PapGap
import com.rndeveloper.paparcar.ui.theme.PapIconSize
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.ads_consent_allow
import paparcar.composeapp.generated.resources.ads_consent_body
import paparcar.composeapp.generated.resources.ads_consent_deny
import paparcar.composeapp.generated.resources.ads_consent_point_control_desc
import paparcar.composeapp.generated.resources.ads_consent_point_control_title
import paparcar.composeapp.generated.resources.ads_consent_point_measurement_desc
import paparcar.composeapp.generated.resources.ads_consent_point_measurement_title
import paparcar.composeapp.generated.resources.ads_consent_point_privacy_desc
import paparcar.composeapp.generated.resources.ads_consent_point_privacy_title
import paparcar.composeapp.generated.resources.ads_consent_title

private val HIGHLIGHT_ICON_CONTAINER_SIZE = 32.dp
private val HIGHLIGHT_TEXT_GAP = 2.dp

/**
 * The one-time question about advertising measurement. [MKT-AD-MEASUREMENT-ASKS-ITS-OWN-CONSENT-001]
 *
 * The mobile counterpart of a cookie banner, and held to the same rules:
 * - **Refusing is as easy as accepting.** Both answers are full-width buttons on the first and only
 *   screen; "no" is not a link, not a second layer, not greyed out.
 * - **It says what, to whom, and what it is NOT** — structured in clear key highlights so the user
 *   understands data protection, what is measured, and that they remain in full control.
 * - **Nothing depends on the answer.** The app works the same either way, and the dialog says so.
 * - **It has no third way out.** A tap outside does nothing: dismissing would be neither a yes nor
 *   a no, and the question would come back on the next launch — which is nagging.
 */
@Composable
fun AdMeasurementConsentDialog(
    onAnswer: (granted: Boolean) -> Unit,
) {
    PapAlertDialog(
        onDismiss = { /* an answer is required: either button closes it for good */ },
        icon = Icons.Rounded.Campaign,
        title = stringResource(Res.string.ads_consent_title),
        body = stringResource(Res.string.ads_consent_body),
        primaryLabel = stringResource(Res.string.ads_consent_allow),
        onPrimary = { onAnswer(true) },
        secondaryLabel = stringResource(Res.string.ads_consent_deny),
        onSecondary = { onAnswer(false) },
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(PapGap.block),
            ) {
                ConsentHighlightRow(
                    icon = Icons.Rounded.Lock,
                    title = stringResource(Res.string.ads_consent_point_privacy_title),
                    description = stringResource(Res.string.ads_consent_point_privacy_desc),
                )
                ConsentHighlightRow(
                    icon = Icons.Rounded.Campaign,
                    title = stringResource(Res.string.ads_consent_point_measurement_title),
                    description = stringResource(Res.string.ads_consent_point_measurement_desc),
                )
                ConsentHighlightRow(
                    icon = Icons.Rounded.Settings,
                    title = stringResource(Res.string.ads_consent_point_control_title),
                    description = stringResource(Res.string.ads_consent_point_control_desc),
                )
            }
        },
    )
}

@Composable
private fun ConsentHighlightRow(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PapGap.iconLabel),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(HIGHLIGHT_ICON_CONTAINER_SIZE)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(PapIconSize.withLabel),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(HIGHLIGHT_TEXT_GAP),
        ) {
            Text(
                text = title,
                style = PaparcarType.current.label,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = PaparcarType.current.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
