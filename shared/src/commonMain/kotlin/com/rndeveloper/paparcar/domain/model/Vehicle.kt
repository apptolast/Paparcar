package com.rndeveloper.paparcar.domain.model

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
    /** BT Classic / BLE device address paired with this vehicle. Synced to Firestore — it IS the
     *  pairing identity the BT strategy resolves against, on every device. (This KDoc claimed
     *  "never synced" until 08-09-2026; the DTO, mapper and datasource had synced it all along.)
     *  [VEH-FUEL-001] */
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
    /**
     * Optional paint colour. `null` = undefined → the vehicle pictogram renders in the default
     * brand-green artwork. A non-null value recolours only the body of the icon (side-profile and
     * top-down) so the user can tell their car apart on the map and in lists. Visual identity only —
     * does not affect detection, sizing, geofence or spot publishing.
     */
    val color: VehicleColor? = null,
    /**
     * What this vehicle burns. Mandatory at registration for [VehicleType.burnsFuel] types, null
     * for human-powered ones. Feeds cost/CO₂ estimates ONLY — never detection, sizing or identity.
     * Synced (not PII). [VEH-FUEL-001]
     */
    val fuelType: FuelType? = null,
    /**
     * User-overridden consumption per 100 km of the PRIMARY energy (litres, or kWh for an EV).
     * Null ⇒ the named default in [com.rndeveloper.paparcar.domain.vehicle.FuelDefaults] applies.
     */
    val consumptionPer100: Float? = null,
    /** User-overridden kWh/100 km of the SECONDARY energy — only meaningful for a plug-in hybrid. */
    val consumptionElectricPer100: Float? = null,
    /**
     * What the user actually pays for a litre. Null ⇒ the national average applies.
     * [FUEL-PRICE-RESOLVER-001]
     *
     * Same shape as [consumptionPer100] and for the same reason: an override the user can give and
     * a named default when they do not. `Money` and not a bare number because a price without its
     * currency is not a price — and the app is installed in nine countries.
     */
    val fuelPricePerLiter: Money? = null,
    /** What the user actually pays for a kWh. Null ⇒ the national average applies. */
    val energyPricePerKwh: Money? = null,
    /** Environmental badge (DGT names in Spain). Optional; stored for the regulated-zone rules. */
    val energyLabel: EnergyLabel? = null,
)
