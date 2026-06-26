package io.apptolast.paparcar.domain.diagnostics

/**
 * Port for recording [DetectionEvent]s during a parking-detection session.
 *
 * The domain (Coordinator, use cases) depends only on this interface. The real implementation
 * (DET-LOG-02) lives in the data layer, writes to Firestore, and self-gates on a Firebase Remote
 * Config flag so only opted-in users emit traces. The default binding everywhere else is
 * [NoOpDetectionEventLogger], so instrumentation call-sites are always safe to invoke.
 *
 * **Contract:** [log] must never throw and must never block the caller on network I/O — the
 * real implementation buffers and flushes off the hot path. Detection logic must behave
 * identically whether logging is enabled or not.
 *
 * Implementations are registered as singletons so every writer (Coordinator, the receivers,
 * the BT detector) shares one buffering instance.
 */
interface DetectionEventLogger {

    /** Records a single [event]. Fire-and-forget: never throws, never blocks on network. */
    suspend fun log(event: DetectionEvent)
}

/**
 * Default no-op logger. Swallows every event. Bound in `commonMain` DI and used wherever remote
 * diagnostics are disabled, so the detection pipeline runs unchanged with logging off.
 */
class NoOpDetectionEventLogger : DetectionEventLogger {
    override suspend fun log(event: DetectionEvent) = Unit
}
