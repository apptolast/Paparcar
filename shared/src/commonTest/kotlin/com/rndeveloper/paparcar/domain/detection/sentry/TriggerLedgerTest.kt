package com.rndeveloper.paparcar.domain.detection.sentry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [DET-EVERY-TRIGGER-LEAVES-A-TRACE-001] The daily ledger that gives the trigger lane's events a
 * collectable parent. Its wire form is covered next door in `DetectionEventDtoTest` — this package
 * may not import `data` (ArchitectureTest), and the split is the right one anyway: the bucket is a
 * domain fact, the columns it lands in are not.
 */
class TriggerLedgerTest {

    @Test
    fun should_file_every_event_of_the_same_day_under_one_id_when_bucketing() {
        val dayStart = 1_787_349_600_000L / DAY * DAY
        val ids = listOf(dayStart, dayStart + 1, dayStart + DAY / 2, dayStart + DAY - 1)
            .map { triggerLedgerSessionId(it) }
            .toSet()
        assertEquals(1, ids.size, "one bucket per day, was $ids")
    }

    @Test
    fun should_open_a_new_id_when_the_day_rolls_over() {
        val dayStart = 1_787_349_600_000L / DAY * DAY
        assertTrue(
            triggerLedgerSessionId(dayStart + DAY - 1) != triggerLedgerSessionId(dayStart + DAY),
            "the bucket must roll with the day",
        )
    }

    /**
     * The reason the ledger exists at all. `cleanupExpiredSessions` finds sessions by querying
     * `startedAt < cutoff`, so the header's stamp is the retention key. Pinning it to the bucket
     * START rather than to whichever trigger happened to arrive first keeps the id and the
     * retention key in agreement — otherwise two devices writing the same bucket disagree on when
     * it ages out.
     */
    @Test
    fun should_stamp_the_bucket_start_not_the_event_time_when_headering() {
        val dayStart = 1_787_349_600_000L / DAY * DAY
        val late = dayStart + DAY - 1
        assertEquals(dayStart, triggerLedgerStartedAtMs(late))
        assertEquals(
            triggerLedgerSessionId(late),
            triggerLedgerSessionId(triggerLedgerStartedAtMs(late)),
            "the startedAt must fall inside the bucket it heads",
        )
    }

    @Test
    fun should_agree_across_every_instant_of_a_bucket_when_stamping() {
        val dayStart = 1_787_349_600_000L / DAY * DAY
        for (offset in listOf(0L, 1L, 3_600_000L, DAY / 2, DAY - 1)) {
            assertEquals(
                dayStart,
                triggerLedgerStartedAtMs(dayStart + offset),
                "offset $offset drifted out of its bucket",
            )
        }
    }

    private companion object {
        const val DAY = 24L * 60L * 60L * 1_000L
    }
}
