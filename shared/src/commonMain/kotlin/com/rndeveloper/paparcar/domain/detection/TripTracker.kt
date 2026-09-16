package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.detection.ports.DrivingRouteStore
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.domain.util.haversineMeters
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Records a trip's driven route WITHOUT any parking-decision authority — the recorder the BT lane
 * was missing. [TRIP-THE-TRACKER-RIDES-THE-SENTINEL-001]
 *
 * Feeds [DrivingRouteStore] from the same two taps the coordinator's detection job uses (the
 * adaptive primary stream + the zero-cost passive piggyback [ROUTE-PASSIVE-FILL-001]), so a trip
 * recorded by this class and one recorded during a coordinator session produce the same route.
 * When the BT lane's disconnect later confirms the park, `ConfirmParkingUseCase.encodeFreshRoute`
 * (common, the convergence point) finds the store full and attaches a MEASURED `routePolyline` —
 * the BT lane itself does not change.
 *
 * ⛔ **Tracking must not influence detection**: this class holds no evaluator, arms nothing, and
 * never touches `DetectionRuntimeState`. Its only output is route points; its only verdict is
 * about its own shutoff ([BtTripShutoff] — the garage-radio null trip).
 *
 * [SHARED-A-BT-CONNECT-PROVES-WHO-TOOK-THE-CAR-001] …and `onDrivingProven` does not break that
 * rule: it REPORTS a measurement, it does not act on one. Nothing it triggers arms, nominates or
 * confirms a parking — it tells a shared car's group that the car is on the move, which is the one
 * fact this class is the first to know for certain.
 *
 * [track] returns normally ONLY on that null-trip shutoff; a real trip records until the caller
 * cancels (BT disconnect, or a detection job superseding the recorder with its own taps).
 */
class TripTracker(
    private val drivingRouteStore: DrivingRouteStore,
    private val nowMs: () -> Long,
) {

    /**
     * @param onDrivingProven se invoca **una sola vez**, en cuanto el viaje deja de poder ser un
     *   null trip. Es el único instante en que este móvil sabe con certeza que el coche al que se
     *   conectó va a algún sitio. Sus fallos no pueden tumbar la grabación.
     */
    suspend fun track(
        primary: Flow<GpsPoint>,
        passive: Flow<GpsPoint>,
        onDrivingProven: suspend () -> Unit = {},
    ): Unit = coroutineScope {
        val startedAtMs = nowMs()
        var firstFix: GpsPoint? = null
        var sawDrivingSpeed = false
        var maxDisplacementM: Double? = null

        val collectors = launch {
            launch {
                primary
                    .catch { e -> PaparcarLogger.w(DIAG, "⚠ tracker primary tap failed: ${e.message}") }
                    .collect { point ->
                        drivingRouteStore.append(point)
                        val first = firstFix ?: point.also { firstFix = it }
                        if (point.speed >= BtTripShutoff.DRIVING_SPEED_MPS) sawDrivingSpeed = true
                        val displacement = haversineMeters(
                            first.latitude, first.longitude, point.latitude, point.longitude,
                        )
                        if (displacement > (maxDisplacementM ?: 0.0)) maxDisplacementM = displacement
                    }
            }
            launch {
                passive
                    .catch { e -> PaparcarLogger.w(DIAG, "⚠ tracker passive tap failed: ${e.message}") }
                    .collect { drivingRouteStore.append(it) }
            }
        }

        var announced = false
        while (true) {
            delay(SHUTOFF_CHECK_INTERVAL_MS)
            // ⛔ Las MISMAS dos pruebas que absuelven del null trip, no un umbral nuevo: la
            // velocidad declarada cuando afirma movimiento, o el desplazamiento medido cuando esa
            // velocidad nunca llega. Un viaje real de arrastre —el que el campo `speed` no cuenta—
            // es tan viaje como el otro, y con una sola de las dos el aviso al grupo se perdería
            // justo en los móviles cuyo GPS calla. [SHARED-A-BT-CONNECT-PROVES-WHO-TOOK-THE-CAR-001]
            val proven = sawDrivingSpeed ||
                (maxDisplacementM ?: 0.0) >= BtTripShutoff.NULL_TRIP_RADIUS_M
            if (proven && !announced) {
                announced = true
                runCatching { onDrivingProven() }
                    .onFailure { PaparcarLogger.w(DIAG, "⚠ onDrivingProven threw: ${it.message}") }
            }
            if (sawDrivingSpeed) {
                // Driving observed — the null-trip question is settled for this ride. Record until
                // the caller cancels; join() suspends with the collectors.
                collectors.join()
                break
            }
            if (BtTripShutoff.isNullTrip(startedAtMs, nowMs(), sawDrivingSpeed, maxDisplacementM)) {
                PaparcarLogger.d(
                    DIAG,
                    "■ null trip — no driving in ${BtTripShutoff.NULL_TRIP_WINDOW_MS / 60_000} min, sampling shut off",
                )
                collectors.cancel()
                break
            }
        }
    }

    private companion object {
        /** Cadence of the shutoff watchdog — a check is a couple of comparisons, once a minute. */
        const val SHUTOFF_CHECK_INTERVAL_MS = 60_000L

        const val DIAG = "PARKDIAG/TripTracker"
    }
}
