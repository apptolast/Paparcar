package com.rndeveloper.paparcar.domain.detection

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The departure question survives opening the app, process death and a notification swipe. */
@Serializable
data class StillParkedQuestion(
    val geofenceId: String,
    val latitude: Double,
    val longitude: Double,
    val shownAtMs: Long,
    val confirmedHereAtMs: Long? = null,
) {
    fun encode(): String = Json.encodeToString(serializer(), this)

    companion object {
        fun decode(value: String?): StillParkedQuestion? =
            value?.let { runCatching { Json.decodeFromString(serializer(), it) }.getOrNull() }
    }
}

/** Both the saved parking and the asking instant identify an answer or editor request. */
fun StillParkedQuestion.isCurrentFor(geofenceId: String?, shownAtMs: Long?): Boolean =
    confirmedHereAtMs == null && this.geofenceId == geofenceId && this.shownAtMs == shownAtMs

/** A human answer wins over the same evidence; a later boarding/exit may raise a new doubt. */
fun stillParkedQuestionMayRepeat(
    previous: StillParkedQuestion?,
    geofenceId: String,
    latestEvidenceAtMs: Long?,
): Boolean {
    if (previous?.geofenceId != geofenceId) return true
    val answeredAt = previous.confirmedHereAtMs ?: return true
    return latestEvidenceAtMs != null && latestEvidenceAtMs > answeredAt
}

/** A notification answer belongs to one live question, never whichever trip replaced it. */
fun promptAnswerIsCurrent(window: PendingPromptWindow?, shownAtMs: Long?, nowMs: Long, timeoutMs: Long): Boolean =
    shownAtMs != null && window?.shownAtMs == shownAtMs && isPromptWindowOpen(window, nowMs, timeoutMs)
