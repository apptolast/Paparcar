@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.data.sync

import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import com.rndeveloper.paparcar.data.mapper.toAddressDto
import com.rndeveloper.paparcar.data.mapper.toParkingHistoryDto
import com.rndeveloper.paparcar.data.mapper.toPlaceInfoDto
import com.rndeveloper.paparcar.domain.geocoder.enrichParkingSession
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.SpotStatus
import com.rndeveloper.paparcar.domain.model.SpotTtlPolicy
import com.rndeveloper.paparcar.domain.repository.SpotRepository
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.sync.OutboxOp
import com.rndeveloper.paparcar.domain.sync.SyncOutbox
import com.rndeveloper.paparcar.domain.sync.reportSpotExpiredInQueue
import com.rndeveloper.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Drains the durable outbox: one attempt per op per drain, serially, under a mutex — the retry
 * cadence is the DRAIN cadence (enqueue-time, app-open, mesh wakes, BGTask), never an in-memory
 * backoff loop the process can take to its grave. A failed op stays with `attempts + 1`; at
 * [MAX_OP_ATTEMPTS] it is dropped WITH A LOG (a poisoned op must not clog the queue forever —
 * and a silent drop would be a lie). [IOS-SYNC-A-QUEUE-THAT-DIES-WITH-THE-PROCESS-001]
 *
 * commonMain on purpose: only the STORE is platform (NSUserDefaults on iOS). Android keeps
 * WorkManager today; if it ever adopts the outbox, this class is already there.
 */
class SyncOutboxDrainer(
    private val outbox: SyncOutbox,
    private val remoteDataSource: RemoteUserProfileDataSource,
    private val authRepository: AuthRepository,
    private val spotRepository: SpotRepository,
    private val userParkingRepository: UserParkingRepository,
    private val getAddressAndPlace: GetAddressAndPlaceUseCase,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val mutex = Mutex()

    suspend fun drain() {
        mutex.withLock {
            val ops = outbox.pending()
            if (ops.isEmpty()) return
            PaparcarLogger.d(TAG, "draining ${ops.size} op(s)")
            for (op in ops) {
                val done = try {
                    execute(op)
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    PaparcarLogger.w(TAG, "op ${op.key} failed (attempt ${op.attempts + 1})", e)
                    false
                }
                when {
                    done -> outbox.remove(op.key)
                    op.attempts + 1 >= MAX_OP_ATTEMPTS -> {
                        PaparcarLogger.e(TAG, "✗ op ${op.key} dropped after $MAX_OP_ATTEMPTS attempts")
                        outbox.remove(op.key)
                    }
                    else -> outbox.persist(op.withAttempts(op.attempts + 1))
                }
            }
        }
    }

    /** True = the op is finished (succeeded, or has provably nothing left to do). */
    private suspend fun execute(op: OutboxOp): Boolean = when (op) {
        is OutboxOp.SaveSession -> {
            val userId = userId() ?: return false
            // Room is the truth: re-read at drain time. Row gone → nothing to sync, op done.
            val session = userParkingRepository.getSessionById(op.sessionId) ?: return true
            op.previousSessionId?.let { remoteDataSource.clearParkingSessionActiveFlag(userId, it) }
            // [SYNC-A-PARKING-MUST-TRAVEL-WHOLE-001] Fresh updatedAt at SEND time — a replayed
            // save must still win Last-Write-Wins over the stale remote it is correcting.
            remoteDataSource.saveParkingSession(
                userId,
                session.toParkingHistoryDto(updatedAt = clock()),
            )
            true
        }
        is OutboxOp.ClearActive -> {
            val userId = userId() ?: return false
            remoteDataSource.clearParkingSessionActiveFlag(userId, op.sessionId)
            true
        }
        is OutboxOp.UpdateAddressAndPlace -> {
            val userId = userId() ?: return false
            val session = userParkingRepository.getSessionById(op.sessionId) ?: return true
            if (session.address == null && session.placeInfo == null) return true // nothing to send
            remoteDataSource.updateParkingSessionAddressAndPlace(
                userId,
                op.sessionId,
                session.address?.toAddressDto(),
                session.placeInfo?.toPlaceInfoDto(),
            )
            true
        }
        is OutboxOp.DeleteVehicle -> {
            val userId = userId() ?: return false
            // [SYNC-A-REMOTE-DELETE-HAS-NO-OUTBOX-BEHIND-IT-001] Sessions FIRST, then the vehicle
            // doc — surviving session docs are what the inbound reconcile resurrects. This op IS
            // the tombstone: persisted before the first attempt, removed only here.
            remoteDataSource.deleteParkingSessionsForVehicle(userId, op.vehicleId)
            remoteDataSource.deleteVehicle(userId, op.vehicleId)
            true
        }
        is OutboxOp.EnrichSession -> {
            // ⛔ NOT done until the POI question has an answer. This used to be a literal copy of
            // the Android worker's loop, bug included: `saved` was raised by the phase-1 street
            // emission, so a dead Overpass took the op out of the outbox with the session holding
            // an address and no place, and nothing ever asked again. One function now, in
            // commonMain. [POI-A-FAILED-LOOKUP-IS-NOT-AN-ANSWER-001]
            enrichParkingSession(
                sessionId = op.sessionId,
                answers = getAddressAndPlace(op.lat, op.lon, persisting = true)
                    .catch { PaparcarLogger.w(TAG, "geocoder failure for ${op.sessionId}", it) },
                repository = userParkingRepository,
            )
        }
        is OutboxOp.ReportSpot -> {
            val now = clock()
            if (reportSpotExpiredInQueue(op.enqueuedAtMs, op.provisional, now)) {
                // The golden gate: a spot that expired IN THE QUEUE is never published — ghost
                // availability is exactly the asymmetric-failure doctrine's forbidden side.
                PaparcarLogger.w(TAG, "spot ${op.spotId} expired in queue — dropped, not published")
                true
            } else {
                spotRepository.reportSpotReleased(op.toSpot(now)).isSuccess
            }
        }
    }

    private suspend fun userId(): String? = authRepository.getCurrentSession()?.userId

    private companion object {
        const val TAG = "PARKDIAG/SyncOutbox"
        const val MAX_OP_ATTEMPTS = 20
    }
}

/** The replayed spot, rebuilt from the persisted minimum. The TTL keeps counting from the
 *  ENQUEUE moment — a replay never extends a spot's life. Address/placeInfo are absent by
 *  design (see the op's KDoc). */
internal fun OutboxOp.ReportSpot.toSpot(nowMs: Long): Spot = Spot(
    id = spotId,
    location = GpsPoint(latitude = lat, longitude = lon, accuracy = 0f, timestamp = nowMs, speed = 0f),
    reportedBy = reportedBy,
    address = null,
    placeInfo = null,
    type = spotType,
    confidence = confidence,
    sizeCategory = sizeCategory,
    carbodyType = carbodyType,
    expiresAt = enqueuedAtMs + SpotTtlPolicy.ttlMs(provisional),
    status = if (provisional) SpotStatus.PROVISIONAL else SpotStatus.CONFIRMED,
)
