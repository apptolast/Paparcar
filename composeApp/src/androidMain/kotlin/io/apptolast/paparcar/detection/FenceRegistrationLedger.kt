package io.apptolast.paparcar.detection

import io.apptolast.paparcar.domain.detection.fence.FenceRegistrationPolicy

/**
 * [DET-FENCE-REREGISTER-BY-CAUSE-001 §A] What this process has already registered, and when.
 *
 * The single place any lane asks "do I actually need to register this fence again?". Both
 * re-registration lanes go through it — the janitor's restoration passes and the safety net's cure —
 * so the answer cannot differ depending on who asks, which is the whole reason two workers
 * re-registering the same fences was a problem in the first place.
 *
 * **In memory on purpose.** A fresh process has no record and therefore always registers: a new
 * process is precisely the situation where the fences may have been wiped (force-stop, reboot, app
 * update) and there is no API — inside the process or out — to check. Persisting the ledger would
 * teach it to skip exactly the case it must never skip.
 *
 * Only SUCCESSFUL registrations are recorded. A failed attempt changed nothing in Play Services, so
 * it neither opened a blind window nor left a fence behind, and it must not stop the next attempt.
 */
object FenceRegistrationLedger {

    private val lastRegisteredAtMs = mutableMapOf<String, Long>()

    /**
     * @param hasKnownCause see [FenceRegistrationPolicy.shouldRegister] — a cause always wins over
     *   the dedup window.
     */
    @Synchronized
    fun shouldRegister(
        geofenceId: String,
        nowMs: Long,
        dedupWindowMs: Long,
        hasKnownCause: Boolean = false,
    ): Boolean = FenceRegistrationPolicy.shouldRegister(
        lastRegisteredAtMs = lastRegisteredAtMs[geofenceId],
        nowMs = nowMs,
        dedupWindowMs = dedupWindowMs,
        hasKnownCause = hasKnownCause,
    )

    /** Records a registration that SUCCEEDED. */
    @Synchronized
    fun recordSuccess(geofenceId: String, nowMs: Long) {
        lastRegisteredAtMs[geofenceId] = nowMs
    }

    /** Forgets a fence we deliberately removed, so a later re-park registers it without waiting. */
    @Synchronized
    fun forget(geofenceId: String) {
        lastRegisteredAtMs.remove(geofenceId)
    }

    /** Test seam. */
    @Synchronized
    internal fun reset() = lastRegisteredAtMs.clear()
}
