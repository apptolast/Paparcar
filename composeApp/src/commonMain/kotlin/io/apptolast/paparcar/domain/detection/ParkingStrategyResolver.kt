package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.bluetooth.BluetoothScanner
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleType
import io.apptolast.paparcar.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.first

/**
 * Which automatic-detection pipeline (if any) should own the current driving session.
 *
 * Resolution honours the **BT-owns-when-connected** invariant: BLUETOOTH is chosen only while the
 * phone is CONNECTED to a paired car (the ACL link is the ground truth of "I'm driving THIS car"),
 * regardless of which vehicle the user marked as primary (`isActive`). A car that is only paired
 * (sitting at home) no longer hijacks the strategy — driving a different, non-BT car resolves to
 * COORDINATOR. This decouples "primary vehicle for identity fallbacks" from "vehicle detection is
 * following right now". [DET-BT-CONNECTED-NOT-PAIRED-001]
 *
 * Resolution order (first match wins):
 * | Condition                                                      | Resolved   |
 * |----------------------------------------------------------------|------------|
 * | Primary vehicle type ∈ {SCOOTER, BIKE}                         | NONE       |
 * | CONNECTED to a paired car (bluetoothDeviceId, BT enabled, ACL up)| BLUETOOTH |
 * | Primary vehicle exists (and is not SCOOTER/BIKE)               | COORDINATOR|
 * | No primary vehicle                                              | COORDINATOR|
 *
 * Both BLUETOOTH and COORDINATOR converge on [ConfirmParkingUseCase]. NONE means
 * we skip parking detection entirely — scooters and bikes are dismounted on the
 * sidewalk and never liberate a parking spot. [BUG-SCOOTER-001]
 *
 * Note: when BLUETOOTH wins (connected to the car), Coordinator is suppressed even if the primary
 * vehicle has no BT pairing. Rationale: the BT receiver already covers the connected car
 * independently, and running Coordinator in parallel risks attributing that trip to the (non-BT)
 * primary. Once DISCONNECTED, the strategy falls back to COORDINATOR — safe because the Coordinator
 * demands measured driving to pin (a walk away from the car aborts) and a mid-session BT edge is
 * arbitrated by [EvaluateBtArbitrationUseCase]. [ARCH-MONITORING-002]
 */
enum class ParkingStrategy {
    /** No detection — vehicle type doesn't occupy parking spots. */
    NONE,
    /** Deterministic BT-disconnect strategy. */
    BLUETOOTH,
    /** Probabilistic Activity Recognition + GPS strategy. */
    COORDINATOR,
}

/**
 * [DET-STRATEGY-GATE-001] Single admission rule for ARMING the probabilistic coordinator: may
 * this trigger start a coordinator session under the resolved strategy?
 *
 * - [DetectionTrigger.MANUAL] is ALWAYS admitted — and ONLY it: the user explicitly asked to track,
 *   which outranks any strategy we resolved for them; if a BT-paired car then parks, the BT
 *   disconnect arbitration supersedes the session anyway ([EvaluateBtArbitrationUseCase]).
 *   [DET-HANDOFF-NOT-MANUAL-001] The safety net's arrival handoff used to ride this exemption by
 *   reusing the MANUAL trigger — an AUTOMATIC arm entering through the door reserved for human
 *   intent, which is precisely the bypass this gate exists to prevent. It now arrives as
 *   [DetectionTrigger.ARRIVAL_HANDOFF] and is gated like every other automatic nominator.
 * - Every automatic trigger (geofence EXIT, AR ENTER, significant motion, arrival handoff) is admitted only when
 *   the COORDINATOR strategy owns detection. Under BLUETOOTH the deterministic pipeline owns the
 *   trip (field 2026-08-01: the sentry/AR lanes armed anyway and pinned the Kamiq's trips on the
 *   primary Focus — the exact misattribution [ARCH-MONITORING-002] suppresses); under NONE the
 *   vehicle type never parks.
 *
 * The service consults this in ONE choke point (`startParkingDetection`); the geofence-EXIT lane
 * additionally short-circuits early with the same rule to skip its pre-arm work.
 */
fun coordinatorMayArm(strategy: ParkingStrategy, trigger: DetectionTrigger): Boolean =
    trigger == DetectionTrigger.MANUAL || strategy == ParkingStrategy.COORDINATOR

class ParkingStrategyResolver(
    private val vehicleRepository: VehicleRepository,
    private val bluetoothScanner: BluetoothScanner,
) {
    /**
     * Resolves the strategy by inspecting **all** registered vehicles. Reads BT state
     * at call time so toggling Bluetooth between sessions flips ownership cleanly.
     */
    suspend fun resolve(): ParkingStrategy = strategyFor(vehicleRepository.observeVehicles().first())

    /**
     * Pure decision over an already-fetched fleet — single source of truth for both the suspend
     * [resolve] and reactive callers that combine the vehicle stream themselves (e.g.
     * [io.apptolast.paparcar.domain.usecase.detection.ObserveDetectionReadinessUseCase]). Reads BT
     * adapter state at call time so toggling Bluetooth flips ownership cleanly. [DET-READY-001b]
     */
    fun strategyFor(vehicles: List<Vehicle>): ParkingStrategy {
        // [DET-BT-CONNECTED-NOT-PAIRED-001] BT owns detection only while the phone is CONNECTED to a
        // paired car — that connection is the ground truth of "I'm driving THIS car". Merely being
        // paired-and-enabled no longer hijacks the strategy: driving a DIFFERENT, non-BT car (with a
        // BT car sitting paired at home) now correctly resolves to COORDINATOR, whose resident FGS
        // watches that car. The BT car is still fully covered — its ACL disconnect is caught by the
        // manifest receiver independently of this resolver. SCOOTER/BIKE never count.
        val btPairedVehicleIds = vehicles.filter { it.isBtPairedAndParks() }.map { it.id }.toSet()
        if (btPairedVehicleIds.isNotEmpty() &&
            bluetoothScanner.isBluetoothEnabled() &&
            bluetoothScanner.isConnectedToPairedCar(btPairedVehicleIds)
        ) {
            return ParkingStrategy.BLUETOOTH
        }

        // No CONNECTED BT car. Coordinator monitors the primary; if the primary is a
        // type that never parks, suppress detection entirely. With no primary at
        // all, fall through to COORDINATOR (legacy "no vehicle" behaviour).
        val primary = vehicles.firstOrNull { it.isActive } ?: vehicles.firstOrNull()
        if (primary != null && primary.vehicleType in NON_PARKING_TYPES) {
            return ParkingStrategy.NONE
        }
        return ParkingStrategy.COORDINATOR
    }

    /**
     * Backwards-compatible boolean facade. Returns true only when the Coordinator
     * should run — false covers BOTH the BT strategy and the NONE case (caller can
     * call [resolve] directly to tell them apart for diagnostics).
     */
    suspend fun shouldUseCoordinator(): Boolean = resolve() == ParkingStrategy.COORDINATOR

    /**
     * `true` when some vehicle in the fleet is paired to a car-Bluetooth device (and is a type
     * that parks) — the one-time SETUP fact behind the BT strategy, deliberately independent of
     * the adapter's momentary on/off state that [strategyFor] additionally gates on. This is the
     * "deterministic trigger available" input of the reliability evaluator. [DET-RELIABILITY-001]
     */
    fun hasBtPairedParkingVehicle(vehicles: List<Vehicle>): Boolean =
        vehicles.any { it.isBtPairedAndParks() }

    private fun Vehicle.isBtPairedAndParks(): Boolean =
        bluetoothDeviceId != null && vehicleType !in NON_PARKING_TYPES

    private companion object {
        val NON_PARKING_TYPES = setOf(VehicleType.SCOOTER, VehicleType.BIKE)
    }
}
