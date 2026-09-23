package com.rndeveloper.paparcar.domain.usecase.detection

import com.rndeveloper.paparcar.domain.detection.DetectionPhase
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig

/** Which physical Bluetooth edge fired from the PAIRED car. [DET-TIERS-001] */
enum class BtArbitrationEvent { CONNECT, DISCONNECT }

/**
 * The arbiter's ruling over an in-progress probabilistic Coordinator session when a deterministic
 * Bluetooth edge from the paired car fires. [DET-TIERS-001]
 *
 * Bluetooth NEVER enters the coordinator's scoring (hard project rule) — it does not add a signal,
 * it SUPERSEDES the whole session.
 */
sealed interface BtArbitrationVerdict {
    /** No running coordinator session to override, or an edge consistent with the session — leave
     *  the coordinator untouched; the normal Bluetooth flow proceeds. */
    data object NoOp : BtArbitrationVerdict

    /** DISCONNECT while a coordinator session runs: the user has left the paired car. Cancel the
     *  coordinator's probabilistic ladder/prompt and let the deterministic Bluetooth path confirm the
     *  park (reliabilityBluetooth) — the event nominates, the paired-MAC disconnect is the measured
     *  proof of egress. */
    data object SupersedeWithBluetooth : BtArbitrationVerdict

    /** CONNECT while a coordinator session is about to pin (Candidate/prompt): the user is back IN
     *  the car, so the tentative park is false. Discard the candidate/prompt and re-seal the anchor
     *  (asymmetric failure — better no pin than a phantom one).
     *
     *  ⛔ Only for a connect that is a real BOARDING. A reconnect landing inside the oscillation
     *  window of the disconnect that opened this stop is the same link bouncing, not a person
     *  getting back in — see [DET-A-LANE-MUST-EARN-THE-TRIP-IT-SILENCES-001]. */
    data object VetoReturnToVehicle : BtArbitrationVerdict

    /** CONNECT to a DIFFERENT own paired car while the coordinator is still driving: the phone is
     *  provably inside that other car, so the session's nominated vehicle (a geofence-exit HYPOTHESIS
     *  — the fence only proves the PHONE left, not that THAT car moved) is refuted. Abort the session;
     *  Bluetooth owns detection while connected [DET-BT-CONNECTED-NOT-PAIRED-001]. Without this the
     *  coordinator confirms a phantom re-park of the wrong car at the destination (field 2026-08-10:
     *  double pin Kamiq bt + Focus steps+egress 75 s apart). [DET-BT-WRONG-CAR-ABORT-001] */
    data object YieldToConnectedCar : BtArbitrationVerdict
}

/**
 * Pure arbiter — Bluetooth as the deterministic OVERRIDE of the probabilistic coordinator, never a
 * scoring signal. [DET-TIERS-001]
 *
 *  - a DISCONNECT while a session runs = the paired car's engine/link dropped → the user is leaving
 *    it → confirm via Bluetooth and cancel the coordinator's ladder;
 *  - a CONNECT while a session is about to pin (Candidate) = the user is back in the car → veto the
 *    pending pin — **unless the link is merely bouncing** (below);
 *  - a CONNECT during plain Driving is consistent with a trip and left alone — unless it is a
 *    DIFFERENT own car (below).
 *
 * **Una reconexión no es un embarque** [DET-A-LANE-MUST-EARN-THE-TRIP-IT-SILENCES-001]. Campo 22-09
 * (Nothing A001, BT *aftermarket* comprado aparte del coche): el enlace se cae y vuelve ~20 veces al
 * día, y cada rebote que caía con el coordinator en `Candidate` mataba la sesión — sin pin, sin
 * nudge y sin outcome (`"ended"`, el valor de nacimiento). Dos aparcamientos perdidos ese día, uno de
 * ellos arrastrando al siguiente: sin pin no hay geocerca, y sin geocerca la salida siguiente no
 * tiene EXIT que disparar. El usuario acabó marcando a mano.
 *
 * El discriminador **no** puede ser [coordinatorDriveProven]: con conducción probada son verdad las
 * DOS lecturas (volviste al coche tras aparcar / el manos libres rebotó). Lo que las separa es el
 * reloj del propio flanco — un regreso real al coche no ocurre dentro de la ventana de oscilación de
 * la desconexión que abrió la parada, y esa ventana ya existe en este proyecto
 * ([ParkingDetectionConfig.btDisconnectDebounceMs], la misma que gobierna la caza BT-005).
 *
 * ⛔ Sin sello de desconexión en disco la respuesta es **veto**, no «pase»: la duda se resuelve del
 * lado del fallo asimétrico (mejor un pin perdido que uno fantasma), igual que el carril BT trata un
 * connect ausente como duda y no como permiso.
 *
 * Vehicle identity [DET-BT-WRONG-CAR-ABORT-001]: every event reaching this arbiter is from one of
 * the user's OWN paired cars (the receiver drops unknown MACs), and the phone can only be in one
 * car. The session's `coordinatorVehicleId` is a NOMINATION HYPOTHESIS (a geofence exit only proves
 * the phone left the area); a BT edge from a different own car refutes it:
 *  - DISCONNECT from a different own car → the user was in THAT car and just left it. The park that
 *    is physically happening belongs to the BT car and the BT path will place it deterministically;
 *    a coordinator pin for the nominated car would always be a misattributed duplicate → supersede.
 *  - CONNECT to a different own car while Driving → the drive being measured from here on is the BT
 *    car's; abort before any pin forms (this kills the field bug at boarding time, minutes before
 *    the pin). While Candidate → NoOp: the pending pin is backed by measured pre-connect evidence
 *    (park car A, walk to car B); aborting could only lose a real park (asymmetric failure prefers
 *    acting on proof, and the proof here refutes the future, not the already-measured past).
 *  - Unknown origin (either id null) keeps trusting the deterministic paired-MAC edge as before.
 *
 * The prerequisite that makes the arbiter trustworthy — the BT speed gate, walk-away timeout and
 * tests of [EvaluateBtParkUseCase] — already landed with DET-AUDIT-002. The out-of-range disconnect
 * of a parked own car cannot reach the different-car branch with a live session: while connected to
 * it the coordinator is not allowed to arm [DET-BT-CONNECTED-NOT-PAIRED-001], and a connect that
 * happens mid-session yields the session first.
 */
class EvaluateBtArbitrationUseCase(private val config: ParkingDetectionConfig) {

    operator fun invoke(
        event: BtArbitrationEvent,
        coordinatorRunning: Boolean,
        coordinatorPhase: DetectionPhase,
        btVehicleId: String?,
        coordinatorVehicleId: String?,
        /** ¿El track de la sesión en curso ha CORROBORADO una conducción?
         *
         *  ⛔ Sin valor por defecto a propósito: un `= false` dejaría que el siguiente call site
         *  naciera matando conducción medida sin haberlo decidido, que es exactamente el bug que
         *  este parámetro cierra. [DET-BT-THE-EDGE-NOMINATES-THE-DISPLACEMENT-DECIDES-001] */
        coordinatorDriveProven: Boolean,
        /** Milisegundos desde la ÚLTIMA desconexión ACL registrada del coche emparejado, o `null` si
         *  no hay sello en disco. Es lo que separa un rebote del enlace de un embarque de verdad.
         *
         *  ⛔ Sin valor por defecto a propósito, por la misma razón que [coordinatorDriveProven]: un
         *  `= null` dejaría que el siguiente call site naciera vetando conducción medida sin haberlo
         *  decidido. [DET-FAIL-CLOSED-BY-CONSTRUCTION-001] */
        msSinceBtDisconnect: Long?,
    ): BtArbitrationVerdict {
        // Nothing to supersede when no session is live.
        if (!coordinatorRunning) return BtArbitrationVerdict.NoOp

        // A different OWN car's edge refutes the session's nominated-vehicle hypothesis — the phone
        // cannot be in two cars. [DET-BT-WRONG-CAR-ABORT-001]
        if (isDifferentOwnCar(btVehicleId, coordinatorVehicleId)) {
            return when (event) {
                BtArbitrationEvent.DISCONNECT -> BtArbitrationVerdict.SupersedeWithBluetooth
                BtArbitrationEvent.CONNECT -> when (coordinatorPhase) {
                    DetectionPhase.Driving -> BtArbitrationVerdict.YieldToConnectedCar
                    // The pending pin was earned with pre-connect measured evidence — let it land.
                    DetectionPhase.Candidate -> BtArbitrationVerdict.NoOp
                }
            }
        }

        return when (event) {
            // ⛔ …salvo que el coordinator ya haya MEDIDO la conducción.
            // [DET-BT-THE-EDGE-NOMINATES-THE-DISPLACEMENT-DECIDES-001]
            //
            // El disconnect del propio coche NOMINA un aparcamiento; no lo prueba. Y su carril puede
            // acabar sin veredicto — un enganche que no movió el coche aborta, y uno que no se pudo
            // medir sólo pregunta. Superseder ahí dejaba la sesión HUÉRFANA: el carril que mata no
            // decide, y el que podía decidir ya está muerto. Campo 16-09 (Nothing A001): 36 sesiones
            // abortadas en 11 días por un MAC que se cae cada 17 s, varias 9 s después de
            // `drive PROVEN by displacement`; el usuario acabó marcando sus aparcamientos a mano.
            //
            // Con conducción probada el coordinator está midiendo algo real, así que se le deja
            // terminar: si el coche se ha parado de verdad, lo confirma él; si sigue rodando (la
            // desconexión fue una oscilación del manos libres a media marcha), sigue midiendo. El
            // carril BT no pierde nada — su caza corre igual y ambos convergen, como siempre, en
            // `ConfirmParkingUseCase`.
            //
            // ⛔ Esto NO mete señales BT en el scoring del coordinator: cambia quién puede ABORTAR a
            // quién, que es arbitraje. Los carriles siguen sin mezclarse.
            BtArbitrationEvent.DISCONNECT ->
                if (coordinatorDriveProven) {
                    BtArbitrationVerdict.NoOp
                } else {
                    BtArbitrationVerdict.SupersedeWithBluetooth
                }
            // ⛔ …salvo que el enlace sólo esté rebotando.
            // [DET-A-LANE-MUST-EARN-THE-TRIP-IT-SILENCES-001]
            BtArbitrationEvent.CONNECT -> when (coordinatorPhase) {
                DetectionPhase.Candidate ->
                    if (isLinkOscillation(msSinceBtDisconnect)) {
                        BtArbitrationVerdict.NoOp
                    } else {
                        BtArbitrationVerdict.VetoReturnToVehicle
                    }
                DetectionPhase.Driving -> BtArbitrationVerdict.NoOp
            }
        }
    }

    /** Both identities known and distinct — the BT car is provably NOT the session's car. A null on
     *  either side means "origin unknown" → trust the deterministic paired-MAC edge (same-car path). */
    private fun isDifferentOwnCar(btVehicleId: String?, coordinatorVehicleId: String?): Boolean =
        btVehicleId != null && coordinatorVehicleId != null && btVehicleId != coordinatorVehicleId

    /**
     * ¿Este CONNECT es el rebote de la desconexión que abrió la parada, en vez de un embarque?
     *
     * Mismo listón que usa la caza BT-005 para decidir si una desconexión fue una oscilación
     * ([ParkingDetectionConfig.btDisconnectDebounceMs]): si el umbral fuera otro, el servicio podría
     * cancelar la caza por rebote y a la vez haber vetado la sesión por embarque — dos respuestas a
     * la misma pregunta.
     *
     * `null` = sin sello en disco → **no** es una oscilación probada, así que el veto sigue en pie.
     */
    private fun isLinkOscillation(msSinceBtDisconnect: Long?): Boolean =
        msSinceBtDisconnect != null && msSinceBtDisconnect < config.btDisconnectDebounceMs
}
