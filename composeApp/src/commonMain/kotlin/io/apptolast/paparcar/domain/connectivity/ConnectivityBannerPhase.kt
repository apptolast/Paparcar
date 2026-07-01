package io.apptolast.paparcar.domain.connectivity

/**
 * UI-facing phase of the root connectivity banner, decoupled from the raw [ConnectivityStatus]
 * so the banner can outlive the instantaneous status: a real Offline→Online transition surfaces a
 * transient [Restored] treatment before settling back to [Hidden]. Mirrors the phase pattern used
 * elsewhere (e.g. detection). [CONN-BANNER-001]
 *
 * - [Hidden]: online and steady — the banner takes no layout space.
 * - [Offline]: no reachability — persistent red banner while the cut lasts.
 * - [Restored]: reconnected just now — transient green banner that auto-hides.
 */
enum class ConnectivityBannerPhase {
    Hidden,
    Offline,
    Restored,
}
