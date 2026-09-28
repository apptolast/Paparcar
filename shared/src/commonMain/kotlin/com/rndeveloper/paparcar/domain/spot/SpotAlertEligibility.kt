package com.rndeveloper.paparcar.domain.spot

import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.SpotStatus

/**
 * Whether a spot is one we would EVER interrupt anybody about, before asking where it is.
 * [ZONE-AN-ALERT-NEAR-YOU-IS-NOT-AN-ALERT-IN-YOUR-ZONE-001]
 *
 * ⛔ **Top-level and lane-agnostic on purpose.** These vetoes lived inside `isAlertableInZone`,
 * bolted to the zone circle, and from this ticket there are TWO lanes that need them — the zone
 * alert (Plus, works with the app closed) and the spot near your own position (free, only with the
 * map in front of you). Re-deriving them in the second lane is how the two would come to disagree,
 * and the disagreement that costs most is the one the user reminded us of the day this was written:
 * *a spot a user leaves must never be announced to that same user.* One copy, both lanes.
 * [DET-VERDICT-NOT-PREDICATE-001]
 *
 * What it deliberately does NOT answer: **distance**. Each lane measures from a different anchor —
 * the zone measures from a place you saved, the nearby lane from where your body is right now — and
 * folding both into one function would take the anchor as a parameter and stop being a shared rule.
 *
 * @param nowMs used only to drop spots that have already expired. A spot whose TTL ran out between
 *        the query and this call is not news; without the clock a background look would notify about
 *        a free space that stopped existing before the phone buzzed.
 */
fun isSpotWorthAnAlert(
    spot: Spot,
    ownUserId: String?,
    nowMs: Long,
): Boolean = when {
    // Withdrawn: the app stopped believing in it. [DET-HANDOFF-NOT-MANUAL-001 §B.3]
    spot.status == SpotStatus.RETRACTED -> false

    // ⛔ [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] A RELEASING spot is not alertable because **the car
    // is still in it**: an alert saying "there is a space" would be false at the instant it buzzed,
    // and it would spend the one resource a parking app cannot give back — somebody else's drive.
    // The news of that boarding is its PROMOTION, which the server announces on the TRANSITION.
    // [SEC-A-RELEASING-SPOT-IS-DENIED-AT-THE-DOOR-001]
    //
    // ⚠️ This used to claim a second, independent half — «it lives four minutes, so a 15-minute look
    // could never reach anybody in time». Both halves of that were wrong by 28-09: there is no
    // 15-minute look ([ZONE-THERE-IS-NO-WORKER-AND-FIVE-KDOCS-SAY-THERE-IS-001]), and the four
    // minutes are the **FLOOR, not the plan** — `SpotTtlPolicy.RELEASING_SPOT_TTL_MS` says so. The
    // normal path is the sensors resolving it: measured driving promotes it, a refuted boarding
    // withdraws it explicitly, and the TTL only covers neither happening.
    // [ALERT-A-PROVISIONAL-SPOT-IS-PUSHED-BUT-NOT-SHOWN-001]
    //
    // ⚠️ Stated rather than left to the `else`: this `when` ends in one, so a NEW status would
    // become alertable by saying nothing. That is how a class of alert gets promised by accident.
    spot.status == SpotStatus.RELEASING -> false

    // ⛔ Your own published spot is the place YOU just left — never an alert, in either lane. The
    // nearby lane makes this sharper than the zone one ever did: you are standing ON it, metres
    // away, so it would be the single most likely alert to fire and the most obviously wrong.
    spot.reportedBy == ownUserId -> false

    // Already dead by the time we looked.
    spot.expiresAt != 0L && spot.expiresAt <= nowMs -> false

    // ⛔ **PROVISIONAL llega aquí, y que llegue es la decisión.**
    // [ALERT-A-PROVISIONAL-SPOT-IS-PUSHED-BUT-NOT-SHOWN-001]
    //
    // Estaba vetada arriba hasta el 28-09, apoyada en la misma cadencia muerta que RELEASING —
    // mientras el servidor SÍ la anunciaba (`fanout.ts`: `isProvisional: spot.status ===
    // 'PROVISIONAL'`) y [AppNotificationManager.showZoneSpotAlert] ya llevaba el flag para matizar la
    // frase, con un KDoc que prescribe justo lo contrario del veto: *«Said, never hidden… Different
    // words, not a different silence.»* Resultado: una plaza deducida te llegaba con la app CERRADA y
    // se perdía mirando el mapa.
    //
    // ⛔ **Y no lleva rama propia `-> true`, a propósito.** La tuvo durante diez minutos y fue un bug
    // de verdad: un `-> true` explícito en una cadena de guardas **cortocircuita todo lo que va
    // detrás**, así que se saltaba el veto de plaza propia y el de caducidad — una plaza deducida
    // TUYA se te habría anunciado. Lo cazó `AlertStatusParityGuardrailTest` en su primera ejecución.
    // La forma correcta de este `when` es: sólo vetos, y el `else` afirma.
    //
    // ⚠️ Matizar las palabras es del llamante. Esto contesta «¿podemos interrumpir por esta plaza?»,
    // nunca «¿qué decimos» — y una deducida anunciada como confirmada gastaría la confianza de la que
    // vive el aviso entero. La lleva `SpotStatus.isUnconfirmedOffer`.
    //
    // ⚠️ Que un estado NUEVO sea alertable por no decir nada lo vigilan dos testigos, no este
    // comentario: `AlertStatusParityGuardrailTest` (contra los vetos del servidor) y
    // `SpotAlertEligibilityTest`, que enumera los estados uno a uno.
    else -> true
}
