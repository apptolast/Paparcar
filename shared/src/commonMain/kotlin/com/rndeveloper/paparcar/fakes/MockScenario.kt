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

    /** [VEH-SHARED-001] El coche del garaje es de OTRA persona y el usuario se unió a su grupo —
     *  la lectura de MIEMBRO, que desde la cuenta del dueño no se puede alcanzar: fila vinculada,
     *  «lo aparcó Marta» y «salir de este coche» en vez del código y «dejar de compartir». */
    val sharedCarJoined = MutableStateFlow(false)

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

    /** [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001] Replays the guided checklist on the REAL
     *  Home: clears the banked steps and the dismissal, so the surface comes back at step 1. The
     *  steps then advance off the same live signals as in production — pair with [ownParkedSession]
     *  and [sentryAlive] to walk the checklist forward without leaving the emulator. */
    val firstStepsPending = MutableStateFlow(false)

    /** [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001] Draws a PRIVATE zone over the
     *  seeded own parking, so leaving it frees no plaza. Requires [ownParkedSession]. With it on,
     *  the release dialog says so instead of promising a spot, and the history detail names the
     *  zone next to (not instead of) the strategy that detected the park. Without it the whole
     *  private-zone half of the app was unreachable in the mock set. */
    val parkedInPrivateZone = MutableStateFlow(false)

    /** [MONEY-001] The user holds the Paparcar Plus entitlement. Drives every gate in the real
     *  graph through [com.rndeveloper.paparcar.fakes.data.repository.FakeEntitlementRepository]:
     *  toggle it OFF while inside the app to exercise the live downgrade rules (history window
     *  shrinks to 30 days, zone alerts pause) that a real store makes painful to reproduce. */
    val isPlus = MutableStateFlow(false)

    fun reset() {
        session.value = Session.LoggedInWithVehicles
        onboardingCompleted.value = true
        permissionTier.value = PermissionTier.All
        parkedInPrivateZone.value = false
        gpsEnabled.value = true
        online.value = true
        aggressiveOem.value = false
        ownParkedSession.value = false
        activeVehicleBluetooth.value = false
        sentryAlive.value = true
        promptOpen.value = false
        approximateParking.value = false
        firstStepsPending.value = false
        isPlus.value = false
        sharedCarJoined.value = false
    }
}
