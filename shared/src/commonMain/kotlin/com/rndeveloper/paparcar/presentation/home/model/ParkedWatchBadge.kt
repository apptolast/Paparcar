package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.detection.ServicePresence

/**
 * Honest status badge for the parked/active vehicle's departure watch. [DET-WATCH-HONEST-001]
 *
 * "Vigilando tu sitio" must be TRUE, not aspirational: it may only show when the watch is genuinely
 * live. The old surface derived "Parked → watching" from a session merely existing, so it kept
 * claiming to watch even after an OEM killed the resident foreground service — a silent false
 * negative dressed as a green badge.
 *
 * This is a pure decision over the real signals so it is unit-testable and platform-free; the UI
 * renders the chosen badge and its action. It is scoped to the "detection applies" context (there is
 * a parking-capable active vehicle, detection is ON, permissions granted) — the caller resolves the
 * NoVehicle / Blocked / Inactive / Silent cases upstream via [DetectionUiState].
 */
enum class ParkedWatchBadge {
    /** No parked session for a car that could park → invite the user to mark where they left it. */
    PARK_MY_VEHICLE,

    /** Parked and genuinely watching (FGS resident/active, or the Bluetooth receiver covers it) on a
     *  reliable setup → the real "Vigilando tu sitio". */
    WATCHING,

    /** Parked and watching, but background survival is fragile (no battery exemption on an aggressive
     *  OEM) → still watching for now, but warn + offer the exemption so it doesn't get killed. */
    WATCHING_FRAGILE,

    /** Parked under COORDINATOR but the foreground service is DEAD — the OS killed the watcher (or it
     *  hasn't been (re)started). Not watching → offer to reactivate (a foreground start is legal). */
    WATCH_INTERRUPTED,
}

/**
 * Resolve the honest watch badge from the real detection signals.
 *
 * @param hasParkedSession whether the active/parked vehicle has a live parking session.
 * @param isBluetoothCovered whether the parked vehicle is BT-paired — its departure (the reconnect
 *   when the user gets back in) is caught by the manifest ACL receiver with no resident FGS, so it is
 *   honestly "watching" regardless of [presence]. Fix-independent of the connected-gate resolver.
 * @param presence the Coordinator foreground service's real lifecycle. Only [ServicePresence.Sentry]
 *   or [ServicePresence.Active] mean the departure watch is actually live.
 * @param isReliabilityReduced true when the setup is fragile (no BT + no battery exemption + aggressive
 *   OEM) — the watch may be killed at any time. Never true for a Bluetooth-covered car.
 */
fun resolveParkedWatchBadge(
    hasParkedSession: Boolean,
    isBluetoothCovered: Boolean,
    presence: ServicePresence,
    isReliabilityReduced: Boolean,
): ParkedWatchBadge = when {
    !hasParkedSession -> ParkedWatchBadge.PARK_MY_VEHICLE
    // Bluetooth: the reconnect is caught by the manifest receiver independently of any FGS, so the
    // watch is live without a resident process — honestly "watching". [DET-BT-CONNECTED-NOT-PAIRED-001]
    isBluetoothCovered -> ParkedWatchBadge.WATCHING
    // Coordinator: the watch is only real while the foreground service is alive.
    presence == ServicePresence.Dead -> ParkedWatchBadge.WATCH_INTERRUPTED
    isReliabilityReduced -> ParkedWatchBadge.WATCHING_FRAGILE
    else -> ParkedWatchBadge.WATCHING
}
