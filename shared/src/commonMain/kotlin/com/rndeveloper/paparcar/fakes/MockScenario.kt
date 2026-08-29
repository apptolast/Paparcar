package com.rndeveloper.paparcar.fakes

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
 * commonMain — must reference its type. It is exclusively wired in [com.rndeveloper.paparcar.di]
 * mock module; prod never instantiates it.
 */
class MockScenario {

    enum class Session { LoggedOut, LoggedInNoVehicle, LoggedInWithVehicles }

    /** Tiers mirror [com.rndeveloper.paparcar.domain.permissions.AppPermissionState] gates. */
    enum class PermissionTier { None, Core, Producer, All }

    val session = MutableStateFlow(Session.LoggedInWithVehicles)
    val onboardingCompleted = MutableStateFlow(true)
    val permissionTier = MutableStateFlow(PermissionTier.All)
    val gpsEnabled = MutableStateFlow(true)
    val online = MutableStateFlow(true)

    /** Simulates an aggressive-OEM device (MIUI/ColorOS…): the OEM manager reports both
     *  proprietary gates, so REDUCED reliability is reachable on any emulator (combine with
     *  tier=Producer — no battery exemption — and vehicles without BT). [DET-RELIABILITY-001] */
    val aggressiveOem = MutableStateFlow(false)

    /** Seeds an ACTIVE parking session for the ACTIVE vehicle (Seat León), so the real Home
     *  reaches the parked/watching state: "Vigilando" story line, parked-car card, ParkingPeek,
     *  release dialog — the whole own-parking loop. [UX-PARKED-STATE-001] */
    val ownParkedSession = MutableStateFlow(false)

    /** Gives the ACTIVE vehicle a paired-Bluetooth identity (the fake scanner is always on), so
     *  the strategy resolves BLUETOOTH and the story shows the BT-armed watching line.
     *  [UX-PARKED-STATE-001] */
    val activeVehicleBluetooth = MutableStateFlow(false)

    /** The resident SENTRY foreground service is alive (genuinely watching). With a Coordinator car
     *  parked this drives the HONEST watch badge: true → "Vigilando tu sitio"; false → "Vigilancia
     *  detenida" (the OEM killed the watcher). The Dev Catalog mirrors it onto the runtime presence.
     *  [DET-WATCH-HONEST-001] */
    val sentryAlive = MutableStateFlow(true)

    /** [DET-ASK-STATE-001] Opens a "did you park?" question, as if the detector had just posted the
     *  prompt. Combine with the driving runtime to see what the field case actually looked like:
     *  a live trip whose sheet asks instead of narrating. The fake preferences serve it as a
     *  [com.rndeveloper.paparcar.domain.detection.PendingPromptWindow] stamped NOW, so it is inside
     *  the response window and the row renders. */
    val promptOpen = MutableStateFlow(false)

    /** [UI-APPROXIMATE-PARKING-DRAWS-ITS-DOUBT-001] Turns the seeded session into an AREA instead of
     *  an exact pin, as an honest close leaves it when the end of the trip could not be followed.
     *  Requires [ownParkedSession]. Home then draws the doubt ring around the car and the peek says
     *  what it means. */
    val approximateParking = MutableStateFlow(false)

    fun reset() {
        session.value = Session.LoggedInWithVehicles
        onboardingCompleted.value = true
        permissionTier.value = PermissionTier.All
        gpsEnabled.value = true
        online.value = true
        aggressiveOem.value = false
        ownParkedSession.value = false
        activeVehicleBluetooth.value = false
        sentryAlive.value = true
        promptOpen.value = false
        approximateParking.value = false
    }
}
