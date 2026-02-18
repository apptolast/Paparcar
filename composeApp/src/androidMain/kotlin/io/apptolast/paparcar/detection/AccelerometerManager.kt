package io.apptolast.paparcar.detection

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class AccelerometerManager(
    private val sensorManager: SensorManager, // Inyectado
    private val onVehicleStartDetected: () -> Unit
) : SensorEventListener {

    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val vibrationThreshold = 2.5f
    private val minSustainedDurationMs = 4000L
    private val movementConfirmSamples = 10
    private val resetPauseMs = 2000L

    private var vibrationStartTime: Long = 0L
    private var consecutiveVibrationCount = 0
    private var isServiceRunning = false

    private var lastVibrationTime: Long = 0L

    fun startListening() {
        if (accelerometer == null) return
        sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_NORMAL
        )
        isServiceRunning = false
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
        resetDetection()
    }

    fun resetDetection() {
        vibrationStartTime = 0L
        consecutiveVibrationCount = 0
        lastVibrationTime = 0L
        isServiceRunning = false
    }

    @OptIn(ExperimentalTime::class)
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val magnitude = sqrt(x * x + y * y + z * z)
        val vibrationLevel = abs(magnitude - SensorManager.GRAVITY_EARTH)
        val currentTime = Clock.System.now().toEpochMilliseconds()

        if (vibrationLevel > vibrationThreshold) {
            if (vibrationStartTime == 0L) {
                vibrationStartTime = currentTime
            }
            lastVibrationTime = currentTime
            consecutiveVibrationCount++

            val duration = currentTime - vibrationStartTime

            if (duration >= minSustainedDurationMs &&
                consecutiveVibrationCount >= movementConfirmSamples &&
                !isServiceRunning
            ) {
                isServiceRunning = true
                onVehicleStartDetected()
            }
        } else {
            if (currentTime - lastVibrationTime > resetPauseMs) {
                resetDetection()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun isAccelerometerAvailable(): Boolean = accelerometer != null
}
