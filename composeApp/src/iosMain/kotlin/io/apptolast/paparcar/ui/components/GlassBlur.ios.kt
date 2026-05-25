package io.apptolast.paparcar.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

// UIVisualEffectView interop requires a composable wrapper, not a Modifier.
// GlassSurface's opacity animation is the glass fallback on iOS.
@Composable
actual fun Modifier.glassBlur(radius: Dp): Modifier = this
