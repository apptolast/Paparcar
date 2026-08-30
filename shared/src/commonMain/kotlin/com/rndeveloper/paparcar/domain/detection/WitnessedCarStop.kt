package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.detection.state.DetectionSessionState
import com.rndeveloper.paparcar.domain.model.GpsPoint

/**
 * [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] **Where this session last saw the CAR stand still.**
 *
 * The place a "did you park?" question is about, and the only place an unconfirmed marker may be
 * drawn. It is the session's anchor — the position the stop reduction pinned or froze, and the same
 * point the frozen car marker has always shown on the map — falling back to the posting fix while
 * no stop has been witnessed yet.
 *
 * ## Why this is not `UserConfirmStage.whereTheCarIs`
 *
 * That cascade answers a different question — *where should the pin GO now that the user has
 * answered* — and it deliberately prefers the fix the user is standing on when they answer from
 * beside the car, because a fix taken next to the car beats a minutes-old anchor. Asking it at
 * PROMPT time would be asking it before the user has moved, and re-asking it every fix would make
 * the marker crawl along with the pedestrian: the walk home would drag the question's own answer,
 * which is precisely the failure `ANCHOR-LOCK-001` and `DET-ANCHOR-FREEZE-001` exist to prevent.
 *
 * So the two are kept apart on purpose. This one is a WITNESS — stable, durable, and true of the
 * car. The cascade is a VERDICT, evaluated once, at answer time. A divergence between them is not
 * drift; it is the answer being better informed than the question was.
 */
fun DetectionSessionState.witnessedCarStop(fix: GpsPoint): GpsPoint = anchorTrust.anchor ?: fix
