package com.rndeveloper.paparcar.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.rndeveloper.paparcar.domain.detection.sentry.triggerLedgerSessionId
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * [DET-MEMORY-LIMITER-IS-AN-ATTRIBUTABLE-KILL-001] Asks the platform WHY the previous process
 * died, once per process start, and turns the answer into the citable diagnostic vocabulary of
 * [DetectionEvent.ProcessDeath] — parkdiag gaps stop being anonymous.
 *
 * A field false negative has carried the same unprovable hypothesis for months: "the OEM killed
 * the process". `ApplicationExitInfo` (API 30+) is the platform's own answer — force-stop is what
 * an OEM deep-kill amounts to, and Android 17 adds a new killer (per-device RAM limits, surfacing
 * as REASON_OTHER + a "MemoryLimiter" description) with the same silhouette as every other silent
 * death. The mirror half is just as valuable: a start that reports `self_exit`, or no new deaths
 * at all, removes the excuse and returns the bug to our side of the net.
 *
 * Each historical exit of the MAIN process is reported exactly once across starts: the newest exit
 * timestamp already reported is persisted, and only strictly newer records are emitted. Below
 * API 30 the signal does not exist — that is stated in the local log as `unknown` instead of
 * pretended around, and nothing is emitted remotely.
 *
 * Not a use case: this is a signal that feeds diagnosis, not a verdict anyone consumes.
 * [DET-VERDICT-NOT-PREDICATE-001]
 */
class ProcessDeathAttributor(
    private val context: Context,
    private val detectionEventLogger: DetectionEventLogger,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    /** Fire-and-forget: never throws, never blocks the caller. Safe to call from Application.onCreate. */
    fun reportPendingDeaths() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            PaparcarLogger.i(DIAG, "exit reasons unavailable below API 30 — previous-death cause is 'unknown' by construction")
            return
        }
        scope.launch {
            runCatching {
                val am = context.getSystemService(ActivityManager::class.java) ?: return@launch
                val records = am.getHistoricalProcessExitReasons(null, ALL_PIDS, MAX_EXITS_QUERIED)
                    // Only the MAIN process: a sandboxed/isolated helper dying is not our detection
                    // process dying, and would poison the census with deaths nobody lived through.
                    .filter { it.processName == context.packageName }
                    .map { ExitRecord(it.reason, it.status, it.description, it.timestamp) }
                report(records, nowMs = System.currentTimeMillis())
            }.onFailure { e ->
                PaparcarLogger.w(DIAG, "exit-reason attribution failed: ${e.message}")
            }
        }
    }

    /** Testable half: dedup against the persisted watermark, emit one event per fresh death. */
    internal suspend fun report(records: List<ExitRecord>, nowMs: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val watermark = prefs.getLong(KEY_LAST_REPORTED_EXIT_AT, 0L)
        val fresh = newExitsSince(records, watermark)
        if (fresh.isEmpty()) {
            // The negative is the valuable half: parkdiag now STATES the process did not die,
            // instead of leaving the absence of a line to be read as anything.
            PaparcarLogger.i(DIAG, "no unreported process deaths (watermark=$watermark)")
            return
        }
        fresh.forEach { record ->
            val cause = attributeExit(record.reasonCode, record.description)
            PaparcarLogger.i(
                DIAG,
                "⚰ previous process death: $cause (${record.raw()}) " +
                    "${(nowMs - record.timestampMs) / 1000}s before this start",
            )
            detectionEventLogger.log(
                DetectionEvent.ProcessDeath(
                    sessionId = triggerLedgerSessionId(nowMs),
                    timestampMs = nowMs,
                    reason = cause,
                    detail = record.raw(),
                    deathAgeMs = (nowMs - record.timestampMs).coerceAtLeast(0L),
                ),
            )
        }
        prefs.edit()
            .putLong(KEY_LAST_REPORTED_EXIT_AT, fresh.maxOf { it.timestampMs })
            .apply()
    }

    private companion object {
        const val DIAG = "PARKDIAG/ExitInfo"
        const val PREFS_NAME = "process_death_attributor"
        const val KEY_LAST_REPORTED_EXIT_AT = "last_reported_exit_at"

        /** `pid = 0` means "all recorded exits", per the `getHistoricalProcessExitReasons` contract. */
        const val ALL_PIDS = 0

        /** The platform keeps a bounded history anyway; this bounds one start's report burst. */
        const val MAX_EXITS_QUERIED = 16
    }
}

/** One historical exit, decoupled from [ApplicationExitInfo] so the attribution is unit-testable. */
internal data class ExitRecord(
    val reasonCode: Int,
    val status: Int,
    val description: String?,
    val timestampMs: Long,
) {
    fun raw(): String = "reason=$reasonCode status=$status desc=${description ?: "-"}"
}

/**
 * Platform reason → citable vocabulary. `memory_limiter` matches Android 17's RAM-limit kill by
 * its description STRING ("MemoryLimiter", e.g. "MemoryLimiter:AnonSwap") — that string is not an
 * API contract; if a future release renames it, the record degrades to `other` and [ExitRecord.raw]
 * still carries the evidence.
 */
internal fun attributeExit(reasonCode: Int, description: String?): String = when (reasonCode) {
    ApplicationExitInfo.REASON_USER_REQUESTED,
    ApplicationExitInfo.REASON_USER_STOPPED,
    -> "force_stop"
    ApplicationExitInfo.REASON_LOW_MEMORY -> "low_memory"
    ApplicationExitInfo.REASON_CRASH,
    ApplicationExitInfo.REASON_CRASH_NATIVE,
    -> "crash"
    ApplicationExitInfo.REASON_ANR -> "anr"
    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive_resource"
    ApplicationExitInfo.REASON_EXIT_SELF -> "self_exit"
    ApplicationExitInfo.REASON_OTHER ->
        if (description?.contains(MEMORY_LIMITER_DESCRIPTION) == true) "memory_limiter" else "other"
    else -> "other"
}

private const val MEMORY_LIMITER_DESCRIPTION = "MemoryLimiter"

/** Strictly-newer-than-watermark exits, oldest first so the emitted order matches history. */
internal fun newExitsSince(records: List<ExitRecord>, watermarkMs: Long): List<ExitRecord> =
    records.filter { it.timestampMs > watermarkMs }.sortedBy { it.timestampMs }
