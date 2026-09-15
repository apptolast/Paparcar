@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.data.sharedcar

import com.rndeveloper.paparcar.data.datasource.local.room.UserParkingDao
import com.rndeveloper.paparcar.data.datasource.local.room.UserProfileDao
import com.rndeveloper.paparcar.data.datasource.local.room.VehicleDao
import com.rndeveloper.paparcar.data.datasource.remote.RemoteSharedCarDataSource
import com.rndeveloper.paparcar.data.mapper.toAddressDto
import com.rndeveloper.paparcar.data.mapper.toDomain
import com.rndeveloper.paparcar.data.mapper.toDto
import com.rndeveloper.paparcar.data.mapper.toPlaceInfoDto
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.sharedcar.SharedCarMirror
import com.rndeveloper.paparcar.domain.sharedcar.sharedCarMirrorPayload
import com.rndeveloper.paparcar.domain.sharedcar.sharedCarStateOf
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock

/**
 * Writes the group's copy of a parking, hanging off the one door every session push goes through.
 * [VEH-SHARED-001]
 *
 * WHAT travels is not decided here — it is decided once, purely, by `sharedCarMirrorPayload()`.
 * This class only answers "is there a group to tell, and who is telling them", and does the I/O.
 *
 * ⛔ Every method is failure-swallowing on purpose: the personal parking is the record that must
 * not be lost, and a mirror that could fail it would trade the user's own data for a courtesy to
 * somebody else. What a failed mirror costs is one stale reading for the group, repaired by the
 * next push of the same session.
 */
class SharedCarMirrorWriter(
    private val remote: RemoteSharedCarDataSource,
    private val vehicleDao: VehicleDao,
    private val parkingDao: UserParkingDao,
    private val profileDao: UserProfileDao,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : SharedCarMirror {

    override suspend fun onSessionSaved(session: UserParking) {
        val carId = sharedCarIdOf(session.userId, session.vehicleId) ?: return
        guard("onSessionSaved", session.id) {
            val payload = sharedCarMirrorPayload(
                session = session,
                authorUid = session.userId,
                authorName = profileDao.getProfile(session.userId)?.displayName,
                nowMs = clock(),
            )
            remote.putSession(carId, payload.toDto())
            // The head of the mirror follows the session that was just written — including when it
            // arrives already closed (a replayed offline save), which is why the state is derived
            // from the payload rather than assumed to be "parked".
            remote.putState(carId, sharedCarStateOf(payload).toDto())
        }
    }

    override suspend fun onSessionClosed(sessionId: String, endedAtMs: Long) {
        val session = parkingDao.getById(sessionId)?.toDomain() ?: return
        val carId = sharedCarIdOf(session.userId, session.vehicleId) ?: return
        guard("onSessionClosed", sessionId) {
            val now = clock()
            remote.closeSession(carId, sessionId, endedAtMs, now)
            // ⛔ Only if this session is still the one the state is showing. Another member may
            // have parked the car since; marking THEIR parking as over because OUR older one
            // closed would tell the group the car is on the move when it is not.
            if (remote.getState(carId)?.sessionId == sessionId) {
                remote.setStateParked(carId, isParked = false, updatedAtMs = now)
            }
        }
    }

    override suspend fun onAddressResolved(
        sessionId: String,
        address: AddressInfo?,
        placeInfo: PlaceInfo?,
    ) {
        val session = parkingDao.getById(sessionId)?.toDomain() ?: return
        val carId = sharedCarIdOf(session.userId, session.vehicleId) ?: return
        guard("onAddressResolved", sessionId) {
            val now = clock()
            remote.updateSessionAddress(
                carId = carId,
                sessionId = sessionId,
                address = address?.toAddressDto(),
                placeInfo = placeInfo?.toPlaceInfoDto(),
                updatedAtMs = now,
            )
            val state = remote.getState(carId)
            if (state?.sessionId == sessionId) {
                remote.putState(
                    carId,
                    state.copy(
                        address = address?.toAddressDto(),
                        placeInfo = placeInfo?.toPlaceInfoDto(),
                        updatedAtMs = now,
                    ),
                )
            }
        }
    }

    /** The group this parking belongs to, or null — which is every parking of every ordinary car. */
    private suspend fun sharedCarIdOf(userId: String, vehicleId: String?): String? {
        val id = vehicleId ?: return null
        return runCatching { vehicleDao.getById(id, userId)?.sharedCarId }.getOrNull()
    }

    private suspend inline fun guard(what: String, sessionId: String, block: () -> Unit) {
        try {
            block()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Throwable) {
            PaparcarLogger.w(TAG, "$what($sessionId) did not reach the group — personal save stands", e)
        }
    }

    private companion object {
        const val TAG = "SharedCarMirror"
    }
}
