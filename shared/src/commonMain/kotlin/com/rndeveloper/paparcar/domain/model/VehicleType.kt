package com.rndeveloper.paparcar.domain.model

/**
 * Type of vehicle, used to decide whether automatic parking detection applies
 * and (paired with the runtime trip profile) to flag mismatched detections.
 *
 * `CAR` / `MOTORCYCLE` are the detection-eligible types — Activity Recognition's
 * `IN_VEHICLE` aligns well with their movement profile. `SCOOTER` / `BIKE` opt
 * out of Coordinator scoring: AR classifies them as `IN_VEHICLE` too, but the
 * stop-and-park pattern produces false spots (see BUG-SCOOTER-001).
 *
 * Existing rows pre-dating this field default to [CAR] in the v3 → v4 Room
 * migration; new vehicles must be explicit during registration.
 *
 * ## [VEH-A-NEW-VEHICLE-TYPE-MUST-NOT-BE-A-CAR-BY-OMISSION-001] Four questions, declared here
 *
 * Everything this enum implies used to be spelled out at the site that needed it —
 * `vehicleType == SCOOTER || vehicleType == BIKE` in the human-power verdict, the same pair again as
 * a `NON_PARKING_TYPES` set in the strategy resolver, the same pair a third time in the confirm
 * policy, and `== CAR` five times across registration. A type added tomorrow would have inherited
 * every one of those answers by omission: a car that parks, with a car body, whose slow trips are
 * suspicious — whatever it actually is.
 *
 * The four properties below are exhaustive `when`s, so **a new type does not compile until its
 * author answers all four**. They are deliberately four and not one: they agree on today's four
 * types by coincidence, not by meaning. A moped has a motor and does not park in a car space; a
 * cargo bike parks in one and has no motor at all.
 */
enum class VehicleType {
    CAR,
    MOTORCYCLE,
    SCOOTER,
    BIKE,
    ;

    /**
     * No engine: the movement is muscle. [BUG-SCOOTER-001][DET-BIKE-NOT-A-CAR-001]
     *
     * A registered bike or scooter never auto-confirms, whatever Activity Recognition thinks — AR
     * reports `IN_VEHICLE` for both, and one downhill sprint over 18 km/h makes the whole session
     * look like a car to every speed-based signal.
     *
     * ⚠️ This is the answer about the GARAGE, not about this trip: `isHumanPoweredRide` asks the
     * same question of the ride and can say yes where this says no (a `CAR` profile on a bicycle,
     * field 2026-08-16).
     */
    val isHumanPowered: Boolean
        get() = when (this) {
            SCOOTER, BIKE -> true
            CAR, MOTORCYCLE -> false
        }

    /**
     * Occupies a parking space the community would want to know about. [BUG-SCOOTER-001]
     *
     * What the strategy resolver reads: a primary vehicle that never parks suppresses detection
     * entirely (`ParkingStrategy.NONE`), and a Bluetooth pairing on such a vehicle does not make it
     * the detection strategy either.
     *
     * ⚠️ Not a synonym of [isHumanPowered] even though the two agree on all four types today: a
     * moped would answer *motorised* AND *does not take a car space*, and the two questions are read
     * by different lanes for different reasons.
     */
    val parksInASpot: Boolean
        get() = when (this) {
            CAR, MOTORCYCLE -> true
            SCOOTER, BIKE -> false
        }

    /**
     * Has a car body, so registration asks for a [CarbodyType] and derives [VehicleSize] from it.
     * Everything else is sized as [VehicleSize.MOTORCYCLE] and persists a null body. [VEH-FREETEXT-001]
     */
    val hasCarbody: Boolean
        get() = when (this) {
            CAR -> true
            MOTORCYCLE, SCOOTER, BIKE -> false
        }

    /**
     * A long, slow trip is evidence this profile is WRONG — the scooter mismatch guard.
     * [BUG-SCOOTER-001]
     *
     * True only for [CAR]: the guard exists because a `CAR` profile sustaining moped speeds for
     * minutes is more likely a misfiled vehicle than a car in traffic, and it suppresses the silent
     * save in favour of asking. [MOTORCYCLE] answers `false` on purpose — it already IS the small
     * slow-capable vehicle, so a slow trip contradicts nothing — and the human-powered types are
     * stopped earlier by [isHumanPowered], which never auto-confirms at all.
     */
    val slowTripContradictsProfile: Boolean
        get() = when (this) {
            CAR -> true
            MOTORCYCLE, SCOOTER, BIKE -> false
        }
}
