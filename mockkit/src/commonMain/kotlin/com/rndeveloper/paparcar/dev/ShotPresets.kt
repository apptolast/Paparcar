package com.rndeveloper.paparcar.dev

import com.rndeveloper.paparcar.domain.detection.MutableDetectionRuntimeState
import com.rndeveloper.paparcar.domain.detection.ServicePresence
import com.rndeveloper.paparcar.fakes.MockScenario

/**
 * [PLAY-SHOTS-EVERY-LANGUAGE-SHOWS-ITS-OWN-UI-001] Repeatable store-shot session.
 *
 * The Play carousel is 8 frames × 9 locales = 72 device captures, and the only way that is
 * reproducible is if the SCENARIO is chosen by the launch intent instead of by tapping a list whose
 * rows move. `DevMainActivity` reads `--es shot <key>` and mounts the **real** app directly under
 * the matching scenario, with the dev chips off — no `if (false)` patch to re-apply and revert
 * (which is how the 01-09 session did it, and why the raws could never be re-shot cheaply).
 *
 * ```
 * adb shell am start -n com.rndeveloper.paparcar.mock/com.rndeveloper.paparcar.DevMainActivity \
 *   --es shot parked
 * ```
 *
 * Only the SCENARIO lives here. Where inside the app each frame is taken (which tab, which marker)
 * is the capture script's business — see `docs/release/play-listing/README.md`.
 */
enum class ShotPreset {
    /** Frames 1-2: own car parked, sentry alive. Home tells the watching story. */
    Parked,

    /** Frames 3-5: nothing parked, everything granted — the map serves spots, fuel and car parks. */
    Map,

    /** Frames 6-7: parked + Plus, so the numbers panel and the group's figures are both populated. */
    Numbers,

    /** Frame 7: the car in the garage belongs to someone else's group (member reading). */
    SharedCar,

    /** Frame 8: plain, everything granted — Settings and its privacy rows. */
    Settings,

    /** Spare: a live trip, for the driving frame the 01-09 script used. */
    Driving,
    ;

    companion object {
        fun from(key: String?): ShotPreset? =
            entries.firstOrNull { it.name.equals(key?.trim(), ignoreCase = true) }
    }
}

/** Writes [this] preset onto the mock scenario + detection runtime. Always starts from `reset()`. */
fun ShotPreset.applyTo(scenario: MockScenario, runtime: MutableDetectionRuntimeState) {
    scenario.reset()
    when (this) {
        ShotPreset.Parked -> {
            scenario.ownParkedSession.value = true
            runtime.setPresence(ServicePresence.Sentry)
        }

        ShotPreset.Map -> Unit

        ShotPreset.Numbers -> {
            scenario.ownParkedSession.value = true
            scenario.isPlus.value = true
            runtime.setPresence(ServicePresence.Sentry)
        }

        ShotPreset.SharedCar -> {
            scenario.sharedCarJoined.value = true
            scenario.isPlus.value = true
        }

        ShotPreset.Settings -> Unit

        ShotPreset.Driving -> {
            scenario.activeVehicleBluetooth.value = true
            runtime.setRunning(true)
        }
    }
}
