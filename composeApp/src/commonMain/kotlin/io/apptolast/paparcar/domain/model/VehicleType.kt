package io.apptolast.paparcar.domain.model

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
 */
enum class VehicleType {
    CAR,
    MOTORCYCLE,
    SCOOTER,
    BIKE,
}
