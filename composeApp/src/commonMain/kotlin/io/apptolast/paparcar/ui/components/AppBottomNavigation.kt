package io.apptolast.paparcar.ui.components

import androidx.compose.animation.AnimatedVisibility
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
    val iconFilled: ImageVector,
    val iconOutline: ImageVector,
)

/**
 * Single source of truth for the app's bottom navigation. Rendered at the
 * root Scaffold in App.kt — no screen should declare its own NavigationBar.
 *
 * Style: icon always visible (filled / outlined based on selection), label
 * only visible on the selected item. Matches the visual language of the
 * bottom sheet above it (`surfaceContainer` token, no tonal elevation,
 * hairline divider at the top).
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
                NavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(item.route) },
                    icon = {
                        Icon(
                            imageVector = if (selected) item.iconFilled else item.iconOutline,
                            contentDescription = label,
                        )
                    },
                    label = {
                        AnimatedVisibility(visible = selected) {
                            Text(
                                text = label,
                                fontSize = LABEL_FONT_SIZE_SP.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    alwaysShowLabel = false,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            .copy(alpha = UNSELECTED_ICON_ALPHA),
                        indicatorColor = MaterialTheme.colorScheme.primary
                            .copy(alpha = SELECTED_INDICATOR_ALPHA),
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
