package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class AppBottomNavItem(
    val route: String,
    val label: @Composable () -> String,
    val icon: ImageVector,
)

/**
 * Single source of truth for the app's bottom navigation. Rendered at the
 * root Scaffold in App.kt — no screen should declare its own NavigationBar.
 *
 * Style: a single Rounded icon per tab (no filled/outlined swap — design-system
 * rule: UI icons are Material Symbols Rounded). Label always visible. Selection
 * is shown by primary color + a translucent primary pill indicator. Matches the
 * visual language of the bottom sheet above it (`surfaceContainer` token, no
 * tonal elevation, hairline divider).
 */
@Composable
fun AppBottomNavigation(
    items: List<AppBottomNavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = NAV_DIVIDER_ALPHA),
        )
        NavigationBar(tonalElevation = 0.dp) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                val label = item.label()
                val cs = MaterialTheme.colorScheme
                NavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(item.route) },
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = label,
                        )
                    },
                    label = {
                        Text(
                            text = label,
                            fontSize = LABEL_FONT_SIZE_SP.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = cs.primary,
                        selectedTextColor = cs.primary,
                        unselectedIconColor = cs.onSurfaceVariant.copy(alpha = UNSELECTED_ICON_ALPHA),
                        unselectedTextColor = cs.onSurfaceVariant.copy(alpha = UNSELECTED_ICON_ALPHA),
                        indicatorColor = cs.primary.copy(alpha = SELECTED_INDICATOR_ALPHA),
                    ),
                )
            }
        }
    }
}

private const val NAV_DIVIDER_ALPHA = 0.12f
private const val UNSELECTED_ICON_ALPHA = 0.55f
private const val SELECTED_INDICATOR_ALPHA = 0.12f
private const val LABEL_FONT_SIZE_SP = 11
