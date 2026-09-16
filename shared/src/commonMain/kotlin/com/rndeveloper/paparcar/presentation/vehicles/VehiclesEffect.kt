package com.rndeveloper.paparcar.presentation.vehicles

import com.rndeveloper.paparcar.domain.entitlement.PaywallReason
import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.presentation.permissions.PermissionsFocus

sealed class VehiclesEffect {
    data object NavigateToAddVehicle : VehiclesEffect()
    data class NavigateToEditVehicle(val vehicleId: String) : VehiclesEffect()
    data class NavigateToMap(val lat: Double, val lon: Double, val sessionId: String = "") : VehiclesEffect()

    /** Volver a Home con ESTE aparcamiento vivo seleccionado y la cámara encima — el mismo gesto que
     *  tocar su marcador. [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001] */
    data class NavigateToLiveParking(val sessionId: String) : VehiclesEffect()
    data class ShowError(val error: PaparcarError) : VehiclesEffect()

    /** Open the Paparcar Plus paywall, stamped with WHY. [MONEY-001][FREE-LIMITS-001] */
    data class NavigateToPaywall(val reason: PaywallReason) : VehiclesEffect()

    /** Open the full figures panel on a given car. [STATS-PANEL-001] */
    data class NavigateToStats(val vehicleId: String) : VehiclesEffect()

    /** The same focused permissions flow Home's banner and Settings' switch open — turning
     *  detection on is one action, so it cannot land on a different screen depending on which
     *  surface asked. [DET-TOGGLE-001] */
    data class NavigateToPermissions(val focus: PermissionsFocus) : VehiclesEffect()
}
