package com.rndeveloper.paparcar.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * Google Maps mide sus trazos en **píxeles de pantalla**, así que aquí la conversión es la de
 * siempre — este `actual` existe para que iOS pueda NO hacerla, no para cambiar Android.
 * [MAP-A-RING-YOU-CANNOT-SEE-IS-TWO-BUGS-001]
 */
@Composable
actual fun mapLineWidth(width: Dp): Float = with(LocalDensity.current) { width.toPx() }
