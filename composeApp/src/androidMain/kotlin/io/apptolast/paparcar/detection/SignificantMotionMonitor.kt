package io.apptolast.paparcar.detection

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import androidx.work.WorkManager
import io.apptolast.paparcar.BuildConfig
import io.apptolast.paparcar.detection.worker.ParkingSafetyNetWorker
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.util.PaparcarLogger

/**
 * Hardware wake-up trigger for the parked-session safety net. [DET-SIGMOTION-001]
 *
 * `TYPE_SIGNIFICANT_MOTION` is a ONE-SHOT sensor that runs on the sensor-hub co-processor: armed,
 * it costs ~zero battery and fires once when the device starts moving after being still — walking
 * or driving, it cannot tell. It does not need to: it is a dumb alarm clock. The brain stays
 * unique — the trigger enqueues [ParkingSafetyNetWorker], whose evaluator applies the SAME
 * evidence rules as the geofence-exit path (speed with credible accuracy, AR ENTER ordering).
 *
 * Why it exists: the geofence EXIT and Activity Recognition both live inside Play Services, whose
 * background sampling an OEM can starve (missed departure, field incident 2026-07-04). This
 * sensor is the one departure wake-up that does not pass through GmsCore at all.
 *
 * Known limitation, by design: a sensor listener does NOT survive process death (there is no
 * PendingIntent API for sensors, unlike geofencing). It is therefore the *immediacy* layer only;
 * the 15-min periodic worker is the resurrection layer and re-arms this monitor on every tick
 * (WorkManager revives the process, [ParkingSafetyNetWorker] calls [sync]).
 *
 * One-shot semantics: after a trigger the sensor auto-disarms; we deliberately do NOT re-arm it
 * here. The enqueued check re-arms via [sync] only if a parked session still exists and detection
 * is idle — otherwise every stride of a walk (or every stop-and-go during a drive) would re-fire.
 */
class SignificantMotionMonitor(
    private val context: Context,
    private val notificationPort: AppNotificationManager,
) {

    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)

    private var armed = false

    private val listener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            synchronized(this@SignificantMotionMonitor) { armed = false }
            PaparcarLogger.d(TAG, "▶ significant motion — enqueueing safety-net check [DET-SIGMOTION-001]")
            debugNotify("SIG-MOTION disparado → chequeo safety-net")
            // A sensor callback is NOT an FGS-start exemption on Android 12+ — expedited work is
            // the legal fast lane from here.
            ParkingSafetyNetWorker.enqueueCheckNow(
                WorkManager.getInstance(context),
                source = ParkingSafetyNetWorker.SOURCE_SIG_MOTION,
            )
        }
    }

    /** Idempotently arms (parked + detection idle) or disarms (anything else) the trigger. */
    @Synchronized
    fun sync(shouldBeArmed: Boolean) {
        val sensorManager = sensorManager ?: return
        val sensor = sensor ?: run {
            // Distinguish "device has no sensor" from "sync never ran" in field captures.
            if (shouldBeArmed) PaparcarLogger.d(TAG, "sync → wanted armed but NO significant-motion hardware")
            return
        }
        when {
            shouldBeArmed && !armed -> {
                armed = sensorManager.requestTriggerSensor(listener, sensor)
                PaparcarLogger.d(TAG, "sync → armed=$armed")
                debugNotify(if (armed) "SIG-MOTION armado (aparcado, detección idle)" else "SIG-MOTION ✗ no se pudo armar")
            }
            !shouldBeArmed && armed -> {
                runCatching { sensorManager.cancelTriggerSensor(listener, sensor) }
                armed = false
                PaparcarLogger.d(TAG, "sync → disarmed")
                debugNotify("SIG-MOTION desarmado (sin sesión o detección en curso)")
            }
        }
    }

    /** DEBUG-build breadcrumb of the trigger lifecycle. [DET-SIGMOTION-001] */
    private fun debugNotify(message: String) {
        if (BuildConfig.DEBUG) notificationPort.showDebug(message)
    }

    private companion object {
        // PARKDIAG prefix: FileAntilog only persists tags with it — an unprefixed tag made this
        // monitor invisible in field captures (2026-07-06: impossible to tell if it ever armed).
        const val TAG = "PARKDIAG/SigMotion"
    }
}
