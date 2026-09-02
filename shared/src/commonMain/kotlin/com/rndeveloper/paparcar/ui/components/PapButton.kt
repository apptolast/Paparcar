package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.theme.PapBorders
import com.rndeveloper.paparcar.ui.theme.PapShapes
import com.rndeveloper.paparcar.ui.theme.PaparcarType

private val ButtonHorizontalPadding = 24.dp
private val ButtonVerticalPadding   = 14.dp
private val LoadingIndicatorSize    = 18.dp
private val LeadingIconSize         = 18.dp
private val IconLabelGap            = 8.dp

private val DefaultContentPadding = PaddingValues(
    horizontal = ButtonHorizontalPadding,
    vertical   = ButtonVerticalPadding,
)

private val CompactHorizontalPadding = 16.dp
private val CompactVerticalPadding   = 8.dp

private val CompactContentPadding = PaddingValues(
    horizontal = CompactHorizontalPadding,
    vertical   = CompactVerticalPadding,
)

/**
 * [UI-BUTTON-ONE-CANONICAL-CTA-001] What the CTA MEANS — resolved INSIDE the component so a call
 * site asks for intention, never for `colorScheme.error` by hand. [UI-COLOR-DOCTRINE-001]
 *
 * [Destructive] is for the action that BLOCKS or destroys (revoke-location blocked state, delete):
 * red on purpose, and the one tone that may never be the default.
 */
enum class PapButtonTone { Brand, Destructive }

/**
 * [UI-BUTTON-ONE-CANONICAL-CTA-001] How much room the CTA owns. [Regular] is the screen CTA;
 * [Compact] is the button living in a row's `trailing` slot, which cannot carry a screen CTA's
 * padding — its padding and shape are one recipe here, not a per-call-site improvisation.
 */
enum class PapButtonSize { Regular, Compact }

/**
 * Primary filled button. Use for the main CTA in a screen.
 *
 * TEXT-ONLY is the DEFAULT — the label already names the action; a glyph next
 * to it is noise, not information. An [icon] has to EARN its place, and only
 * two cases do: a destructive action that deserves an extra beat of attention
 * (Delete), or provider identity (social-login logos). When in doubt, no icon.
 * [UI-BUTTON-ICONS-EARN-THEIR-PLACE-001]
 *
 * Supports an [isLoading] state — while loading the content is replaced by a
 * [CircularProgressIndicator] and the button is disabled.
 *
 * [UI-BUTTON-ONE-CANONICAL-CTA-001] [tone] and [size] are the only two styling axes, on purpose:
 * a canonical button with five style parameters is M3 with another name. Everything else —
 * height, shape, padding, spinner — is this file's decision, so a silhouette change lands on
 * every CTA at once.
 */
@Composable
fun PapPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    tone: PapButtonTone = PapButtonTone.Brand,
    size: PapButtonSize = PapButtonSize.Regular,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !isLoading,
        shape = when (size) {
            PapButtonSize.Regular -> ButtonDefaults.shape
            PapButtonSize.Compact -> PapShapes.cardSmall
        },
        colors = when (tone) {
            PapButtonTone.Brand -> ButtonDefaults.buttonColors()
            PapButtonTone.Destructive -> ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
        },
        contentPadding = when (size) {
            PapButtonSize.Regular -> DefaultContentPadding
            PapButtonSize.Compact -> CompactContentPadding
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(LoadingIndicatorSize),
                    strokeWidth = 2.dp,
                    // The content colour the tone resolved — onPrimary was hardcoded here, which
                    // would have painted an invisible spinner on a Destructive fill.
                    color = LocalContentColor.current,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(LeadingIconSize))
                        Spacer(Modifier.width(IconLabelGap))
                    }
                    Text(text = label, style = PaparcarType.current.cta)
                }
            }
        }
    }
}

/**
 * Identity-provider button (Google, Apple…). The outlined sibling of
 * [PapPrimaryButton]: same shape, same padding, same label role — it is not the
 * CTA of the screen, so it carries an outline instead of the brand fill.
 *
 * This is the second of the two cases where an icon earns its place on a
 * button: the logo IS the provider's identity, and the label alone would make
 * the user read instead of recognise. [UI-BUTTON-ICONS-EARN-THEIR-PLACE-001]
 *
 * A multicolour mark (Google, Facebook) keeps [tint] null and is drawn with
 * [Image]: it is somebody else's artwork and recolouring it would misrepresent
 * the brand. A single-colour silhouette (Apple, GitHub) passes the tint it
 * needs, because black-on-black would vanish in the dark theme.
 * [UI-COLOR-DOCTRINE-001]
 *
 * While [isLoading] only the logo is swapped for a spinner — the label keeps
 * naming which provider is authenticating.
 */
@Composable
fun PapProviderButton(
    label: String,
    icon: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    isLoading: Boolean = false,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !isLoading,
        // Read from the filled button's defaults, not copied as a literal: the
        // pair has to stay one family across Material bumps.
        shape = ButtonDefaults.shape,
        border = BorderStroke(PapBorders.thin, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = DefaultContentPadding,
    ) {
        Box(modifier = Modifier.size(LeadingIconSize), contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(LoadingIndicatorSize),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                )
            } else if (tint == null) {
                Image(painter = icon, contentDescription = null, modifier = Modifier.size(LeadingIconSize))
            } else {
                Icon(painter = icon, contentDescription = null, modifier = Modifier.size(LeadingIconSize), tint = tint)
            }
        }
        Spacer(Modifier.width(IconLabelGap))
        Text(text = label, style = PaparcarType.current.cta)
    }
}
