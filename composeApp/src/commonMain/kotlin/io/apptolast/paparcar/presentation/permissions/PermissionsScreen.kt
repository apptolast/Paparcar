package io.apptolast.paparcar.presentation.permissions

import androidx.compose.runtime.Composable

expect @Composable fun PermissionsScreen(onPermissionsGranted: () -> Unit)
