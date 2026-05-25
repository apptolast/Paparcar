package io.apptolast.paparcar.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Applies a frosted-glass blur behind the composable.
 * Android ≥ API 31 (S): real `RenderEffect` blur.
 * Android < API 31 / all other platforms: no-op (opacity via GlassSurface is the fallback).
 */
@Composable
expect fun Modifier.glassBlur(radius: Dp): Modifier
