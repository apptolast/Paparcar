package io.apptolast.paparcar.detection.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.apptolast.paparcar.domain.sensor.StepDetectorSource
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Android implementation of [StepDetectorSource] backed by `Sensor.TYPE_STEP_DETECTOR`.
 *
 * The step detector reports one event per real pedestrian step, computed by the device's
 * sensor hub from accelerometer data. Latency is typically < 2 s. It does NOT count steps
 * during car/bus/train motion — Google's step-detection algorithm rejects non-pedestrian
 * cadence patterns server-side in the sensor hub, which is exactly what we need to
 * distinguish "user exited the car" from "user is sitting in a queue".
 *
 * If the device hardware lacks `TYPE_STEP_DETECTOR` (very rare; required by Android 4.4+
 * for devices declaring `android.hardware.sensor.stepdetector`), the flow is empty and the
 * coordinator's timeout-based fallback handles confirmation.
 */
class AndroidStepDetectorSource(
    context: Context,
) : StepDetectorSource {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    override fun steps(): Flow<Unit> {
        val sensor = stepSensor ?: run {
            PaparcarLogger.w(TAG, "Sensor.TYPE_STEP_DETECTOR unavailable — emitting empty flow")
            return emptyFlow()
        }
        return callbackFlow {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event?.sensor?.type == Sensor.TYPE_STEP_DETECTOR) {
                        trySend(Unit)
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
                PaparcarLogger.w(TAG, "registerListener returned false — closing flow")
                close()
                return@callbackFlow
            }
            PaparcarLogger.d(TAG, "▶ step detector listener registered")
            awaitClose {
                PaparcarLogger.d(TAG, "■ step detector listener unregistered")
                sensorManager.unregisterListener(listener)
            }
        }
    }

    private companion object {
        const val TAG = "StepDetector"
    }
}
