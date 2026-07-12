@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LocationOn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheet
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheetBanner
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheetEyebrowTone
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheetLead
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.confirmation_sheet_confirm
import paparcar.composeapp.generated.resources.confirmation_sheet_method_ar
import paparcar.composeapp.generated.resources.confirmation_sheet_method_bt
import paparcar.composeapp.generated.resources.confirmation_sheet_question
import paparcar.composeapp.generated.resources.confirmation_sheet_title
import paparcar.composeapp.generated.resources.confirmation_sheet_withdraw
import paparcar.composeapp.generated.resources.home_address_unknown

private const val CONFIRMATION_TIMEOUT_SECONDS = 240 // 4 minutes — auto-publish if no answer

/**
 * Modal bottom sheet shown when parking is auto-detected.
 *
 * Uses the shared [PapSheet] molde so the visual rhythm matches every
 * other "state" surface in Home (lead tile + eyebrow + close × + title +
 * banner + action footer). [UI-SHEET-001]
 *
 * The 4-minute countdown still runs silently and auto-publishes if the user
 * doesn't answer — the timer is intentionally hidden so the sheet doesn't
 * feel pushy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmationBottomSheet(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    addressLine: String? = null,
    detectionTimestampMs: Long? = null,
    bluetoothActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    var secondsLeft by remember { mutableIntStateOf(CONFIRMATION_TIMEOUT_SECONDS) }
    val onConfirmLatest = rememberUpdatedState(onConfirm)

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000L)
            secondsLeft--
        }
        onConfirmLatest.value()
    }

    val resolvedAddress = addressLine ?: stringResource(Res.string.home_address_unknown)
    val methodLine = detectionMethodLine(
        bluetoothActive = bluetoothActive,
        timestampMs = detectionTimestampMs,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        PapSheet(
            lead = PapSheetLead.GenericIcon(icon = Icons.Rounded.DirectionsCar),
            eyebrow = stringResource(Res.string.confirmation_sheet_title),
            eyebrowTone = PapSheetEyebrowTone.Action,
            title = stringResource(Res.string.confirmation_sheet_question),
            onDismiss = onDismiss,
            modifier = Modifier.padding(bottom = SHEET_BOTTOM_DP.dp),
            banner = {
                PapSheetBanner(
                    icon = Icons.Rounded.LocationOn,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = resolvedAddress,
                    subtitle = methodLine,
                )
            },
            actions = {
                PapFooterButton(
                    label = stringResource(Res.string.confirmation_sheet_confirm),
                    leadingIcon = Icons.Rounded.Check,
                    onClick = onConfirm,
                    style = PapFooterButtonStyle.Filled,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                // No leading icon: "Retirar" is a cancel-twin (generic dismiss) — the sheet
                // context already fixes its meaning, so a ✕ glyph is redundant. [UI-SHEET-002]
                PapFooterButton(
                    label = stringResource(Res.string.confirmation_sheet_withdraw),
                    onClick = onDismiss,
                    style = PapFooterButtonStyle.Outlined,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }
}

@Composable
private fun detectionMethodLine(bluetoothActive: Boolean, timestampMs: Long?): String? {
    if (timestampMs == null) return null
    val ago = compactAgo(timestampMs) ?: return null
    return if (bluetoothActive) {
        stringResource(Res.string.confirmation_sheet_method_bt, ago)
    } else {
        stringResource(Res.string.confirmation_sheet_method_ar, ago)
    }
}

private fun compactAgo(timestampMs: Long): String? {
    val nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds()
    val deltaSec = ((nowMs - timestampMs) / 1_000L).coerceAtLeast(0L)
    return when {
        deltaSec < SECONDS_PER_MINUTE -> "${deltaSec}s"
        deltaSec < SECONDS_PER_HOUR   -> "${deltaSec / SECONDS_PER_MINUTE}m"
        else                          -> "${deltaSec / SECONDS_PER_HOUR}h"
    }
}

private const val SHEET_BOTTOM_DP = 20
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L
