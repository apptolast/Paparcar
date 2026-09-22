package com.rndeveloper.paparcar.presentation.permissions

import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.permissions_bg_guide_body_ios
import paparcar.composeapp.generated.resources.permissions_perm_location_services_desc_ios
import paparcar.composeapp.generated.resources.permissions_perm_precise_desc_ios

actual val platformPermissionCopy: PlatformPermissionCopy = PlatformPermissionCopy(
    backgroundGuideBody = Res.string.permissions_bg_guide_body_ios,
    locationServicesReason = Res.string.permissions_perm_location_services_desc_ios,
    preciseLocationReason = Res.string.permissions_perm_precise_desc_ios,
)
