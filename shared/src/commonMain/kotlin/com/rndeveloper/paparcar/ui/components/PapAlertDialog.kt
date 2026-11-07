package com.rndeveloper.paparcar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import com.rndeveloper.paparcar.ui.theme.PapBorders
import com.rndeveloper.paparcar.ui.theme.PapShapes
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
import androidx.compose.runtime.remember
import com.rndeveloper.paparcar.ui.theme.PapMotion
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.theme.PapAlpha
import com.rndeveloper.paparcar.ui.theme.PapControlHeight
import com.rndeveloper.paparcar.ui.theme.PapGap
import com.rndeveloper.paparcar.ui.theme.PaparcarSpacing
import com.rndeveloper.paparcar.ui.theme.PapIconSize

/**
 * Visual tone for [PapAlertDialog]. Drives the icon-circle color and the
 * filled/outlined button accent.
 *
 *  - [Primary]: positive/informational (share, confirm, save). Accent = green.
 *  - [Neutral]: quiet refusal/exit, neutral text and outline.
 *  - [Destructive]: irreversible actions (delete account, delete vehicle).
 *    Accent = error red so the user has a clear visual stop before tapping.
 */
enum class PapDialogAccent { Primary, Destructive, Neutral }

/**
 * WHICH action of a [PapAlertDialog] is in flight.
 *
 * [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001] This replaced a plain `isLoading:
 * Boolean`, and the boolean is the whole bug: it said "something is running", so the dialog had no
 * choice but to paint the spinner on its primary — even when the user had pressed the secondary.
 * In the release dialog the two actions publish or DON'T publish the spot, so a spinner on the
 * wrong one claims the opposite of the decision just taken.
 */
enum class PapDialogAction { Primary, Secondary }

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
 *  │ │     Primary (filled accent)      │ │
 *  │ └──────────────────────────────────┘ │
 *  │ ┌──────────────────────────────────┐ │
 *  │ │      Secondary (outlined)        │ │   ← optional
 *  │ └──────────────────────────────────┘ │
 *  │           Cancel (text)              │   ← optional
 *  └──────────────────────────────────────┘
 *
 * @param accent [PapDialogAccent.Primary] for positive flows,
 *               [PapDialogAccent.Destructive] for delete/irreversible actions.
 * @param secondaryLabel non-null adds an outlined middle action between
 *                       primary and cancel.
 * @param cancelLabel non-null adds a text-only cancel as the third action.
 * @param loadingAction names the action whose async work is in flight: its label is replaced by a
 *                      spinner and every other action is disabled. A dialog can only ever be
 *                      running ONE of its actions, and the spinner belongs to that one.
 *                      [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001]
 * @param content optional block between the body and the actions — the dialog's
 *                only escape hatch, for a confirm that also CAPTURES something
 *                (the zone name). A dialog with a form still is this molde, not
 *                a parallel one. [UI-ZONE-MANAGE-001]
 * @param primaryEnabled gates the primary action on that captured value (blank
 *                       name → can't save). [loadingAction] still disables everything.
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
    // TEXT-ONLY is the default — the label already names the action. An icon has to earn
    // its place: destructive actions (Delete) or provider identity, nothing else.
    // [UI-BUTTON-ICONS-EARN-THEIR-PLACE-001]
    primaryLeadingIcon: ImageVector? = null,
    accent: PapDialogAccent = PapDialogAccent.Primary,
    tertiaryLabel: String? = null,
    onTertiary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    secondaryLeadingIcon: ImageVector? = null,
    /** Overrides the SECONDARY action's accent — a destructive companion (red outlined
     *  "Delete") next to a green primary ("Move"). Null = inherit [accent]. [UI-SHEET-004] */
    secondaryAccent: PapDialogAccent? = null,
    cancelLabel: String? = null,
    loadingAction: PapDialogAction? = null,
    primaryEnabled: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
    /** Replaces the tinted [icon] hero with a custom composable (e.g. the real vehicle pictogram).
     *  When set, the hero circle uses a neutral container so a multi-colour glyph reads. [icon] is
     *  still required as the semantic fallback but is not drawn while this is non-null. */
    heroContent: (@Composable () -> Unit)? = null,
) {
    val accentColor = accent.color()
    val accentContainer = accent.container()
    val onAccent = accent.onAccent()

    // Drive a one-shot enter the first time the dialog enters composition so it
    // scales+fades up from 92% instead of popping in (BasicAlertDialog has no
    // built-in transition). Centralised here → every dialog in the app inherits it.
    val visibleState = remember { MutableTransitionState(false) }.apply { targetState = true }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        properties = imeAwareDialogProperties(),
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = scaleIn(
                initialScale = DIALOG_ENTER_SCALE,
                animationSpec = PapMotion.medium(),
            ) + fadeIn(animationSpec = PapMotion.medium()),
        ) {
        Surface(
            shape = PapShapes.dialog,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                // A dialog that CAPTURES text raises the keyboard on top of itself, and the
                // actions are the part that goes under it — measured on the Oppo: with the report
                // field full, "Cancel" sat 200 px below the IME and could not be reached. Insetting
                // by the IME and letting the column scroll is the fix for every dialog with a
                // `content` slot, not just the one that exposed it.
                // [SUPPORT-A-REPORT-MUST-SAY-WHAT-WENT-WRONG-001]
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = PaparcarSpacing.xl, vertical = PaparcarSpacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ── Icon hero ─────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .size(ICON_CIRCLE_DP.dp)
                        .clip(CircleShape)
                        .background(
                            if (heroContent != null) MaterialTheme.colorScheme.surfaceContainerHighest
                            else accentContainer,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (heroContent != null) {
                        heroContent()
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(HERO_ICON_DP),
                        )
                    }
                }

                // ── Title + body ──────────────────────────────────────────
                Spacer(Modifier.height(PapGap.block))
                Text(
                    text = title,
                    style = PaparcarType.current.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(PapGap.chipRow))
                Text(
                    text = body,
                    style = PaparcarType.current.caption,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = BODY_ALPHA),
                    textAlign = TextAlign.Center,
                )

                // ── Content (escape hatch: a confirm that captures a value) ─
                if (content != null) {
                    Spacer(Modifier.height(PaparcarSpacing.lg))
                    content()
                }

                // ── Actions ───────────────────────────────────────────────
                Spacer(Modifier.height(PaparcarSpacing.xl))
                PrimaryAction(
                    label = primaryLabel,
                    onClick = onPrimary,
                    leadingIcon = primaryLeadingIcon,
                    accentColor = accentColor,
                    onAccent = onAccent,
                    isLoading = loadingAction == PapDialogAction.Primary,
                    // Any action in flight freezes the others: the dialog is resolving ONE choice.
                    enabled = primaryEnabled && loadingAction == null,
                )

                if (secondaryLabel != null && onSecondary != null) {
                    Spacer(Modifier.height(PaparcarSpacing.sm))
                    SecondaryAction(
                        label = secondaryLabel,
                        onClick = onSecondary,
                        leadingIcon = secondaryLeadingIcon,
                        accentColor = (secondaryAccent ?: accent).color(),
                        borderColor = (secondaryAccent ?: accent).outlineColor(),
                        isLoading = loadingAction == PapDialogAction.Secondary,
                        enabled = loadingAction == null,
                    )
                }

                if (tertiaryLabel != null && onTertiary != null) {
                    Spacer(Modifier.height(PaparcarSpacing.sm))
                    SecondaryAction(
                        label = tertiaryLabel,
                        onClick = onTertiary,
                        leadingIcon = null,
                        accentColor = accentColor,
                        borderColor = accent.outlineColor(),
                        isLoading = false,
                        enabled = loadingAction == null,
                    )
                }

                if (cancelLabel != null) {
                    Spacer(Modifier.height(CANCEL_GAP_DP))
                    TextButton(
                        onClick = onDismiss,
                        enabled = loadingAction == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // No leading icon: cancel is generic flow-control, its meaning is
                        // already fixed by the dialog context — a ✕ glyph would just be
                        // redundant noise on the label. [UI-SHEET-002]
                        Text(
                            text = cancelLabel,
                            style = PaparcarType.current.cta,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = CANCEL_ALPHA),
                        )
                    }
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
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(PapControlHeight.button),
        // Pill radius — same button silhouette as PapFooterButton. [UI-SHEET-002]
        shape = PapShapes.chip,
        colors = ButtonDefaults.buttonColors(
            containerColor = accentColor,
            contentColor = onAccent,
        ),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(DIALOG_BUTTON_GLYPH_DP),
                strokeWidth = PapBorders.strong,
                color = onAccent,
            )
        } else {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(DIALOG_BUTTON_GLYPH_DP))
                Spacer(Modifier.width(PapGap.iconLabel))
            }
            Text(label, style = PaparcarType.current.cta)
        }
    }
}

/**
 * The outlined middle action. It carries the SAME spinner path as [PrimaryAction] — an async action
 * is an async action wherever it sits, and the ranking of the two buttons says which one advances
 * the community loop, not which one is allowed to report its own progress.
 * [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001]
 */
@Composable
private fun SecondaryAction(
    label: String,
    onClick: () -> Unit,
    leadingIcon: ImageVector?,
    accentColor: Color,
    borderColor: Color,
    isLoading: Boolean,
    enabled: Boolean,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(PapControlHeight.button),
        shape = PapShapes.chip,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
        border = BorderStroke(PapBorders.medium, borderColor),
    ) {
        if (isLoading) {
            // The outline's own accent, not `onAccent`: this button has no fill to read against.
            CircularProgressIndicator(
                modifier = Modifier.size(DIALOG_BUTTON_GLYPH_DP),
                strokeWidth = PapBorders.strong,
                color = accentColor,
            )
        } else if (leadingIcon != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(DIALOG_BUTTON_GLYPH_DP))
                Spacer(Modifier.width(PapGap.iconLabel))
                Text(label, style = PaparcarType.current.cta)
            }
        } else {
            Text(label, style = PaparcarType.current.cta)
        }
    }
}

@Composable
private fun PapDialogAccent.color(): Color = when (this) {
    PapDialogAccent.Primary -> MaterialTheme.colorScheme.primary
    PapDialogAccent.Destructive -> MaterialTheme.colorScheme.error
    PapDialogAccent.Neutral -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun PapDialogAccent.container(): Color = when (this) {
    PapDialogAccent.Primary -> MaterialTheme.colorScheme.primaryContainer
    PapDialogAccent.Destructive -> MaterialTheme.colorScheme.errorContainer
    PapDialogAccent.Neutral -> MaterialTheme.colorScheme.surfaceContainerHighest
}

@Composable
private fun PapDialogAccent.onAccent(): Color = when (this) {
    PapDialogAccent.Primary -> MaterialTheme.colorScheme.onPrimary
    PapDialogAccent.Destructive -> MaterialTheme.colorScheme.onError
    PapDialogAccent.Neutral -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun PapDialogAccent.outlineColor(): Color = when (this) {
    PapDialogAccent.Primary -> com.rndeveloper.paparcar.ui.theme.greenOutline
    PapDialogAccent.Destructive -> MaterialTheme.colorScheme.error
    PapDialogAccent.Neutral -> MaterialTheme.colorScheme.outlineVariant
}

private const val DIALOG_ENTER_SCALE = 0.92f
private const val ICON_CIRCLE_DP = 56
private const val BUTTON_CORNER_DP = 12
private val HERO_ICON_DP = 28.dp
private val CANCEL_GAP_DP = 2.dp
/**
 * ✅ **Unificado con el sistema** (decisión del user, 12-10-2026)
 * [UI-THE-SPACING-SWEEP-BY-VOLUME-001]. El spinner y el icono de los botones de ESTE dialogo
 * median 16 dp; `PapButton` -el boton canonico del que `PrimaryAction`/`SecondaryAction` son
 * variantes- ya usa `PapIconSize.withLabel` (18 dp) para el MISMO rol desde #136. Mismo defecto que
 * ya se corrigio en Settings (Logout/Delete, 16->18), en `BluetoothConfigScreen` y en
 * `PapFooterButton` (#136, gap 10->8) — aqui con el mayor alcance de los cuatro: TODO dialogo de la
 * app.
 *
 * Y el gap que acompaña al icono NO era un literal: ya era `PapGap.chipRow` (6 dp), un token real
 * -pero el EQUIVOCADO. `chipRow` es "el aire dentro de una fila de chips"; el rol de este gap es
 * "icono junto a su etiqueta dentro de un boton", que es `PapGap.iconLabel` (8 dp) -el mismo que
 * usa `PapButton`-. Un token mal elegido pasa el chequeo de "nunca literal" y sigue siendo el
 * defecto equivocado; se corrige junto con el icono para que la pareja quede a juego.
 *
 * Capturado antes/despues antes de aplicar.
 */
private val DIALOG_BUTTON_GLYPH_DP = PapIconSize.withLabel
private val BODY_ALPHA = PapAlpha.body
private const val CANCEL_ALPHA = 0.7f
