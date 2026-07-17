package io.apptolast.paparcar.domain.diagnostics

/**
 * Port for recording [UiLocationSample]s from the Home map's consumer location stream. [UI-LOC-FOREGROUND-001]
 *
 * The presentation layer (HomeViewModel) depends only on this interface. The real implementation
 * (data layer) logs every sample **locally** (logcat, always — so a dev attached to the field device
 * sees fixes flow) and mirrors them **remotely** to Firestore behind the same opt-in flag as the
 * detection log (`diagnostics_config/{userId}.enabled`), throttling the high-rate [UiLocationSample.Kind.FIX]
 * samples so a map left open doesn't flood the collection. The default binding is [NoOpUiLocationLogger].
 *
 * **Contract:** [log] must never throw and must never block the caller — the real implementation buffers
 * and flushes off the hot path. UI behaviour must be identical whether logging is enabled or not.
 */
interface UiLocationLogger {

    /** Records a single [sample]. Fire-and-forget: never throws, never blocks on network. */
    fun log(sample: UiLocationSample)
}

/** Default no-op logger. Swallows every sample. Bound wherever UI-location diagnostics are off. */
class NoOpUiLocationLogger : UiLocationLogger {
    override fun log(sample: UiLocationSample) = Unit
}
