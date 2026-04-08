package io.apptolast.paparcar.presentation.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.ui.components.PapPrimaryButton
import io.apptolast.paparcar.ui.components.PapTextButton
import io.apptolast.paparcar.ui.theme.PaparcarSpacing
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.perm_rationale_cta
import paparcar.composeapp.generated.resources.perm_rationale_skip
import paparcar.composeapp.generated.resources.perm_rationale_subtitle
import paparcar.composeapp.generated.resources.perm_rationale_title
import paparcar.composeapp.generated.resources.permissions_perm_activity
import paparcar.composeapp.generated.resources.permissions_perm_activity_desc
import paparcar.composeapp.generated.resources.permissions_perm_background
import paparcar.composeapp.generated.resources.permissions_perm_background_desc
import paparcar.composeapp.generated.resources.permissions_perm_location
import paparcar.composeapp.generated.resources.permissions_perm_location_desc
import paparcar.composeapp.generated.resources.permissions_perm_notifications
import paparcar.composeapp.generated.resources.permissions_perm_notifications_desc

private val StepBadgeSize              = 28.dp
private val StepConnectorWidth         = 2.dp
private val TOP_CONTENT_PADDING        = 56.dp
private val BOTTOM_CONTENT_PADDING     = 140.dp
private val TITLE_EMOJI_SIZE           = 56.sp
private val STEP_EMOJI_SIZE            = 18.sp
private val STEP_TEXT_SPACER           = 2.dp

@Composable
fun PermissionsRationaleScreen(
    onAccept: () -> Unit,
    onSkip: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = PaparcarSpacing.xxxl)
                .padding(top = TOP_CONTENT_PADDING, bottom = BOTTOM_CONTENT_PADDING)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "🔐", fontSize = TITLE_EMOJI_SIZE)
            Spacer(Modifier.height(PaparcarSpacing.lg))
            Text(
                text = stringResource(Res.string.perm_rationale_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(PaparcarSpacing.sm))
            Text(
                text = stringResource(Res.string.perm_rationale_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(PaparcarSpacing.xxl))

            val steps = listOf(
                Triple("📍", stringResource(Res.string.permissions_perm_location), stringResource(Res.string.permissions_perm_location_desc)),
                Triple("🗺", stringResource(Res.string.permissions_perm_background), stringResource(Res.string.permissions_perm_background_desc)),
                Triple("🚶", stringResource(Res.string.permissions_perm_activity), stringResource(Res.string.permissions_perm_activity_desc)),
                Triple("🔔", stringResource(Res.string.permissions_perm_notifications), stringResource(Res.string.permissions_perm_notifications_desc)),
            )

            steps.forEachIndexed { index, (emoji, label, desc) ->
                PermissionStep(
                    stepNumber = index + 1,
                    emoji = emoji,
                    label = label,
                    description = desc,
                    isLast = index == steps.lastIndex,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = PaparcarSpacing.xxl)
                .navigationBarsPadding()
                .padding(bottom = PaparcarSpacing.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PapPrimaryButton(
                label = stringResource(Res.string.perm_rationale_cta),
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth(),
            )
            PapTextButton(
                label = stringResource(Res.string.perm_rationale_skip),
                onClick = onSkip,
            )
        }
    }
}

@Composable
private fun PermissionStep(
    stepNumber: Int,
    emoji: String,
    label: String,
    description: String,
    isLast: Boolean,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val badgeBg = MaterialTheme.colorScheme.primaryContainer
    val badgeFg = MaterialTheme.colorScheme.onPrimaryContainer

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        // Left column: badge + connector line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(StepBadgeSize),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(StepBadgeSize)
                    .background(badgeBg, CircleShape),
            ) {
                Text(
                    text = stepNumber.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = badgeFg,
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(StepConnectorWidth)
                        .height(PaparcarSpacing.xxl + PaparcarSpacing.xl)
                        .drawBehind {
                            drawLine(
                                color = lineColor,
                                start = Offset(size.width / 2, 0f),
                                end = Offset(size.width / 2, size.height),
                                strokeWidth = size.width,
                            )
                        },
                )
            }
        }
        Spacer(Modifier.width(PaparcarSpacing.md))
        // Right column: emoji + label + description
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else PaparcarSpacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = emoji, fontSize = STEP_EMOJI_SIZE)
                Spacer(Modifier.width(PaparcarSpacing.sm))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(STEP_TEXT_SPACER))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
