package io.apptolast.paparcar.domain.model

/**
 * Represents a vehicle registered by the user.
 *
 * Privacy rules:
 * - [brand] and [model] are shared on [Spot] only when [showBrandModelOnSpot] = true.
 * - [bluetoothDeviceId] is synced to Firestore so the pairing survives the
 *   syncFromRemote replace on bootstrap and across reinstall. A MAC address is
 *   not personally identifying without device proximity, and the alternative
 *   (on-device-only + defensive merge) added too many failure modes.
 * - [licensePlate] is on-device only and is never sent to Firestore or shared on Spot.
 *
 * Each user may have multiple vehicles; exactly one is [isActive] at a time.
 */
data class Vehicle(
    val id: String,
    val userId: String,
    /**
     * Optional friendly name chosen by the user (e.g. "My Golf", "Work Van").
     * Private always — never sent to Firestore or shared on Spot.
     * Conditionally required: if both [brand] and [model] are blank, [name] must be set.
     */
    val name: String? = null,
    /** Optional brand shown on freed spot (e.g. "Toyota"). */
    val brand: String? = null,
    /** Optional model shown on freed spot (e.g. "Corolla"). */
    val model: String? = null,
    /** Length-based size category used on Spot for space estimation and geofence radius. */
    val sizeCategory: VehicleSize,
    /** Body-shape category. Non-null only when [vehicleType] = CAR; the registration screen
     *  infers it from brand+model via VehicleCatalog and lets the user override it manually. */
    val carbodyType: CarbodyType? = null,
    /** Mandatory vehicle type. Controls whether automatic detection runs (CAR/MOTORCYCLE) or is
     *  suppressed (SCOOTER/BIKE), and feeds the vehicle-mismatch prompt heuristic. [BUG-SCOOTER-001] */
    val vehicleType: VehicleType = VehicleType.CAR,
    /** BT Classic / BLE device address paired with this vehicle. On-device only, never synced. */
    val bluetoothDeviceId: String? = null,
    /** Whether this vehicle's brand/model should appear on the public Spot. */
    val showBrandModelOnSpot: Boolean = false,
    /** The vehicle currently used for detection and spot reporting. Only one is active at a time. */
    val isActive: Boolean = false,
    /**
     * Optional license plate (e.g. "1234 ABC"). On-device only — never sent to Firestore or
     * shared on Spot. Displayed on the map marker so the user recognises their car at a glance.
     */
    val licensePlate: String? = null,
)
