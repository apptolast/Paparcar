package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.GpsPoint

/**
 * Where a fix came from, said in one token a diagnostic can group on.
 * [DET-A-FIX-MUST-SAY-WHERE-IT-CAME-FROM-001]
 *
 * Field 29→30-08-2026 (Redmi Note 11): thirty-eight consecutive fixes were discarded by
 * `minGpsAccuracyForDriving` across eleven minutes of real driving, accuracies running 59 → 245 m,
 * and the log could not say whether they were GNSS with bad geometry or network fixes that should
 * never have carried a driving speed at all. The parking survived only as a 250 m zone. This
 * function is what makes that question answerable from the log alone.
 *
 * ⛔ It reads nothing but provenance and decides nothing. No guard consumes it, no threshold moves.
 * What a network fix is allowed to prove is a separate question, to be settled with a week of field
 * data rather than with the reflex that produced this ticket.
 *
 * The satellite count outranks the provider string on purpose: the fused client labels almost every
 * fix `fused`, while a satellite count is only ever present on a GNSS-derived one.
 */
fun GpsPoint.provenanceLabel(): String {
    // A count present at all — zero included — is itself the proof that a satellite engine answered.
    satelliteCount?.let { return "gnss(${it}sat)" }
    val named = provider?.lowercase()?.takeIf { it.isNotBlank() } ?: return UNKNOWN
    return if (named == PROVIDER_GPS) GNSS else named
}

private const val PROVIDER_GPS = "gps"
private const val GNSS = "gnss"
private const val UNKNOWN = "?"
