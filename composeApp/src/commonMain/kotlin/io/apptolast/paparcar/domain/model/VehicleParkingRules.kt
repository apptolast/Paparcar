package io.apptolast.paparcar.domain.model

/**
 * Physical constraints a parking spot must satisfy for a given [CarbodyType].
 *
 * Derived in pure Kotlin so it can be reused on both platforms and exercised
 * by unit tests without any Compose dependency. The textual advice that
 * accompanies the rules lives behind [alertKey] — the actual translated copy
 * is resolved in the UI via a Compose `stringResource` mapping so locales
 * stay centralised in `strings.xml`.
 */
data class VehicleParkingRules(
    /** Minimum useful plaza width in meters (door-opening clearance included). */
    val minPlazaWidthMeters: Double,
    /** True when the vehicle does not fit under standard underground-garage clearance (~2.10 m). */
    val requiresHighCeiling: Boolean,
    /** Resolves to the user-visible alert label via the UI layer. */
    val alertKey: ParkingAlertKey,
)

/** Identifies which advisory copy to show in the carbody info card. */
enum class ParkingAlertKey {
    /** Vehicle is tall — warn about garage clearance bars. */
    HIGH_CEILING,
    /** Vehicle is long — warn about overhang on short bays. */
    LONG_CAR,
    /** Vehicle is wide — warn about lateral columns. */
    WIDE_CAR,
    /** Fits the vast majority of spots without caveats. */
    STANDARD,
}

/**
 * Computes the [VehicleParkingRules] for a given [CarbodyType].
 *
 * Thresholds are kept on the companion object (per CLAUDE.md "no magic
 * numbers" rule) so future tuning can happen in one place and tests can
 * reference the same constants.
 */
fun CarbodyType.getParkingRules(): VehicleParkingRules {
    val minWidth = when (this) {
        CarbodyType.SUV_MEDIUM,
        CarbodyType.SUV_LARGE -> MIN_WIDTH_SUV_METERS
        CarbodyType.VAN_COMMERCIAL,
        CarbodyType.PICKUP -> MIN_WIDTH_COMMERCIAL_METERS
        else -> MIN_WIDTH_STANDARD_METERS
    }
    val highCeiling = sizeCategory == VehicleSize.VAN_HIGH
    val alert = when {
        highCeiling -> ParkingAlertKey.HIGH_CEILING
        this == CarbodyType.SEDAN || this == CarbodyType.FAMILY_LONG -> ParkingAlertKey.LONG_CAR
        minWidth >= MIN_WIDTH_SUV_METERS -> ParkingAlertKey.WIDE_CAR
        else -> ParkingAlertKey.STANDARD
    }
    return VehicleParkingRules(minWidth, highCeiling, alert)
}

private const val MIN_WIDTH_STANDARD_METERS = 2.20
private const val MIN_WIDTH_SUV_METERS = 2.40
private const val MIN_WIDTH_COMMERCIAL_METERS = 2.50
