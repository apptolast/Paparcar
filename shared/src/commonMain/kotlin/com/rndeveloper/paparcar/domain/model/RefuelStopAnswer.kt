package com.rndeveloper.paparcar.domain.model

/**
 * What the user has said about a stop the app read as a visit to a pump.
 * [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001]
 *
 * Lives on [UserParking.refuelStop], where **null is the fourth value and the normal one**: this
 * session was never read as a refuel stop at all. Only a session the app flagged carries one of
 * these, which is why the flag and the answer are ONE column instead of two — a boolean candidate
 * plus a nullable verdict can contradict each other (answered but not a candidate), and nothing
 * downstream could tell which half to believe.
 *
 * ⛔ **The answer never decides the publication.** Whether a community spot went out was settled at
 * departure, by [com.rndeveloper.paparcar.domain.spot.SpotReleasePolicy], from the measurement —
 * and it is not reopened by a «No» arriving two days later from the history. The question enriches
 * the user's own record; it does not hand a forecourt to the community retroactively.
 */
enum class RefuelStopAnswer {
    /** Flagged, not yet answered — the state a candidate is born in. The question survives app
     *  restarts and is offered on Home and in the vehicle's history until it is answered. */
    PENDING,

    /** «Sí»: the user refuelled here. The only state the figures count. */
    CONFIRMED,

    /** «No»: the user did not refuel. The question is closed — it is never asked about this
     *  session again — and the stop simply stays out of the refuel figures. */
    DISMISSED,
}
