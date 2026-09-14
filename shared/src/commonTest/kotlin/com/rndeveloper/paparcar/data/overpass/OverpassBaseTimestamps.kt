package com.rndeveloper.paparcar.data.overpass

import kotlin.time.Clock

/**
 * A base timestamp a fixture can call **healthy** without it rotting.
 * [OVERPASS-A-MIRROR-WALK-MUST-OUTLIVE-THE-SERVER-IT-ASKS-001]
 *
 * ⛔ Every Overpass fixture in the suite used to hardcode a date — `2026-07-15T15:22:01Z`,
 * `2026-09-09T21:00:00Z`, `2026-09-10T12:48:02Z`. That was harmless while the health check only
 * looked at the SHAPE of the string. The moment it started reading the AGE, those fixtures became
 * clocks: the July ones were already two months stale, and the September ones had days to live.
 *
 * A fixture that says "a healthy server answered" must mean it on every day it is ever run. So the
 * fixture states the PROPERTY — fresh — and lets the date follow, which is the same move this
 * ticket made on the walk's patience: derive the number, do not carve it.
 *
 * Its opposite, [monthsStale], is carved on purpose: that one is a fixed historical fact — what
 * `overpass.private.coffee` really served on 14-09-2026 — and a fact must not drift.
 */
internal fun freshBase(): String = Clock.System.now().toString()

/** What the first fallback served on 14-09-2026: a real date, 105 days behind. */
internal const val MONTHS_STALE_BASE = "2026-06-01T08:52:28Z"
