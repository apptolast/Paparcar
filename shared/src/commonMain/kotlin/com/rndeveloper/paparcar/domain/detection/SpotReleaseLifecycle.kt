package com.rndeveloper.paparcar.domain.detection

/**
 * [DET-A-RELEASING-SPOT-MUST-SAY-IT-WENT-OUT-001] What became of a boarding's claim on the plaza the
 * boarded car still holds — the whole life of a
 * [com.rndeveloper.paparcar.domain.model.SpotStatus.RELEASING] publication in one vocabulary.
 *
 * Modelled on [HoldAction] for the same reason it exists: a phase that opens, waits and resolves
 * several different ways is untestable in the field while its branches are local-log only. This one
 * was worse than the hold — **every** branch was mute in remote, so a field-test of the lane could
 * not distinguish "we published and something took it down" from "we never published" from "we
 * refused, and here is why". Measured on the 27-09-2026 headphone test: the night's whole trace was
 * in Firestore and not one event of this lane was.
 *
 * ⛔ The reason vocabulary is NOT repeated here. The refusals belong to
 * [com.rndeveloper.paparcar.domain.usecase.spot.EvaluateSpotReleaseNominationUseCase] and travel as
 * the event's free-form `reason`, exactly as they are: a second enumeration would be a second place
 * to drift, and the wire format tolerates new literals on purpose.
 */
enum class SpotReleaseAction {

    /** A releasing spot went out: somebody boarded a car that still holds a plaza. */
    NOMINATED,

    /** The boarding nominated nothing, and `reason` says which refusal. */
    SKIPPED,

    /** The nomination came down because the boarding never became a departure. */
    WITHDRAWN,

    /**
     * A withdrawal was asked for past the releasing TTL, so nothing was written.
     *
     * Told apart from [WITHDRAWN] because they answer different questions: this one means the spot
     * expired on its own and the retraction never reached the wire —
     * [DET-RETRACT-DENIED-FOREVER-001] is the whole reason that branch exists, and a lane that
     * reports it as a retraction would hide the denial storm it was built to stop.
     */
    WITHDRAW_EXPIRED,

    /**
     * The retraction was attempted inside the window and the wire refused it (rules denial, no
     * network). `reason` carries what came back.
     *
     * Told apart from [WITHDRAWN] because the plaza is **still standing**: reporting a failed
     * retraction as a retraction is exactly the kind of trace that sends a field diagnosis looking
     * for a phantom it has already been told was taken down.
     */
    WITHDRAW_FAILED,

    /**
     * The real departure arrived and re-published the SAME document as a true spot. The nomination
     * was promoted, not abandoned — the only ending in which the boarding did its job.
     *
     * Without this the successful case is silent and the lane reads as a funnel with no bottom:
     * every published nomination looks unresolved until its TTL runs out.
     */
    PROMOTED,
}

/**
 * [DET-A-RELEASING-SPOT-MUST-SAY-IT-WENT-OUT-001] Who asked — the two lanes that can nominate or
 * withdraw a releasing plaza, as the trace spells them.
 *
 * They share a host since [DET-ONE-SERVICE-TWO-LANES-001] but not a job: the Bluetooth half runs in
 * `btWatchJob`, the assisted half in the coordinator's detection job. That is exactly why the label
 * has to travel — **a withdrawal with no author cannot be told from one lane taking down the
 * other's nomination**, which is the open question this vocabulary is here to settle in the field.
 *
 * ⛔ Strings, not an enum, deliberately: they land in the event's free-form `source` column
 * alongside `cure`, `janitor:periodic`, `worker` and `pre-arm`, and that column's contract is that
 * new literals may appear without breaking anyone's deserialization.
 */
object SpotReleaseLane {

    /** The paired car's ACL CONNECT — the deterministic boarding. */
    const val BT = "bt"

    /** An AR `IN_VEHICLE` ENTER inside the parked car's own fence, or that trip's own teardown. */
    const val COORDINATOR = "coordinator"

    /** The detected departure, promoting the document it finds. */
    const val DEPARTURE = "departure"

    /**
     * [DET-A-WITHDRAWAL-MUST-NAME-WHAT-IT-TAKES-BACK-001] What a stamp written before lanes were
     * recorded reads as. Any lane may withdraw one: the alternative leaves a phantom standing across
     * the app update with nothing but the four-minute TTL able to reach it.
     */
    const val LEGACY = "legacy"

    /** The user's own "Me voy", promoting it. Told apart from [DEPARTURE] because the head start
     *  was then earned by a declaration and not by a measurement. */
    const val USER = "user"
}
