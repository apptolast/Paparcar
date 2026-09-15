package com.rndeveloper.paparcar.domain.sharedcar

import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.model.UserParking

/**
 * The hook the ONE door hangs the mirror on. [VEH-SHARED-001]
 *
 * Every parking reaches Firestore through a single place — the remote profile data source — and
 * that is deliberate: the save worker, the iOS outbox drainer, the pending-push and the in-place
 * mutations all converge there, which is why [PRIV-A-TRACE-IS-NOT-A-STAT-001] put the geometry
 * strip at the same spot. The group mirror hangs off the same door for the same reason: a second
 * write path is a second place to forget.
 *
 * ⛔ **Nothing here may fail the personal save.** The parking is the user's own record; the mirror
 * is a courtesy to the group. Every implementation swallows and logs, and the next push of the same
 * session repairs what a failure left behind.
 */
interface SharedCarMirror {

    /** A parking was written. Mirrors it when its vehicle belongs to a group; no-op otherwise. */
    suspend fun onSessionSaved(session: UserParking)

    /** A parking ended. The car's last position STAYS — only "still parked" turns false. */
    suspend fun onSessionClosed(sessionId: String, endedAtMs: Long)

    /** The geocoder answered late, as it usually does. The group sees the street too. */
    suspend fun onAddressResolved(sessionId: String, address: AddressInfo?, placeInfo: PlaceInfo?)
}
