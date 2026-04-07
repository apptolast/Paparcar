package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.theme.PaparcarSpacing
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.confirmation_sheet_confirm
import paparcar.composeapp.generated.resources.confirmation_sheet_subtitle
import paparcar.composeapp.generated.resources.confirmation_sheet_title
import paparcar.composeapp.generated.resources.confirmation_sheet_withdraw

private const val CONFIRMATION_TIMEOUT_SECONDS = 240 // 4 minutes

/**
 * Modal bottom sheet shown when parking is auto-detected.
 *
 * - Displays a 4-minute countdown; when it reaches 0 the spot is auto-published via [onConfirm].
 * - The user can confirm immediately ("Publish spot") or withdraw ("Not now").
 * - Dismissing the sheet via drag also calls [onDismiss].
 *
 * @param onConfirm Called when the user taps "Publish" or the countdown expires.
 * @param onDismiss Called when the user taps "Not now" or drags the sheet away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmationBottomSheet(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartialExpansion = true)
    var secondsLeft by remember { mutableIntStateOf(CONFIRMATION_TIMEOUT_SECONDS) }
    val onConfirmLatest = rememberUpdatedState(onConfirm)

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000L)
            secondsLeft--
        }
        onConfirmLatest.value()
    }

    val minutes = secondsLeft / 60
    val seconds = secondsLeft % 60
    val countdown = "$minutes:${seconds.toString().padStart(2, '0')}"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PaparcarSpacing.lg)
                .padding(bottom = PaparcarSpacing.huge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.DirectionsCar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(PaparcarSpacing.md))
            Text(
                text = stringResource(Res.string.confirmation_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(PaparcarSpacing.sm))
            Text(
                text = stringResource(Res.string.confirmation_sheet_subtitle, countdown),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(PaparcarSpacing.xxl))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.md),
            ) {
                PapSecondaryButton(
                    label = stringResource(Res.string.confirmation_sheet_withdraw),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                PapPrimaryButton(
                    label = stringResource(Res.string.confirmation_sheet_confirm),
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
