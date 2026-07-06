package io.apptolast.paparcar.detection.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.apptolast.paparcar.domain.sensor.StepCounterSource
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Android implementation of [StepCounterSource] backed by `Sensor.TYPE_STEP_COUNTER`.
 *
 * The counter lives in the sensor hub and accumulates across process death and CPU sleep;
 * registering a listener delivers the current cumulative value almost immediately (the HAL
 * pushes the latest sample on registration). We register, take the first sample, unregister —
 * a sub-second one-shot read on healthy hardware, bounded by [READ_TIMEOUT_MS] on broken HALs.
 */
class AndroidStepCounterSource(
    context: Context,
) : StepCounterSource {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val counterSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    override suspend fun currentCumulativeSteps(): Long? {
        val sensor = counterSensor ?: run {
            PaparcarLogger.w(TAG, "Sensor.TYPE_STEP_COUNTER unavailable on this device")
            return null
        }
        val value = withTimeoutOrNull(READ_TIMEOUT_MS) {
            callbackFlow {
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent?) {
                        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
                            trySend(event.values[0].toLong())
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                val registered = sensorManager.registerListener(
                    listener,
                    sensor,
                    SensorManager.SENSOR_DELAY_NORMAL,
                )
                if (!registered) {
                    PaparcarLogger.w(TAG, "registerListener returned false")
                    close()
                    return@callbackFlow
                }
                awaitClose { sensorManager.unregisterListener(listener) }
            }.firstOrNull()
        }
        PaparcarLogger.d(TAG, "cumulative steps read → ${value ?: "TIMEOUT"}")
        return value
    }

    private companion object {
        const val TAG = "PARKDIAG/StepCounter"
        const val READ_TIMEOUT_MS = 5_000L
    }
}
