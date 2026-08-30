package com.rndeveloper.paparcar.domain.model

import kotlinx.serialization.Serializable

/**
 * One position fix.
 *
 * [provider] and [satelliteCount] are **provenance, not measurement**: they say which world produced
 * the fix, so a diagnostic can tell a GNSS fix with bad geometry from a WiFi/cell one. Both are
 * nullable and default to null — every fix synthesised from storage, a test, or a preview honestly
 * has no provenance, and iOS has no equivalent to expose.
 * [DET-A-FIX-MUST-SAY-WHERE-IT-CAME-FROM-001]
 *
 * @property provider Platform provider string, verbatim (`gps` · `network` · `fused` · `passive`).
 *   ⚠️ Not trustworthy alone: the fused client labels almost everything `fused`. Kept raw so the
 *   label is derived from evidence rather than a guess being stored as fact.
 * @property satelliteCount Satellites used in the fix. Present **only** on GNSS-derived fixes, which
 *   makes it the real discriminator against a network fix — measured on the field devices, where the
 *   `gps` fix carries the count and the `network` one carries nothing.
 */
@Serializable
data class GpsPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long,
    val speed: Float,
    val provider: String? = null,
    val satelliteCount: Int? = null,
)
