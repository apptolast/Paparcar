@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.analytics.AnalyticsEvent
import com.rndeveloper.paparcar.domain.analytics.AnalyticsTracker
import com.rndeveloper.paparcar.domain.analytics.SessionEndReason
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.service.GeofenceManager
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.firstOrNull
import kotlin.time.Clock

/**
 * Reverts a previously auto-confirmed parking session.
 *
 * Invoked when the user taps "No, cancelar" on the post-save notification (state B in
 * the unified notification state machine — see docs/detection/PARKING-DETECTION.md).
 *
 * **El sujeto se resuelve, no se supone.** [DET-A-SAVED-CARD-MUST-NOT-OUTLIVE-ITS-TRIP-001] El
 * `parkingId` llega en los extras de un PendingIntent que puede sobrevivir al viaje del que habla,
 * así que lo PRIMERO es comprobar que sigue siendo una sesión activa. Si no lo es, no se retira ni
 * se ficha nada: sólo se apaga la tarjeta y se deja constancia (`REVERT_STALE`). Retirar por un id
 * caducado sacaba del histórico un viaje real y lo registraba como falso positivo etiquetado por el
 * usuario — justo la señal que este caso de uso existe para producir limpia.
 *
 * **Composición (v1).** No introduce nuevos esquemas Room/Firestore: solo encadena
 * operaciones ya existentes que son inversas a [ConfirmParkingUseCase]:
 *
 *  1. [UserParkingRepository.clearActiveParkingSession] — marca `isActive=false` en
 *     Room y propaga a Firestore.
 *  1b. [UserParkingRepository.retractParkingSession] — y la RETIRA del histórico.
 *     [PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001] Esto era el `TODO-REVERT-P1` de esta
 *     misma nota, que decía literalmente *"borrarla del histórico … semánticamente:
 *     'esto no era un aparcamiento, no quiero verlo en mi historial'"*. Se ha resuelto
 *     con la OTRA respuesta a esa pregunta: **retirada, no borrado**, por la razón que
 *     [com.rndeveloper.paparcar.domain.model.SpotStatus] ya escribió cuando la plaza
 *     comunitaria se enfrentó a la misma elección — *un documento borrado simplemente
 *     deja de llegar, y se lleva la explicación con él*. La fila sobrevive para el
 *     diagnóstico (es justo el pin que un informe de campo intenta explicar) y
 *     desaparece de las cuatro lecturas que alimentan el histórico.
 *  2. [GeofenceManager.removeGeofence] — desregistra la geofence Android/iOS.
 *  3. Dismiss de la notificación 2002 — la única visible para este evento.
 *
 *     ⚠️ Esto fue FALSO durante meses y lo dijo esta misma línea: prometía que «ya no se
 *     muestra la antigua showParkingSaved duplicada» mientras el carril BT y el guardado
 *     manual seguían publicándola, en una ranura (1002) que nadie apagaba nunca. Un revert
 *     dejaba en la bandeja la tarjeta del aparcamiento que acababa de retirar. Cierto desde
 *     [NOTIF-A-FLOW-SPEAKS-THROUGH-ONE-SLOT-001], donde el símbolo desapareció.
 *
 * **Spot comunitario.** No hay nada que retractar en este punto: el spot público se
 * publica únicamente cuando el usuario sale del geofence (vía [ReportSpotWorker]),
 * NO en el momento del save. Al haber removido la geofence, esa publicación nunca
 * llegará a dispararse. ✓
 *
 * **DepartureEventBus.** [ConfirmParkingUseCase] llama a `departureEventBus.reset()`
 * para evitar falsos departures al caminar lejos del coche. Tras revert no lo
 * tocamos: si la sesión nunca fue real, el siguiente `IN_VEHICLE_ENTER` repoblará
 * el bus correctamente. Tocarlo aquí sería resucitar un timestamp obsoleto.
 *
 * **Best-effort.** Cada paso loguea su fallo y continúa con el siguiente. La
 * idempotencia de cada operación (Room delete-by-id, GMS removeGeofences) lo
 * permite — un retry manual del usuario no rompe nada.
 */
class RevertParkingUseCase(
    private val userParkingRepository: UserParkingRepository,
    private val geofenceService: GeofenceManager,
    private val notificationPort: AppNotificationManager,
    // Nullable so existing test doubles / call sites need no change. [DET-SOLID-001]
    private val detectionEventLogger: DetectionEventLogger? = null,
    // Optional product-analytics sink; fire-and-forget, nullable for test doubles. [ANALYTICS-EVENTS-001]
    private val analyticsTracker: AnalyticsTracker? = null,
) {

    suspend operator fun invoke(parkingId: String): Result<Unit> {
        PaparcarLogger.d(DIAG, "▶ RevertParking.invoke parkingId=$parkingId")

        // [DET-SOLID-001] A revert is a USER-LABELLED false positive — the single most valuable
        // signal detection telemetry can produce. Capture the session age before clearing.
        val now = Clock.System.now().toEpochMilliseconds()

        // [DET-A-SAVED-CARD-MUST-NOT-OUTLIVE-ITS-TRIP-001] …which is precisely why the subject has
        // to be RESOLVED and not assumed. This id arrives in the extras of a PendingIntent attached
        // to a notification that can outlive the trip it talks about (the Coordinator protects the
        // undo window for 15 min; the BT lane never took the card down at all). Tapping «No,
        // cancelar» on such a card used to withdraw a REAL trip from the history and file it as a
        // user-labelled false positive — the one signal detection cannot afford to have poisoned.
        //
        // The fix is the shape the watchdog's twin already had: read the session, and when it is not
        // there, converge the notification and say so instead of acting. Doing it HERE rather than
        // at each caller is what also closes it on iOS, whose `IosNotificationActionHandler` hands
        // this use case an id out of `userInfo` with exactly the same blind trust.
        //
        // Three outcomes, deliberately distinguished — the third is not the second:
        //  · found     → the revert the user asked for, unchanged.
        //  · absent    → a stale tap. Withdraw nothing, file nothing, dismiss the card.
        //  · unreadable→ we do not KNOW. Same restraint, different trace: a Room hiccup must never
        //    be recorded as a stale tap, and asymmetric failure picks the silence either way —
        //    declining costs a pin the user can still delete from the history (this class's own
        //    fallback, below); acting costs a real trip nobody can get back.
        val lookup = runCatching {
            userParkingRepository.observeActiveSessions().firstOrNull()
                ?.firstOrNull { it.id == parkingId }
        }
        val revertedSession = lookup.getOrNull()
        if (revertedSession == null) {
            val reason = if (lookup.isFailure) "sessions_unreadable" else "not_an_active_session"
            PaparcarLogger.w(
                DIAG,
                "  ⊘ revert DECLINED — $reason (parkingId=$parkingId). The card outlived its trip; " +
                    "nothing withdrawn [DET-A-SAVED-CARD-MUST-NOT-OUTLIVE-ITS-TRIP-001]",
            )
            // A stale tap is field data, not a false positive: it says a card stayed reachable after
            // its session ended, which is the very thing this ticket measures. It must NOT ride
            // `Reverted`, whose whole meaning is "the user refuted this pin".
            detectionEventLogger?.log(
                DetectionEvent.Decision(
                    sessionId = parkingId,
                    timestampMs = now,
                    outcome = "REVERT_STALE",
                    pathLabel = null,
                    reason = reason,
                )
            )
            notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
            PaparcarLogger.d(DIAG, "■ RevertParking.invoke DONE (declined)")
            return Result.success(Unit)
        }

        detectionEventLogger?.log(
            DetectionEvent.Reverted(
                sessionId = parkingId,
                timestampMs = now,
                sessionAgeMs = now - revertedSession.location.timestamp,
                location = revertedSession.location,
            )
        )

        // A revert ends the session NOW and never published anything — the pin was wrong.
        // [VEH-STATS-SAY-SOMETHING-USEFUL-001]
        val clearResult = userParkingRepository.clearActiveParkingSession(
            parkingId, endedAtMs = now, publishedSpot = false,
        )
        clearResult.onFailure { e ->
            PaparcarLogger.e(DIAG, "  ✗ clearActiveParkingSession failed", e)
        }.onSuccess {
            PaparcarLogger.d(DIAG, "  ✓ session cleared (isActive=false in Room + queued for Firestore)")
            // [ANALYTICS-EVENTS-001] A retraction IS a fact — analysis filters it by reason. The
            // "only when the session was found" guard this used to carry is now the early return
            // above: past this point the session IS the one being withdrawn.
            analyticsTracker?.track(
                AnalyticsEvent.ParkingSessionEnded(
                    location = revertedSession.location,
                    reason = SessionEndReason.REVERTED,
                )
            )
        }

        // [PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001] …and the row LEAVES the history, which is the
        // whole meaning of the button the user just pressed. Closing it was never enough: this
        // use case's own comment already said "the pin was wrong", and the wrong pin then sat in
        // their history as an ordinary parking. No policy is consulted here — a revert is an
        // INSTRUCTION, not a verdict, and nothing the app measures outranks the user's own word
        // ([DET-ASSERTION-OUTRANKS-INFERENCE-001]). It is withdrawn, not deleted: the field report
        // that follows a wrong pin needs to be able to read it.
        userParkingRepository.retractParkingSession(parkingId, now)
            .onFailure { e -> PaparcarLogger.e(DIAG, "  ✗ retractParkingSession failed", e) }
            .onSuccess { PaparcarLogger.d(DIAG, "  ✓ parking withdrawn — it leaves the history") }

        val geofenceResult = geofenceService.removeGeofence(parkingId)
        geofenceResult.onFailure { e ->
            PaparcarLogger.w(DIAG, "  ⚠ removeGeofence failed (continuing)", e)
        }.onSuccess {
            PaparcarLogger.d(DIAG, "  ✓ geofence removed")
        }

        notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
        PaparcarLogger.d(DIAG, "■ RevertParking.invoke DONE")

        // Best-effort overall: if the user later sees the session still around because
        // a step failed, manual cleanup from the history screen is the fallback.
        return Result.success(Unit)
    }

    private companion object {
        const val DIAG = "PARKDIAG/Revert"
    }
}
