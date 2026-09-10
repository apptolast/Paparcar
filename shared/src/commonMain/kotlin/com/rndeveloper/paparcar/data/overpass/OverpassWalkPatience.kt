package com.rndeveloper.paparcar.data.overpass

/**
 * How long each server in the failover gets, and when the walk stops paying.
 * [OVERPASS-A-FAILOVER-MUST-HAVE-A-DEADLINE-001]
 *
 * Pure arithmetic on purpose: it is the DECISION of the walk, and a decision that cannot be quoted
 * in a test is a decision nobody can check. [OverpassClient] does the I/O and nothing else.
 *
 * The invariant this exists to state:
 *
 * > **Asking N servers must not cost N times one server's patience.**
 *
 * `FuelHttpClient` already gives every request a deadline; what had none was the WALK. Three mirrors
 * at 25 s each meant a car-park lane could spin for 75 s and end with nothing — measured on device
 * on 10-09-2026, with `overpass.private.coffee` (the FIRST fallback) accepting the connection,
 * completing the handshake and then saying nothing for over a minute. It was not down: its own
 * `/api/status` declared 15 queries running, the oldest three minutes old, with limits up to 600 s.
 * Saturated servers are the worst case for a blind failover, and they are the common one.
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
         * Silence allowed before this server passes its turn — inactivity between bytes, so a server
         * that is COMPUTING (it sends its header) is told apart from one that is merely holding the
         * socket open.
         */
        val firstByteMs: Long,
        /** TCP connect. A connect this slow is a dead server whoever is asking. */
        val connectMs: Long,
    )

    /**
     * The attempt this server gets, or null when what is left cannot fund a real try.
     *
     * Two rules, one idea — *patience is only worth spending when there is nobody else to ask*:
     * 1. a mirror that has not said one byte in [QUIET_MIRROR_MS] passes its turn;
     * 2. …unless it is the [isLast] one, which inherits whatever patience is left, because there is
     *    no longer anyone to fail over TO.
     */
    fun attempt(spentMs: Long, serverBudgetMs: Long, isLast: Boolean): Attempt? {
        val remainingMs = WALK_CEILING_MS - spentMs
        if (remainingMs < MIN_ATTEMPT_MS) return null

        val fullPatienceMs = minOf(serverBudgetMs + NETWORK_GRACE_MS, remainingMs)
        val firstByteMs = if (isLast) fullPatienceMs else minOf(QUIET_MIRROR_MS, fullPatienceMs)
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
     * measured car-park query in **2,0 s**, so past this the question is not "is it slow" but "is
     * anybody there".
     */
    internal const val WALK_CEILING_MS = 30_000L

    /**
     * Silence that buys a server its turn back. A saturated Overpass sends nothing at all; a busy one
     * that is genuinely working starts streaming its response.
     */
    internal const val QUIET_MIRROR_MS = 8_000L

    /** What the network is allowed to add on top of the budget the server itself was given. */
    internal const val NETWORK_GRACE_MS = 5_000L

    /** Below this, asking is theatre: the answer could not arrive before the walk is over anyway. */
    internal const val MIN_ATTEMPT_MS = 3_000L

    /** A TCP connect slower than this is a dead server, whatever patience the walk still has. */
    internal const val CONNECT_CEILING_MS = 10_000L

    /** For a query that declares nothing — the budget every current caller but one already uses. */
    internal const val DEFAULT_SERVER_BUDGET_S = 20
}
