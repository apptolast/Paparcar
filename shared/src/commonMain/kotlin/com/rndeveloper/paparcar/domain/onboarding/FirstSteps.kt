package com.rndeveloper.paparcar.domain.onboarding

/**
 * The three first steps a new user takes, in the order the product needs them.
 * [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001]
 *
 * The order is not cosmetic: [MARK_PARKING] is what arms everything else, so until it is done the
 * other two have nothing to talk about — [UNDERSTAND_WATCH] describes what happens to a parking
 * that does not exist yet, and [FIND_SPOT] asks the user to care about a community they have not
 * fed yet.
 */
enum class FirstStep {
    /** Mark where the car is left. Creates the session, the fence and the watch. */
    MARK_PARKING,

    /** See that the app is actually watching that parking, and learn what happens on departure. */
    UNDERSTAND_WATCH,

    /** Meet the community side: a free spot someone else left, or one you report. */
    FIND_SPOT,
}

/**
 * Where the user stands in the guided first steps.
 *
 * @param done every step already completed — the persisted latch UNION the steps whose live signal
 *   is satisfied right now. Both halves are needed: the latch alone would never grow, and the live
 *   signal alone would UN-complete a step the moment its state went away (releasing a parking would
 *   walk step 1 backwards).
 * @param current the step being asked for, or null when there is nothing left to do.
 * @param isComplete all three done — the checklist earns its closing card instead of vanishing
 *   mid-gesture.
 */
data class FirstStepsProgress(
    val done: Set<FirstStep>,
    val current: FirstStep?,
    val isVisible: Boolean,
) {
    val isComplete: Boolean get() = done.size == FirstStep.entries.size
}

/**
 * Pure projection (persisted latch × live product state) → the one [FirstStepsProgress] to render.
 * Lives here, not in the composable, so the "when is a step done" rule is unit-testable — the same
 * discipline `resolveDetectionStory` follows. [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001]
 *
 * **A step completes on MEASURED STATE, never on a tap.** Pressing "Mark parking" teaches nothing;
 * having the car marked does. This mirrors the detection doctrine one layer up: the event nominates,
 * the measured state confirms.
 *
 * @param done the persisted latch — steps already banked, which never un-complete.
 * @param dismissed the user closed the checklist (skipped it, or acknowledged the closing card).
 * @param hasActiveSession there is at least one live parking session → [FirstStep.MARK_PARKING].
 * @param isWatching detection is genuinely covering that parking → [FirstStep.UNDERSTAND_WATCH].
 *   Honest by construction: the caller passes the badge-backed watch state, so the step cannot be
 *   ticked by a watch the OS already killed. [DET-WATCH-HONEST-001]
 * @param hasTouchedSpots the user opened a community spot or reported one → [FirstStep.FIND_SPOT].
 */
fun resolveFirstSteps(
    done: Set<FirstStep>,
    dismissed: Boolean,
    hasActiveSession: Boolean,
    isWatching: Boolean,
    hasTouchedSpots: Boolean,
): FirstStepsProgress {
    val live = buildSet {
        if (hasActiveSession) add(FirstStep.MARK_PARKING)
        if (isWatching) add(FirstStep.UNDERSTAND_WATCH)
        if (hasTouchedSpots) add(FirstStep.FIND_SPOT)
    }
    val effective = done + live
    return FirstStepsProgress(
        done = effective,
        // The FIRST unfinished step in declaration order — never "the next one after the last done".
        // A user who reports a spot before parking has step 3 banked and is still asked for step 1,
        // which is the right ask: nothing is watching their car yet.
        current = FirstStep.entries.firstOrNull { it !in effective },
        isVisible = !dismissed,
    )
}
