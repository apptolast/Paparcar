package io.apptolast.paparcar.presentation.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.components.PapFooterButton
import io.apptolast.paparcar.ui.components.PapFooterButtonStyle
import io.apptolast.paparcar.ui.components.PaparcarBottomActionScaffold
import io.apptolast.paparcar.ui.theme.PaparcarSpacing
import io.apptolast.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.gps_disclaimer_body
import paparcar.composeapp.generated.resources.gps_disclaimer_confirm
import paparcar.composeapp.generated.resources.gps_disclaimer_title

private val ICON_SIZE = 72.dp

/**
 * Mandatory notice screen about GPS accuracy variability.
 * Shown as the final step of the onboarding/permission flow to ensure the user
 * understands the technical limitations of automatic detection.
 */
@Composable
fun GpsDisclaimerScreen(
    onAccepted: () -> Unit,
) {
    PaparcarBottomActionScaffold(
        contentArrangement = Arrangement.Center,
        contentAlignment = Alignment.CenterHorizontally,
        footer = {
            PapFooterButton(
                label = stringResource(Res.string.gps_disclaimer_confirm),
                leadingIcon = Icons.Rounded.GpsFixed,
                onClick = onAccepted,
                style = PapFooterButtonStyle.Filled,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Icon(
            imageVector = Icons.Rounded.GpsFixed,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(ICON_SIZE),
        )
        Spacer(Modifier.height(PaparcarSpacing.xxxl))
        Text(
            text = stringResource(Res.string.gps_disclaimer_title),
            style = PaparcarType.current.heroTitle,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(PaparcarSpacing.lg))
        Text(
            text = stringResource(Res.string.gps_disclaimer_body),
            // Roomier line spacing for this centred disclaimer paragraph (1.2× the subtitle's).
            style = PaparcarType.current.subtitle.let { it.copy(lineHeight = it.lineHeight * 1.2) },
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
