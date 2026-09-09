package com.rndeveloper.paparcar.presentation.permissions

import androidx.compose.runtime.Composable

/**
 * Launches the platform's fine-location permission dialog and refreshes [PermissionManager] with
 * the outcome. Used by Home's first-entry ask — the dialog appears OVER the map, which is its own
 * context, so no permissions screen stands in front of it.
 * [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]
 */
@Composable
expect fun rememberRequestFineLocationAction(): () -> Unit
