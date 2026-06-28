package io.apptolast.paparcar.domain.detection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Read-only visibility into whether a detection tracking job is currently active.
 *
 * The Coordinator foreground service owns the real job; this interface exposes a platform-agnostic
 * flag so the domain (e.g. [io.apptolast.paparcar.domain.usecase.detection.ObserveDetectionReadinessUseCase])
 * can tell "armed & idle" (Ready) apart from "actively tracking a trip" (Monitoring) without
 * depending on Android. [DET-READY-001c]
 */
interface DetectionRuntimeState {
    /** True while a tracking job is running in the foreground service. */
    val isRunning: StateFlow<Boolean>
}

/**
 * Production [DetectionRuntimeState]: a shared singleton the Coordinator foreground service
 * updates as its tracking job starts and ends. Held as a single in DI so the service and the
 * readiness use case observe the same flag. [DET-READY-001c]
 */
class MutableDetectionRuntimeState : DetectionRuntimeState {
    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /** Called by the detection service when a tracking job starts (true) or ends (false). */
    fun setRunning(running: Boolean) {
        _isRunning.value = running
    }
}

/**
 * Fixed-value [DetectionRuntimeState] for tests and previews — reports a constant `running`.
 * [DET-READY-001c]
 */
class StaticDetectionRuntimeState(running: Boolean = false) : DetectionRuntimeState {
    override val isRunning: StateFlow<Boolean> = MutableStateFlow(running).asStateFlow()
}
