package io.apptolast.paparcar.presentation.bluetooth

import androidx.compose.runtime.Composable

@Composable
expect fun rememberRequestBluetoothPermissionAction(): () -> Unit

@Composable
expect fun rememberOpenBluetoothSettingsAction(): () -> Unit
