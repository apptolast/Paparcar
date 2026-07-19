@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.data.datasource.remote

import com.apptolast.customlogin.domain.AuthRepository
import dev.gitlive.firebase.firestore.FirebaseFirestore
import io.apptolast.paparcar.data.datasource.remote.dto.toDto
import io.apptolast.paparcar.data.datasource.remote.dto.toSessionDto
import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import io.apptolast.paparcar.domain.diagnostics.DetectionEventLogger
import io.apptolast.paparcar.domain.diagnostics.DeviceInfoProvider
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.math.roundToInt

/**
 * Firestore-backed [DetectionEventLogger] for field diagnostics. [DET-LOG-02]
 *
 * **Gate (interim).** Self-disables unless `diagnostics_config/{userId}.enabled == true`. This is a
 * plain Firestore flag rather than Remote Config because the project's network blocks fetching new
 * Gradle artifacts (the GitLive `firebase-config` SDK), and a Firestore flag is toggled directly
 * via the Firestore MCP tools (`firestore_update_document`) anyway — no `remoteconfig` round-trip.
 * The flag is read once per process (lazily, on the first event, when a session exists) and cached.
 * Default (doc/field absent or unreadable) is `false`, so only opted-in users emit traces.
 *
 * **Non-blocking contract.** [log] only `trySend`s onto a buffered [Channel] — it never throws and
 * never touches the network on the caller's thread, so detection logic runs identically whether
 * logging is on or off. A background consumer drains the channel and writes off the hot path. When
 * the buffer saturates, events are dropped silently (diagnostics are best-effort).
 *
 * **Schema.** `diagnostics/{userId}/sessions/{sessionId}` header doc +
 * `diagnostics/{userId}/sessions/{sessionId}/events/{autoId}` one doc per event. The per-event
 * subcollection keeps a long drive well under Firestore's 1 MiB document limit and is exactly the
 * replay trace consumed by the Fase D pure-decision tests.
 */
class FirestoreDetectionEventLogger(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val deviceInfo: DeviceInfoProvider,
    scope: CoroutineScope,
) : DetectionEventLogger {

    private val channel = Channel<DetectionEvent>(capacity = BUFFER_CAPACITY)
    private val cleanupScope = scope

    /** Per-session rollup accumulated from the drained stream, keyed by sessionId. Touched ONLY on
     *  the single consumer coroutine (the `for (event in channel)` loop) → no synchronization needed.
     *  Flushed to the header doc on SESSION_ENDED. [DIAG-READABLE-001] */
    private val rollups = mutableMapOf<String, SessionRollup>()

    /** Cached gate value: null until first **successfully** resolved, then the flag from Firestore.
     *  @Volatile so [log] can read it from the producer thread to short-circuit. */
    @Volatile private var gate: Boolean? = null

    /** One retention sweep per process — see [cleanupExpiredSessions]. [DIAG-RETENTION-001] */
    @Volatile private var cleanupStarted = false

    init {
        scope.launch {
            for (event in channel) {
                val userId = runCatching { authRepository.getCurrentSession()?.userId }.getOrNull()
                    ?: continue
                if (!isEnabled(userId)) continue
                runCatching { writeEvent(userId, event) }
                    .onFailure { e -> PaparcarLogger.w(TAG, "diagnostics write failed: ${e.message}") }
            }
        }
    }

    override suspend fun log(event: DetectionEvent) {
        // Fire-and-forget: never throws, never blocks. Drops if the buffer is saturated.
        // [code-review #3] Once the gate is known-disabled, skip the channel entirely so the
        // non-opted-in majority (all of production) does not pay a trySend + a consumer wakeup per
        // detection event. The first event (gate still null) goes through so the gate can resolve.
        if (gate == false) return
        channel.trySend(event)
    }

    private suspend fun isEnabled(userId: String): Boolean {
        gate?.let { return it }
        // [code-review #2] Cache only a SUCCESSFUL read. A transient failure (offline at the first
        // event, or a timed-out read) must NOT latch `false` for the whole process — that would
        // silently lose the entire field drive the tester was trying to capture. Leave the gate
        // unresolved (null) so the next event retries.
        return runCatching {
            firestore.collection(COLLECTION_CONFIG)
                .document(userId)
                .get()
                .get<Boolean?>(FIELD_ENABLED) ?: false
        }.fold(
            onSuccess = { value ->
                gate = value
                PaparcarLogger.d(TAG, "detection remote log enabled=$value")
                if (value) cleanupExpiredSessions(userId)
                value
            },
            onFailure = { e ->
                PaparcarLogger.w(TAG, "diagnostics gate read failed — will retry next event: ${e.message}")
                false
            },
        )
    }

    /**
     * [DIAG-RETENTION-001] Best-effort sweep of this user's OWN diagnostic sessions older than
     * [RETENTION_DAYS]: events subcollection first, then the session doc. Runs at most once per
     * process, right after the gate resolves enabled — i.e. only opted-in field devices pay it,
     * and analysed traces stop accumulating as garbage. Departure traces keyed by geofenceId have
     * no header doc (missing parents) and are not reachable via this query — their event counts
     * are tiny (4–9 docs) and they age out when swept manually.
     */
    private fun cleanupExpiredSessions(userId: String) {
        if (cleanupStarted) return
        cleanupStarted = true
        cleanupScope.launch {
            runCatching {
                val cutoffMs = kotlin.time.Clock.System.now().toEpochMilliseconds() -
                    RETENTION_DAYS * 24 * 60 * 60 * 1_000L
                val sessions = firestore.collection(COLLECTION_DIAGNOSTICS)
                    .document(userId)
                    .collection(COLLECTION_SESSIONS)
                    .where { FIELD_STARTED_AT lessThan cutoffMs }
                    .get()
                    .documents
                var deleted = 0
                for (session in sessions) {
                    session.reference.collection(COLLECTION_EVENTS).get().documents.forEach { event ->
                        event.reference.delete()
                        deleted++
                    }
                    session.reference.delete()
                }
                if (sessions.isNotEmpty()) {
                    PaparcarLogger.d(TAG, "retention sweep: ${sessions.size} sessions / $deleted events older than $RETENTION_DAYS d removed")
                }
            }.onFailure { e -> PaparcarLogger.w(TAG, "retention sweep failed (best-effort): ${e.message}") }
        }
    }

    private suspend fun writeEvent(userId: String, event: DetectionEvent) {
        val sessionDoc = firestore.collection(COLLECTION_DIAGNOSTICS)
            .document(userId)
            .collection(COLLECTION_SESSIONS)
            .document(event.sessionId)

        accumulate(event)
        when (event) {
            is DetectionEvent.SessionStarted -> sessionDoc.set(
                // Stamp device identity so a trace says which phone produced it [DIAG-READABLE-001],
                // plus the background-survival state so a silent death says whether the exemptions
                // were in place [DET-SESSION-RELIABILITY-STAMP-001].
                event.toSessionDto().copy(
                    deviceModel = deviceInfo.deviceModel,
                    appVersion = deviceInfo.appVersion,
                    osVersion = deviceInfo.osVersion,
                    batteryUnrestricted = deviceInfo.isBatteryUnrestricted,
                    requiresAutostart = deviceInfo.requiresAutostartWhitelist,
                    requiresOemBatteryFreeze = deviceInfo.requiresOemBatteryFreezeExemption,
                ),
            )
            is DetectionEvent.SessionEnded -> flushSession(sessionDoc, event)
            else -> Unit
        }
        sessionDoc.collection(COLLECTION_EVENTS).add(event.toDto())
    }

    /** Fold each event into its session's rollup. Runs on the consumer coroutine only. */
    private fun accumulate(event: DetectionEvent) {
        when (event) {
            is DetectionEvent.SessionStarted ->
                rollups[event.sessionId] = SessionRollup(startedAt = event.timestampMs)
            is DetectionEvent.LocationFix -> {
                val r = rollups.getOrPut(event.sessionId) { SessionRollup() }
                r.fixCount++
                val kmh = (event.location?.speed ?: 0f) * MS_TO_KMH
                if (kmh > r.maxSpeedKmh) r.maxSpeedKmh = kmh
                if (kmh >= DRIVING_SPEED_KMH) r.drivingFixes++
                event.location?.let { r.finalLat = it.latitude; r.finalLon = it.longitude }
            }
            is DetectionEvent.Step -> {
                val r = rollups.getOrPut(event.sessionId) { SessionRollup() }
                if (event.stepCount > r.maxStepCount) r.maxStepCount = event.stepCount
            }
            else -> Unit
        }
    }

    /** Patch the header with the terminal outcome + rollup, and mirror a one-line summary to the
     *  local log so the same digest shows in logcat. [DIAG-READABLE-001] */
    private suspend fun flushSession(
        sessionDoc: dev.gitlive.firebase.firestore.DocumentReference,
        ended: DetectionEvent.SessionEnded,
    ) {
        val r = rollups.remove(ended.sessionId)
        val summary = buildSummary(ended, r)
        sessionDoc.update(
            FIELD_OUTCOME to ended.outcome,
            FIELD_ENDED_AT to ended.timestampMs,
            FIELD_MAX_SPEED_KMH to r?.maxSpeedKmh,
            FIELD_DRIVING_FIXES to r?.drivingFixes,
            FIELD_FIX_COUNT to r?.fixCount,
            FIELD_MAX_STEP_COUNT to r?.maxStepCount,
            FIELD_FINAL_LAT to r?.finalLat,
            FIELD_FINAL_LON to r?.finalLon,
            FIELD_SUMMARY to summary,
        )
        PaparcarLogger.i(TAG, "session ${ended.sessionId} [${deviceInfo.deviceModel}]: $summary")
    }

    private fun buildSummary(ended: DetectionEvent.SessionEnded, r: SessionRollup?): String {
        val parts = mutableListOf(ended.outcome)
        if (r != null && r.startedAt > 0L) {
            parts += "${round1((ended.timestampMs - r.startedAt) / 60_000.0)}min"
        }
        if (r != null) {
            parts += "vmax ${r.maxSpeedKmh.roundToInt()}km/h"
            parts += "drive ${r.drivingFixes}/${r.fixCount}fix"
            parts += "steps ${r.maxStepCount}"
            val lat = r.finalLat
            val lon = r.finalLon
            if (lat != null && lon != null) parts += "end ${round5(lat)},${round5(lon)}"
        }
        return parts.joinToString(" · ")
    }

    /** Mutable per-session accumulator (consumer-thread confined). */
    private class SessionRollup(val startedAt: Long = 0L) {
        var fixCount = 0
        var maxSpeedKmh = 0f
        var drivingFixes = 0
        var maxStepCount = 0
        var finalLat: Double? = null
        var finalLon: Double? = null
    }

    private companion object {
        const val TAG = "FirestoreDetectionEventLogger"
        const val COLLECTION_CONFIG = "diagnostics_config"
        const val FIELD_ENABLED = "enabled"
        const val COLLECTION_DIAGNOSTICS = "diagnostics"
        const val COLLECTION_SESSIONS = "sessions"
        const val COLLECTION_EVENTS = "events"
        const val FIELD_OUTCOME = "outcome"
        const val FIELD_STARTED_AT = "startedAt"
        // Rollup header fields patched on SESSION_ENDED [DIAG-READABLE-001]
        const val FIELD_ENDED_AT = "endedAt"
        const val FIELD_MAX_SPEED_KMH = "maxSpeedKmh"
        const val FIELD_DRIVING_FIXES = "drivingFixes"
        const val FIELD_FIX_COUNT = "fixCount"
        const val FIELD_MAX_STEP_COUNT = "maxStepCount"
        const val FIELD_FINAL_LAT = "finalLat"
        const val FIELD_FINAL_LON = "finalLon"
        const val FIELD_SUMMARY = "summary"
        const val BUFFER_CAPACITY = 128
        /** [DIAG-RETENTION-001] Diagnostic sessions older than this are swept on gate resolve. */
        const val RETENTION_DAYS = 7L
        /** m/s → km/h; a fix at/above [DRIVING_SPEED_KMH] counts as a "driving" fix in the rollup. */
        const val MS_TO_KMH = 3.6f
        const val DRIVING_SPEED_KMH = 18f
    }
}

/** Round to 1 decimal without `String.format` (unavailable in commonMain). */
private fun round1(v: Double): Double = (v * 10).roundToInt() / 10.0

/** Round a coordinate to 5 decimals (~1 m) for a compact, readable summary. */
private fun round5(v: Double): Double = (v * 100_000).roundToInt() / 100_000.0
