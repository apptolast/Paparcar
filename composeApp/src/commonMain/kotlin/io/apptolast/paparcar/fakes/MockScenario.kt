package io.apptolast.paparcar.fakes

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Mutable test scenario shared by the mock-flavor fakes. The Dev Catalog (mock launcher)
 * writes these flows; the fakes read them so the **real** app graph routes accordingly
 * (login → onboarding → permissions → vehicle registration → home) without any backend.
 *
 * Defaults reproduce the previous hard-coded behaviour (logged in, with vehicles, all
 * permissions granted, GPS on, online) so the mock app still boots straight to Home when
 * the scenario is untouched, and so fakes constructed without a scenario are unaffected.
 *
 * Lives in commonMain (not the mock source set) only because the fakes — which also live in
 * commonMain — must reference its type. It is exclusively wired in [io.apptolast.paparcar.di]
 * mock module; prod never instantiates it.
 */
class MockScenario {

    enum class Session { LoggedOut, LoggedInNoVehicle, LoggedInWithVehicles }

    /** Tiers mirror [io.apptolast.paparcar.domain.permissions.AppPermissionState] gates. */
    enum class PermissionTier { None, Core, Producer, All }

    val session = MutableStateFlow(Session.LoggedInWithVehicles)
    val onboardingCompleted = MutableStateFlow(true)
    val permissionTier = MutableStateFlow(PermissionTier.All)
    val gpsEnabled = MutableStateFlow(true)
    val online = MutableStateFlow(true)

    fun reset() {
        session.value = Session.LoggedInWithVehicles
        onboardingCompleted.value = true
        permissionTier.value = PermissionTier.All
        gpsEnabled.value = true
        online.value = true
    }
}
