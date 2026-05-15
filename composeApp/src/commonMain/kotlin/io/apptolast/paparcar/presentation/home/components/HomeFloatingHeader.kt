package io.apptolast.paparcar.presentation.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import io.apptolast.paparcar.ui.components.GlassSurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_menu_close_cd
import paparcar.composeapp.generated.resources.home_menu_open_cd
import paparcar.composeapp.generated.resources.home_nav_history
import paparcar.composeapp.generated.resources.home_nav_my_car
import paparcar.composeapp.generated.resources.home_nav_settings

private val MenuButtonSize = 56.dp
// Matches MapCircleFab default so the floating header reads as a peer of the
// circular FABs (layers / GPS / parked car) on the opposite side of the map.
// [HOME-DEPTH-001]
private val FLOATING_SHADOW_ELEVATION = 6.dp

@Composable
internal fun HomeFloatingHeader(
    onHistoryClick: () -> Unit,
    onMyCarClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Hamburger menu button — same height as the search bar ──────────
        GlassSurface(
            onClick = { expanded = !expanded },
            shape = RoundedCornerShape(16.dp),
            shadowElevation = FLOATING_SHADOW_ELEVATION,
            modifier = Modifier
                .width(MenuButtonSize)
                .height(MenuButtonSize),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (expanded) Icons.Outlined.Close else Icons.Outlined.Menu,
                    contentDescription = if (expanded) stringResource(Res.string.home_menu_close_cd) else stringResource(Res.string.home_menu_open_cd),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // ── Expanded items ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(8.dp))

                GlassSurface(
                    onClick = {
                        expanded = false
                        onHistoryClick()
                    },
                    shape = CircleShape,
                    shadowElevation = FLOATING_SHADOW_ELEVATION,
                ) {
                    Box(
                        modifier = Modifier.padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.History,
                            contentDescription = stringResource(Res.string.home_nav_history),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                GlassSurface(
                    onClick = {
                        expanded = false
                        onMyCarClick()
                    },
                    shape = CircleShape,
                    shadowElevation = FLOATING_SHADOW_ELEVATION,
                ) {
                    Box(
                        modifier = Modifier.padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.DirectionsCar,
                            contentDescription = stringResource(Res.string.home_nav_my_car),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                GlassSurface(
                    onClick = {
                        expanded = false
                        onSettingsClick()
                    },
                    shape = CircleShape,
                    shadowElevation = FLOATING_SHADOW_ELEVATION,
                ) {
                    Box(
                        modifier = Modifier.padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = stringResource(Res.string.home_nav_settings),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}