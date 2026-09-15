package com.rndeveloper.paparcar.zone

import androidx.work.WorkManager
import com.rndeveloper.paparcar.detection.worker.ZoneSpotWatchWorker
import com.rndeveloper.paparcar.domain.repository.ZoneRepository
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the zone watch's clock in step with whether any zone asks to be watched.
 * [ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001]
 *
 * ⛔ **A stream, not a read at app start.** A single read on launch is the shape
 * [DET-WATCH-REACTIVATE-001] had to undo: on a clean install Room is still empty while the Firestore
 * sync lands, so the one read sees "nothing subscribed" and the watch stays dead until the next
 * process launch. Subscribing a zone must also arm the watch there and then.
 *
 * ⛔ It does not read the entitlement. Plus is decided live inside the use case at every run; a
 * second place deciding it is a second place to keep in step.
 */
class ZoneWatchScheduler(
    private val workManager: WorkManager,
    private val zoneRepository: ZoneRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        scope.launch {
            zoneRepository.observeZones()
                .map { zones -> zones.any { it.notifyOnSpot } }
                // Whether ANY zone is subscribed is the question — subscribing a fourth must not
                // re-enqueue the periodic and reset its clock.
                .distinctUntilChanged()
                .catch { e -> PaparcarLogger.w(TAG, "zone stream failed; watch left as it was", e) }
                .collect { hasSubscribedZones ->
                    PaparcarLogger.d(TAG) {
                        if (hasSubscribedZones) "▶ zone watch armed" else "■ no zone subscribed — watch cancelled"
                    }
                    runCatching { ZoneSpotWatchWorker.syncSchedule(workManager, hasSubscribedZones) }
                        .onFailure { PaparcarLogger.w(TAG, "could not sync the zone watch schedule", it) }
                }
        }
    }

    private companion object {
        const val TAG = "ZoneSpotWatch"
    }
}
