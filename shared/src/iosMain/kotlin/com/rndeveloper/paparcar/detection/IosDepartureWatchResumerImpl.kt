package com.rndeveloper.paparcar.detection

import com.rndeveloper.paparcar.domain.detection.ports.DepartureWatchResumer
import io.github.aakira.napier.Napier

/**
 * [DET-WATCH-REACTIVATE-001] On iOS the departure watcher is the OS-held `CLCircularRegion`, not a
 * resident service — so "resume the watch" means: reconcile the region inventory against Room and
 * answer whether the parked session's fence is standing. An explicit CTA tap therefore always does
 * something visible and TRUE. [IOS-F1-A-CONTROLLER-FOR-THE-HAPPY-PATH-001]
 */
class IosDepartureWatchResumerImpl(
    private val controller: IosDetectionController,
) : DepartureWatchResumer {
    override suspend fun resume(source: String, force: Boolean): Boolean {
        Napier.i("resume watch requested (source=$source, force=$force)", tag = "IosDepartureWatchResumer")
        return controller.resumeWatch()
    }
}
