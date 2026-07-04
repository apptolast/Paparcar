package io.apptolast.paparcar.domain.detection

/**
 * Narrow port through which the departure pipeline notifies a live detection session that the
 * departure was confirmed after the arm (late evidence — AR ENTER can deliver ~2 min after the
 * geofence exit). Implemented by `CoordinatorParkingDetector`; extracted as an interface so
 * [io.apptolast.paparcar.domain.usecase.parking.RunDepartureCheckUseCase] is testable without
 * constructing the whole detector. [DET-G-05][DET-SOLID-001]
 */
interface DepartureConfirmationListener {
    /** Upgrade the RUNNING session with confirmed-departure evidence. No-op between sessions. */
    fun notifyDepartureConfirmed()
}
