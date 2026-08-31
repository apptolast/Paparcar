package com.rndeveloper.paparcar.domain.detection

/**
 * [PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001] **Did this parking's own departure refute the parking
 * itself?**
 *
 * A PREDICATE, not a verdict: it produces no `detectionPath`, no `outcome` and nothing the user
 * reads on its own — it decides whether one already-written row is withdrawn. So it lives here with
 * detection's other pure policy functions ([nextSentryWakeAbortStreak], [SentryLifecycleDecision],
 * [VehicleFenceOwnershipPolicy]…) instead of as an injected `Evaluate…UseCase`.
 * [DET-VERDICT-NOT-PREDICATE-001]
 *
 * ## Why it has to exist
 *
 * Every one of the seven redesign pieces is PREVENTIVE: they decide better before planting. None of
 * them can touch a row already written, and two field days produced exactly that — a pin the app
 * itself disproved seconds later, left standing in the user's history as an ordinary parking:
 *
 * - **2026-08-27, Oppo.** The backfill planted `724befda` at 12:29:18; at 12:29:36 the app emitted
 *   an EXIT for that very pin's geofence and at 12:30:21 confirmed the departure at 16,3 km/h. The
 *   pin **lived 63 s**. `ClearActiveParkingSessionWorker` set `isActive=false` and the row stayed —
 *   addressless, `detectionPath = safety_net_backfill` — indistinguishable from a real parking. The
 *   user reported it as *"un FALSO POSITIVAZO en Dia · Calle Ronda del Puerto 15"*.
 * - **2026-08-30, Oppo, Calle del Verdugo.** The same shape, on the road, mid-drive, with a
 *   community space published. It **lived 52 s**. `DET-BACKFILL-MUST-NOT-PIN-A-MOVING-CAR-001`
 *   stops the next one being created; it cannot reach the one already there.
 *
 * ## Doctrine
 *
 * *Better a false negative than a false positive.* A parking the app has ALREADY concluded never
 * happened is not an uncertain datum worth keeping — it is a claim the app has retracted. Keeping it
 * is asserting something we know to be false.
 *
 * And the withdrawal is a STATE, never a delete, for the reason
 * [com.rndeveloper.paparcar.domain.model.SpotStatus] wrote down when the community spot faced the
 * same choice: *a deleted document just stops arriving, taking the explanation with it.* The row
 * stays for diagnostics; it leaves the history.
 *
 * ## The three conditions, and why each one is load-bearing
 *
 * @param path What placed the pin. Only a path that answers [DetectionPath.mayBeWithdrawnByTheApp]
 *   may be withdrawn — today, only the safety net's backfill, the one pin with no live session
 *   behind it. **Fails CLOSED**: an unrecognised label parses to `null` and nothing is withdrawn.
 * @param parkedAtMs When the pin was placed.
 * @param departedAtMs When the app confirmed the departure FROM that pin.
 * @param maxLifeMs How short a life makes the departure a refutation rather than an ending
 *   (`ParkingDetectionConfig.refutedPinMaxLifeMs`). This is the bound that keeps a REAL short errand
 *   safe: the backfill only ever fires ~15 min after the fact, so a genuine two-minute stop is long
 *   over before it could place anything. Both field cases sit at 52 s and 63 s, well inside.
 *
 * ⚠️ **A negative age is not a young pin.** Clocks move backwards (NTP, timezone, a restored
 * backup), and `departedAtMs < parkedAtMs` would otherwise read as an even shorter life and
 * withdraw. It answers `false`: an order we cannot trust is not evidence of anything.
 */
fun pinIsRefutedByItsOwnDeparture(
    path: DetectionPath?,
    parkedAtMs: Long,
    departedAtMs: Long,
    maxLifeMs: Long,
): Boolean {
    if (path?.mayBeWithdrawnByTheApp != true) return false
    val lifeMs = departedAtMs - parkedAtMs
    if (lifeMs < 0) return false
    return lifeMs <= maxLifeMs
}
