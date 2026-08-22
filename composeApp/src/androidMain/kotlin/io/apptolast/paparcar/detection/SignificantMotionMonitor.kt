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
            // [DET-CHEAP-WAKE-INSTEAD-OF-SILENCE-001] Inside a quiet period the wake is not skipped,
            // it is made cheap: the service buys ONE fix and only escalates to a full session if
            // that fix cannot be a walk. The flag is read here, at trigger time, so it reflects the
            // quiet period as it stands NOW rather than when the sensor was armed.
            val triageOnly = synchronized(this@SignificantMotionMonitor) { inQuietPeriod() }
            if (detectionRuntime.presence.value == ServicePresence.Sentry) {
                PaparcarLogger.d(
                    TAG,
                    if (triageOnly) {
                        "▶ significant motion — SENTRY resident, CHEAP triage (quiet period) [DET-CHEAP-WAKE-INSTEAD-OF-SILENCE-001]"
                    } else {
                        "▶ significant motion — SENTRY resident, direct wake [DET-RESIDENT-FGS-001]"
                    },
                )
                debugNotify(
                    if (triageOnly) {
                        "Sensor de movimiento: varios paseos seguidos lo despertaron sin viaje, así que compruebo con UNA sola lectura de posición antes de arrancar nada"
                    } else {
                        "Sensor de movimiento: el móvil se ha movido y el centinela está vivo → arranco la detección al instante (siguiente aviso: 'Detección ARRANCADA')"
                    },
                )
                runCatching {
                    context.startService(
                        Intent(context, CoordinatorDetectionService::class.java)
                            .setAction(CoordinatorDetectionService.ACTION_SENTRY_WAKE)
                            .putExtra(CoordinatorDetectionService.EXTRA_TRIAGE_ONLY, triageOnly),
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
    /** [DET-CHEAP-WAKE-INSTEAD-OF-SILENCE-001] Is a quiet period running right now? Not a public
     *  policy question — it only decides whether the NEXT trigger buys one fix or a whole session.
     *  Callers must hold this monitor's lock (both call sites are `@Synchronized` / synchronized). */
    private fun inQuietPeriod(): Boolean =
        rearmBlockedUntilElapsedMs - android.os.SystemClock.elapsedRealtime() > 0

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
        // [DET-CHEAP-WAKE-INSTEAD-OF-SILENCE-001] The quiet period NO LONGER suppresses the arm.
        // It used to `return` here, leaving the cheapest nominator in the system switched off — and
        // on 2026-08-22 the Redmi earned a quiet period from three aborts in 114 s and then drove at
        // 75 km/h three minutes later, saved only because the fence happened to deliver its EXIT.
        // The sensor stays armed; what the quiet period changes now is what a trigger BUYS
        // ([inQuietPeriod] → one fix, not a session).
        val cooldownLeftMs = rearmBlockedUntilElapsedMs - android.os.SystemClock.elapsedRealtime()
        if (shouldBeArmed && !armed && cooldownLeftMs > 0) {
            PaparcarLogger.d(TAG, "sync → arming in CHEAP-TRIAGE mode, quiet period ${cooldownLeftMs / 1000}s left [DET-CHEAP-WAKE-INSTEAD-OF-SILENCE-001]")
            debugNotify("Sensor de movimiento en modo AHORRO ${cooldownLeftMs / 1000}s: varios paseos seguidos lo despertaron sin que hubiera viaje, así que cada aviso se comprobará con una sola lectura antes de arrancar nada")
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
