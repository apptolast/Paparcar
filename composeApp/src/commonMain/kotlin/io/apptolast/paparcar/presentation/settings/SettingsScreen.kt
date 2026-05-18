@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swmansion.kmpmaps.core.MapType
import io.apptolast.paparcar.domain.preferences.ThemeMode
import io.apptolast.paparcar.ui.components.PapAlertDialog
import io.apptolast.paparcar.ui.components.PapDialogAccent
import io.apptolast.paparcar.presentation.history.MONTH_SHORT_RES
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.settings_auto_detect
import paparcar.composeapp.generated.resources.settings_auto_detect_desc
import paparcar.composeapp.generated.resources.settings_cd_back
import paparcar.composeapp.generated.resources.settings_contact
import paparcar.composeapp.generated.resources.settings_danger_zone
import paparcar.composeapp.generated.resources.settings_danger_zone_subtitle
import paparcar.composeapp.generated.resources.settings_delete_account_cancel
import paparcar.composeapp.generated.resources.settings_delete_account_confirm_action
import paparcar.composeapp.generated.resources.settings_delete_account_confirm_message
import paparcar.composeapp.generated.resources.settings_delete_account_confirm_title
import paparcar.composeapp.generated.resources.settings_distance_unit
import paparcar.composeapp.generated.resources.settings_distance_unit_desc
import paparcar.composeapp.generated.resources.settings_language
import paparcar.composeapp.generated.resources.settings_language_auto
import paparcar.composeapp.generated.resources.settings_language_desc
import paparcar.composeapp.generated.resources.settings_licenses
import paparcar.composeapp.generated.resources.settings_map_type
import paparcar.composeapp.generated.resources.settings_map_type_normal
import paparcar.composeapp.generated.resources.settings_map_type_satellite
import paparcar.composeapp.generated.resources.settings_map_type_terrain
import paparcar.composeapp.generated.resources.settings_nav_my_car
import paparcar.composeapp.generated.resources.settings_nav_my_car_desc
import paparcar.composeapp.generated.resources.settings_notif_parking
import paparcar.composeapp.generated.resources.settings_notif_parking_desc
import paparcar.composeapp.generated.resources.settings_notif_spot
import paparcar.composeapp.generated.resources.settings_notif_spot_desc
import paparcar.composeapp.generated.resources.settings_notifications_subtitle
import paparcar.composeapp.generated.resources.settings_notifications_title
import paparcar.composeapp.generated.resources.settings_privacy
import paparcar.composeapp.generated.resources.settings_profile_delete_account
import paparcar.composeapp.generated.resources.settings_profile_logout
import paparcar.composeapp.generated.resources.settings_profile_member_since
import paparcar.composeapp.generated.resources.settings_profile_name_placeholder
import paparcar.composeapp.generated.resources.settings_section_about
import paparcar.composeapp.generated.resources.settings_section_appearance
import paparcar.composeapp.generated.resources.settings_section_detection
import paparcar.composeapp.generated.resources.settings_section_map
import paparcar.composeapp.generated.resources.settings_section_notifications
import paparcar.composeapp.generated.resources.settings_theme_mode
import paparcar.composeapp.generated.resources.settings_theme_mode_dark
import paparcar.composeapp.generated.resources.settings_theme_mode_desc
import paparcar.composeapp.generated.resources.settings_theme_mode_light
import paparcar.composeapp.generated.resources.settings_theme_mode_system
import paparcar.composeapp.generated.resources.settings_title
import paparcar.composeapp.generated.resources.settings_version

/**
 * Settings v2 — visual refresh (Phase A).
 *
 * Cambios respecto a v1:
 *  - Profile Card con avatar grande + active-vehicle row + logout outlined
 *  - Section headers muted (no primary green)
 *  - Theme picker visual con mini-previews (no segmented text)
 *  - Map type thumbnails (no segmented text)
 *  - Notifications agrupadas con master toggle + sub-switches
 *  - Danger zone aislada con borde rojo
 *
 * NO incluido en esta fase (requiere backend):
 *  - Profile stats reales (sessions/this-month/reliability)
 *  - Advanced section (export data, clear cache)
 *  - Sync status indicator
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToVehicles: () -> Unit = {},
    onNavigateToAuth: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onSetThemeMode: (ThemeMode) -> Unit = {},
    imperialUnits: Boolean = false,
    onToggleImperialUnits: (Boolean) -> Unit = {},
    selectedLanguage: String = "auto",
    onSetLanguage: (String) -> Unit = {},
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current

    // Refresh pref-backed fields every time the screen re-enters composition,
    // so preferences mutated elsewhere (e.g. auto-detect toggled from the
    // BT-config flow) show up as soon as the user comes back to Settings.
    // AppPreferences doesn't expose Flow accessors yet — this is the
    // cheapest workaround until we add them.
    LaunchedEffect(Unit) { viewModel.refreshFromPreferences() }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SettingsEffect.NavigateBack -> onNavigateBack()
                is SettingsEffect.NavigateToVehicles -> onNavigateToVehicles()
                is SettingsEffect.NavigateToAuth -> onNavigateToAuth()
                is SettingsEffect.OpenUrl -> uriHandler.openUri(effect.url)
            }
        }
    }

    SettingsContent(
        state = state,
        onIntent = viewModel::handleIntent,
        onNavigateBack = onNavigateBack,
        themeMode = themeMode,
        onSetThemeMode = onSetThemeMode,
        imperialUnits = imperialUnits,
        onToggleImperialUnits = onToggleImperialUnits,
        selectedLanguage = selectedLanguage,
        onSetLanguage = onSetLanguage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onSetThemeMode: (ThemeMode) -> Unit = {},
    imperialUnits: Boolean = false,
    onToggleImperialUnits: (Boolean) -> Unit = {},
    selectedLanguage: String = "auto",
    onSetLanguage: (String) -> Unit = {},
) {
    if (state.showDeleteAccountConfirmation) {
        PapAlertDialog(
            onDismiss = { onIntent(SettingsIntent.DismissDeleteAccount) },
            icon = Icons.Outlined.Delete,
            title = stringResource(Res.string.settings_delete_account_confirm_title),
            body = stringResource(Res.string.settings_delete_account_confirm_message),
            primaryLabel = stringResource(Res.string.settings_delete_account_confirm_action),
            primaryLeadingIcon = Icons.Outlined.Delete,
            onPrimary = { onIntent(SettingsIntent.ConfirmDeleteAccount) },
            cancelLabel = stringResource(Res.string.settings_delete_account_cancel),
            accent = PapDialogAccent.Destructive,
            isLoading = state.isDeletingAccount,
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.settings_title),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = TITLE_LETTER_SPACING_SP.sp,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.settings_cd_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ── Profile (no section header — the card speaks for itself) ─
            item {
                ProfileCardV2(
                    displayName = state.userProfile?.displayName
                        ?: stringResource(Res.string.settings_profile_name_placeholder),
                    email = state.userProfile?.email,
                    createdAtMs = state.userProfile?.createdAt,
                    onLogout = { onIntent(SettingsIntent.Logout) },
                )
            }

            // ── Appearance ───────────────────────────────────────────────
            item { SectionHeaderMuted(stringResource(Res.string.settings_section_appearance)) }
            item { ThemePickerCard(selected = themeMode, onSelect = onSetThemeMode) }
            item {
                val autoLabel = stringResource(Res.string.settings_language_auto)
                val languageOptions = remember(autoLabel) {
                    linkedMapOf(
                        "auto" to autoLabel,
                        "en" to "English", "es" to "Español", "it" to "Italiano",
                        "pt" to "Português", "fr" to "Français", "de" to "Deutsch",
                        "nl" to "Nederlands", "pl" to "Polski", "ro" to "Română",
                    )
                }
                SettingsDropdownItem(
                    icon = Icons.Outlined.Language,
                    label = stringResource(Res.string.settings_language),
                    description = stringResource(Res.string.settings_language_desc),
                    options = languageOptions,
                    selected = selectedLanguage,
                    onSelect = onSetLanguage,
                )
            }

            // ── Map ──────────────────────────────────────────────────────
            item { SectionHeaderMuted(stringResource(Res.string.settings_section_map)) }
            item {
                MapTypePickerCard(
                    selected = state.mapType,
                    onSelect = { onIntent(SettingsIntent.SetMapType(it)) },
                )
            }
            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.Map,
                    label = stringResource(Res.string.settings_distance_unit),
                    description = stringResource(Res.string.settings_distance_unit_desc),
                    checked = imperialUnits,
                    onCheckedChange = onToggleImperialUnits,
                )
            }

            // ── Detection ────────────────────────────────────────────────
            item { SectionHeaderMuted(stringResource(Res.string.settings_section_detection)) }
            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.DirectionsCar,
                    label = stringResource(Res.string.settings_auto_detect),
                    description = stringResource(Res.string.settings_auto_detect_desc),
                    checked = state.autoDetectParking,
                    onCheckedChange = { onIntent(SettingsIntent.ToggleAutoDetect(it)) },
                )
            }
            item {
                SettingsNavItem(
                    icon = Icons.Outlined.Bluetooth,
                    label = stringResource(Res.string.settings_nav_my_car),
                    description = stringResource(Res.string.settings_nav_my_car_desc),
                    onClick = { onIntent(SettingsIntent.NavigateToVehicles) },
                )
            }

            // ── Notifications (master + grouped subs) ───────────────────
            item { SectionHeaderMuted(stringResource(Res.string.settings_section_notifications)) }
            item {
                // Master is derived: ON when at least one sub is ON. Toggling
                // master via the single ToggleMasterNotifications intent flips
                // both subs at once and persists via prefs.
                val masterOn = state.notifyParkingDetected || state.notifySpotFreed
                NotificationsGroupCard(
                    masterOn = masterOn,
                    onMasterChange = { onIntent(SettingsIntent.ToggleMasterNotifications(it)) },
                    parkingOn = state.notifyParkingDetected,
                    onParkingChange = { onIntent(SettingsIntent.ToggleParkingDetectedNotif(it)) },
                    spotOn = state.notifySpotFreed,
                    onSpotChange = { onIntent(SettingsIntent.ToggleSpotFreedNotif(it)) },
                )
            }

            // ── About ────────────────────────────────────────────────────
            item { SectionHeaderMuted(stringResource(Res.string.settings_section_about)) }
            item {
                SettingsInfoItem(
                    icon = Icons.Outlined.Info,
                    label = stringResource(Res.string.settings_version),
                    value = state.appVersion,
                )
            }
            item {
                SettingsNavItem(
                    icon = Icons.Outlined.Shield,
                    label = stringResource(Res.string.settings_privacy),
                    onClick = { onIntent(SettingsIntent.OpenPrivacyPolicy) },
                )
            }
            item {
                SettingsNavItem(
                    icon = Icons.Outlined.VerifiedUser,
                    label = stringResource(Res.string.settings_licenses),
                    onClick = { onIntent(SettingsIntent.OpenLicenses) },
                )
            }
            item {
                SettingsNavItem(
                    icon = Icons.Outlined.Email,
                    label = stringResource(Res.string.settings_contact),
                    onClick = { onIntent(SettingsIntent.OpenContact) },
                )
            }

            // ── Danger zone ──────────────────────────────────────────────
            item { SectionHeaderDanger(stringResource(Res.string.settings_danger_zone)) }
            item {
                DangerZoneCard(
                    deleting = state.isDeletingAccount,
                    subtitle = stringResource(Res.string.settings_danger_zone_subtitle),
                    label = stringResource(Res.string.settings_profile_delete_account),
                    onClick = { onIntent(SettingsIntent.RequestDeleteAccount) },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section headers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeaderMuted(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECTION_LABEL_ALPHA),
        letterSpacing = SECTION_LABEL_TRACKING_SP.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp, start = 4.dp),
    )
}

@Composable
private fun SectionHeaderDanger(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.error,
        letterSpacing = SECTION_LABEL_TRACKING_SP.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp, start = 4.dp),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Profile Card V2 — avatar + active vehicle row + logout
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileCardV2(
    displayName: String,
    email: String?,
    createdAtMs: Long?,
    onLogout: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CARD_CORNER_DP.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outline.copy(alpha = CARD_BORDER_ALPHA)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(AVATAR_DP.dp)
                        .clip(CircleShape)
                        .background(cs.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = cs.onPrimary,
                    )
                }
                Spacer(Modifier.size(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface,
                    )
                    if (email != null) {
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurface.copy(alpha = SUBTITLE_ALPHA),
                        )
                    }
                    val memberSinceLine = memberSinceText(createdAtMs)
                    if (memberSinceLine != null) {
                        Spacer(Modifier.size(2.dp))
                        Text(
                            text = memberSinceLine.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = SECTION_LABEL_TRACKING_SP.sp,
                            color = cs.onSurface.copy(alpha = SECTION_LABEL_ALPHA),
                        )
                    }
                }
            }

            // Logout outlined
            Spacer(Modifier.size(16.dp))
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Outlined.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(
                    stringResource(Res.string.settings_profile_logout),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * Returns the "Member since MAR 2026" line for the profile card.
 * Returns null when [createdAtMs] is missing or pre-app-launch sentinel
 * (epoch / legacy migrated accounts without the timestamp populated).
 *
 * Month/year extraction is memoized so it doesn't repeat on every recompose
 * (e.g. when the user scrolls Settings or toggles a switch nearby).
 */
@Composable
private fun memberSinceText(createdAtMs: Long?): String? {
    if (createdAtMs == null || createdAtMs < MIN_VALID_CREATED_AT_MS) return null
    val (monthIdx, year) = remember(createdAtMs) {
        val tz = TimeZone.currentSystemDefault()
        val dt = Instant.fromEpochMilliseconds(createdAtMs).toLocalDateTime(tz)
        dt.month.ordinal.coerceIn(0, MONTH_SHORT_RES.lastIndex) to dt.year
    }
    val monthShort = stringResource(MONTH_SHORT_RES[monthIdx])
    return stringResource(Res.string.settings_profile_member_since, monthShort, year)
}

/** App launch is 2025; anything before this is a corrupt/legacy timestamp. */
private const val MIN_VALID_CREATED_AT_MS = 1_700_000_000_000L  // 2023-11-14

// ─────────────────────────────────────────────────────────────────────────────
// Theme picker — mini previews
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ThemePickerCard(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CARD_CORNER_DP.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outline.copy(alpha = CARD_BORDER_ALPHA)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(Res.string.settings_theme_mode),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
            )
            Text(
                stringResource(Res.string.settings_theme_mode_desc),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurface.copy(alpha = SUBTITLE_ALPHA),
            )
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemePreview(
                    mode = ThemeMode.LIGHT,
                    selected = selected == ThemeMode.LIGHT,
                    label = stringResource(Res.string.settings_theme_mode_light),
                    onClick = { onSelect(ThemeMode.LIGHT) },
                    modifier = Modifier.weight(1f),
                )
                ThemePreview(
                    mode = ThemeMode.DARK,
                    selected = selected == ThemeMode.DARK,
                    label = stringResource(Res.string.settings_theme_mode_dark),
                    onClick = { onSelect(ThemeMode.DARK) },
                    modifier = Modifier.weight(1f),
                )
                ThemePreview(
                    mode = ThemeMode.SYSTEM,
                    selected = selected == ThemeMode.SYSTEM,
                    label = stringResource(Res.string.settings_theme_mode_system),
                    onClick = { onSelect(ThemeMode.SYSTEM) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ThemePreview(
    mode: ThemeMode,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val (bg, surfaceColor) = when (mode) {
        ThemeMode.LIGHT  -> THEME_LIGHT_BG to THEME_LIGHT_SURFACE
        ThemeMode.DARK   -> THEME_DARK_BG to THEME_DARK_SURFACE
        ThemeMode.SYSTEM -> THEME_LIGHT_BG to THEME_DARK_SURFACE
    }
    val borderColor = if (selected) cs.primary else cs.outline.copy(alpha = CARD_BORDER_ALPHA)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(THEME_PREVIEW_RATIO),
            shape = RoundedCornerShape(10.dp),
            color = bg,
            border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        ) {
            if (mode == ThemeMode.SYSTEM) {
                // diagonal split light/dark
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                0.0f to THEME_LIGHT_BG,
                                0.5f to THEME_LIGHT_BG,
                                0.5f to THEME_DARK_BG,
                                1.0f to THEME_DARK_BG,
                            ),
                        ),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(bg)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .align(Alignment.BottomCenter)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(surfaceColor),
                    )
                }
            }
        }
        Spacer(Modifier.size(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (selected) cs.primary else cs.onSurface.copy(alpha = SUBTITLE_ALPHA),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Map type picker — thumbnails
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MapTypePickerCard(selected: MapType, onSelect: (MapType) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CARD_CORNER_DP.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outline.copy(alpha = CARD_BORDER_ALPHA)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(Res.string.settings_map_type),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
            )
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MapTypeThumb(
                    selected = selected == MapType.NORMAL,
                    label = stringResource(Res.string.settings_map_type_normal),
                    onClick = { onSelect(MapType.NORMAL) },
                    gradient = Brush.linearGradient(listOf(MAP_NORMAL_A, MAP_NORMAL_B)),
                    stripeColor = Color.White,
                    modifier = Modifier.weight(1f),
                )
                MapTypeThumb(
                    selected = selected == MapType.SATELLITE,
                    label = stringResource(Res.string.settings_map_type_satellite),
                    onClick = { onSelect(MapType.SATELLITE) },
                    gradient = Brush.linearGradient(listOf(MAP_SAT_A, MAP_SAT_B, MAP_SAT_C)),
                    stripeColor = null,
                    modifier = Modifier.weight(1f),
                )
                MapTypeThumb(
                    selected = selected == MapType.TERRAIN,
                    label = stringResource(Res.string.settings_map_type_terrain),
                    onClick = { onSelect(MapType.TERRAIN) },
                    gradient = Brush.linearGradient(listOf(MAP_TERRAIN_A, MAP_TERRAIN_B)),
                    stripeColor = MAP_TERRAIN_STRIPE,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MapTypeThumb(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    gradient: Brush,
    stripeColor: Color?,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            shape = RoundedCornerShape(12.dp),
            color = Color.Transparent,
            border = BorderStroke(
                if (selected) 2.dp else 1.dp,
                if (selected) cs.primary else cs.outline.copy(alpha = CARD_BORDER_ALPHA),
            ),
        ) {
            Box(modifier = Modifier.fillMaxSize().background(gradient)) {
                if (stripeColor != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.7f)
                            .height(2.dp)
                            .background(stripeColor.copy(alpha = STRIPE_ALPHA)),
                    )
                }
                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(cs.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = cs.onPrimary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.size(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (selected) cs.primary else cs.onSurface.copy(alpha = SUBTITLE_ALPHA),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Notifications group card — master + sub
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NotificationsGroupCard(
    masterOn: Boolean,
    onMasterChange: (Boolean) -> Unit,
    parkingOn: Boolean,
    onParkingChange: (Boolean) -> Unit,
    spotOn: Boolean,
    onSpotChange: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CARD_CORNER_DP.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outline.copy(alpha = CARD_BORDER_ALPHA)),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SettingsIconBox(icon = Icons.Outlined.Notifications)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.settings_notifications_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                    )
                    Text(
                        stringResource(Res.string.settings_notifications_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurface.copy(alpha = SUBTITLE_ALPHA_STRONG),
                    )
                }
                Switch(checked = masterOn, onCheckedChange = onMasterChange)
            }
            if (masterOn) {
                HorizontalDivider(color = cs.outline.copy(alpha = DIVIDER_ALPHA))
                SubNotifRow(
                    label = stringResource(Res.string.settings_notif_parking),
                    description = stringResource(Res.string.settings_notif_parking_desc),
                    checked = parkingOn,
                    onCheckedChange = onParkingChange,
                )
                HorizontalDivider(color = cs.outline.copy(alpha = DIVIDER_ALPHA))
                SubNotifRow(
                    label = stringResource(Res.string.settings_notif_spot),
                    description = stringResource(Res.string.settings_notif_spot_desc),
                    checked = spotOn,
                    onCheckedChange = onSpotChange,
                )
            }
        }
    }
}

@Composable
private fun SubNotifRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.padding(start = 64.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
            )
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurface.copy(alpha = SUBTITLE_ALPHA_STRONG),
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Danger zone
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DangerZoneCard(
    deleting: Boolean,
    subtitle: String,
    label: String,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CARD_CORNER_DP.dp),
        color = cs.errorContainer.copy(alpha = DANGER_BG_ALPHA),
        border = BorderStroke(1.5.dp, cs.error.copy(alpha = DANGER_BORDER_ALPHA)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurface.copy(alpha = DANGER_SUBTITLE_ALPHA),
            )
            Spacer(Modifier.size(10.dp))
            OutlinedButton(
                onClick = onClick,
                enabled = !deleting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error),
                border = BorderStroke(1.5.dp, cs.error),
            ) {
                if (deleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = cs.error,
                    )
                } else {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(label, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reused primitives — switch, info, nav, dropdown, icon box
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CARD_CORNER_DP.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outline.copy(alpha = CARD_BORDER_ALPHA)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsIconBox(icon = icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                Text(description, style = MaterialTheme.typography.bodySmall, color = cs.onSurface.copy(alpha = SUBTITLE_ALPHA_STRONG))
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SettingsInfoItem(icon: ImageVector, label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CARD_CORNER_DP.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outline.copy(alpha = CARD_BORDER_ALPHA)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsIconBox(icon = icon)
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodySmall, color = cs.onSurface.copy(alpha = SUBTITLE_ALPHA_STRONG))
        }
    }
}

@Composable
private fun SettingsNavItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    description: String? = null,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CARD_CORNER_DP.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outline.copy(alpha = CARD_BORDER_ALPHA)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsIconBox(icon = icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                if (description != null) {
                    Text(description, style = MaterialTheme.typography.bodySmall, color = cs.onSurface.copy(alpha = SUBTITLE_ALPHA_STRONG))
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = cs.onSurface.copy(alpha = CHEVRON_DIM_ALPHA),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SettingsIconBox(icon: ImageVector) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cs.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = cs.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdownItem(
    icon: ImageVector,
    label: String,
    description: String,
    options: Map<String, String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CARD_CORNER_DP.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outline.copy(alpha = CARD_BORDER_ALPHA)),
    ) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            Row(
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SettingsIconBox(icon = icon)
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = cs.onSurface.copy(alpha = SUBTITLE_ALPHA_STRONG))
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = options[selected] ?: selected,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (tag, displayName) ->
                    DropdownMenuItem(
                        text = { Text(displayName) },
                        onClick = { onSelect(tag); expanded = false },
                        leadingIcon = if (tag == selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp)) }
                        } else null,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tokens
// ─────────────────────────────────────────────────────────────────────────────

private const val TITLE_LETTER_SPACING_SP = -0.5
private const val SECTION_LABEL_TRACKING_SP = 1.2
private const val CARD_CORNER_DP = 16
private const val AVATAR_DP = 56

private const val THEME_PREVIEW_RATIO = 0.85f

private const val CARD_BORDER_ALPHA = 0.3f
private const val SECTION_LABEL_ALPHA = 0.55f
private const val SUBTITLE_ALPHA = 0.55f
private const val SUBTITLE_ALPHA_STRONG = 0.5f
private const val CHEVRON_DIM_ALPHA = 0.3f
private const val DIVIDER_ALPHA = 0.15f
private const val DANGER_BG_ALPHA = 0.15f
private const val DANGER_BORDER_ALPHA = 0.7f
private const val DANGER_SUBTITLE_ALPHA = 0.6f
private const val STRIPE_ALPHA = 0.7f

private val THEME_LIGHT_BG = Color(0xFFF5FBF4)
private val THEME_LIGHT_SURFACE = Color(0xFFFFFFFF)
private val THEME_DARK_BG = Color(0xFF0D1C14)
private val THEME_DARK_SURFACE = Color(0xFF0F2218)

private val MAP_NORMAL_A = Color(0xFFE8EEE6)
private val MAP_NORMAL_B = Color(0xFFCEE3CB)
private val MAP_SAT_A = Color(0xFF2D3B2D)
private val MAP_SAT_B = Color(0xFF4A5942)
private val MAP_SAT_C = Color(0xFF6B7B5C)
private val MAP_TERRAIN_A = Color(0xFFD7C3A0)
private val MAP_TERRAIN_B = Color(0xFFA89478)
private val MAP_TERRAIN_STRIPE = Color(0xFF8B7960)
