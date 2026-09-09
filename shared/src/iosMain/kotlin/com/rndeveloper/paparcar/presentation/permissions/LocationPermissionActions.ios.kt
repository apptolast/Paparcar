package com.rndeveloper.paparcar.presentation.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.rndeveloper.paparcar.domain.permissions.PermissionManager
import com.rndeveloper.paparcar.ios.permissions.IosPermissionRequester
import org.koin.compose.koinInject

@Composable
actual fun rememberRequestFineLocationAction(): () -> Unit {
    val permissionManager = koinInject<PermissionManager>()
    val requester = remember { IosPermissionRequester(permissionManager) }
    // Step 1 on iOS is the when-in-use location request — the same first ask the permissions
    // screen makes, just triggered from the map. [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]
    return { requester.requestStep1() }
}
