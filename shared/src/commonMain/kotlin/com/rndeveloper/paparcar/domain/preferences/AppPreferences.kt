package com.rndeveloper.paparcar.domain.preferences

import com.rndeveloper.paparcar.domain.detection.PendingParkNudge
import com.rndeveloper.paparcar.domain.detection.PendingPromptWindow
import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.domain.onboarding.NearbyComplement
import kotlinx.coroutines.flow.Flow

interface AppPreferences {

    /**
     * Drops every preference that belonged to the user who just signed out.
     * [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001]
     *
     * The third arm of `SESSION-ISOLATION-001`, which until now covered Room and the OS geofences
     * and left this store standing: the guided tutorial, the first-park nudge counters and the two
     * open questions ("where did you leave your car?", "did you park?") all survived a sign-out and
     * greeted the NEXT account — the last two pointing at a `vehicleId` the Room wipe had just
     * deleted.
     *
     * The line it draws, and the one thing to think about before adding a key to either side: a
     * preference that describes the USER goes; one that describes this DEVICE or this INSTALL
     * stays. So `onboarding_completed`, the GPS disclaimer, the location-permission-asked flag and
     * the legal consent are KEPT — they are facts about this phone, and wiping them would punish a
     * user who merely signed out — while theme, units, map type and the detection toggles are kept
     * for the same reason.
     *
     * What is lost stays lost: only the tutorial's dismissal comes back, and it comes back from
     * the account (`UserProfile.firstStepsDismissedAt`), not from here.
     */
    fun clearUserScopedState()

    val isOnboardingCompleted: Boolean
    fun setOnboardingCompleted()

    val hasSeenGpsAccuracyDisclaimer: Boolean
    fun setGpsAccuracyDisclaimerSeen()

    /** True once the user has ticked "I accept the Privacy Policy" on the auth flow. Device-local,
     *  asked once per install: a first Google sign-in CREATES the account, so consent must gate the
     *  login screen's CTAs too, not just the register form.
     *  [AUTH-A-SIGN-IN-ASKS-FOR-CONSENT-FIRST-001] */
    val hasAcceptedLegalConsent: Boolean
    fun setLegalConsentAccepted()

    /** True once we have ever fired the foreground-location request dialog. Lets the permissions
     *  screen tell a genuine first launch (offer the system dialog) apart from a permanently denied /
     *  revoked permission (jump straight to system settings). Android-only concept. [DET-READY-001m] */
    val hasRequestedLocationPermission: Boolean
    fun setLocationPermissionRequested()

    val autoDetectParking: Boolean
    fun setAutoDetectParking(enabled: Boolean)
    /** Reactive view of [autoDetectParking] so Home's detection banner and the Android arming
     *  orchestration update live when the user flips the Settings toggle. [DET-TOGGLE-001] */
    fun observeAutoDetectParking(): Flow<Boolean>

    // ── First-park nudge (cold-start reminder). [DET-TOGGLE-002] ──
    /** How many cold-start nudges have been shown — capped so we never nag. */
    val firstParkNudgeCount: Int
    fun setFirstParkNudgeCount(count: Int)
    /** Epoch millis of the last cold-start nudge (0 = never). Cooldown anchor. */
    val lastFirstParkNudgeAtMillis: Long
    fun setLastFirstParkNudgeAt(millis: Long)
    /** True once the user has had a first parking confirmed — auto-disables the cold-start nudge. */
    val hasConfirmedFirstPark: Boolean
    fun setHasConfirmedFirstPark()

    // ── Guided first steps. [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001] ──
    /**
     * Steps the guided checklist has already banked, so a completed step never walks backwards when
     * the state that completed it goes away (releasing a parking must not un-teach step 1).
     *
     * ⛔ Deliberately NOT expressed through [hasConfirmedFirstPark]. That flag is the arming
     * condition of the cold-start notification (`FirstParkNudgeWorker` → `isFirstParkNudgeSpent`),
     * so clearing it to replay the tutorial from Settings would wake a machine that has nothing to
     * do with onboarding. Two questions, two flags.
     */
    fun observeFirstStepsDone(): Flow<Set<FirstStep>>
    fun setFirstStepsDone(steps: Set<FirstStep>)

    /** The user skipped the checklist, or acknowledged its closing card. Replaying from Settings
     *  clears this together with [setFirstStepsDone]. */
    fun observeFirstStepsDismissed(): Flow<Boolean>
    fun setFirstStepsDismissed(dismissed: Boolean)

    /**
     * Steps the user answered with "not yet" — a permission they chose not to grant.
     * [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
     *
     * A SECOND set, never folded into [observeFirstStepsDone]: deferred is not done. Banking a
     * declined step as completed would make the checklist claim the user did something they
     * explicitly refused, and the closing card would congratulate them for it.
     *
     * Persisted for the reason the whole ticket exists: a deferral that only lived in memory would
     * put the user back on the same wall at the next cold start. Replaying from Settings clears it
     * with the rest.
     */
    fun observeFirstStepsDeferred(): Flow<Set<FirstStep>>
    fun setFirstStepsDeferred(steps: Set<FirstStep>)

    /**
     * The complements whose provenance has already been said — where their data comes from and what
     * it does not promise. [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001]
     *
     * ONE fact with TWO readers, and that is the point of it living here rather than as a flag per
     * lane: the sheet asks it to decide whether the lane still owes its line, and the checklist asks
     * it to decide whether [FirstStep.EXPLORE_NEARBY] is done. A second latch for the step could
     * claim the user had been told something the lane never said.
     *
     * User-scoped, like the tutorial: what somebody KNOWS is theirs, not this phone's. Unknown names
     * are dropped on read, so retiring a complement reads as "never introduced" instead of crashing.
     */
    fun observeIntroducedComplements(): Flow<Set<NearbyComplement>>
    fun setIntroducedComplements(complements: Set<NearbyComplement>)

    // ── Pending "where did you leave your car?" nudge. [DET-NUDGE-PERSIST-001] ──
    /** The unanswered mark-parking nudge, or null. Single slot: a new set replaces the previous
     *  one (there is only ever one lost car to ask about). Written by the notification adapter at
     *  the SAME choke point that posts the nudge, so the question survives as app state (field
     *  2026-07-25: the notification alone was slept through and the session was lost). */
    fun observePendingParkNudge(): Flow<PendingParkNudge?>
    fun setPendingParkNudge(nudge: PendingParkNudge)
    fun clearPendingParkNudge()

    // ── Open "did you park?" question. [DET-ASK-STATE-001] ──
    /** The prompt currently posted on the confirmation notification channel, or null. Single slot:
     *  that channel only ever holds one question. Written and cleared by the notification adapter at
     *  the same two verbs that post and close the notification, so the in-app row and the tray can
     *  never disagree about whether there is something to answer. */
    fun observePendingPromptWindow(): Flow<PendingPromptWindow?>
    fun setPendingPromptWindow(window: PendingPromptWindow)
    fun clearPendingPromptWindow()

    /** Gate for the INFORMATIVE parking notifications (e.g. "parking saved"). Safety asks —
     *  "did you park?", the revertable auto-confirm, "still parked?" — are never gated by this:
     *  they are the anti-false-positive mechanism. [SETTINGS-AUDIT-REMEDIATION-001] */
    val notifyParkingDetected: Boolean
    fun setNotifyParkingDetected(enabled: Boolean)

    /** Master gate for the Plus zone-alert notifications ("free spot in your zone"). ON by
     *  default — subscribing a zone already expressed the wish; this switch exists so Settings
     *  can silence the channel without touching any zone's own opt-in. [MONEY-001] */
    val notifyZoneSpots: Boolean
    fun setNotifyZoneSpots(enabled: Boolean)

    /** Reactive twin of [notifyZoneSpots]. Needed since the push index must be rewritten the
     *  moment the channel is silenced — a snapshot read would leave the server pushing.
     *  [ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001] */
    fun observeNotifyZoneSpots(): Flow<Boolean>

    /** Master gate for the Plus fuel-price alerts (a cheaper pump nearby, and what a followed
     *  station did overnight). Sibling of [notifyZoneSpots] and ON by default for the same reason:
     *  following a station already expressed the wish, and this switch exists so Settings can
     *  silence the channel without the user having to unfollow anything.
     *  [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001] */
    val notifyFuelPrices: Boolean
    fun setNotifyFuelPrices(enabled: Boolean)

    /** [PRIV-A-TRACE-IS-NOT-A-STAT-001] Whether the ROUTE GEOMETRY (polyline + inferred spans) may
     *  leave the device. Opt-in and Plus-gated in Settings, default **OFF**: the trip's FIGURES
     *  (distance, search time) always sync -- they are numbers; the trace itself (where you drove)
     *  only with this consent. USER-scoped: consent belongs to the account, cleared on sign-out. */
    val syncRouteGeometry: Boolean
    fun setSyncRouteGeometry(enabled: Boolean)

    val themeMode: ThemeMode
    fun setThemeMode(mode: ThemeMode)

    val useImperialUnits: Boolean
    fun setUseImperialUnits(enabled: Boolean)

    /** Stores map type as a plain string ("NORMAL" | "HYBRID"); "TERRAIN" and "SATELLITE" are legacy values still read, never written. */
    val defaultMapType: String
    fun setDefaultMapType(type: String)

    // ⛔ There are no map-LAYER preferences here any more (`showFuelStationsLayer`,
    // `showPublicParkingsLayer`). What the map paints of fuel and car parks is decided by the sheet's
    // lens — where the user is LOOKING — not by a switch that outlives the session. A subject visible
    // in one surface and hidden in the other was two controls for one concept, and the pair could
    // also stack two label layers over the spot pins that are the product.
    // [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001]
}
