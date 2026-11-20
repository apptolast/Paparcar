package com.rndeveloper.paparcar.domain.detection

/**
 * The three interruptions sharing a six-hour budget per vehicle.
 * Recovery notifications with no saved location (unattended, orphan pending and lost BT fix)
 * retain their existing doors.
 * [DET-AN-HONEST-CLOSE-THAT-GOT-IT-RIGHT-MUST-NOT-APOLOGISE-001]
 */
enum class ParkingAskKind {
    StillParked,
    WhereDidYouPark,
    RefineApproximateMark;

    val verdictLabel: String
        get() = when (this) {
            StillParked -> "safety_net_prompt"
            WhereDidYouPark -> "safety_net_arrival_ask"
            RefineApproximateMark -> "honest_close_ask"
        }
}

/** The budget refills at the boundary. An absent or invalid stamp cannot mute recovery forever. */
fun parkingAskIsDue(lastAskAtMs: Long?, nowMs: Long, throttleMs: Long): Boolean {
    val last = lastAskAtMs?.takeIf { it > 0L } ?: return true
    val elapsed = nowMs - last
    if (elapsed < 0L) return true
    return elapsed >= throttleMs
}

/**
 * Durable vehicle stamps. RaiseParkingAskUseCase serializes access and stamps before notification.
 * The platform's geofence janitor must preserve these across session replacement.
 */
interface ParkingAskBudget {
    fun lastAskAtMs(vehicleId: String): Long?
    fun stampAsk(vehicleId: String, atMs: Long)
}
