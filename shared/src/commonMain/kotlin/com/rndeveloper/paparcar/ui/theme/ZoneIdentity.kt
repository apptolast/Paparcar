package com.rndeveloper.paparcar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * THE single source of colour for a saved zone across every surface — header chip, map centre
 * label, radius ring, and the pin you drag while placing one. [UI-COLOR-DOCTRINE-001 F6]
 *
 * Colour model: **a zone is BRAND green; a PRIVATE zone goes neutral and grows a lock.** With
 * `tertiary` retired there is no third hue to spend on "private", and there shouldn't be: private
 * is a CATEGORY, and a category is the glyph's job — the padlock says it, the colour just stops
 * claiming the zone is a place the community can be sent to.
 *
 * The contract has two halves and both are mandatory wherever there is room for them: the neutral
 * tint from here, AND a trailing `Icons.Rounded.Lock` in the same tint. Surfaces with no room for a
 * glyph (the placement pin, the radius ring) carry the tint alone.
 *
 * Written as a resolver — not repeated per call site — because it already drifted: the map label
 * and the ring honoured it while the header chip and the placement pin kept painting `primary`, so
 * the same zone read green-without-lock and grey-with-lock in one screenshot.
 * [UI-A-PRIVATE-ZONE-IS-PRIVATE-ON-EVERY-SURFACE-001]
 */
@Composable
fun zoneIdentityColor(isPrivate: Boolean): Color =
    if (isPrivate) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.primary

/**
 * Ring colour for the zone's radius circle — the same two-legged rule as [zoneIdentityColor], but
 * the private leg lands on `outline` rather than `onSurfaceVariant`.
 *
 * That difference is deliberate, not drift: the ring is a translucent WASH over map tiles (see the
 * fill/stroke alphas at the call site), while [zoneIdentityColor] tints glyphs and hairlines that
 * sit on an opaque pill. `onSurfaceVariant` at those alphas turns to fog over a dark basemap.
 */
@Composable
fun zoneRingColor(isPrivate: Boolean): Color =
    if (isPrivate) MaterialTheme.colorScheme.outline
    else MaterialTheme.colorScheme.primary
