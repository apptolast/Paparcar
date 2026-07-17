package io.apptolast.paparcar.domain.diagnostics

/**
 * One observability sample of the **consumer** location stream that paints the user dot on the Home
 * map. Distinct from [DetectionEvent] (which traces a parking-detection *session*): this measures the
 * plain "where am I on the map" stream, whose cadence + accuracy is exactly what [UI-LOC-FOREGROUND-001]
 * changed — high-accuracy while the map is foreground, stopped otherwise.
 *
 * Purpose: prove the fix on the device that misbehaved. Reading a stream of [Kind.FIX] samples shows
 * the inter-fix gap ([sinceLastFixMs]) and [accuracy] dropping from the old ~30 s / coarse balanced
 * fixes to a few seconds / fine — and the [Kind.SUBSCRIBED]/[Kind.STOPPED] pair shows the foreground
 * scoping actually starting and stopping the request (battery bound).
 *
 * All fields are primitives/strings so the wire form tolerates new [priority] tiers without breaking
 * deserialization; analysis is downstream.
 */
data class UiLocationSample(
    /** Wall-clock epoch-ms the sample was produced (for [Kind.FIX] this is the GPS fix time). */
    val timestampMs: Long,
    val kind: Kind,
    /** Whether the map was foreground (RESUMED) when the sample was produced. */
    val foreground: Boolean,
    /** The [android LocationRequest] priority behind the active request, e.g. "HIGH_ACCURACY". */
    val priority: String,
    /** Fix accuracy in metres; null for lifecycle samples ([Kind.SUBSCRIBED]/[Kind.STOPPED]). */
    val accuracy: Float? = null,
    /** Millis since the previous [Kind.FIX] in this subscription; null on the first fix / lifecycle. */
    val sinceLastFixMs: Long? = null,
    /** Ground speed in m/s at the fix, when known. */
    val speed: Float? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    /** Whether this sample is the start/stop of a request or an actual fix in between. */
    enum class Kind { SUBSCRIBED, FIX, STOPPED }
}
