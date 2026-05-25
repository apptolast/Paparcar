package io.apptolast.paparcar.ui.components

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

@Composable
actual fun Modifier.glassBlur(radius: Dp): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    val density = LocalDensity.current
    val radiusPx = with(density) { radius.toPx() }
    return graphicsLayer {
        renderEffect = android.graphics.RenderEffect
            .createBlurEffect(radiusPx, radiusPx, android.graphics.Shader.TileMode.CLAMP)
            .asComposeRenderEffect()
    }
}
