package com.rndeveloper.paparcar.domain.zone

import com.rndeveloper.paparcar.domain.model.Zone

/**
 * One row of the server-side index the Cloud Function queries.
 * [ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001]
 *
 * ⛔ **Being in this index IS the entitlement.** The server has no idea who holds Plus — that lives
 * in RevenueCat and is read on the device — and giving it a second opinion would mean two sources of
 * truth for the same fact, drifting apart at every renewal and every refund. Instead the client only
 * keeps a row while Plus ∧ the Settings switch ∧ the zone's own opt-in all hold, and deletes it the
 * moment one stops. Losing Plus therefore pauses the alert **without touching any zone**: the same
 * downgrade rule as always, enforced in a new place. [MONEY-001]
 */
data class ZoneAlertSubscription(
    /** `{uid}_{zoneId}` — one row per zone, so unsubscribing one leaves the others alone. */
    val id: String,
    val uid: String,
    val zoneId: String,
    val zoneName: String,
    val latitude: Double,
    val longitude: Double,
    /** The ALERT radius, not the drawn one — see [zoneAlertRadiusMeters]. */
    val radiusMeters: Float,
    /**
     * Geohash cells the alert circle touches, at the precision the function slices a spot's own
     * geohash to.
     *
     * ⛔ Computed HERE, by the client, on purpose: the geohash implementation stays in exactly one
     * place and one language. The server never encodes anything — it takes the spot's stored
     * geohash, cuts it to the same length and asks `cells array-contains`. A second implementation
     * of that algorithm, in TypeScript, maintained by nobody, works right up until two edges
     * disagree and a whole neighbourhood quietly stops being told.
     */
    val cells: List<String>,
    /** Every device of this user that can receive a push. */
    val tokens: List<String>,
    /**
     * Epoch millis after which the server ignores this row.
     *
     * ⚠️ The weak point of putting the entitlement in the index: someone whose Plus lapses and who
     * never opens the app again would otherwise be watched for ever. Refreshed on every sync, so a
     * live user is always well inside it. The exact answer is a RevenueCat webhook — follow-up.
     */
    val expiresAt: Long,
)

/**
 * What the index SHOULD contain right now. Pure, so the rule that decides who gets watched is
 * testable without a network, a clock or a token.
 * [ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001]
 *
 * Returns an empty list — meaning "delete everything of mine" — whenever the user is not entitled,
 * has silenced the channel, has no zone asking to be watched, or has no device that can be reached.
 *
 * @param cellsOf the geohash cells covering a circle. Injected rather than imported so this file
 *        stays pure domain; the real one is the same `geohashQueryBounds` the spot query uses.
 */
fun zoneAlertSubscriptionsFor(
    uid: String?,
    zones: List<Zone>,
    isPlus: Boolean,
    notificationsEnabled: Boolean,
    tokens: List<String>,
    nowMs: Long,
    cellsOf: (latitude: Double, longitude: Double, radiusMeters: Double) -> List<String>,
): List<ZoneAlertSubscription> {
    if (uid.isNullOrBlank() || !isPlus || !notificationsEnabled) return emptyList()
    // ⛔ No token, no row. A subscription nobody can be told about is an entry the server would read
    // on every nearby spot for nothing — the cheapest way to make a cheap fan-out expensive.
    val live = tokens.filter { it.isNotBlank() }
    if (live.isEmpty()) return emptyList()

    return zones.filter { it.notifyOnSpot }.map { zone ->
        val radius = zoneAlertRadiusMeters(zone)
        ZoneAlertSubscription(
            id = "${uid}_${zone.id}",
            uid = uid,
            zoneId = zone.id,
            zoneName = zone.name,
            latitude = zone.lat,
            longitude = zone.lon,
            radiusMeters = radius,
            cells = cellsOf(zone.lat, zone.lon, radius.toDouble()),
            tokens = live,
            expiresAt = nowMs + SUBSCRIPTION_TTL_MS,
        )
    }
}

/**
 * Thirty days. Long enough that an ordinary user never brushes it — the app syncs on every start —
 * and short enough that an account which goes quiet stops costing reads within a month.
 */
const val SUBSCRIPTION_TTL_MS: Long = 30L * 24 * 60 * 60 * 1_000
