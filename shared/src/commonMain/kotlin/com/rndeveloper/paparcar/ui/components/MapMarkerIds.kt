package com.rndeveloper.paparcar.ui.components

/**
 * The grammar of map-marker `contentId`s, and the single place that reads it back.
 *
 * Every marker the map renders carries a `contentId` instead of a title: `Marker.title` is null on
 * all of them so Google Maps suppresses its default info-window balloon, which leaves the id as the
 * only thing a tap can be routed by (the object it refers to is then recovered by coordinates).
 * The id is also the key kmpmaps caches the rasterised bitmap under, so ids are built to encode
 * every visual variant — dim, selection, theme, doubt.
 *
 * Those two jobs used to live apart: the ids were built across a 2.000-line composable and read
 * back by a `when` chain inside a lambda, where nothing could call it. A prefix test then decided
 * a tap's destination by ACCIDENT of branch order — `my_car_asking_*` matched `my_car` first and
 * the open question's marker was routed to the parked-car branch, which found no session and did
 * nothing at all. [UI-THE-ASK-MARKER-TAP-NEVER-REACHES-ITS-HANDLER-001]
 *
 * So: the families are declared here, together, and [resolveMarkerTapTarget] is the one function
 * that maps an id to what a tap on it means. Two rules keep it honest, both enforced by
 * `MapMarkerIdsTest`:
 *  1. every id the map can emit resolves to its own family, and
 *  2. no family's prefix is a prefix of another family's id — which is what makes rule 1 hold
 *     regardless of the order the branches happen to be written in.
 */

// ── Family prefixes ─────────────────────────────────────────────────────────
// Parked cars: the badge marker (the normal surface, one per parked vehicle) and the legacy
// teardrop fallback (ParkingHistoryDetailScreen, which has no ParkedVehicleSummary to build a
// badge from). Both mean the same thing to a tap: select that session.
internal const val MARKER_VEHICLE_BADGE_PREFIX = "vehicle_badge_"
internal const val MARKER_MY_CAR = "my_car"
internal const val MARKER_MY_CAR_DIM = "my_car_dim"
internal const val MARKER_MY_CAR_SELECTED = "my_car_selected"

/**
 * [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] The place an OPEN "did you park?" question is
 * about. Its own contentId because kmpmaps caches bitmaps by it: the dashed frame and the `?` disc
 * must be baked in, exactly as the dim pass is, or the marker would never flip reliably.
 *
 * Deliberately NOT under [MARKER_MY_CAR]: the question is not a parked car, and while it lived in
 * that namespace every tap on it was swallowed by the parked-car branch. It is `parking_`, not
 * `spot_`, because what is in doubt is YOUR session — a spot is what the community sees.
 * [COPY-SPOT-IS-NOT-A-PARKING-001] [UI-THE-ASK-MARKER-TAP-NEVER-REACHES-ITS-HANDLER-001]
 */
internal const val MARKER_PARKING_ASK = "parking_ask"

internal const val MARKER_ZONE_PREFIX = "zone_"
internal const val MARKER_FREE_SPOT_PREFIX = "free_spot_"
internal const val MARKER_CLUSTER = "cluster"
internal const val MARKER_CLUSTER_DIM = "cluster_dim"

// ── Id builders shared by the marker list and its bitmap registry ────────────
// Both sides must agree exactly or kmpmaps finds no handler and renders the default pin, so
// neither builds its own string.

/** The open question's marker. Theme is part of the key: the tag fill is white in light, ink in dark. */
internal fun parkingAskContentId(themeKey: String): String = "${MARKER_PARKING_ASK}_$themeKey"

/** Per-zone × dim: each zone has two cached bitmaps. [MAP-MARKERS-DIM-002] */
internal fun zoneContentId(zoneId: String, isPrivate: Boolean, dim: Boolean): String =
    "$MARKER_ZONE_PREFIX${zoneId}_${if (isPrivate) "prv" else "pub"}_${if (dim) "dim" else "nrm"}"

// ── What a tap on a marker means ────────────────────────────────────────────

/** What the map should do when a marker is tapped. The object itself is recovered by coordinates. */
internal sealed interface MarkerTapTarget {
    /** A parked car — badge or legacy teardrop. Selects that session. */
    data object ParkedVehicle : MarkerTapTarget

    /** The place an unanswered "did you park?" is about. Frames it and opens the two answers. */
    data object ParkingAsk : MarkerTapTarget

    /** A saved zone. */
    data object Zone : MarkerTapTarget

    /** A free spot published by the community. */
    data object FreeSpot : MarkerTapTarget

    /**
     * Deliberately unhandled: clusters (tapping one would be ambiguous — zoom in instead), the
     * trip's departure/arrival dots, the "you" dot and the driving puck. All of them are read-only
     * scenery, and an id nobody claims lands here rather than being routed by accident.
     */
    data object Inert : MarkerTapTarget
}

/**
 * Maps a marker's `contentId` to what a tap on it means. Pure, total, and order-independent: the
 * families are disjoint by construction (see the class doc), so no branch can shadow another.
 */
internal fun resolveMarkerTapTarget(contentId: String?): MarkerTapTarget = when {
    contentId == null -> MarkerTapTarget.Inert
    contentId.startsWith(MARKER_PARKING_ASK) -> MarkerTapTarget.ParkingAsk
    contentId.startsWith(MARKER_VEHICLE_BADGE_PREFIX) ||
        contentId.startsWith(MARKER_MY_CAR) -> MarkerTapTarget.ParkedVehicle
    contentId.startsWith(MARKER_ZONE_PREFIX) -> MarkerTapTarget.Zone
    // Checked before the free-spot prefix only for readability; `cluster` shares no prefix with it.
    contentId == MARKER_CLUSTER || contentId == MARKER_CLUSTER_DIM -> MarkerTapTarget.Inert
    contentId.startsWith(MARKER_FREE_SPOT_PREFIX) -> MarkerTapTarget.FreeSpot
    else -> MarkerTapTarget.Inert
}
