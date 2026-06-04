package io.apptolast.paparcar.presentation.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Work
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

/** Filled variant — chips, peek rows, pickers. */
fun zoneIconFor(key: String): ImageVector = when (key) {
    ZoneIcon.HOME -> Icons.Filled.Home
    ZoneIcon.WORK -> Icons.Filled.Work
    ZoneIcon.FAMILY -> Icons.Filled.Group
    ZoneIcon.FAVORITE -> Icons.Filled.Bookmark
    ZoneIcon.GYM -> Icons.Filled.FitnessCenter
    ZoneIcon.SCHOOL -> Icons.Filled.School
    ZoneIcon.SHOPPING -> Icons.Filled.ShoppingBag
    ZoneIcon.OTHER -> Icons.Filled.Place
    else -> Icons.Filled.Bookmark
}

/** Outlined variant — map center pin (inside the area circle). */
fun zoneIconOutlinedFor(key: String): ImageVector = when (key) {
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
