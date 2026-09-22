package com.rndeveloper.paparcar.presentation.permissions

import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.permissions_bg_guide_body
import paparcar.composeapp.generated.resources.permissions_perm_location_services_desc
import paparcar.composeapp.generated.resources.permissions_perm_precise_desc

actual val platformPermissionCopy: PlatformPermissionCopy = PlatformPermissionCopy(
    backgroundGuideBody = Res.string.permissions_bg_guide_body,
    locationServicesReason = Res.string.permissions_perm_location_services_desc,
    // En Android la precisión no es una fila aparte: `ACCESS_FINE_LOCATION` ES el permiso que se
    // pide, así que esta cadena no llega a pintarse nunca. Se declara igual porque el contrato es
    // del tipo, no de un `if`. [IOS-PERMISSIONS-A-ROW-MUST-BE-GRANTABLE-HERE-001]
    preciseLocationReason = Res.string.permissions_perm_precise_desc,
)
