package com.rndeveloper.paparcar.domain.sync

import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.SpotTtlPolicy
import com.rndeveloper.paparcar.domain.model.SpotStatus
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.model.VehicleSize
import kotlinx.serialization.Serializable

/**
 * The durable outbox: every remote side-effect a scheduler owes becomes a persisted [OutboxOp]
 * BEFORE it is attempted, and is removed only on success — so a process death mid-flight loses
 * nothing. [IOS-SYNC-A-QUEUE-THAT-DIES-WITH-THE-PROCESS-001]
 *
 * Payload doctrine: ops carry IDENTITY, not data, wherever Room already holds the truth —
 * [SaveSession] and [UpdateAddressAndPlace] re-read the session at drain time (which also stamps
 * a FRESH `updatedAt` for Last-Write-Wins, strictly better than freezing one at enqueue).
 * [ReportSpot] is the exception (a freed spot has no Room row) and persists a minimal payload —
 * WITHOUT `AddressInfo`/`PlaceInfo`, which stay free of serialization annotations: a spot
 * replayed after process death arrives without its street label; the first in-memory attempt
 * carries the full payload. A declared degradation, not an accident.
 *
 * [DeleteVehicle] is the op this outbox exists for: an update lost to process death self-heals
 * via `pendingSync`, but a delete has no row left to hang a flag on — without a tombstone the
 * inbound reconcile resurrects the car (the measured TODO this closes).
 */
@Serializable
sealed class OutboxOp {
    /** REPLACE key: a newer op for the same fact supersedes the queued one. */
    abstract val key: String

    /** Drain attempts so far — a poisoned op is dropped (with a log) at the cap, it never
     *  clogs the queue forever. */
    abstract val attempts: Int

    abstract fun withAttempts(attempts: Int): OutboxOp

    @Serializable
    data class SaveSession(
        val sessionId: String,
        val previousSessionId: String?,
        override val attempts: Int = 0,
    ) : OutboxOp() {
        override val key get() = "save:$sessionId"
        override fun withAttempts(attempts: Int) = copy(attempts = attempts)
    }

    @Serializable
    data class ClearActive(val sessionId: String, override val attempts: Int = 0) : OutboxOp() {
        override val key get() = "clear:$sessionId"
        override fun withAttempts(attempts: Int) = copy(attempts = attempts)
    }

    @Serializable
    data class UpdateAddressAndPlace(val sessionId: String, override val attempts: Int = 0) : OutboxOp() {
        override val key get() = "addr:$sessionId"
        override fun withAttempts(attempts: Int) = copy(attempts = attempts)
    }

    @Serializable
    data class DeleteVehicle(val vehicleId: String, override val attempts: Int = 0) : OutboxOp() {
        override val key get() = "delveh:$vehicleId"
        override fun withAttempts(attempts: Int) = copy(attempts = attempts)
    }

    @Serializable
    data class EnrichSession(
        val sessionId: String,
        val lat: Double,
        val lon: Double,
        override val attempts: Int = 0,
    ) : OutboxOp() {
        override val key get() = "enrich:$sessionId"
        override fun withAttempts(attempts: Int) = copy(attempts = attempts)
    }

    @Serializable
    data class ReportSpot(
        val spotId: String,
        val lat: Double,
        val lon: Double,
        val spotType: SpotType,
        val confidence: Float,
        val sizeCategory: VehicleSize?,
        val carbodyType: CarbodyType?,
        val reportedBy: String,
        /**
         * ⛔ **Legacy, and it stays for exactly one reason**: this op is persisted to disk, so a
         * queue written by the previous build is deserialized by this one. Dropping the field would
         * make those ops unreadable; reading it as false would hand a deduced spot the full
         * two-hour lifetime. Never written any more — [claim] is. [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001]
         */
        val provisional: Boolean = false,
        /** What the publication claims about the space. Null ONLY for ops persisted before this
         *  field existed; read it through [effectiveClaim], never directly. */
        val claim: SpotStatus? = null,
        /** When the report was ENQUEUED — the TTL-in-queue gate measures from here. */
        val enqueuedAtMs: Long,
        override val attempts: Int = 0,
    ) : OutboxOp() {
        override val key get() = "spot:$spotId"
        override fun withAttempts(attempts: Int) = copy(attempts = attempts)

        /** The claim this queued publication carries, whichever build wrote it. */
        val effectiveClaim: SpotStatus
            get() = claim ?: if (provisional) SpotStatus.PROVISIONAL else SpotStatus.CONFIRMED
    }
}

/** Durable op store. REPLACE semantics by [OutboxOp.key]; implementations persist synchronously
 *  (the whole point is surviving the very next instant). */
interface SyncOutbox {
    fun persist(op: OutboxOp)
    fun pending(): List<OutboxOp>
    fun remove(key: String)
}

/**
 * The Android worker's golden gate, made pure: a spot that EXPIRED while queued must not be
 * published — on iOS the queue can legitimately wait hours for a BGTask, and publishing a
 * spot whose lifetime already ran out plants ghost availability. [SPOT TTL-in-queue]
 */
fun reportSpotExpiredInQueue(enqueuedAtMs: Long, claim: SpotStatus, nowMs: Long): Boolean =
    nowMs >= enqueuedAtMs + SpotTtlPolicy.ttlMs(claim)
