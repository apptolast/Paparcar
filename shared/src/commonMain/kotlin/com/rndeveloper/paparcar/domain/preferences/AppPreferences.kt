package com.rndeveloper.paparcar.domain.preferences

import com.rndeveloper.paparcar.domain.detection.PendingParkNudge
import com.rndeveloper.paparcar.domain.detection.PendingPromptWindow
import kotlinx.coroutines.flow.Flow

interface AppPreferences {
    val isOnboardingCompleted: Boolean
    fun setOnboardingCompleted()

    val hasSeenGpsAccuracyDisclaimer: Boolean
    fun setGpsAccuracyDisclaimerSeen()

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

    val themeMode: ThemeMode
    fun setThemeMode(mode: ThemeMode)

    val useImperialUnits: Boolean
    fun setUseImperialUnits(enabled: Boolean)

    /** Stores map type as a plain string ("TERRAIN" | "SATELLITE" | "HYBRID"). */
    val defaultMapType: String
    fun setDefaultMapType(type: String)
}
