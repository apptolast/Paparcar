package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.detection.PendingParkNudge
import com.rndeveloper.paparcar.domain.detection.PendingPromptWindow
import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.preferences.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAppPreferences(
    initialCompleted: Boolean = false,
    initialAutoDetect: Boolean = true,
    initialNotifyParking: Boolean = true,
    initialThemeMode: ThemeMode = ThemeMode.SYSTEM,
    initialUseImperialUnits: Boolean = false,
    initialDefaultMapType: String = "TERRAIN",
) : AppPreferences {

    private var _isOnboardingCompleted = initialCompleted
    override val isOnboardingCompleted: Boolean get() = _isOnboardingCompleted

    var setOnboardingCompletedCount = 0
        private set

    override fun setOnboardingCompleted() {
        _isOnboardingCompleted = true
        setOnboardingCompletedCount++
    }

    private var _hasSeenGpsAccuracyDisclaimer = false
    override val hasSeenGpsAccuracyDisclaimer: Boolean get() = _hasSeenGpsAccuracyDisclaimer
    override fun setGpsAccuracyDisclaimerSeen() { _hasSeenGpsAccuracyDisclaimer = true }

    private var _hasAcceptedLegalConsent = false
    override val hasAcceptedLegalConsent: Boolean get() = _hasAcceptedLegalConsent
    override fun setLegalConsentAccepted() { _hasAcceptedLegalConsent = true }

    private var _hasRequestedLocationPermission = false
    override val hasRequestedLocationPermission: Boolean get() = _hasRequestedLocationPermission
    override fun setLocationPermissionRequested() { _hasRequestedLocationPermission = true }

    private val _autoDetectParking = MutableStateFlow(initialAutoDetect)
    override val autoDetectParking: Boolean get() = _autoDetectParking.value
    override fun setAutoDetectParking(enabled: Boolean) { _autoDetectParking.value = enabled }
    override fun observeAutoDetectParking(): Flow<Boolean> = _autoDetectParking

    private var _firstParkNudgeCount = 0
    override val firstParkNudgeCount: Int get() = _firstParkNudgeCount
    override fun setFirstParkNudgeCount(count: Int) { _firstParkNudgeCount = count }
    private var _lastFirstParkNudgeAt = 0L
    override val lastFirstParkNudgeAtMillis: Long get() = _lastFirstParkNudgeAt
    override fun setLastFirstParkNudgeAt(millis: Long) { _lastFirstParkNudgeAt = millis }
    private var _hasConfirmedFirstPark = false
    override val hasConfirmedFirstPark: Boolean get() = _hasConfirmedFirstPark
    override fun setHasConfirmedFirstPark() { _hasConfirmedFirstPark = true }

    // [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001] Public so a test can assert what the
    // controller BANKED, not just what it rendered — the latch is the half that survives a restart.
    val firstStepsDone = MutableStateFlow<Set<FirstStep>>(emptySet())
    val firstStepsDismissed = MutableStateFlow(false)
    override fun observeFirstStepsDone(): Flow<Set<FirstStep>> = firstStepsDone
    override fun setFirstStepsDone(steps: Set<FirstStep>) { firstStepsDone.value = steps }
    val firstStepsDeferred = MutableStateFlow<Set<FirstStep>>(emptySet())
    override fun observeFirstStepsDeferred(): Flow<Set<FirstStep>> = firstStepsDeferred
    override fun setFirstStepsDeferred(steps: Set<FirstStep>) { firstStepsDeferred.value = steps }
    override fun observeFirstStepsDismissed(): Flow<Boolean> = firstStepsDismissed
    override fun setFirstStepsDismissed(dismissed: Boolean) { firstStepsDismissed.value = dismissed }

    // [DET-NUDGE-PERSIST-001]
    val pendingParkNudge = MutableStateFlow<PendingParkNudge?>(null)
    override fun observePendingParkNudge(): Flow<PendingParkNudge?> = pendingParkNudge
    override fun setPendingParkNudge(nudge: PendingParkNudge) { pendingParkNudge.value = nudge }
    override fun clearPendingParkNudge() { pendingParkNudge.value = null }

    // [DET-ASK-STATE-001]
    val pendingPromptWindow = MutableStateFlow<PendingPromptWindow?>(null)
    override fun observePendingPromptWindow(): Flow<PendingPromptWindow?> = pendingPromptWindow
    override fun setPendingPromptWindow(window: PendingPromptWindow) { pendingPromptWindow.value = window }
    override fun clearPendingPromptWindow() { pendingPromptWindow.value = null }

    private var _notifyParkingDetected = initialNotifyParking
    override val notifyParkingDetected: Boolean get() = _notifyParkingDetected
    override fun setNotifyParkingDetected(enabled: Boolean) { _notifyParkingDetected = enabled }

    private var _themeMode = initialThemeMode
    override val themeMode: ThemeMode get() = _themeMode
    override fun setThemeMode(mode: ThemeMode) { _themeMode = mode }

    private var _useImperialUnits = initialUseImperialUnits
    override val useImperialUnits: Boolean get() = _useImperialUnits
    override fun setUseImperialUnits(enabled: Boolean) { _useImperialUnits = enabled }

    private var _defaultMapType = initialDefaultMapType
    override val defaultMapType: String get() = _defaultMapType
    override fun setDefaultMapType(type: String) { _defaultMapType = type }
}
