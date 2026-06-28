package io.apptolast.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.theme.PaparcarSpacing
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_det_core_cta
import paparcar.composeapp.generated.resources.home_det_core_sub
import paparcar.composeapp.generated.resources.home_det_core_title

/**
 * Full-sheet blocker shown when foreground location / GPS is missing
 * ([io.apptolast.paparcar.presentation.home.model.DetectionUiState.BlockedCore]): the consumer Home
 * can't function (no map position, no nearby spots), so instead of a degraded peek + a small red
 * surface + a redundant header we take over the sheet with one clear icon + title + body + CTA. Only
 * for CORE/GPS — the PRODUCER tier keeps the small nudge because the consumer still works. [DET-READY-001n]
 */
@Composable
internal fun HomeLocationBlockedState(
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PaparcarSpacing.xxl, vertical = PaparcarSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.LocationOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(ICON_DP.dp),
        )
        Spacer(Modifier.height(PaparcarSpacing.lg))
        Text(
            text = stringResource(Res.string.home_det_core_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(PaparcarSpacing.sm))
        Text(
            text = stringResource(Res.string.home_det_core_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(PaparcarSpacing.xl))
        Button(
            onClick = onActivate,
            modifier = Modifier.fillMaxWidth().height(BUTTON_DP.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Text(text = stringResource(Res.string.home_det_core_cta), fontWeight = FontWeight.Bold)
        }
    }
}

private const val ICON_DP = 52
private const val BUTTON_DP = 52
