package io.apptolast.paparcar.domain.model

/**
 * How the spot was detected / reported.
 *
 * Used to derive an initial confidence value and the marker ring colour:
 * - [AUTO_DETECTED] via BT disconnect + GPS fix → HIGH confidence (green ring)
 * - [MANUAL_REPORT] tapped by the user on the map → confidence set explicitly
 */
enum class SpotType {
    AUTO_DETECTED,
    MANUAL_REPORT,
}
