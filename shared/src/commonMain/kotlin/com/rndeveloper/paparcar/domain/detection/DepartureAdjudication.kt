package com.rndeveloper.paparcar.domain.detection

/**
 * [DET-TWO-DISPATCHES-OF-ONE-DEPARTURE-READ-DIFFERENT-STATE-001 · fase 2] **One fact, one
 * adjudication.**
 *
 * A departure is a FACT — *(the fence that broke, the instant the first trigger dispatched it)* —
 * and the OS delivers it several times: an EXIT at the border, a far-delivered EXIT, an AR boarding,
 * and any safety-net wake that deduces it. Those are OBSERVATIONS of one fact, not new facts. Until
 * now each observation opened its own adjudication against state the previous one had already
 * consumed.
 *
 * ## Why this is not deduplication, which the data ruled out
 *
 * Field 2026-08-30 21:27:33.967 → 21:27:34.563 (Oppo): the same fence dispatched twice, 596 ms
 * apart, from two safety-net wakes. The first read `arrivalWalk=12` and planted the wrong pin; the
 * second read `arrivalWalk=0` and vetoed, which was right. *"Keep the first"* would have kept the
 * bad one; *"keep the last"* would be picking by arrival order, which IS the defect.
 *
 * The finding is that the two verdicts were never independent: **the first pass resealed the witness
 * slot, so the second measured a step budget the first had just spent.** The second was not better
 * informed — it was right as a side effect of the first having consumed its evidence. So a second
 * adjudication of the same fact adds no evidence by construction, and [Adhere] is not "discarding a
 * possibly better verdict": it is declining to re-derive a verdict from state that has been eaten.
 *
 * That also settles what happens to the witness reseal: nothing. It stays where it is, because once
 * no second adjudication of the same fact exists, there is nobody left to mislead — and every OTHER
 * session in that tick is a different fact, for which the fresh seal is the correct reading.
 *
 * ## The one thing that may re-open it: strictly more proof
 *
 * [Upgrade] exists because *preconfirmed* is not a matter of arrival order — it is a different
 * claim. A preconfirmed dispatch says *the trip is already OVER* (step budget / AR boarding /
 * exit∧enter / pedestrian physics), so the departure worker must skip the speed re-check that would
 * wrongly veto it. An observation carrying that proof when the open adjudication lacks it is
 * strictly more evidence about the same fact, so it is allowed through. The reverse — a plain
 * observation arriving after a preconfirmed one — adds nothing and adheres.
 *
 * ## Why the window, and why it is not a throttle
 *
 * [Adjudicate] returns for an expired adjudication because a fence can legitimately be left twice:
 * park, leave, come back, leave again. The window bounds "the same fact", not "how often we may
 * act" — it must outlive the dispatch chain's own retries (15/30/60 s) so a retry in flight is
 * never re-adjudicated underneath itself, and stay far below the interval at which a human parks
 * and departs the same fence again.
 */
sealed interface DepartureAdjudicationVerdict {
    /** No open adjudication for this fact (or the previous one expired): this observation owns it. */
    data object Adjudicate : DepartureAdjudicationVerdict

    /** The fact is already adjudicated and this observation carries nothing the open one lacks. */
    data object Adhere : DepartureAdjudicationVerdict

    /** Open, but this observation proves the trip already ENDED and the open adjudication did not. */
    data object Upgrade : DepartureAdjudicationVerdict
}

/**
 * The adjudication currently open for a departure fact.
 *
 * @param openedAtMs when the first observation of this fact dispatched it.
 * @param preconfirmed whether that dispatch already carried end-of-trip proof.
 */
data class OpenDepartureAdjudication(
    val openedAtMs: Long,
    val preconfirmed: Boolean,
)

/**
 * What a new observation of a departure fact may do. See [DepartureAdjudicationVerdict].
 *
 * @param open the adjudication already open for this fence, or null when none is.
 * @param nowMs this observation's instant.
 * @param observationPreconfirmed whether THIS observation carries end-of-trip proof.
 * @param windowMs how long an adjudication stays open — past it, the same fence breaking again is a
 *   new fact rather than a late observation of the old one.
 */
fun adjudicateDeparture(
    open: OpenDepartureAdjudication?,
    nowMs: Long,
    observationPreconfirmed: Boolean,
    windowMs: Long,
): DepartureAdjudicationVerdict = when {
    open == null -> DepartureAdjudicationVerdict.Adjudicate
    // A clock that went backwards (reboot, NTP correction) must not freeze a fence forever: an
    // adjudication we cannot date is an adjudication we cannot trust to still be open.
    nowMs < open.openedAtMs -> DepartureAdjudicationVerdict.Adjudicate
    nowMs - open.openedAtMs > windowMs -> DepartureAdjudicationVerdict.Adjudicate
    observationPreconfirmed && !open.preconfirmed -> DepartureAdjudicationVerdict.Upgrade
    else -> DepartureAdjudicationVerdict.Adhere
}
