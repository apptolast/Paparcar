package io.apptolast.paparcar.presentation.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector
import io.apptolast.paparcar.domain.model.ZoneIcon

/**
 * Icono Rounded de una zona — único mapeo, usado en todos los contextos (chips, peek rows, pickers
 * y el pin centrado en el mapa). Nivel 2 → Material Symbols Rounded, sin variante outlined. [ICON-SWEEP]
 */
fun zoneIconFor(key: String): ImageVector = when (key) {
    ZoneIcon.HOME -> Icons.Rounded.Home
    ZoneIcon.WORK -> Icons.Rounded.Work
    ZoneIcon.FAMILY -> Icons.Rounded.Group
    ZoneIcon.FAVORITE -> Icons.Rounded.Bookmark
    ZoneIcon.GYM -> Icons.Rounded.FitnessCenter
    ZoneIcon.SCHOOL -> Icons.Rounded.School
    ZoneIcon.SHOPPING -> Icons.Rounded.ShoppingBag
    ZoneIcon.OTHER -> Icons.Rounded.Place
    else -> Icons.Rounded.Bookmark
}
