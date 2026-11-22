package com.rndeveloper.paparcar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.theme.PapMotion

/**
 * A one-shot **spotlight**: dims everything inside its own bounds except a circular hole at the
 * centre. [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001]
 *
 * ### It POINTS; the surface below it SPEAKS
 * It carries no words of its own, and that is the whole design. It used to write its own line of
 * instruction, which meant the screen said the same thing twice: the sheet underneath already
 * names what to do with the pin, and it stays there for every parking, not just the first one.
 * Two captions on one screen is not emphasis, it is noise — so the sentence lives where it is
 * always true (`home_add_parking_helper_primary_create`) and the hole only says WHICH pin.
 * [HOME-FIRST-ENTRY-FLOW-001]
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
 */
@Composable
fun PapSpotlight(
    visible: Boolean,
    /** Called by the HOST when the user does the thing this mark is pointing at — the overlay itself
     *  never listens for touches, see the comment on its Box. */
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
            // ⛔ NO pointer handling here AT ALL. [UI-A-COACH-MARK-MUST-NOT-EAT-THE-GESTURE-IT-TEACHES-001]
            //
            // It used to consume every pointer change on purpose ("so the gesture that closes the
            // coach mark never reaches the map as a pan"), which ate the one gesture this thing
            // exists to teach — the sheet below says «arrastra el MAPA». Making it merely OBSERVE
            // (Initial pass, no consume) was not enough either, and the device said so: the map
            // underneath is a native view embedded in Compose, and a Compose overlay on top of it
            // takes the touch away whether or not it consumes.
            //
            // So the overlay is purely decorative — a hole, nothing else — and WHO closes it is the
            // map's own gesture observer, which is where the touch really lands. See
            // `onUserMapGesture` in HomeScreen.
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
    }
}

private const val HOLE_RADIUS_DP = 76
/** Dark enough to make the hole read as the only thing on screen, light enough to keep the map
 *  legible around it — the user is being told to DRAG that map, so it cannot go black. */
private const val SCRIM_ALPHA = 0.72f
