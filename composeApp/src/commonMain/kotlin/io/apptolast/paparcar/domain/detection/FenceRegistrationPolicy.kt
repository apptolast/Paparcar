package io.apptolast.paparcar.domain.detection

/**
 * [DET-FENCE-REREGISTER-BY-CAUSE-001 §A] When is it worth registering a geofence we may already
 * have registered?
 *
 * Re-registering is not free. Play Services resets its internal INSIDE/OUTSIDE state to "unknown"
 * on every SUCCESSFUL registration, until its next evaluation — a blind window in which a
 * drive-away produces no EXIT at all (field 2026-07-11: a re-registration landed ~40 s before
 * drive-off and the departure was silent). So every redundant registration is a small hole punched
 * in exactly the trigger we are trying to protect.
 *
 * And they ARE redundant: measured on the Oppo, 2026-08-20, a single app start re-registered the
 * same fence **twice, 4.3 s apart** — `PaparcarApp` and the post-sync scheduler both enqueue the
 * janitor, and `ExistingWorkPolicy.REPLACE` does not dedupe a run that already finished. Two blind
 * windows in four seconds, every time the app is opened.
 *
 * ## Why this is a time window and NOT a distance gate
 *
 * The obvious guard — "don't re-register while the user is standing next to the car, that is when
 * the blind window hurts" — is **wrong, and dangerous**. After a force-stop Play Services has
 * ERASED the fences, and the app has no way to know that (there is no API, in or out of the
 * process, to ask whether a fence is still registered). Skipping the registration because the user
 * is near the car would leave that car with **no fence at all**, permanently, instead of a blind
 * window that closes by itself on the next fix. A temporary hole beats a permanent one.
 *
 * What is safe to skip is a registration we can PROVE is redundant: one we already performed
 * ourselves, moments ago, in this same process. That is the rule below.
 *
 * ## Process-scoped by design
 *
 * The ledger this consults lives in memory, so a fresh process starts with no record and registers
 * — which is exactly right: a new process is the situation in which the fences may have been wiped
 * (force-stop, reboot, update) and we must assume they were.
 */
object FenceRegistrationPolicy {

    /**
     * @param lastRegisteredAtMs when THIS process last registered this fence, or null if never.
     * @param nowMs now.
     * @param dedupWindowMs how long a registration is considered to still hold.
     * @param hasKnownCause true when something told us the fence is missing or its state is
     *   poisoned (a dismissed false EXIT, the return-anchor ENTER). A known cause always wins: the
     *   fence may well be registered and still be useless, which is precisely what the cure exists
     *   for, so "I registered it a minute ago" is not an argument against curing it now.
     */
    fun shouldRegister(
        lastRegisteredAtMs: Long?,
        nowMs: Long,
        dedupWindowMs: Long,
        hasKnownCause: Boolean = false,
    ): Boolean {
        if (hasKnownCause) return true
        val last = lastRegisteredAtMs ?: return true
        val elapsed = nowMs - last
        // A stamp from the future (clock jumped backwards) must never mute registration forever.
        if (elapsed < 0L) return true
        return elapsed >= dedupWindowMs
    }
}
