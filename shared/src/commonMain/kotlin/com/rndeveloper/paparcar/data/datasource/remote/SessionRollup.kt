package com.rndeveloper.paparcar.data.datasource.remote

import kotlin.math.roundToInt

/**
 * [DET-A-SESSION-ROLLUP-MUST-USE-THE-NUMBERS-THE-VERDICT-USED-001] The per-session digest the
 * diagnostics header carries — and the one line a field diagnosis reads FIRST.
 *
 * ## Why this is a class of its own now
 *
 * It was a private accumulator inside [FirestoreDetectionEventLogger] with its arithmetic spread
 * across two of its methods, so the one thing worth pinning — *does this digest agree with the
 * verdict beside it?* — had no way to be asked. Firestore is not needed to answer that question;
 * only these numbers are. Consumer-thread confined, like the map that holds it.
 *
 * ## The rule the digest broke
 *
 * A session summary that reports a number the DECISION never used is not a rounding difference, it
 * is a contradiction printed at the top of the page. Field 2026-08-16 (Samsung SM-A536B, session
 * `1786873042480`): the header said `vmax 80km/h` and the outcome said
 * `aborted_unattended_no_drive`, and reconciling the two cost an entire diagnosis. Both were
 * right: the 80 km/h fix carried 180 m of accuracy, so the detector never admitted it — every
 * confirm path reads the accuracy-gated statistic, and the header was printing the ungated one.
 *
 * The same trap was already half-closed for the fix COUNT ([drivingFixes] vs
 * [credibleDrivingFixes], [DET-THE-EVIDENCE-MUST-REACH-THE-TRACE-001]) and left open for the peak,
 * which is the number a human actually quotes. `HumanPoweredRideTest` even writes it down —
 * *"the summary said vmax 40 km/h, but its best CREDIBLE fix was 21,3 — the peak is a rumour"*.
 *
 * So both are kept and both are printed, deliberately: the raw peak still describes what the
 * receiver claimed (and a wild one is itself a symptom worth seeing), while the credible peak is
 * the one any verdict can be held to. Dropping either would trade one blind spot for another.
 */
internal class SessionRollup(val startedAt: Long = 0L) {
    var fixCount = 0
        private set

    /** Highest speed any fix CLAIMED, accuracy notwithstanding. A rumour, kept as one. */
    var maxSpeedKmh = 0f
        private set

    /** Highest speed a fix claimed while its accuracy was good enough for the detector to believe
     *  it — the peak every confirm decision could actually have used. */
    var credibleMaxSpeedKmh = 0f
        private set

    /** ⛔ NO accuracy gate, and never had one — it counts every fix above the speed bar, however
     *  wild its accuracy. Kept as it was so old and new sessions stay comparable, but it is NOT
     *  what `DriveProof.credibleFixCount` means. [DET-THE-EVIDENCE-MUST-REACH-THE-TRACE-001] */
    var drivingFixes = 0
        private set

    /** The gated count, which IS the one every confirm decision reads. */
    var credibleDrivingFixes = 0
        private set

    var maxStepCount = 0
        private set
    var finalLat: Double? = null
        private set
    var finalLon: Double? = null
        private set

    /**
     * Fold one location fix into the digest.
     *
     * @param accuracyMeters null when the fix carried no accuracy — treated as NOT credible, since
     *   a fix that cannot say how wrong it might be is exactly what the gate exists to hold back.
     */
    fun onFix(
        speedKmh: Float,
        accuracyMeters: Float?,
        latitude: Double?,
        longitude: Double?,
        drivingBarKmh: Float,
        accuracyGateMeters: Float,
    ) {
        fixCount++
        if (speedKmh > maxSpeedKmh) maxSpeedKmh = speedKmh
        val credible = accuracyMeters != null && accuracyMeters <= accuracyGateMeters
        if (credible && speedKmh > credibleMaxSpeedKmh) credibleMaxSpeedKmh = speedKmh
        if (speedKmh >= drivingBarKmh) {
            drivingFixes++
            if (credible) credibleDrivingFixes++
        }
        if (latitude != null && longitude != null) {
            finalLat = latitude
            finalLon = longitude
        }
    }

    fun onStep(stepCount: Int) {
        if (stepCount > maxStepCount) maxStepCount = stepCount
    }

    /**
     * The one-line digest, mirrored to both Firestore and logcat.
     *
     * Every claimed number is printed next to the admissible one it can contradict, so the reader
     * never has to reconcile the header with the verdict by hand.
     */
    fun summary(outcome: String, endedAtMs: Long): String {
        val parts = mutableListOf(outcome)
        if (startedAt > 0L) parts += "${round1((endedAtMs - startedAt) / 60_000.0)}min"
        parts += "vmax ${maxSpeedKmh.roundToInt()}km/h (cred ${credibleMaxSpeedKmh.roundToInt()})"
        parts += "drive $drivingFixes/${fixCount}fix (cred $credibleDrivingFixes)"
        parts += "steps $maxStepCount"
        val lat = finalLat
        val lon = finalLon
        if (lat != null && lon != null) parts += "end ${round5(lat)},${round5(lon)}"
        return parts.joinToString(" · ")
    }
}

private fun round1(v: Double): Double = (v * 10).roundToInt() / 10.0

private fun round5(v: Double): Double = (v * 100_000).roundToInt() / 100_000.0
