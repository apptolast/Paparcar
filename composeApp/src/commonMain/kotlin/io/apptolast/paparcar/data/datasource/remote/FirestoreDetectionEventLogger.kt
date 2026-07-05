@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.data.datasource.remote

import com.apptolast.customlogin.domain.AuthRepository
import dev.gitlive.firebase.firestore.FirebaseFirestore
import io.apptolast.paparcar.data.datasource.remote.dto.toDto
import io.apptolast.paparcar.data.datasource.remote.dto.toSessionDto
import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import io.apptolast.paparcar.domain.diagnostics.DetectionEventLogger
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

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
    scope: CoroutineScope,
) : DetectionEventLogger {

    private val channel = Channel<DetectionEvent>(capacity = BUFFER_CAPACITY)
    private val cleanupScope = scope

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

        when (event) {
            is DetectionEvent.SessionStarted -> sessionDoc.set(event.toSessionDto())
            is DetectionEvent.SessionEnded -> sessionDoc.update(FIELD_OUTCOME to event.outcome)
            else -> Unit
        }
        sessionDoc.collection(COLLECTION_EVENTS).add(event.toDto())
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
        const val BUFFER_CAPACITY = 128
        /** [DIAG-RETENTION-001] Diagnostic sessions older than this are swept on gate resolve. */
        const val RETENTION_DAYS = 7L
    }
}
