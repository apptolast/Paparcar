package com.rndeveloper.paparcar.domain.usecase.detection

import com.rndeveloper.paparcar.domain.detection.DetectionPhase
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [DET-TIERS-001] Bluetooth-as-arbiter truth table: the deterministic paired-car edge SUPERSEDES a
 * running probabilistic coordinator session, it never scores. Disconnect confirms via BT; connect
 * while about to pin vetoes; anything else is a no-op.
 *
 * [DET-BT-WRONG-CAR-ABORT-001] A BT edge from a DIFFERENT own car refutes the session's
 * nominated-vehicle hypothesis (the phone cannot be in two cars): disconnect supersedes, connect
 * while driving yields — field 2026-08-10 double pin (Kamiq bt 19:50:29 + Focus steps+egress
 * 19:51:44) happened exactly because both used to be NoOp.
 */
class EvaluateBtArbitrationUseCaseTest {

    private val config = ParkingDetectionConfig()
    private val useCase = EvaluateBtArbitrationUseCase(config)
    private val car = "vehicle-A"

    /** `msSinceBtDisconnect = null` por defecto: sin sello de desconexión en disco no hay
     *  oscilación PROBADA, así que el arbitraje de siempre sigue en pie. Es lo que mantiene honestos
     *  los casos escritos antes de [DET-A-LANE-MUST-EARN-THE-TRIP-IT-SILENCES-001]. */
    private fun evaluate(
        event: BtArbitrationEvent,
        running: Boolean = true,
        phase: DetectionPhase = DetectionPhase.Driving,
        btVehicleId: String? = car,
        coordinatorVehicleId: String? = car,
        driveProven: Boolean = false,
        msSinceBtDisconnect: Long? = null,
    ) = useCase(event, running, phase, btVehicleId, coordinatorVehicleId, driveProven, msSinceBtDisconnect)

    // ── Una reconexión no es un embarque ────────────────────────────────────
    // [DET-A-LANE-MUST-EARN-THE-TRIP-IT-SILENCES-001]

    /**
     * Campo 22-09 (Nothing A001, BT *aftermarket* comprado aparte del coche). Dos sesiones con
     * conducción MEDIDA —92 fixes de 3,0 a 10,3 m/s en una de ellas— llegaron a `Candidate` con el
     * prompt en pantalla y murieron con `outcome = "ended"` justo cuando arrancaba la caminata de
     * egreso: en la de las 12:20 los pasos van 1→11 en 8 segundos y 3 s después se acabó. El enlace
     * se caía y volvía ~20 veces al día, y cada rebote vetaba el pin que estaba a punto de caer.
     *
     * Uno de los dos arrastró al siguiente: sin pin no hay geocerca, y sin geocerca la salida
     * siguiente no tiene EXIT que disparar. El usuario acabó marcando a mano.
     */
    @Test
    fun should_leaveTheCandidateAlone_when_theConnectIsTheLinkBouncing() {
        assertEquals(
            BtArbitrationVerdict.NoOp,
            evaluate(
                BtArbitrationEvent.CONNECT,
                phase = DetectionPhase.Candidate,
                driveProven = true,
                msSinceBtDisconnect = 17_000L, // el MAC del campo se caía cada 17 s
            ),
            "a reconnect inside the oscillation window is the same link bouncing, not a boarding",
        )
    }

    @Test
    fun should_leaveTheCandidateAlone_when_theLinkBouncesWithNoDriveProven() {
        // El discriminador es el RELOJ del flanco, no la conducción probada: un rebote sigue siendo
        // un rebote aunque el coordinator todavía no hubiera medido nada.
        assertEquals(
            BtArbitrationVerdict.NoOp,
            evaluate(
                BtArbitrationEvent.CONNECT,
                phase = DetectionPhase.Candidate,
                driveProven = false,
                msSinceBtDisconnect = 1_000L,
            ),
        )
    }

    @Test
    fun should_vetoTheReturn_when_theConnectComesLongAfterTheDisconnect() {
        // Aparcaste, te fuiste, y media hora después vuelves al coche: eso SÍ desmiente el pin.
        assertEquals(
            BtArbitrationVerdict.VetoReturnToVehicle,
            evaluate(
                BtArbitrationEvent.CONNECT,
                phase = DetectionPhase.Candidate,
                driveProven = true,
                msSinceBtDisconnect = 30 * 60_000L,
            ),
        )
    }

    @Test
    fun should_vetoTheReturn_when_theConnectLandsExactlyOnTheWindowEdge() {
        // El borde pertenece al VETO, no al rebote: la ventana es estrictamente «dentro de».
        assertEquals(
            BtArbitrationVerdict.VetoReturnToVehicle,
            evaluate(
                BtArbitrationEvent.CONNECT,
                phase = DetectionPhase.Candidate,
                msSinceBtDisconnect = config.btDisconnectDebounceMs,
            ),
        )
    }

    @Test
    fun should_vetoTheReturn_when_thereIsNoDisconnectStampOnDisk() {
        // Fallo asimétrico: la duda se resuelve del lado de no plantar un pin fantasma. Cubre la
        // instalación recién actualizada, cuyo store todavía no tiene sellos de desconexión.
        assertEquals(
            BtArbitrationVerdict.VetoReturnToVehicle,
            evaluate(
                BtArbitrationEvent.CONNECT,
                phase = DetectionPhase.Candidate,
                msSinceBtDisconnect = null,
            ),
        )
    }

    @Test
    fun should_stayNoOp_when_theLinkBouncesWhileStillDriving() {
        // La rama Driving ya era NoOp y sigue siéndolo: la ventana no la toca.
        assertEquals(
            BtArbitrationVerdict.NoOp,
            evaluate(
                BtArbitrationEvent.CONNECT,
                phase = DetectionPhase.Driving,
                msSinceBtDisconnect = 5_000L,
            ),
        )
    }

    @Test
    fun should_stillYield_when_aDifferentOwnCarConnectsInsideTheWindow() {
        // La ventana de rebote sólo habla del MISMO enlace. Otro coche propio conectando sigue
        // refutando la hipótesis del vehículo nominado. [DET-BT-WRONG-CAR-ABORT-001]
        assertEquals(
            BtArbitrationVerdict.YieldToConnectedCar,
            evaluate(
                BtArbitrationEvent.CONNECT,
                phase = DetectionPhase.Driving,
                btVehicleId = "vehicle-B",
                coordinatorVehicleId = car,
                msSinceBtDisconnect = 1_000L,
            ),
        )
    }

    // ── Un carril que puede no decidir no mata al que ya midió ──────────────
    // [DET-BT-THE-EDGE-NOMINATES-THE-DISPLACEMENT-DECIDES-001]

    /**
     * Campo 16-09 (Nothing A001): el manos libres emparejado como coche se cae cada 17 s. A las
     * 09:09:09 el coordinator registra `drive PROVEN by displacement` (181 m, 11,9 m/s); nueve
     * segundos después un disconnect de 10 s lo abortaba, y el propio carril BT declaraba acto
     * seguido que el enganche «no era un viaje» y tampoco colocaba nada. **36 sesiones así en 11
     * días** — nadie decidía, y la app acababa preguntando.
     */
    @Test
    fun should_leaveTheCoordinatorAlone_when_disconnectArrivesOnAMeasuredDrive() {
        assertEquals(
            BtArbitrationVerdict.NoOp,
            evaluate(BtArbitrationEvent.DISCONNECT, driveProven = true),
            "a disconnect must not kill a drive the coordinator already measured",
        )
    }

    @Test
    fun should_stillSupersede_when_disconnectArrivesOnAnUnprovenSession() {
        // La sesión sin conducción medida es una HIPÓTESIS, y el disconnect del coche emparejado es
        // determinista: ahí el arbitraje de siempre sigue mandando.
        assertEquals(
            BtArbitrationVerdict.SupersedeWithBluetooth,
            evaluate(BtArbitrationEvent.DISCONNECT, driveProven = false),
        )
    }

    @Test
    fun should_stillVetoTheReturn_when_connectArrivesOnAMeasuredDrive() {
        // La conducción medida no blinda la sesión contra TODO: volver a entrar en el coche sigue
        // desmintiendo un pin a punto de caer. Lo que se retira es el aborto por DISCONNECT.
        //
        // ⚠ `driveProven` NO es lo que separa un regreso de un rebote — con conducción medida son
        // verdad las dos lecturas. Lo que decide es el reloj del flanco, y aquí no hay sello de
        // desconexión (el defecto del helper), así que el veto de siempre sigue mandando.
        // [DET-A-LANE-MUST-EARN-THE-TRIP-IT-SILENCES-001]
        assertEquals(
            BtArbitrationVerdict.VetoReturnToVehicle,
            evaluate(BtArbitrationEvent.CONNECT, phase = DetectionPhase.Candidate, driveProven = true),
        )
    }

    // ── DISCONNECT ──────────────────────────────────────────────────────────

    @Test
    fun should_supersede_when_disconnectWithRunningSessionSameCar() {
        assertEquals(
            BtArbitrationVerdict.SupersedeWithBluetooth,
            evaluate(BtArbitrationEvent.DISCONNECT, phase = DetectionPhase.Candidate),
        )
    }

    @Test
    fun should_supersede_when_disconnectWhileDriving() {
        // A paired-MAC disconnect is authoritative even if the coordinator still thinks it's driving.
        assertEquals(
            BtArbitrationVerdict.SupersedeWithBluetooth,
            evaluate(BtArbitrationEvent.DISCONNECT, phase = DetectionPhase.Driving),
        )
    }

    @Test
    fun should_noOp_when_disconnectWithNoRunningSession() {
        // BT is primary and the coordinator is suppressed — the normal BT flow proceeds untouched.
        assertEquals(
            BtArbitrationVerdict.NoOp,
            evaluate(BtArbitrationEvent.DISCONNECT, running = false),
        )
    }

    @Test
    fun should_supersede_when_disconnectFromDifferentOwnCar() {
        // Field 2026-08-10: session nominated the Focus (geofence exit of its parked fence) while
        // the user actually drove the Kamiq; the Kamiq's disconnect at the destination must abort
        // the session — the park belongs to the BT car, a coordinator pin would be a misattributed
        // duplicate. [DET-BT-WRONG-CAR-ABORT-001]
        assertEquals(
            BtArbitrationVerdict.SupersedeWithBluetooth,
            evaluate(BtArbitrationEvent.DISCONNECT, btVehicleId = "vehicle-B", coordinatorVehicleId = car),
        )
    }

    @Test
    fun should_supersede_when_disconnectFromDifferentOwnCarWhileCandidate() {
        assertEquals(
            BtArbitrationVerdict.SupersedeWithBluetooth,
            evaluate(
                BtArbitrationEvent.DISCONNECT,
                phase = DetectionPhase.Candidate,
                btVehicleId = "vehicle-B",
                coordinatorVehicleId = car,
            ),
        )
    }

    @Test
    fun should_supersede_when_disconnectAndSessionOriginUnknown() {
        // Manual-start session has no resolved origin vehicle → trust the deterministic BT edge.
        assertEquals(
            BtArbitrationVerdict.SupersedeWithBluetooth,
            evaluate(BtArbitrationEvent.DISCONNECT, coordinatorVehicleId = null),
        )
    }

    // ── CONNECT ─────────────────────────────────────────────────────────────

    @Test
    fun should_veto_when_connectWhileCandidateSameCar() {
        assertEquals(
            BtArbitrationVerdict.VetoReturnToVehicle,
            evaluate(BtArbitrationEvent.CONNECT, phase = DetectionPhase.Candidate),
        )
    }

    @Test
    fun should_noOp_when_connectWhileDrivingSameCar() {
        // Reconnecting mid-trip to the SAME car is consistent with driving — no pin to veto.
        assertEquals(
            BtArbitrationVerdict.NoOp,
            evaluate(BtArbitrationEvent.CONNECT, phase = DetectionPhase.Driving),
        )
    }

    @Test
    fun should_yield_when_connectToDifferentOwnCarWhileDriving() {
        // Boarding a different own car mid-session (field 2026-08-10 19:44:18, BT enabled mid-drive)
        // proves the phone is in THAT car — the nominated-vehicle hypothesis is refuted; abort before
        // any pin forms. Bluetooth owns detection while connected. [DET-BT-WRONG-CAR-ABORT-001]
        assertEquals(
            BtArbitrationVerdict.YieldToConnectedCar,
            evaluate(
                BtArbitrationEvent.CONNECT,
                phase = DetectionPhase.Driving,
                btVehicleId = "vehicle-B",
                coordinatorVehicleId = car,
            ),
        )
    }

    @Test
    fun should_noOp_when_connectToDifferentOwnCarWhileCandidate() {
        // Park car A, walk to car B: the pending pin for A was earned with measured pre-connect
        // evidence — boarding B refutes the future, not the already-measured past. Let it land.
        assertEquals(
            BtArbitrationVerdict.NoOp,
            evaluate(
                BtArbitrationEvent.CONNECT,
                phase = DetectionPhase.Candidate,
                btVehicleId = "vehicle-B",
                coordinatorVehicleId = car,
            ),
        )
    }

    @Test
    fun should_noOp_when_connectWithNoRunningSession() {
        assertEquals(
            BtArbitrationVerdict.NoOp,
            evaluate(BtArbitrationEvent.CONNECT, running = false, phase = DetectionPhase.Candidate),
        )
    }

    @Test
    fun should_veto_when_connectCandidateAndSessionOriginUnknown() {
        assertEquals(
            BtArbitrationVerdict.VetoReturnToVehicle,
            evaluate(BtArbitrationEvent.CONNECT, phase = DetectionPhase.Candidate, coordinatorVehicleId = null),
        )
    }
}
