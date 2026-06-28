package io.apptolast.paparcar.domain.usecase.detection

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.util.haversineMeters

/**
 * Outcome of evaluating an Activity-Recognition IN_VEHICLE_ENTER as a re-arm signal. [DET-AR-REARM-001]
 *
 * Kept as a sealed result (not a bare Boolean) so the arming site can log *why* it did or did not
 * arm — the single most useful thing during field diagnosis of a phantom or missed arm.
 */
sealed interface VehicleEnterArmDecision {
    /** Arm: the phone is within proximity of the parked car, so this is the user's OWN car. */
    data class Arm(val distanceMeters: Double) : VehicleEnterArmDecision

    /** Do not arm: no active parked session to anchor against. */
    data object NoActiveSession : VehicleEnterArmDecision

    /** Do not arm: no GPS fix available, so proximity cannot be verified (fail closed). */
    data object NoFix : VehicleEnterArmDecision

    /** Do not arm: vehicle boarded too far from the parked car (a bus / taxi / friend's car). */
    data class TooFar(val distanceMeters: Double) : VehicleEnterArmDecision
}

/**
 * Decides whether an IN_VEHICLE_ENTER transition should arm the Coordinator. [DET-AR-REARM-001]
 *
 * This is the *anchor* half of the geofence's two-part signal reconstructed in software: the
 * geofence EXIT proves "I left the place where my car is", and the downstream speed/egress gates
 * prove "at vehicle speed". AR alone fires on any vehicle (bus, taxi, a friend's car); the proximity
 * gate restores the anchor — boarding a vehicle *where the car was parked* is overwhelmingly the
 * user's own car.
 *
 * **The proximity radius IS the parked car's geofence radius** (size-based base + GPS-accuracy
 * padding, via [ParkingDetectionConfig.geofenceRadiusFor]) — the same boundary the geofence EXIT
 * uses. Equal-by-construction so AR and the EXIT meet at one line: no dead ring between them (which a
 * smaller flat constant would leave for vans/poor GPS), and no extra bus surface (which a larger one
 * would open for motorcycles). The proximity gate — not the egress gate — is the decisive defence
 * against the bus/taxi false positive, because a bus ride satisfies the egress gate (drive + walk
 * away); so it must sit exactly on the anchor.
 *
 * Fails closed: with no parked session or no GPS fix it does NOT arm, accepting a false negative
 * (the geofence EXIT remains the primary path) to avoid a phantom session.
 */
class ShouldArmFromVehicleEnterUseCase(
    private val config: ParkingDetectionConfig,
) {
    operator fun invoke(
        currentFix: GpsPoint?,
        parkedSession: UserParking?,
    ): VehicleEnterArmDecision {
        if (parkedSession == null) return VehicleEnterArmDecision.NoActiveSession
        if (currentFix == null) return VehicleEnterArmDecision.NoFix

        val parked = parkedSession.location
        val proximityRadius = config.geofenceRadiusFor(parkedSession.sizeCategory, parked.accuracy)
        val distance = haversineMeters(
            currentFix.latitude,
            currentFix.longitude,
            parked.latitude,
            parked.longitude,
        )
        return if (distance <= proximityRadius) {
            VehicleEnterArmDecision.Arm(distance)
        } else {
            VehicleEnterArmDecision.TooFar(distance)
        }
    }
}
