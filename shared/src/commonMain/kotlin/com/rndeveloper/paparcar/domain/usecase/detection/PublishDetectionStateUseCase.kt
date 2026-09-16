@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.detection

import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.domain.analytics.AnalyticsTracker
import com.rndeveloper.paparcar.domain.analytics.AnalyticsUserProperty
import com.rndeveloper.paparcar.domain.detection.DetectionStateSnapshot
import com.rndeveloper.paparcar.domain.detection.label
import com.rndeveloper.paparcar.domain.model.DetectionReliabilityLevel
import com.rndeveloper.paparcar.domain.model.DetectionReliabilityReport
import com.rndeveloper.paparcar.domain.permissions.AppPermissionState
import com.rndeveloper.paparcar.domain.permissions.PermissionManager
import com.rndeveloper.paparcar.domain.permissions.missingProducerPermissions
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.UserProfileRepository
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.time.Clock

/**
 * Makes "can this account's automatic detection actually run?" observable off the device.
 * [DET-A-SWITCH-THAT-IS-OFF-LEAVES-NO-TRACE-001]
 *
 * Until this existed, a user who never granted background location and a user who simply had not
 * parked were the same silence. The only remote evidence was *post-hoc* — a pin with `armEvidence`
 * proves detection ran at that moment — and the full snapshot reached Firestore only for the handful
 * of accounts on the diagnostics allowlist.
 *
 * Two sinks, deliberately different, along the line
 * [AnalyticsTracker] already draws between per-user diagnostics and population counters:
 *
 *  - **Firestore**, per account, deduped: *which* permission is missing on THIS account.
 *  - **Analytics**, a user property, every emission: which BUCKET this install falls into.
 *
 * ⛔ **The dedupe is not an optimisation, it is the feature's affordability.**
 * [PermissionManager.permissionState] is refreshed on every resume, so publishing on observation
 * would spend one Firestore write per app opening, per user. See [DetectionStateSnapshot.needsPublishing].
 */
class PublishDetectionStateUseCase(
    private val permissionManager: PermissionManager,
    private val appPreferences: AppPreferences,
    private val observeDetectionReliability: ObserveDetectionReliabilityUseCase,
    private val userProfileRepository: UserProfileRepository,
    private val authRepository: AuthRepository,
    private val analyticsTracker: AnalyticsTracker,
) {

    /**
     * The live snapshot. Exposed separately from [publish] so the decision stays testable without a
     * collector, and so a caller can never observe one thing and publish another.
     */
    fun observe(): Flow<Pair<DetectionStateSnapshot, DetectionReliabilityReport>> = combine(
        permissionManager.permissionState,
        appPreferences.observeAutoDetectParking(),
        observeDetectionReliability(),
    ) { permissions, autoDetect, reliability ->
        permissions.snapshot(autoDetect, reliability) to reliability
    }.distinctUntilChanged()

    /**
     * Reports [snapshot], and does nothing expensive when nothing changed.
     *
     * Order matters: the analytics property is set FIRST and unconditionally — it costs no network
     * call of its own and a dropped one would silently bias the population counts — while the
     * Firestore write is gated and best-effort.
     *
     * Silent no-op when signed out: there is no document to write to, and stamping the local
     * fingerprint anyway would make the next sign-in believe it had already reported.
     */
    suspend fun publish(snapshot: DetectionStateSnapshot, reliability: DetectionReliabilityReport) {
        analyticsTracker.setUserProperty(AnalyticsUserProperty.DetectionState(snapshot.label()))
        analyticsTracker.setUserProperty(AnalyticsUserProperty.DetectionTier(reliability.tier))

        if (!snapshot.needsPublishing(appPreferences.lastPublishedDetectionState)) return
        val userId = authRepository.getCurrentSession()?.userId ?: return

        userProfileRepository.publishDetectionState(
            userId = userId,
            autoDetectParking = snapshot.autoDetectParking,
            detectionBlockers = snapshot.blockers.map { it.name }.sorted(),
            batteryOptimizationExempt = snapshot.batteryOptimizationExempt,
            updatedAtMs = Clock.System.now().toEpochMilliseconds(),
        )
            // Only on success: a failed write must NOT bank the fingerprint, or the state that never
            // arrived would be treated as published and the next attempt would never come. Losing a
            // snapshot offline is fine — the field is a photo of now, not a log — and the next real
            // change republishes.
            .onSuccess { appPreferences.setLastPublishedDetectionState(snapshot.remoteFingerprint()) }
            .onFailure { e -> PaparcarLogger.w(TAG, "detection state not published: ${e.message}") }
    }

    /** Folds the three live sources into the snapshot. The battery whitelist is a system setting
     *  rather than a runtime permission, which is why it is its own field and not a blocker:
     *  its absence degrades detection, it never stops it. [DOZE-001] */
    private fun AppPermissionState.snapshot(
        autoDetectParking: Boolean,
        reliability: DetectionReliabilityReport,
    ) = DetectionStateSnapshot(
        autoDetectParking = autoDetectParking,
        blockers = missingProducerPermissions(),
        batteryOptimizationExempt = isBatteryOptimizationExempt,
        reliabilityReduced = reliability.level == DetectionReliabilityLevel.REDUCED,
    )

    private companion object {
        const val TAG = "PublishDetectionState"
    }
}
