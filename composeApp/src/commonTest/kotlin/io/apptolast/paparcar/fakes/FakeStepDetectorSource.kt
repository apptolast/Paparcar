package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.sensor.StepDetectorSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Test double for [StepDetectorSource]. Tests call [emitStep] to push pedestrian step
 * events through the flow exposed by [steps].
 *
 * `extraBufferCapacity = 64` ensures emissions are non-suspending when the coordinator
 * collector is not actively suspended, so tests can fire bursts of steps without
 * dispatching the test scheduler between each call.
 */
class FakeStepDetectorSource : StepDetectorSource {

    private val _steps = MutableSharedFlow<Unit>(extraBufferCapacity = 64)

    override fun steps(): Flow<Unit> = _steps

    suspend fun emitStep() {
        _steps.emit(Unit)
    }

    suspend fun emitSteps(count: Int) {
        repeat(count) { _steps.emit(Unit) }
    }
}
