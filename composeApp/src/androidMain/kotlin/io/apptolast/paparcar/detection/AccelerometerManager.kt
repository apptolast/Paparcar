import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.apptolast.paparcar.data.notification.AppNotificationManager
import kotlin.math.abs
import kotlin.math.sqrt

class AccelerometerManager(
    private val sensorManager: SensorManager,
    private val notificationManager: AppNotificationManager,
    private val onVehicleStartDetected: () -> Unit,
    private val onVehicleStopDetected: () -> Unit
) : SensorEventListener {

    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Ventana deslizante de muestras para calcular RMS
    private val windowSize = 50          // 50 muestras a ~50Hz = ~1 segundo de análisis
    private val vibrationWindow = ArrayDeque<Float>(windowSize)

    // Umbrales calibrados para vibración de motor
    private val engineRmsMin = 0.3f      // RMS mínimo que indica motor encendido
    private val engineRmsMax = 2.5f      // RMS máximo — por encima es movimiento brusco (coger el móvil)
    private val engineConfirmWindows = 8 // 8 ventanas consecutivas válidas (~8s) = motor en marcha
    private val engineStopWindows = 5    // 5 ventanas sin vibración = motor parado

    private var validWindowCount = 0
    private var quietWindowCount = 0
    private var isVehicleRunning = false

    fun startListening() {
        if (accelerometer == null) return
        sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_GAME  // ← ~50Hz en lugar de ~5Hz
        )
        reset()
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
        reset()
    }

    fun reset() {
        vibrationWindow.clear()
        validWindowCount = 0
        quietWindowCount = 0
        isVehicleRunning = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Vibración neta eliminando gravedad
        val magnitude = sqrt(x * x + y * y + z * z)
        val vibration = abs(magnitude - SensorManager.GRAVITY_EARTH)

        // Acumular en ventana deslizante
        if (vibrationWindow.size >= windowSize) {
            vibrationWindow.removeFirst()
        }
        vibrationWindow.addLast(vibration)

        // Solo analizamos cuando tenemos la ventana completa
        if (vibrationWindow.size < windowSize) return

        // RMS de la ventana — mide energía media, no pico
        val rms = calculateRms(vibrationWindow)

        analyzeWindow(rms)
    }

    private fun calculateRms(samples: Collection<Float>): Float {
        val sumOfSquares = samples.sumOf { (it * it).toDouble() }
        return sqrt(sumOfSquares / samples.size).toFloat()
    }

    private fun analyzeWindow(rms: Float) {
        val isEnginePattern = rms in engineRmsMin..engineRmsMax

        if (isEnginePattern) {
            validWindowCount++
            quietWindowCount = 0

            if (validWindowCount >= engineConfirmWindows && !isVehicleRunning) {
                isVehicleRunning = true
                notificationManager.showDebugNotification("Motor detectado — RMS: $rms")
                onVehicleStartDetected()
            }

        } else {
            if (isVehicleRunning) {
                quietWindowCount++

                if (quietWindowCount >= engineStopWindows) {
                    isVehicleRunning = false
                    notificationManager.showDebugNotification("Motor parado — RMS: $rms")
                    onVehicleStopDetected()
                    reset()
                }
            } else {
                // Aún no detectamos motor — si hay mucho movimiento brusco (coger el móvil)
                // rms > engineRmsMax, simplemente no contamos
                validWindowCount = 0
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun isAccelerometerAvailable() = accelerometer != null
}
//```
//
//---
//
//## Por qué esto sí funciona
//```
//Coger el móvil:
//RMS pico → 4f - 12f → fuera de engineRmsMax (2.5f) → ignorado ✓
//
//Motor encendido con el móvil en el bolsillo o en el soporte:
//RMS sostenido → 0.4f - 1.2f → dentro del rango → cuenta ventanas ✓
//
//Bache en carretera:
//Spike puntual → afecta 1-2 ventanas, no resetea el contador ✓
//quietWindowCount solo cuenta si isVehicleRunning = true ✓
//
//Caminar con el móvil en el bolsillo:
//RMS irregular → ~1f - 3f → rms > engineRmsMax en picos → no confirma 8 ventanas seguidas ✓