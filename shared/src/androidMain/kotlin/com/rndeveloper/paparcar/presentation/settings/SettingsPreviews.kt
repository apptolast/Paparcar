package com.rndeveloper.paparcar.presentation.settings

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.rndeveloper.paparcar.domain.diagnostics.DiagnosticsReport
import com.rndeveloper.paparcar.domain.model.UserProfile
import com.rndeveloper.paparcar.domain.permissions.RequiredPermission
import com.rndeveloper.paparcar.domain.preferences.ThemeMode
import com.rndeveloper.paparcar.ui.theme.PaparcarTheme

private val loggedInProfile = UserProfile(
    userId = "u1",
    email = "user@paparcar.app",
    displayName = "Carlos López",
    photoUrl = null,
    createdAt = 0L,
    updatedAt = 0L,
)

@Preview(name = "Settings — claro", showBackground = true)
@Composable
private fun SettingsLightPreview() {
    PaparcarTheme(darkTheme = false) {
        SettingsContent(
            state = SettingsState(userProfile = loggedInProfile),
        )
    }
}

@Preview(name = "Settings — oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        SettingsContent(
            state = SettingsState(userProfile = loggedInProfile),
        )
    }
}

@Preview(name = "Settings — sin perfil", showBackground = true)
@Composable
private fun SettingsNoProfilePreview() {
    PaparcarTheme(darkTheme = false) {
        SettingsContent(
            state = SettingsState(userProfile = null),
        )
    }
}

@Preview(name = "Settings — satélite + unidades imperiales", showBackground = true)
@Composable
private fun SettingsSatelliteImperialPreview() {
    PaparcarTheme(darkTheme = false) {
        SettingsContent(
            state = SettingsState(
                userProfile = loggedInProfile,
                autoDetectParking = false,
                notifyParkingDetected = false,
            ),
            themeMode = ThemeMode.DARK,
            imperialUnits = true,
        )
    }
}

@Preview(name = "Settings — diálogo borrar cuenta", showBackground = true)
@Composable
private fun SettingsDeleteAccountDialogPreview() {
    PaparcarTheme(darkTheme = false) {
        SettingsContent(
            state = SettingsState(
                userProfile = loggedInProfile,
                showDeleteAccountConfirmation = true,
            ),
        )
    }
}

@Preview(name = "Settings — diálogo enviar diagnóstico", showBackground = true)
@Composable
private fun SettingsSendDiagnosticsDialogPreview() {
    PaparcarTheme(darkTheme = false) {
        SettingsContent(
            state = SettingsState(
                userProfile = loggedInProfile,
                showSendDiagnosticsConfirmation = true,
            ),
        )
    }
}

@Preview(name = "Settings — diagnóstico con descripción", showBackground = true)
@Composable
private fun SettingsSendDiagnosticsWithMessagePreview() {
    PaparcarTheme(darkTheme = false) {
        SettingsContent(
            state = SettingsState(
                userProfile = loggedInProfile,
                showSendDiagnosticsConfirmation = true,
                diagnosticsMessage = PREVIEW_REPORT_MESSAGE,
            ),
        )
    }
}

/** El contador solo se pinta cerca del techo; esta preview existe para verlo.
 *  [SUPPORT-A-REPORT-MUST-SAY-WHAT-WENT-WRONG-001] */
@Preview(name = "Settings — diagnóstico al límite", showBackground = true)
@Composable
private fun SettingsSendDiagnosticsNearLimitPreview() {
    PaparcarTheme(darkTheme = false) {
        SettingsContent(
            state = SettingsState(
                userProfile = loggedInProfile,
                showSendDiagnosticsConfirmation = true,
                diagnosticsMessage = PREVIEW_REPORT_MESSAGE.repeat(4)
                    .take(DiagnosticsReport.MAX_MESSAGE_CHARS - 30),
            ),
        )
    }
}

private const val PREVIEW_REPORT_MESSAGE =
    "Ayer sobre las 9:15 aparqué en Fuencarral y el pin salió en la calle anterior. " +
        "Tuve que moverlo a mano."

@Preview(name = "Settings — permisos incompletos", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsMissingPermissionsPreview() {
    PaparcarTheme(darkTheme = true) {
        SettingsContent(
            state = SettingsState(
                userProfile = loggedInProfile,
                // Amber health row + "Fix"
                missingDetectionPermissions = setOf(RequiredPermission.BACKGROUND_LOCATION),
                isLocationServicesEnabled = true,
            ),
        )
    }
}

@Preview(name = "Settings — detección lista + BT configurado", showBackground = true)
@Composable
private fun SettingsDetectionReadyPreview() {
    PaparcarTheme(darkTheme = false) {
        SettingsContent(
            state = SettingsState(
                userProfile = loggedInProfile,
                missingDetectionPermissions = emptySet(),
                isLocationServicesEnabled = true,
                isBatteryOptimizationExempt = true,
                activeVehicleId = "v1",
                btDeviceConfigured = true,
            ),
        )
    }
}
