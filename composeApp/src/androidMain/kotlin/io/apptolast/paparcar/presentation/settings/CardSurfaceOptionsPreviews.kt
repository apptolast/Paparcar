package io.apptolast.paparcar.presentation.settings

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import io.apptolast.paparcar.domain.model.UserProfile
import io.apptolast.paparcar.ui.theme.PaparcarTheme

// ═══════════════════════════════════════════════════════════════════════════════
//  Comparativa de surface card — Pantalla Ajustes (modo oscuro)
//
//  Elementos que usan surfaceContainerHigh en esta pantalla:
//    · ProfileCardV2 (tarjeta de perfil + stats)
//    · ThemePickerCard
//    · MapTypePickerCard
//    · NotificationsGroupCard
//    · SettingsSwitchItem / SettingsInfoItem / SettingsNavItem / SettingsDropdownItem
//
//  Opción A — Plano   #141918
//  Opción B — Sutil   #181D1C
//  Opción C — Elevado #1C2221
// ═══════════════════════════════════════════════════════════════════════════════

private val SURFACE_A = Color(0xFF141918)
private val SURFACE_B = Color(0xFF181D1C)
private val SURFACE_C = Color(0xFF1C2221)

@Composable
private fun DarkOption(cardSurface: Color, content: @Composable () -> Unit) {
    PaparcarTheme(darkTheme = true) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                surfaceContainerHigh = cardSurface,
                surfaceVariant = cardSurface,
            ),
        ) {
            content()
        }
    }
}

@Composable
private fun DarkOptionNoBorder(cardSurface: Color, content: @Composable () -> Unit) {
    PaparcarTheme(darkTheme = true) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                surfaceContainerHigh = cardSurface,
                surfaceVariant = cardSurface,
                outline = Color.Transparent,
                outlineVariant = Color.Transparent,
            ),
        ) {
            content()
        }
    }
}

private val profile = UserProfile(
    userId = "u1",
    email = "user@paparcar.app",
    displayName = "Carlos López",
    photoUrl = null,
    createdAt = 0L,
    updatedAt = 0L,
)

@Preview(
    name = "Settings · Opción A — Plano #141918 (oscuro)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsOptionADarkPreview() {
    DarkOption(SURFACE_A) { SettingsContent(state = SettingsState(userProfile = profile)) }
}

@Preview(
    name = "Settings · Opción B — Sutil #181D1C (oscuro)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsOptionBDarkPreview() {
    DarkOption(SURFACE_B) { SettingsContent(state = SettingsState(userProfile = profile)) }
}

@Preview(
    name = "Settings · Opción C — Elevado #1C2221 (oscuro)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsOptionCDarkPreview() {
    DarkOption(SURFACE_C) { SettingsContent(state = SettingsState(userProfile = profile)) }
}

// ─── Sin borde ────────────────────────────────────────────────────────────────

@Preview(
    name = "Settings · Sin borde · Opción A — Plano #141918 (oscuro)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsNoBorderOptionADarkPreview() {
    DarkOptionNoBorder(SURFACE_A) { SettingsContent(state = SettingsState(userProfile = profile)) }
}

@Preview(
    name = "Settings · Sin borde · Opción B — Sutil #181D1C (oscuro)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsNoBorderOptionBDarkPreview() {
    DarkOptionNoBorder(SURFACE_B) { SettingsContent(state = SettingsState(userProfile = profile)) }
}

@Preview(
    name = "Settings · Sin borde · Opción C — Elevado #1C2221 (oscuro)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsNoBorderOptionCDarkPreview() {
    DarkOptionNoBorder(SURFACE_C) { SettingsContent(state = SettingsState(userProfile = profile)) }
}
