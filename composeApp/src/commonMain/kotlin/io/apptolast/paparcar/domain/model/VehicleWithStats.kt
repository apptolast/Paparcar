package io.apptolast.paparcar.domain.model

data class VehicleWithStats(
    val vehicle: Vehicle,
    val sessionCount: Int,
    val lastSession: UserParking?,
)
