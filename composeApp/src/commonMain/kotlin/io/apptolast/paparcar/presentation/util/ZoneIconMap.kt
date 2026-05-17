package io.apptolast.paparcar.presentation.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector
import io.apptolast.paparcar.domain.model.ZoneIcon

/**
 * Maps a [ZoneIcon] preset key to its Material outlined ImageVector for
 * rendering chips, peek rows, and pickers. Unknown keys (e.g. from future
 * preset additions a client doesn't recognise yet) fall back to a
 * bookmark icon so the chip still has a visual.
 */
fun zoneIconFor(key: String): ImageVector = when (key) {
    ZoneIcon.HOME -> Icons.Outlined.Home
    ZoneIcon.WORK -> Icons.Outlined.Work
    ZoneIcon.FAMILY -> Icons.Outlined.Group
    ZoneIcon.FAVORITE -> Icons.Outlined.Bookmark
    ZoneIcon.GYM -> Icons.Outlined.FitnessCenter
    ZoneIcon.SCHOOL -> Icons.Outlined.School
    ZoneIcon.SHOPPING -> Icons.Outlined.ShoppingBag
    ZoneIcon.OTHER -> Icons.Outlined.Place
    else -> Icons.Outlined.Bookmark
}
