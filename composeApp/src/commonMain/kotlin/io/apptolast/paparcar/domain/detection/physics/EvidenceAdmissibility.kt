package io.apptolast.paparcar.domain.detection.physics

/**
 * [DET-SESSION-BIRTH-001] Can this piece of evidence speak about **this** parking session at all?
 *
 * Evidence older than the session itself describes the trip that CREATED the parking (or an earlier
 * life entirely) — it can never prove a departure FROM it. Field 2026-07-08 18:52 (Redmi): MIUI
 * re-delivered the inbound drive's `IN_VEHICLE ENTER` (true time 17 min old) seconds after the park;
 * 23 s later the fresh fence's *walking* EXIT was "verified" by it and a correct parking was erased.
 *
 * One filter applied at every evidence source kills the whole class. It lived as five hand-written
 * comparisons across four use cases — one of them spelled inside-out (`<` + reject rather than `>=`
 * + admit) — which is four chances for the next edit to fix one and miss the rest.
 *
 * **The two nulls mean opposite things, and that is deliberate:**
 *  - `evidenceAtMs == null` → there is no evidence, so there is nothing to admit → **false**.
 *    Fail closed: absent evidence must never license a release.
 *  - `sessionStartMs == null` → the session's birth is unknown, so nothing here can refute the
 *    evidence → **true**. Fail open: an unknown birth is not grounds to throw away a real signal;
 *    the caller's own guards still have to clear it.
 *
 * ⚠️ **Not every `x >= sessionStart` in this subsystem is this predicate.** The BT identity gate
 * (`EvaluateSafetyNetCheckUseCase` [DET-BT-IDENTITY-GATE-001]) has the same shape and asks a
 * different question — *did this car's Bluetooth connect at or after the park* — and its null means
 * the opposite (absent connection ⇒ gated). It is deliberately NOT routed through here.
 *
 * @param evidenceAtMs when the evidence happened, in epoch-ms **true time** (not delivery time —
 *        an OEM re-delivery is exactly what this guard exists to catch).
 * @param sessionStartMs when the parking session began, epoch-ms, or null when unknown.
 */
fun isAdmissibleEvidence(evidenceAtMs: Long?, sessionStartMs: Long?): Boolean {
    if (evidenceAtMs == null) return false
    if (sessionStartMs == null) return true
    return evidenceAtMs >= sessionStartMs
}
