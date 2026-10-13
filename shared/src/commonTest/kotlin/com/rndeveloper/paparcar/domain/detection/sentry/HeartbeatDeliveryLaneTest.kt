package com.rndeveloper.paparcar.domain.detection.sentry

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [DET-HEARTBEAT-LANE-REPAIR-001] Which door the heartbeat knocks on.
 *
 * Field 2026-08-21, Oppo CPH2371: the broadcast lane died at 21:16:56 — both receivers in the same
 * second — while the service lane kept delivering. At 22:09:23 one geofence event fired both
 * PendingIntents and only the service one arrived. Moving the fast lane there is the fix; the two
 * guards below are what stop the fix from costing more than the bug.
 */
class HeartbeatDeliveryLaneTest {

    @Test
    fun should_use_the_service_lane_when_the_alarm_is_exact_and_the_sentry_is_resident() {
        // The only combination where the service lane is both LEGAL (exact alarm ⇒ the FGS
        // background-start exemption) and FREE (already-foreground service ⇒ no new notification).
        assertEquals(
            HeartbeatLane.SERVICE,
            heartbeatDeliveryLane(canScheduleExact = true, sentryResident = true),
        )
    }

    @Test
    fun should_stay_on_broadcast_without_the_exact_alarm_permission() {
        // Without it the scheduler falls back to setAndAllowWhileIdle, which carries no FGS
        // exemption: a service start from a killed process would THROW. A lane that is merely slow
        // beats one that raises.
        assertEquals(
            HeartbeatLane.BROADCAST,
            heartbeatDeliveryLane(canScheduleExact = false, sentryResident = true),
        )
    }

    @Test
    fun should_stay_on_broadcast_when_no_resident_sentry_is_watching() {
        // Under Bluetooth the sentry deliberately does not stay resident [DET-STRATEGY-GATE-001],
        // so a service start every 5 min would blink a foreground notification beside a parked car.
        assertEquals(
            HeartbeatLane.BROADCAST,
            heartbeatDeliveryLane(canScheduleExact = true, sentryResident = false),
        )
    }

    @Test
    fun should_stay_on_broadcast_when_neither_guard_holds() {
        assertEquals(
            HeartbeatLane.BROADCAST,
            heartbeatDeliveryLane(canScheduleExact = false, sentryResident = false),
        )
    }

    /**
     * The witness that this is a CONJUNCTION, not an either-or. Written because the cheap version of
     * this function — one guard, whichever was remembered — is the version that either throws on an
     * install without the special access or blinks a notification at every Bluetooth driver, and
     * both bugs would only show up in the field, on someone else's phone.
     */
    @Test
    fun should_demand_both_guards_so_a_single_one_can_never_open_the_service_lane() {
        val combinations = listOf(
            true to true,
            true to false,
            false to true,
            false to false,
        )
        val service = combinations.filter { (exact, resident) ->
            heartbeatDeliveryLane(exact, resident) == HeartbeatLane.SERVICE
        }
        assertEquals(listOf(true to true), service, "only both-guards-held may take the service lane")
    }
}
