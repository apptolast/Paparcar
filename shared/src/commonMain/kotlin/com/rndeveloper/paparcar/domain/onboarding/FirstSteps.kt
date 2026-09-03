package com.rndeveloper.paparcar.domain.onboarding

/**
 * The first steps a new user takes, in the order the product needs them.
 * [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001]
 *
 * The order is not cosmetic: [MARK_PARKING] is what arms everything else, so until it is done the
 * others have nothing to talk about — [UNDERSTAND_WATCH] describes what happens to a parking
 * that does not exist yet, and [FIND_SPOT] asks the user to care about a community they have not
 * fed yet.
 *
 * Not every step is on every user's list: [FORTIFY_WATCH] only exists when there is something to
 * reinforce. "Which steps apply" is a question for the projection, never for a composable — see
 * `FirstStepsProgress.applicable`. [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
 */
enum class FirstStep {
    /** Mark where the car is left. Creates the session, the fence and the watch. */
    MARK_PARKING,

    /** See that the app is actually watching that parking, and learn what happens on departure. */
    UNDERSTAND_WATCH,

    /**
     * Make that watch SOLID — the step that only exists when the watch is real but fragile.
     * [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
     *
     * Separate from [UNDERSTAND_WATCH] because "is it on?" and "is it robust?" are two questions,
     * and the system has always answered them apart (permission tiers, `WATCHING` vs
     * `WATCHING_FRAGILE`, the surface's own fortify row). Fusing them is what let a battery
     * exemption the user declined lock the whole checklist.
     */
    FORTIFY_WATCH,

    /** Meet the community side: a free spot someone else left, or one you report. */
    FIND_SPOT;

    /**
     * Whether ENGAGING with this step is doing it — reading its explanation, or opening the flow it
     * teaches, whether or not anything came of it.
     * [ONBOARDING-THE-COMMUNITY-STEP-CANNOT-DEMAND-A-SPOT-001]
     *
     * True only for steps of KNOWLEDGE. [FIND_SPOT] teaches that the community half exists: that
     * spots others leave show on the map, and that a free space you see can be reported. Having read
     * that IS knowing it. Demanding a published spot instead left a user with none nearby and no
     * free space to report unable to ever close the step — the only ways out being a false report,
     * which dirties the map for everyone, or a checklist open forever.
     *
     * Its CTA counts too, and for the same reason: pressing "report a spot" puts the user IN the
     * reporting flow — the pin, the map, the whole gesture — which shows them what it is even if
     * they back out. That is knowing it. What would NOT be knowledge is ticking the step for a tap
     * on a row nobody read, which is why the door into the explanation is a labelled button and not
     * a hidden touch area.
     *
     * False for every step that is an ACT — marking the car, turning detection on, granting the
     * exemption. Reading about them does not do them, and [MARK_PARKING] in particular is REQUIRED:
     * it is what arms everything else, and it completes only with a parking that really exists,
     * whether it was marked from the checklist or from the app's normal flow.
     *
     * The whole projection exists because *a step completes on measured state, never on a tap*. This
     * property is the ONE narrow exception, and it is narrow because what this step measures is what
     * the user KNOWS.
     *
     * A new step has to declare which kind it is HERE, not inherit one from a `when` elsewhere.
     */
    val completesOnEngage: Boolean get() = this == FIND_SPOT
}

/**
 * What would make the active vehicle's watch solid, if anything.
 * [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
 *
 * Ranked, not listed: [BLUETOOTH] comes first because it is the stronger fix AND it makes the other
 * one unnecessary — a Bluetooth-covered car is watched by the manifest receiver, with no resident
 * process to keep alive, which is exactly why `resolveParkedWatchBadge` reads it as `WATCHING`
 * without asking about battery at all. Asking for a battery exemption to prop up a watch that
 * Bluetooth would make deterministic is asking for the wrong thing.
 */
enum class WatchReinforcement {
    /** Nothing to do: Bluetooth covers this car, or the watch is already unrestricted. */
    NONE,

    /** The car has no MAC linked yet. One tap picks it from the phone's already-paired devices. */
    BLUETOOTH,

    /** Coordinator watch running under battery restrictions — it can be killed mid-parking. */
    BATTERY,
}

/**
 * Which of its two faces [FirstStep.UNDERSTAND_WATCH] is wearing.
 * [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001]
 *
 * The step explains that leaving frees your spot for someone else. That is only TRUE while automatic
 * detection is on: with it off, nothing notices you drove away and the spot is only freed when you
 * say so. Telling every user the app "watches your car" would be a promise the app is not keeping,
 * and this is the app's own step about itself — the worst possible place to say something untrue.
 *
 * So when the watch is down the step stops describing and starts ASKING, which also makes it the
 * natural second moment to invite the user to turn detection on. The half that is always true — you
 * can always close your parking yourself — stays in both faces.
 */
enum class WatchAsk {
    /** Detection is available: explain the two ways a parking frees a spot. */
    EXPLAIN_RELEASE,

    /** Detection is stopped: say what actually happens today, and offer to turn it on. */
    TURN_IT_ON,
}

/**
 * Which story of the detection surface the checklist is currently SPEAKING FOR, if any.
 * [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001]
 *
 * The surface below the checklist owns exactly the same two asks in its own rows ("mark your
 * parking", "turn on detection"), and two rows asking for one action is the drift
 * `resolveDetectionStory` exists to prevent. Whichever ask the checklist's CURRENT step is making,
 * the matching row stands down — and only that row. A step can only be one step, so this is one
 * value and not a bag of booleans.
 */
enum class FirstStepsOwnership {
    NOTHING,

    /** The checklist is asking for the first parking → `DetectionStory.AwaitingFirstPark` stands down. */
    COLD_START,

    /** The checklist is asking to turn detection on → `DetectionStory.Inactive` stands down. */
    DETECTION_OFF,

    /**
     * The checklist is asking to make the watch solid → the fragile-watch line stands down.
     * [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
     *
     * Only the FRAGILE variant, and only while the step is the current ask. An INTERRUPTED watch (a
     * killed service) is a different, louder thing and is never taken over. [DET-WATCH-HONEST-001]
     */
    WATCH_FRAGILE,
}

/**
 * Which of its two faces [FirstStep.FIND_SPOT] is wearing.
 * [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001]
 *
 * The community half of the product has a chicken-and-egg problem the tutorial cannot talk its way
 * out of: on day one there are no spots, because there are no users yet. Telling a brand-new user to
 * "see spots" and handing them an empty map teaches them the app is empty — and the button underneath
 * opened the REPORT flow anyway, so it did not even do what it said.
 *
 * So the step asks for whichever half is actually available:
 *  - [SEE_NEARBY] — somebody has freed a spot nearby: show it to them.
 *  - [REPORT_ONE] — nothing on offer: teach the gesture that does not depend on anyone else, and that
 *    is what creates the density the other face needs.
 */
enum class FindSpotAsk {
    /** There are spots on offer → take the user to them. */
    SEE_NEARBY,

    /** Nothing on offer → ask them to report one they see from the street. */
    REPORT_ONE,
}

/**
 * What, if anything, would make the active vehicle's watch solid.
 * [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
 *
 * A pure predicate shared by the projection and its tests — it decides nothing about the checklist,
 * it just answers the question the fortify step is about. [DET-VERDICT-NOT-PREDICATE-001]
 *
 * @param vehicleHasBluetoothMac the active car already has a MAC linked in Paparcar.
 * @param hasPairedBluetoothDevices the PHONE has at least one bonded device to pick that MAC from. Without
 *   this the Bluetooth ask would send the user to an empty list — linking a car in Paparcar means
 *   choosing among devices the phone is already paired with, not pairing anything new.
 * @param isReliabilityReduced the coordinator watch is running under battery restrictions.
 */
fun resolveWatchReinforcement(
    vehicleHasBluetoothMac: Boolean,
    hasPairedBluetoothDevices: Boolean,
    isReliabilityReduced: Boolean,
): WatchReinforcement = when {
    // A LINKED car needs nothing: its watch is the manifest receiver, with no resident process to
    // keep alive, which is why `resolveParkedWatchBadge` reads it as WATCHING without ever asking
    // about battery. Asking such a user for an exemption would be asking for something their watch
    // does not use — the same simplification the badge makes, made in the same place.
    vehicleHasBluetoothMac -> WatchReinforcement.NONE
    // Not linked, but linkable: offer the fix that makes the exemption unnecessary, not the one that
    // props up the weaker watch.
    hasPairedBluetoothDevices -> WatchReinforcement.BLUETOOTH
    isReliabilityReduced -> WatchReinforcement.BATTERY
    else -> WatchReinforcement.NONE
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
    /** Which face [FirstStep.FIND_SPOT] wears — see [FindSpotAsk]. Decided HERE, so no composable
     *  ever asks "are there spots?" to pick a copy. [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001] */
    val findSpotAsk: FindSpotAsk = FindSpotAsk.REPORT_ONE,
    /** Which face [FirstStep.UNDERSTAND_WATCH] wears — see [WatchAsk]. */
    val watchAsk: WatchAsk = WatchAsk.EXPLAIN_RELEASE,
    /**
     * The steps this user actually has. [FirstStep.FORTIFY_WATCH] drops out when there is nothing to
     * reinforce, so the checklist never shows a step whose CTA would have nothing to do.
     * [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
     */
    val applicable: Set<FirstStep> = FirstStep.entries.toSet(),
    /**
     * Steps the user answered with "not yet". They stop being [current] — the checklist moves on —
     * but they are NOT [done]: nothing was measured, so nothing is claimed. Persisted, because a
     * deferral that forgot itself would trap the user on the same step at the next cold start, which
     * is the very bug this ticket exists to remove.
     */
    val deferred: Set<FirstStep> = emptySet(),
    /** What would make the watch solid — picks [FirstStep.FORTIFY_WATCH]'s face. */
    val reinforcement: WatchReinforcement = WatchReinforcement.NONE,
) {
    /** Every step that applies is genuinely done. A deferred step is NOT done, so a checklist with a
     *  deferral never claims the user is "all set". */
    val isComplete: Boolean get() = applicable.all { it in done }

    /** How many steps this user has, for the "N of M" line — the applicable ones, never the enum's
     *  size: a user with nothing to reinforce has three steps, not four. */
    val total: Int get() = applicable.size

    /** Steps of [applicable] already banked — the numerator of that same line. */
    val doneCount: Int get() = applicable.count { it in done }

    /** True when the checklist has run out of things to ASK but something was left deferred: the
     *  closing card has to say so instead of "you're all set". */
    val hasDeferrals: Boolean get() = applicable.any { it in deferred && it !in done }

    /**
     * Which detection-surface row the checklist is speaking for right now — see
     * [FirstStepsOwnership]. Derived from the CURRENT step and its face, so it can never claim a row
     * the checklist is not actually asking for.
     *
     * It does not know whether the checklist is on screen at all (permissions and a registered
     * vehicle are the caller's gate); the caller reads [FirstStepsOwnership.NOTHING] when it is not.
     * [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001]
     */
    val owns: FirstStepsOwnership
        get() = when {
            current == FirstStep.MARK_PARKING -> FirstStepsOwnership.COLD_START
            current == FirstStep.UNDERSTAND_WATCH && watchAsk == WatchAsk.TURN_IT_ON ->
                FirstStepsOwnership.DETECTION_OFF
            // The fortify step says what the fragile-watch row says, so that row stands down while
            // the step is the one asking. [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
            current == FirstStep.FORTIFY_WATCH -> FirstStepsOwnership.WATCH_FRAGILE
            else -> FirstStepsOwnership.NOTHING
        }
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
 * @param hasSpotsOnOffer there is at least one community spot actually available nearby — the same
 *   list the sheet feeds its "PLAZAS LIBRES CERCA" section from, so the step can never offer to show
 *   spots that are not there. Picks the step's face, never whether it is done: SEEING a spot is not
 *   completing the step, OPENING one is. [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001]
 * @param isAutoDetectionStopped automatic detection is off / blocked and one tap can bring it back
 *   (`DetectionUiState.isDetectionStopped`). Picks [FirstStep.UNDERSTAND_WATCH]'s face, so the step
 *   never promises a departure watch that is not running. Not the same question as [isWatching],
 *   which asks whether a REAL parking is covered right now: with detection healthy but no car
 *   marked, there is nothing to watch and still nothing to fix.
 *   [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001]
 */
fun resolveFirstSteps(
    done: Set<FirstStep>,
    dismissed: Boolean,
    hasActiveSession: Boolean,
    isWatching: Boolean,
    hasTouchedSpots: Boolean,
    hasSpotsOnOffer: Boolean = false,
    isAutoDetectionStopped: Boolean = false,
    reinforcement: WatchReinforcement = WatchReinforcement.NONE,
    deferred: Set<FirstStep> = emptySet(),
): FirstStepsProgress {
    val live = buildSet {
        if (hasActiveSession) add(FirstStep.MARK_PARKING)
        // "Understanding the watch" is a car of yours BEING WATCHED — a marked parking plus
        // detection running. Two deliberate changes from what this used to be
        // [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]:
        //
        //  - it no longer rides on the strictest badge there is. A user who declined a battery
        //    exemption got `WATCHING_FRAGILE`, never finished this step, and every step after it was
        //    unreachable forever. How ROBUST that watch is belongs to the next step.
        //  - it still needs the parking, so the step cannot be born already ticked. Its whole job is
        //    to teach what happens when you leave, and a step that completes before the user has
        //    parked teaches nothing.
        //
        // The latch keeps it after the parking is released — the same reason step 1 has one.
        if (!isAutoDetectionStopped && (hasActiveSession || FirstStep.MARK_PARKING in done)) {
            add(FirstStep.UNDERSTAND_WATCH)
        }
        if (hasTouchedSpots) add(FirstStep.FIND_SPOT)
    }
    val effective = done + live
    // A step with nothing to ask for is not on this user's list at all: the fortify step exists
    // while — and only while — there is a reinforcement to make. Granting it does not tick the step,
    // it RETIRES it: the checklist gets shorter and the "N of M" line follows, which is the honest
    // reading of "there is nothing left to fix here". [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
    val applicable = FirstStep.entries.filterTo(mutableSetOf()) {
        it != FirstStep.FORTIFY_WATCH || reinforcement != WatchReinforcement.NONE
    }
    return FirstStepsProgress(
        done = effective,
        // The FIRST unfinished step in declaration order — never "the next one after the last done".
        // A user who reports a spot before parking has the community step banked and is still asked
        // to park, which is the right ask: nothing is watching their car yet.
        //
        // Deferred steps are skipped, not completed: the checklist moves on to what it can still
        // teach, and the step keeps its honest "not done" state. A step the user declined is not a
        // reason to stop guiding them. [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
        current = applicable.firstOrNull { it !in effective && it !in deferred },
        isVisible = !dismissed,
        findSpotAsk = if (hasSpotsOnOffer) FindSpotAsk.SEE_NEARBY else FindSpotAsk.REPORT_ONE,
        watchAsk = if (isAutoDetectionStopped) WatchAsk.TURN_IT_ON else WatchAsk.EXPLAIN_RELEASE,
        applicable = applicable,
        deferred = deferred,
        reinforcement = reinforcement,
    )
}
