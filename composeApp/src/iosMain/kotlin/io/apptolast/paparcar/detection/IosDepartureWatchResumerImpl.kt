package io.apptolast.paparcar.detection

import io.apptolast.paparcar.domain.detection.ports.DepartureWatchResumer

/** iOS has no resident departure watcher to rebuild — no-op until detection lands there.
 *  [DET-WATCH-REACTIVATE-001] */
class IosDepartureWatchResumerImpl : DepartureWatchResumer {
    override suspend fun resume(source: String, force: Boolean): Boolean = false
}
