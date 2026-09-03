package com.rndeveloper.paparcar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.theme.PapMotion
import com.rndeveloper.paparcar.ui.theme.PaparcarSpacing
import com.rndeveloper.paparcar.ui.theme.PaparcarType

/**
 * A one-shot **spotlight**: dims everything inside its own bounds except a circular hole at the
 * centre, and writes one line of instruction under it.
 * [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001]
 *
 * ### Why the hole is at the CENTRE and not wherever you point it
 * It anchors GEOMETRICALLY, not to a measured control. Give it the same bounds as the thing it is
 * lighting up (in Home: the same `Modifier.layout` the map gets) and the hole lands exactly on that
 * area's centre — which is where `PaparcarMapView` draws its centre pin.
 *
 * The alternative — `onGloballyPositioned` on the target — is what makes this kind of overlay
 * fragile in this screen specifically: Home's bottom sheet changes height on every drag frame, so a
 * measured anchor would chase a moving target and lag it by a frame. A centre that both the pin and
 * the hole derive from cannot drift, because there is nothing to keep in sync.
 *
 * Tapping ANYWHERE dismisses. It swallows the tap that dismisses it (the map underneath must not
 * also pan), and nothing else — once gone, the surface is fully interactive again.
 */
@Composable
fun PapSpotlight(
    visible: Boolean,
    caption: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    holeRadius: Dp = HOLE_RADIUS_DP.dp,
) {
    val scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(PapMotion.medium()),
        exit = fadeOut(PapMotion.medium()),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // A tap anywhere dismisses AND is consumed here, so the gesture that closes the
                    // coach mark never reaches the map as a pan.
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                            onDismiss()
                        }
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Offscreen compositing is what makes BlendMode.Clear punch a real hole instead
                    // of blending against whatever is already on the window.
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(color = scrimColor)
                        drawCircle(
                            color = Color.Transparent,
                            radius = holeRadius.toPx(),
                            blendMode = BlendMode.Clear,
                        )
                    },
            )
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    // Below the hole, never on top of it: the caption explains the thing the hole is
                    // showing, so it must not cover it.
                    .offset(y = holeRadius + PaparcarSpacing.xl)
                    .widthIn(max = CAPTION_MAX_WIDTH_DP.dp)
                    .padding(horizontal = PaparcarSpacing.lg),
            ) {
                Text(
                    text = caption,
                    style = PaparcarType.current.subtitle,
                    // On the scrim, not on a surface — the inverse pair is the only one guaranteed
                    // to read against it in both themes.
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private const val HOLE_RADIUS_DP = 76
private const val CAPTION_MAX_WIDTH_DP = 320
/** Dark enough to make the hole read as the only thing on screen, light enough to keep the map
 *  legible around it — the user is being told to DRAG that map, so it cannot go black. */
private const val SCRIM_ALPHA = 0.72f
