package io.apptolast.paparcar.presentation.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import paparcar.composeapp.generated.resources.home_nav_history
import paparcar.composeapp.generated.resources.home_nav_settings
import paparcar.composeapp.generated.resources.home_nav_sign_out

private val MenuButtonSize = 56.dp
private val ItemSize = 44.dp

@Composable
internal fun HomeFloatingHeader(
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Trigger — perfect circle, same shape language as the map FABs ───
        Surface(
            onClick = { expanded = !expanded },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            shadowElevation = 6.dp,
            modifier = Modifier.size(MenuButtonSize),
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Animated fade between hamburger and close icon
                AnimatedContent(
                    targetState = expanded,
                    transitionSpec = {
                        fadeIn(tween(160)) togetherWith fadeOut(tween(160))
                    },
                    label = "menu_icon",
                ) { isExpanded ->
                    Icon(
                        imageVector = if (isExpanded) Icons.Outlined.Close else Icons.Outlined.Menu,
                        contentDescription = if (isExpanded) "Cerrar menú" else "Abrir menú",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        // ── Expanded items — slide down from trigger ──────────────────────
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -it / 2 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 2 },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(8.dp))
                MenuCircleItem(
                    icon = { Icon(Icons.Outlined.History, stringResource(Res.string.home_nav_history), modifier = Modifier.size(20.dp)) },
                    onClick = { expanded = false; onHistoryClick() },
                )
                Spacer(Modifier.height(8.dp))
                MenuCircleItem(
                    icon = { Icon(Icons.Outlined.Settings, stringResource(Res.string.home_nav_settings), modifier = Modifier.size(20.dp)) },
                    onClick = { expanded = false; onSettingsClick() },
                )
                Spacer(Modifier.height(14.dp))
                // Sign out — visually separated + error colour = destructive action
                MenuCircleItem(
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Outlined.ExitToApp,
                            stringResource(Res.string.home_nav_sign_out),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                    onClick = { expanded = false; onSignOutClick() },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable circular menu item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MenuCircleItem(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        shadowElevation = 6.dp,
    ) {
        Box(
            modifier = Modifier
                .size(ItemSize)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
    }
}
