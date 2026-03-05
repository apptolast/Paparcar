package io.apptolast.paparcar.presentation.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.ui.theme.EcoGreen
import io.apptolast.paparcar.ui.theme.EcoGreenMuted
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_brand_name
import paparcar.composeapp.generated.resources.home_cd_profile
import paparcar.composeapp.generated.resources.home_eco_driver_label
import paparcar.composeapp.generated.resources.home_nav_history
import paparcar.composeapp.generated.resources.home_nav_settings

private const val DROPDOWN_ENTER_DURATION = 220
private const val DROPDOWN_EXIT_DURATION  = 150
private const val DROPDOWN_ITEM_STAGGER   = 90L
private const val DROPDOWN_EXIT_DELAY     = 160L

@Composable
internal fun EcoFloatingHeader(
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            // ── Eco-Driver identity pill ───────────────────────────────────
            Surface(
                onClick = { dropdownExpanded = true },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(EcoGreenMuted),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = stringResource(Res.string.home_cd_profile),
                            tint = EcoGreen,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    Column {
                        Text(
                            stringResource(Res.string.home_brand_name),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 8.sp,
                            letterSpacing = 1.sp,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                stringResource(Res.string.home_eco_driver_label),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Icon(
                                imageVector = if (dropdownExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }

        var menuShowing by remember { mutableStateOf(false) }
        var item1Visible by remember { mutableStateOf(false) }
        var item2Visible by remember { mutableStateOf(false) }

        // menuShowing stays true during exit animation so DropdownMenu doesn't
        // collapse instantly and cut off the outgoing AnimatedVisibility transitions.
        LaunchedEffect(dropdownExpanded) {
            if (dropdownExpanded) {
                menuShowing = true
                item1Visible = false
                item2Visible = false
                item1Visible = true          // item 1 slides in immediately
                delay(DROPDOWN_ITEM_STAGGER)
                item2Visible = true          // item 2 follows 90 ms later
            } else {
                item2Visible = false         // item 2 leaves first
                delay(DROPDOWN_ITEM_STAGGER)
                item1Visible = false         // item 1 follows 90 ms later
                delay(DROPDOWN_EXIT_DELAY)   // wait for item1 exit tween(150) to complete
                menuShowing = false          // now actually close the popup
            }
        }
        DropdownMenu(
            expanded = menuShowing,
            onDismissRequest = { dropdownExpanded = false },
            containerColor = Color.Transparent,
            shadowElevation = 0.dp,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            AnimatedVisibility(
                visible = item1Visible,
                enter = slideInVertically(
                    initialOffsetY = { -it },          // comes from above
                    animationSpec = tween(DROPDOWN_ENTER_DURATION),
                ) + fadeIn(tween(DROPDOWN_ENTER_DURATION)),
                exit = slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = tween(DROPDOWN_EXIT_DURATION),
                ) + fadeOut(tween(DROPDOWN_EXIT_DURATION)),
            ) {
                EcoDropdownPillItem(
                    icon = Icons.Outlined.History,
                    label = stringResource(Res.string.home_nav_history),
                    onClick = { dropdownExpanded = false; onHistoryClick() },
                )
            }

            AnimatedVisibility(
                visible = item2Visible,
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = tween(DROPDOWN_ENTER_DURATION),
                ) + fadeIn(tween(DROPDOWN_ENTER_DURATION)),
                exit = slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = tween(DROPDOWN_EXIT_DURATION),
                ) + fadeOut(tween(DROPDOWN_EXIT_DURATION)),
            ) {
                EcoDropdownPillItem(
                    icon = Icons.Outlined.Settings,
                    label = stringResource(Res.string.home_nav_settings),
                    onClick = { dropdownExpanded = false; onSettingsClick() },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dropdown pill item — each one is its own Surface(CircleShape) so it has an
// independent background, identical in language to the Eco-Driver pill.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun EcoDropdownPillItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(EcoGreenMuted),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = EcoGreen,
                    modifier = Modifier.size(12.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable pill wrapper
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun EcoHeaderPill(
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shadowElevation = 6.dp,
        ) {
            content()
        }
    } else {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shadowElevation = 6.dp,
        ) {
            content()
        }
    }
}
