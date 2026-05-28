package io.apptolast.paparcar.presentation.permissions

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.apptolast.paparcar.ui.theme.PaparcarTheme

// ── All denied (first-launch state) ──────────────────────────────────────────

@Preview(name = "Permissions — all denied · Light", showBackground = true)
@Composable
private fun PermissionsAllDeniedLightPreview() {
    PaparcarTheme(darkTheme = false) {
        PermissionsContent(
            state = PermissionsState(),
            onRequestPermissions = {},
        )
    }
}

@Preview(
    name = "Permissions — all denied · Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PermissionsAllDeniedDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        PermissionsContent(
            state = PermissionsState(),
            onRequestPermissions = {},
        )
    }
}

// ── Partially granted (fine location + activity, missing background) ──────────

@Preview(name = "Permissions — partial · Light", showBackground = true)
@Composable
private fun PermissionsPartialLightPreview() {
    PaparcarTheme(darkTheme = false) {
        PermissionsContent(
            state = PermissionsState(
                hasFineLocation = true,
                hasActivityRecognition = true,
                hasNotifications = true,
                isLocationServicesEnabled = true,
                hasBackgroundLocation = false,
            ),
            onRequestPermissions = {},
        )
    }
}

// ── All critical permissions granted (Bluetooth optional, not yet granted) ────

@Preview(name = "Permissions — all critical granted · Light", showBackground = true)
@Composable
private fun PermissionsAllCriticalGrantedLightPreview() {
    PaparcarTheme(darkTheme = false) {
        PermissionsContent(
            state = PermissionsState(
                hasFineLocation = true,
                hasBackgroundLocation = true,
                hasActivityRecognition = true,
                hasNotifications = true,
                isLocationServicesEnabled = true,
                hasBluetoothConnect = false,
            ),
            onRequestPermissions = {},
        )
    }
}

// ── All granted including Bluetooth ──────────────────────────────────────────

@Preview(name = "Permissions — all granted · Light", showBackground = true)
@Composable
private fun PermissionsAllGrantedLightPreview() {
    PaparcarTheme(darkTheme = false) {
        PermissionsContent(
            state = PermissionsState(
                hasFineLocation = true,
                hasBackgroundLocation = true,
                hasActivityRecognition = true,
                hasNotifications = true,
                isLocationServicesEnabled = true,
                hasBluetoothConnect = true,
            ),
            onRequestPermissions = {},
        )
    }
}

// ── Settings prompt (user denied, must open system settings) ─────────────────

@Preview(name = "Permissions — settings prompt · Light", showBackground = true)
@Composable
private fun PermissionsSettingsPromptLightPreview() {
    PaparcarTheme(darkTheme = false) {
        PermissionsContent(
            state = PermissionsState(
                showSettingsPrompt = true,
            ),
            onRequestPermissions = {},
        )
    }
}

@Preview(
    name = "Permissions — settings prompt · Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PermissionsSettingsPromptDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        PermissionsContent(
            state = PermissionsState(
                showSettingsPrompt = true,
            ),
            onRequestPermissions = {},
        )
    }
}

// ── OEM autostart card visible (manufacturer requires whitelist) ─────────────

@Preview(name = "Permissions — autostart card · Light", showBackground = true)
@Composable
private fun PermissionsAutostartCardLightPreview() {
    PaparcarTheme(darkTheme = false) {
        PermissionsContent(
            state = PermissionsState(
                hasFineLocation = true,
                hasBackgroundLocation = true,
                hasActivityRecognition = true,
                hasNotifications = true,
                isLocationServicesEnabled = true,
                isBatteryOptimizationExempt = true,
                showAutostartCard = true,
            ),
            onRequestPermissions = {},
        )
    }
}

@Preview(
    name = "Permissions — autostart card · Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PermissionsAutostartCardDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        PermissionsContent(
            state = PermissionsState(
                hasFineLocation = true,
                hasBackgroundLocation = true,
                hasActivityRecognition = true,
                hasNotifications = true,
                isLocationServicesEnabled = true,
                isBatteryOptimizationExempt = true,
                showAutostartCard = true,
            ),
            onRequestPermissions = {},
        )
    }
}

// ── OEM autostart card visible WITH battery hint (early onboarding state) ────

@Preview(name = "Permissions — autostart + battery pending · Light", showBackground = true)
@Composable
private fun PermissionsAutostartAndBatteryLightPreview() {
    PaparcarTheme(darkTheme = false) {
        PermissionsContent(
            state = PermissionsState(
                hasFineLocation = true,
                hasBackgroundLocation = true,
                hasActivityRecognition = true,
                hasNotifications = true,
                isLocationServicesEnabled = true,
                isBatteryOptimizationExempt = false,
                showAutostartCard = true,
            ),
            onRequestPermissions = {},
        )
    }
}
