package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import io.apptolast.paparcar.ui.theme.PapShapes
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Visual tone for [PapAlertDialog]. Drives the icon-circle color and the
 * filled/outlined button accent.
 *
 *  - [Primary]: positive/informational (share, confirm, save). Accent = green.
 *  - [Destructive]: irreversible actions (delete account, delete vehicle).
 *    Accent = error red so the user has a clear visual stop before tapping.
 */
enum class PapDialogAccent { Primary, Destructive }

/**
 * The app's single confirmation dialog molde. Drop-in for every "are you
 * sure?" surface so they all read as one family:
 *
 *  ┌──────────────────────────────────────┐
 *  │            [icon circle]              │
 *  │                                       │
 *  │              Title                    │
 *  │          Body explanation             │
 *  │                                       │
 *  │ ┌──────────────────────────────────┐ │
 *  │ │  [icon]  Primary (filled accent) │ │
 *  │ └──────────────────────────────────┘ │
 *  │ ┌──────────────────────────────────┐ │
 *  │ │  [icon]  Secondary (outlined)    │ │   ← optional
 *  │ └──────────────────────────────────┘ │
 *  │           Cancel (text)              │   ← optional
 *  └──────────────────────────────────────┘
 *
 * @param accent [PapDialogAccent.Primary] for positive flows,
 *               [PapDialogAccent.Destructive] for delete/irreversible actions.
 * @param secondaryLabel non-null adds an outlined middle action between
 *                       primary and cancel.
 * @param cancelLabel non-null adds a text-only cancel as the third action.
 * @param isLoading replaces the primary label with a spinner and disables
 *                  all actions — use while an async confirm is in flight.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PapAlertDialog(
    onDismiss: () -> Unit,
    icon: ImageVector,
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryLeadingIcon: ImageVector? = null,
    accent: PapDialogAccent = PapDialogAccent.Primary,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    secondaryLeadingIcon: ImageVector? = null,
    cancelLabel: String? = null,
    isLoading: Boolean = false,
) {
    val accentColor = accent.color()
    val accentContainer = accent.container()
    val onAccent = accent.onAccent()

    BasicAlertDialog(onDismissRequest = onDismiss, modifier = modifier) {
        Surface(
            shape = PapShapes.dialog,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ── Icon hero ─────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .size(ICON_CIRCLE_DP.dp)
                        .clip(CircleShape)
                        .background(accentContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(28.dp),
                    )
                }

                // ── Title + body ──────────────────────────────────────────
                Spacer(Modifier.height(14.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = BODY_ALPHA),
                    textAlign = TextAlign.Center,
                )

                // ── Actions ───────────────────────────────────────────────
                Spacer(Modifier.height(20.dp))
                PrimaryAction(
                    label = primaryLabel,
                    onClick = onPrimary,
                    leadingIcon = primaryLeadingIcon,
                    accentColor = accentColor,
                    onAccent = onAccent,
                    isLoading = isLoading,
                )

                if (secondaryLabel != null && onSecondary != null) {
                    Spacer(Modifier.height(8.dp))
                    SecondaryAction(
                        label = secondaryLabel,
                        onClick = onSecondary,
                        leadingIcon = secondaryLeadingIcon,
                        accentColor = accentColor,
                        enabled = !isLoading,
                    )
                }

                if (cancelLabel != null) {
                    Spacer(Modifier.height(2.dp))
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = cancelLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CANCEL_ALPHA),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryAction(
    label: String,
    onClick: () -> Unit,
    leadingIcon: ImageVector?,
    accentColor: Color,
    onAccent: Color,
    isLoading: Boolean,
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(BUTTON_HEIGHT_DP.dp),
        shape = RoundedCornerShape(BUTTON_CORNER_DP.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accentColor,
            contentColor = onAccent,
        ),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = onAccent,
            )
        } else {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SecondaryAction(
    label: String,
    onClick: () -> Unit,
    leadingIcon: ImageVector?,
    accentColor: Color,
    enabled: Boolean,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(BUTTON_HEIGHT_DP.dp),
        shape = RoundedCornerShape(BUTTON_CORNER_DP.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
        border = BorderStroke(1.5.dp, accentColor),
    ) {
        if (leadingIcon != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Text(label, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PapDialogAccent.color(): Color = when (this) {
    PapDialogAccent.Primary -> MaterialTheme.colorScheme.primary
    PapDialogAccent.Destructive -> MaterialTheme.colorScheme.error
}

@Composable
private fun PapDialogAccent.container(): Color = when (this) {
    PapDialogAccent.Primary -> MaterialTheme.colorScheme.primaryContainer
    PapDialogAccent.Destructive -> MaterialTheme.colorScheme.errorContainer
}

@Composable
private fun PapDialogAccent.onAccent(): Color = when (this) {
    PapDialogAccent.Primary -> MaterialTheme.colorScheme.onPrimary
    PapDialogAccent.Destructive -> MaterialTheme.colorScheme.onError
}

private const val ICON_CIRCLE_DP = 56
private const val BUTTON_HEIGHT_DP = 48
private const val BUTTON_CORNER_DP = 12
private const val BODY_ALPHA = 0.65f
private const val CANCEL_ALPHA = 0.7f
