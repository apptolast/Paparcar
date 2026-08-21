package io.apptolast.paparcar.data.datasource.remote.dto

import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import kotlinx.serialization.Serializable

/**
 * Header document for a detection session, stored at
 * `diagnostics/{userId}/sessions/{sessionId}`. Written on [DetectionEvent.SessionStarted];
 * [outcome] and the rollup fields are patched on [DetectionEvent.SessionEnded]. [DET-LOG-02]
 *
 * **Device identity** ([deviceModel]/[appVersion]/[osVersion]) is stamped by the logger so a trace
 * says which phone produced it — no more triangulating by garage/anchor. **Rollup** fields
 * ([endedAt]…[summary]) are computed by the logger from the drained event stream and let a session
 * be read at a glance without downloading its whole `events` subcollection. [DIAG-READABLE-001]
 */
@Serializable
data class DetectionSessionDto(
    val sessionId: String,
    val startedAt: Long,
    val strategy: String? = null,
    val vehicleType: String? = null,
    val outcome: String? = null,
    val evidence: String? = null,
    // Device identity [DIAG-READABLE-001]
    val deviceModel: String? = null,
    val appVersion: String? = null,
    val osVersion: String? = null,
    // Background-survival state at arm time [DET-SESSION-RELIABILITY-STAMP-001]: says whether a
    // session that later dies silently had its exemptions in place, instead of leaving us to guess.
    val batteryUnrestricted: Boolean? = null,
    val requiresAutostart: Boolean? = null,
    val requiresOemBatteryFreeze: Boolean? = null,
    // Per-session rollup, patched on SESSION_ENDED [DIAG-READABLE-001]
    val endedAt: Long? = null,
    val maxSpeedKmh: Float? = null,
    val drivingFixes: Int? = null,
    val fixCount: Int? = null,
    val maxStepCount: Int? = null,
    val finalLat: Double? = null,
    val finalLon: Double? = null,
    val summary: String? = null,
)

/**
 * Flat wire form of a [DetectionEvent], stored in the `events` subcollection. One document per
 * event — a long drive cannot overflow Firestore's 1 MiB document limit. The flat shape (a
 * discriminator `type` + nullable fields) maps cleanly to a Firestore doc and is trivial to query
 * and replay. [DET-LOG-02]
 */
@Serializable
data class DetectionEventDto(
    val type: String,
    val timestampMs: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracy: Float? = null,
    val speed: Float? = null,
    val strategy: String? = null,
    val vehicleType: String? = null,
    val outcome: String? = null,
    val activity: String? = null,
    val transition: String? = null,
    val event: String? = null,
    val geofenceId: String? = null,
    val deviceAddress: String? = null,
    val stepCount: Int? = null,
    val stopped: Boolean? = null,
    val stoppedDurationMs: Long? = null,
    val action: String? = null,
    val phase: String? = null,
    val pathLabel: String? = null,
    val confidence: Float? = null,
    // Departure/correction observability [DET-SOLID-001]
    val verdict: String? = null,
    val source: String? = null,
    val attempt: Int? = null,
    val speedKmh: Float? = null,
    val enterAgeMs: Long? = null,
    val published: Boolean? = null,
    val sessionCleared: Boolean? = null,
    val sessionAgeMs: Long? = null,
    val success: Boolean? = null,
    val radiusMeters: Float? = null,
    val evidence: String? = null,
    // OEM background-kill telemetry [OEM-KILL-001]
    val gapMs: Long? = null,
    // Session supersede [DET-SUPERSEDE-001]
    val distanceMeters: Double? = null,
    // Honest-close forensics [DET-FROZEN-COUNTER-001]
    val reason: String? = null,
    val walkDistanceMeters: Double? = null,
    val stepsDelta: Long? = null,
    val requiredSteps: Int? = null,
    // Abort-fix coherence vs the last witnessed position [DET-UNWITNESSED-DISPLACEMENT-001]
    val witnessDistanceMeters: Double? = null,
)

/** Canonical wire discriminator for each event subtype. */
fun DetectionEvent.typeName(): String = when (this) {
    is DetectionEvent.SessionStarted -> "SESSION_STARTED"
    is DetectionEvent.SessionEnded -> "SESSION_ENDED"
    is DetectionEvent.ActivityTransition -> "ACTIVITY_TRANSITION"
    is DetectionEvent.Geofence -> "GEOFENCE"
    is DetectionEvent.Bluetooth -> "BLUETOOTH"
    is DetectionEvent.LocationFix -> "LOCATION_FIX"
    is DetectionEvent.Step -> "STEP"
    is DetectionEvent.Candidate -> "CANDIDATE"
    is DetectionEvent.Decision -> "DECISION"
    is DetectionEvent.HonestClose -> "HONEST_CLOSE"
    is DetectionEvent.DepartureVerdict -> "DEPARTURE_VERDICT"
    is DetectionEvent.DepartureProcessed -> "DEPARTURE_PROCESSED"
    is DetectionEvent.SpotRetracted -> "SPOT_RETRACTED"
    is DetectionEvent.Reverted -> "REVERTED"
    is DetectionEvent.Released -> "RELEASED"
    is DetectionEvent.OrphanCleaned -> "ORPHAN_CLEANED"
    is DetectionEvent.SessionSuperseded -> "SESSION_SUPERSEDED"
    is DetectionEvent.GeofenceRegistration -> "GEOFENCE_REGISTRATION"
    is DetectionEvent.BackgroundKillSuspected -> "BACKGROUND_KILL_SUSPECTED"
    is DetectionEvent.ForceStopConfirmed -> "FORCE_STOP_CONFIRMED"
    is DetectionEvent.Sentry -> "SENTRY"
}

fun DetectionEvent.SessionStarted.toSessionDto(): DetectionSessionDto = DetectionSessionDto(
    sessionId = sessionId,
    startedAt = timestampMs,
    strategy = strategy,
    vehicleType = vehicleType,
    evidence = evidence,
)

/**
 * [code-review #6] Maps each sealed variant via an exhaustive `when (this)` rather than per-field
 * `as?` casts. The compiler now forces a branch for every [DetectionEvent] subtype, so adding or
 * renaming a variant is a compile error here (not a silently-null Firestore column that corrupts the
 * replay fixture). Each branch only sets the fields it owns. See MEMORY: "DTO field parity".
 */
fun DetectionEvent.toDto(): DetectionEventDto {
    val loc = location
    val base = DetectionEventDto(
        type = typeName(),
        timestampMs = timestampMs,
        lat = loc?.latitude,
        lon = loc?.longitude,
        accuracy = loc?.accuracy,
        speed = loc?.speed,
    )
    return when (this) {
        is DetectionEvent.SessionStarted -> base.copy(strategy = strategy, vehicleType = vehicleType, evidence = evidence)
        is DetectionEvent.SessionEnded -> base.copy(outcome = outcome)
        // [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001] The AR staleness rides the existing `enterAgeMs`
        // column — the same "how old was this transition" the departure lane already stores there.
        is DetectionEvent.ActivityTransition ->
            base.copy(activity = activity, transition = transition, enterAgeMs = trueTimeAgeMs)
        is DetectionEvent.Geofence -> base.copy(event = event, geofenceId = geofenceId)
        is DetectionEvent.Bluetooth -> base.copy(event = event, deviceAddress = deviceAddress)
        is DetectionEvent.LocationFix -> base.copy(stoppedDurationMs = stoppedDurationMs)
        is DetectionEvent.Step -> base.copy(stepCount = stepCount, stopped = stopped)
        is DetectionEvent.Candidate -> base.copy(action = action, phase = phase)
        // [DET-PROMPT-STATES-ITS-REASON-001] The verdict's cause rides the existing `reason` column
        // (same one HonestClose/Released/GeofenceRegistration use) so the six producers of
        // `CONFIRM_DEGRADED_PROMPT` become groupable — no serializer surface change, and the
        // `outcome` string every saved trace quotes is left alone.
        is DetectionEvent.Decision -> base.copy(outcome = outcome, pathLabel = pathLabel, confidence = confidence, distanceMeters = distanceMeters, radiusMeters = radiusMeters, reason = reason)
        is DetectionEvent.HonestClose -> base.copy(
            verdict = verdict,
            reason = reason,
            distanceMeters = distanceMeters,
            walkDistanceMeters = walkDistanceMeters,
            stepsDelta = stepsDelta,
            requiredSteps = requiredSteps,
            stepCount = sessionStepEvents,
            speedKmh = sessionMaxSpeedKmh,
            radiusMeters = radiusMeters,
            witnessDistanceMeters = witnessDistanceMeters,
            // The witness's age rides the existing "how old" column on purpose (no serializer
            // surface change) — same reuse the Sentry event applies. [DET-UNWITNESSED-DISPLACEMENT-001]
            sessionAgeMs = witnessAgeMs,
        )
        is DetectionEvent.DepartureVerdict -> base.copy(verdict = verdict, source = source, attempt = attempt, speedKmh = speedKmh, enterAgeMs = enterAgeMs)
        is DetectionEvent.DepartureProcessed -> base.copy(published = published, sessionCleared = sessionCleared)
        // [DET-HANDOFF-NOT-MANUAL-001 §B.3] How long the withdrawn spot was live rides the existing
        // "how old" column, same reuse as Reverted — no serializer surface change.
        is DetectionEvent.SpotRetracted -> base.copy(sessionAgeMs = sessionAgeMs)
        is DetectionEvent.Reverted -> base.copy(sessionAgeMs = sessionAgeMs)
        // [PARK-DELETE-NO-DECLARE-001] The close reason rides in the existing `reason` column (same
        // one HonestClose uses) — no serializer surface change for a field that only adds provenance.
        is DetectionEvent.Released -> base.copy(published = published, reason = reason.name)
        is DetectionEvent.OrphanCleaned -> base
        is DetectionEvent.SessionSuperseded -> base.copy(distanceMeters = distanceMeters, sessionAgeMs = ageMs)
        // [DET-FENCE-REREGISTER-BY-CAUSE-001 §D] The lane rides `source` and the failure reason rides
        // `reason` — both columns already exist (Sentry and HonestClose/Released use them), so a
        // registration failure becomes groupable in telemetry with no serializer surface change.
        is DetectionEvent.GeofenceRegistration -> base.copy(
            success = success,
            radiusMeters = radiusMeters,
            source = source,
            reason = failure?.label,
        )
        is DetectionEvent.BackgroundKillSuspected -> base.copy(gapMs = gapMs)
        is DetectionEvent.ForceStopConfirmed -> base
        // [DET-RESIDENT-FGS-001 · F2] Reuses existing columns on purpose (no serializer surface
        // change): the waking/entering signal rides in `source`, time-in-SENTRY in `sessionAgeMs`.
        is DetectionEvent.Sentry -> base.copy(event = event, source = signal, gapMs = gapMs, sessionAgeMs = residencyMs)
    }
}
