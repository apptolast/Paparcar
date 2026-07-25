package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.model.UserParking

/**
 * [DET-NUDGE-PERSIST-001] Durable record of an UNANSWERED "where did you leave your car?" nudge.
 *
 * Field 2026-07-25 (Redmi, session 1784939810210): the coordinator correctly refused to guess the
 * park (`UNATTENDED_WALK_ENTERED_NUDGE`), posted the nudge notification at 03:11 — and that
 * notification was the ONLY trace of the question. The user slept through it, opened the app the
 * next morning and Home said "no car parked" with zero way to recover. The question must survive
 * as APP STATE until the user answers it, not just as a dismissible notification.
 *
 * Persisted by the notification adapter at the same choke point that posts the nudge (every ask
 * path goes through [io.apptolast.paparcar.domain.notification.AppNotificationManager.showMarkParkingNudge]);
 * cleared when the user marks a parking, a session for the nudged vehicle is confirmed, or the
 * user explicitly dismisses the Home banner. A new nudge REPLACES the previous one — there is
 * only ever one lost car to ask about.
 */
data class PendingParkNudge(
    /** Epoch-ms the nudge was raised — the banner's "since when" context. */
    val createdAtMs: Long,
    /** Which detection path asked (session outcome / worker source) — diagnostics only. */
    val source: String,
    /** Vehicle the lost trip belonged to, when the asking session knew it. */
    val vehicleId: String? = null,
)

/**
 * Pure visibility rule for the Home banner: an unanswered nudge is shown until it is resolved.
 *
 * Defensive second check: if the nudged vehicle already has an active session again (confirm path
 * raced the clear, or a remote sync restored it), the question answered itself — never show a
 * "car lost" banner over a live pin. With no [PendingParkNudge.vehicleId], ANY active session
 * resolves it: the nudge asks about the only trip in flight, and whatever got pinned since IS
 * that answer.
 */
fun shouldShowParkNudgeBanner(
    nudge: PendingParkNudge?,
    activeSessions: List<UserParking>,
): Boolean {
    if (nudge == null) return false
    return if (nudge.vehicleId != null) {
        activeSessions.none { it.vehicleId == nudge.vehicleId }
    } else {
        activeSessions.isEmpty()
    }
}
