package io.apptolast.paparcar.detection

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import androidx.work.WorkManager
import io.apptolast.paparcar.BuildConfig
import io.apptolast.paparcar.detection.service.CoordinatorDetectionService
import io.apptolast.paparcar.detection.worker.ParkingSafetyNetWorker
import io.apptolast.paparcar.domain.detection.DetectionRuntimeState
import io.apptolast.paparcar.domain.detection.ServicePresence
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
    // [DET-RESIDENT-FGS-001] Read-only view of the service lifecycle: a trigger fired while the
    // service is resident in SENTRY wakes the LIVE process directly (no WorkManager latency); from
    // a dead process the sensor callback is not an FGS-start exemption, so the worker is the lane.
    private val detectionRuntime: DetectionRuntimeState,
) {

    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)

    private var armed = false

    private val listener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            synchronized(this@SignificantMotionMonitor) { armed = false }
            // [DET-RESIDENT-FGS-001] When the service is resident in SENTRY the process is ALREADY
            // foreground, so re-delivering a start intent is legal (not a background FGS start) and
            // skips the WorkManager tick entirely — the immediacy win of residency. From any other
            // presence (Dead: no process; Active: a job already runs) fall back to the durable,
            // legal-from-dead worker lane exactly as before.
            if (detectionRuntime.presence.value == ServicePresence.Sentry) {
                PaparcarLogger.d(TAG, "▶ significant motion — SENTRY resident, direct wake [DET-RESIDENT-FGS-001]")
                debugNotify("Sensor de movimiento: el móvil se ha movido y el centinela está vivo → arranco la detección al instante (siguiente aviso: 'Detección ARRANCADA')")
                runCatching {
                    context.startService(
                        Intent(context, CoordinatorDetectionService::class.java)
                            .setAction(CoordinatorDetectionService.ACTION_SENTRY_WAKE),
                    )
                }.onFailure { e ->
                    // The process may have been killed between the presence read and startService;
                    // fall back to the worker so the departure is never dropped.
                    PaparcarLogger.w(TAG, "  ⚠ direct SENTRY wake failed (${e.message}) — worker fallback")
                    ParkingSafetyNetWorker.enqueueCheckNow(
                        WorkManager.getInstance(context),
                        source = ParkingSafetyNetWorker.SOURCE_SIG_MOTION,
                    )
                }
                return
            }
            PaparcarLogger.d(TAG, "▶ significant motion — enqueueing safety-net check [DET-SIGMOTION-001]")
            debugNotify("Sensor de movimiento: el móvil se ha movido (sin centinela vivo) → pido a la red de seguridad que compruebe si te estás alejando del coche")
            // A sensor callback is NOT an FGS-start exemption on Android 12+ — expedited work is
            // the legal fast lane from here.
            ParkingSafetyNetWorker.enqueueCheckNow(
                WorkManager.getInstance(context),
                source = ParkingSafetyNetWorker.SOURCE_SIG_MOTION,
            )
        }
    }

    /** [DET-SENTRY-COOLDOWN-001] elapsedRealtime deadline before which [sync] refuses to re-arm.
     *  Lives HERE, not in any caller, because three independent mirrors call [sync] (the service's
     *  enterSentry, the safety-net worker tick, the exact heartbeat) — a cooldown any of them could
     *  bypass would not be a cooldown. In-memory only: a process death resets it, and with it the
     *  storm state it damps — acceptable, the storm needs a live resident process to exist. */
    private var rearmBlockedUntilElapsedMs = 0L

    /** [DET-SENTRY-COOLDOWN-001] Applies (cooldownMs > 0) or clears (== 0) the re-arm quiet
     *  period. Called by the service teardown after folding the ended session into the
     *  walking-abort streak (`nextSentryWakeAbortStreak`/`sentryWakeRearmCooldownMs`). */
    @Synchronized
    fun applyRearmCooldown(cooldownMs: Long) {
        rearmBlockedUntilElapsedMs = if (cooldownMs > 0) {
            android.os.SystemClock.elapsedRealtime() + cooldownMs
        } else {
            0L
        }
        if (cooldownMs > 0) {
            PaparcarLogger.d(TAG, "re-arm cooldown for ${cooldownMs / 1000}s — walking-abort storm damper [DET-SENTRY-COOLDOWN-001]")
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
        // [DET-SENTRY-COOLDOWN-001] Quiet period after a walking-abort storm: refuse to re-arm
        // (the geofence EXIT / AR / safety-net lanes keep watching); the next mirror tick past the
        // deadline arms normally. Disarm requests are always honored.
        val cooldownLeftMs = rearmBlockedUntilElapsedMs - android.os.SystemClock.elapsedRealtime()
        if (shouldBeArmed && !armed && cooldownLeftMs > 0) {
            PaparcarLogger.d(TAG, "sync → arm SUPPRESSED, cooldown ${cooldownLeftMs / 1000}s left [DET-SENTRY-COOLDOWN-001]")
            debugNotify("Sensor de movimiento en PAUSA ${cooldownLeftMs / 1000}s: varios paseos seguidos lo despertaron sin que hubiera viaje — valla/AR y red de seguridad siguen vigilando")
            return
        }
        when {
            shouldBeArmed && !armed -> {
                armed = sensorManager.requestTriggerSensor(listener, sensor)
                PaparcarLogger.d(TAG, "sync → armed=$armed")
                debugNotify(
                    if (armed) {
                        "Sensor de movimiento ARMADO: coche aparcado y detección en reposo → un movimiento fuerte del móvil disparará una comprobación"
                    } else {
                        "Sensor de movimiento NO se pudo armar → tu salida dependerá de valla/AR y de la red de seguridad periódica"
                    },
                )
            }
            !shouldBeArmed && armed -> {
                runCatching { sensorManager.cancelTriggerSensor(listener, sensor) }
                armed = false
                PaparcarLogger.d(TAG, "sync → disarmed")
                debugNotify("Sensor de movimiento DESARMADO: no hay plaza aparcada o la detección ya está trabajando — no hace falta vigilar")
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
