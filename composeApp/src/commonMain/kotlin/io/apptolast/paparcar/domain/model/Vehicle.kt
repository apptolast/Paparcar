package io.apptolast.paparcar.domain.model

/**
 * Represents a vehicle registered by the user.
 *
 * Privacy rules:
 * - [brand] and [model] are shared on [Spot] only when [showBrandModelOnSpot] = true.
 * - [bluetoothDeviceId] stays on-device and is never sent to Firestore.
 * - No license plate is stored — [sizeCategory] is enough for community use.
 *
 * Each user may have multiple vehicles; exactly one is [isDefault] at a time.
 */
data class Vehicle(
    val id: String,
    val userId: String,
    /** Optional brand shown on freed spot (e.g. "Toyota"). */
    val brand: String? = null,
    /** Optional model shown on freed spot (e.g. "Corolla"). */
    val model: String? = null,
    /** Mandatory size category used on Spot for space estimation. */
    val sizeCategory: VehicleSize,
    /** BT Classic / BLE device address paired with this vehicle. On-device only, never synced. */
    val bluetoothDeviceId: String? = null,
    /** Whether this vehicle's brand/model should appear on the public Spot. */
    val showBrandModelOnSpot: Boolean = false,
    /** The currently active vehicle used for detection and spot reporting. */
    val isDefault: Boolean = false,
)
