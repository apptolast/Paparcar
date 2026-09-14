package com.rndeveloper.paparcar.data.overpass

/**
 * How long each server in the failover gets, and when the walk stops paying.
 * [OVERPASS-A-FAILOVER-MUST-HAVE-A-DEADLINE-001]
 * [OVERPASS-A-MIRROR-WALK-MUST-OUTLIVE-THE-SERVER-IT-ASKS-001]
 *
 * Pure arithmetic on purpose: it is the DECISION of the walk, and a decision that cannot be quoted
 * in a test is a decision nobody can check. [OverpassClient] does the I/O and nothing else.
 *
 * Two invariants, and the second one was bought expensively:
 *
 * > **Asking N servers must not cost N times one server's patience.**
 * >
 * > **Every server the walk asks must get a try that could actually have succeeded.**
 *
 * ⛔ **The old cut was 8 s, and it was justified by something false.** It claimed a server that is
 * genuinely working "starts streaming its response", so silence could be read as a server that had
 * given up. Measured against the live endpoint on 14-09-2026: `TTFB=4,68 s` with `total=4,74 s` for
 * a 31 KB body. **Overpass buffers the whole answer** — nothing arrives until the query has
 * finished. Silence does not separate a stuck server from a busy one, because every Overpass server
 * is silent while it works. The cut was not a safety valve; it was a guillotine placed in the middle
 * of the latency distribution:
 *
 * | Ciudad | HTTP | primer byte |
 * |---|---|---|
 * | París | 200 | 3,90 s |
 * | Madrid | 200 | 4,68 s en frío · 1,4 s en caliente |
 * | **Málaga** | **200** | **7,72 s** ← 0,3 s por debajo del corte, desde fibra |
 * | Barcelona | 504 | 8,06 s |
 * | Berlín | 429 | 9,48 s |
 *
 * So the cut is no longer a constant anybody can forget to revisit: it is **the walk's remaining
 * time divided by the servers still unasked**, floored at what the slowest measured ANSWER needs.
 * Change the mirror list and the arithmetic re-balances itself — which is precisely what did not
 * happen last time, when the list rotted while the number stayed put.
 *
 * ⛔ **This is a budget that gets SPENT, never a wall around the walk.** An outer `withTimeout` was
 * tried and paid for in [OVERPASS-A-SINGLE-MIRROR-HAS-NO-FAILOVER-001]: it cancels the failover
 * mid-stride without anything failing, turning "the third mirror answered" into silence.
 */
internal object OverpassWalkPatience {

    /** What one attempt is allowed to spend, per phase. */
    data class Attempt(
        /** Whole call: sending the query and receiving the answer. */
        val requestMs: Long,
        /**
         * Silence allowed before this server passes its turn. Since Overpass buffers, this is in
         * practice "how long this server may think before we go ask the next one" — so it is sized
         * by what an ANSWER costs, never by a guess about what a healthy server sounds like.
         */
        val firstByteMs: Long,
        /** TCP connect. A connect this slow is a dead server whoever is asking. */
        val connectMs: Long,
    )

    /**
     * The attempt this server gets, or null when what is left cannot fund a real try.
     *
     * @param mirrorsLeft how many servers are still unasked, **including this one**. With one left
     *   the share is everything remaining, which is how the last mirror inherits the rest of the
     *   walk without needing to be a special case.
     */
    fun attempt(spentMs: Long, serverBudgetMs: Long, mirrorsLeft: Int): Attempt? {
        val remainingMs = WALK_CEILING_MS - spentMs
        if (remainingMs < MIN_ATTEMPT_MS) return null

        val fullPatienceMs = minOf(serverBudgetMs + NETWORK_GRACE_MS, remainingMs)
        // An even share of what is left — so nobody is asked with time that could not carry an
        // answer, and nobody hogs the walk while others go unasked.
        val evenShareMs = remainingMs / mirrorsLeft.coerceAtLeast(1)
        // …but never below what a real answer has been measured to need. When the list is long
        // enough that the even share drops under this, the honest outcome is asking FEWER servers
        // properly rather than all of them pointlessly — and [OverpassClient] logs the ones it left.
        val quietCutMs = maxOf(SLOWEST_MEASURED_ANSWER_MS, evenShareMs)
        val firstByteMs = minOf(quietCutMs, fullPatienceMs)
        return Attempt(
            requestMs = fullPatienceMs,
            firstByteMs = firstByteMs,
            connectMs = minOf(CONNECT_CEILING_MS, firstByteMs),
        )
    }

    /**
     * The budget the query itself declared, in millis.
     *
     * ⛔ Read from the query rather than passed alongside it, so the number exists ONCE. Every
     * Overpass query in the app opens with `[out:json][timeout:N];` — 8 s for the parked-place POI,
     * 20 s for car parks, stations, French names and the road network — and that N is the patience
     * the SERVER was granted. A client that waits for a different number is waiting on a guess; two
     * copies of the same number are two numbers that will drift.
     */
    fun declaredServerBudgetMs(query: String): Long {
        val declared = DECLARED_TIMEOUT.find(query)?.groupValues?.get(1)?.toIntOrNull()
        return (declared ?: DEFAULT_SERVER_BUDGET_S) * 1_000L
    }

    private val DECLARED_TIMEOUT = Regex("""\[timeout:(\d+)]""")

    /**
     * The whole walk, from the first server asked to the last one giving up. 30 s is what is left of
     * the old 75 s once the point of a failover is taken seriously: the healthy mirror answered the
     * measured car-park query in **1,4 s warm**, so past this the question is not "is it slow" but
     * "is anybody there".
     */
    internal const val WALK_CEILING_MS = 30_000L

    /**
     * The floor under any one server's turn: the slowest SUCCESSFUL answer measured, plus room for a
     * phone's radio.
     *
     * Málaga came back 200 at **7,72 s over fibre** on 14-09-2026. A handset on 4G pays connection
     * setup and a slower transfer on top of that, so a cut anywhere near 8 s throws away answers
     * that were on their way. Twelve seconds is that measurement with the phone's share added — and
     * with two mirrors in the list it never even binds, because half of 30 s is more.
     */
    internal const val SLOWEST_MEASURED_ANSWER_MS = 12_000L

    /** What the network is allowed to add on top of the budget the server itself was given. */
    internal const val NETWORK_GRACE_MS = 5_000L

    /** Below this, asking is theatre: the answer could not arrive before the walk is over anyway. */
    internal const val MIN_ATTEMPT_MS = 3_000L

    /** A TCP connect slower than this is a dead server, whatever patience the walk still has. */
    internal const val CONNECT_CEILING_MS = 10_000L

    /** For a query that declares nothing — the budget every current caller but one already uses. */
    internal const val DEFAULT_SERVER_BUDGET_S = 20
}
