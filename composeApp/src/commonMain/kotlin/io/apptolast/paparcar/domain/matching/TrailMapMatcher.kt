package io.apptolast.paparcar.domain.matching

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.places.RoadWay
import io.apptolast.paparcar.domain.util.haversineMeters
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToLong

/**
 * Pure, free map-matching of the live trip trail onto OSM roads.
 *
 * v4 — full HMM map-matching (Newson & Krumm 2009), the industry-standard algorithm behind OSRM
 * `match`, Valhalla's Meili and the commercial map-matching APIs. [ROUTE-LINE-PRO-001]
 *
 * The professional insight: the drawn line is NOT the corrected fixes joined by chords — it is the
 * ROAD GEOMETRY of the most likely route. Per measurement the candidates are its projections onto
 * nearby road edges; Viterbi picks the candidate sequence minimising
 *   emission   — how far the street is from the fix (Gaussian, σ = [EMISSION_SIGMA_METERS])
 * + transition — |route distance ALONG the graph − straight-line distance| (exponential,
 *   β = [TRANSITION_BETA_METERS]),
 * and the output concatenates the actual shortest road paths between the chosen candidates. The
 * line therefore follows the street exactly — bends, corners, roundabouts — even when every fix is
 * metres off the road; and a spatially-near-but-unconnected parallel street is unreachable along
 * the graph, so it can never steal a noisy fix. Because Viterbi is global, newly arrived fixes can
 * re-decide earlier stretches: each re-match self-corrects the whole drawn line.
 *
 * Honesty rules (asymmetric failure applied to the visual):
 * - No road within [MAX_SNAP_METERS] → the fix is off-road: interior runs up to [MAX_OUTLIER_RUN]
 *   are dropped as spikes; longer runs and trail ends (the backdated origin can sit in a car park)
 *   are kept RAW — there is no street there and drawing one would lie.
 * - No plausible road path between consecutive candidates ([MAX_DETOUR_FACTOR] × straight +
 *   [DETOUR_SLACK_METERS], or a disconnected graph) → HMM break: the matched stretches are joined
 *   by an honest straight chord instead of an invented detour.
 *
 * Supersedes v1 (independent nearest-road snap), v2 (A* gap-fill — now inherent, every transition
 * is routed) and v3 (straight-line-transition Viterbi). See docs/backlog/route-line-pro-001.md.
 */
object TrailMapMatcher {

    /** Beyond this fix→street distance there is no candidate: the fix is off-road. */
    const val MAX_SNAP_METERS = 60.0

    /** Trail points are decimated to this spacing before matching: a transition discriminates
     *  streets only when the measured step is large vs GPS noise, and the routed geometry between
     *  matched points restores every street detail the decimation skipped. */
    const val MATCH_SPACING_METERS = 25.0

    /** Newson–Krumm emission σ (GPS noise std-dev). Urban phone traces run noisier than the
     *  4.07 m of the original paper — 10 m keeps a 20–40 m multipath fix matchable without
     *  flattening the preference for the near street. */
    const val EMISSION_SIGMA_METERS = 10.0

    /** Newson–Krumm transition β: expected |route − straight| circuitousness per step (Valhalla's
     *  default is 3). Smaller = stricter about routes that detour vs the measured movement. */
    const val TRANSITION_BETA_METERS = 3.0

    /** Candidate streets per measurement (nearest first) fed to the Viterbi. */
    const val MAX_CANDIDATES_PER_POINT = 4

    /** Candidates closer than this to an already-kept better one are the same street twice
     *  (adjacent edges of one way near a vertex) — skipped so alternatives aren't crowded out. */
    const val CANDIDATE_DEDUPE_METERS = 5.0

    /** A road path longer than factor × straight + slack is not a plausible direct drive: the
     *  transition is impossible and the match BREAKS to an honest chord rather than invent a
     *  scenic detour. The slack keeps short steps routable through a corner. */
    const val MAX_DETOUR_FACTOR = 3.0
    const val DETOUR_SLACK_METERS = 120.0

    /** Off-road runs up to this long between on-road neighbours are spikes → dropped. */
    const val MAX_OUTLIER_RUN = 2

    fun snap(points: List<GpsPoint>, roads: List<RoadWay>): List<GpsPoint> {
        if (roads.isEmpty() || points.size < 2) return points
        val graph = RoadGraph.build(roads)
        if (graph.isEmpty) return points
        val measurements = decimate(points)
        val layers = measurements.map { graph.candidatesFor(it) }
        return decode(measurements, layers, graph)
    }

    /** Keeps points ≥ [MATCH_SPACING_METERS] apart — always the first and the last. */
    private fun decimate(points: List<GpsPoint>): List<GpsPoint> {
        val kept = ArrayList<GpsPoint>(points.size)
        kept.add(points.first())
        for (i in 1 until points.size - 1) {
            val last = kept.last()
            val d = haversineMeters(last.latitude, last.longitude, points[i].latitude, points[i].longitude)
            if (d >= MATCH_SPACING_METERS) kept.add(points[i])
        }
        kept.add(points.last())
        return kept
    }

    /**
     * Walks the measurement sequence emitting matched street geometry, raw off-road stretches and
     * honest chords at HMM breaks.
     */
    private fun decode(ms: List<GpsPoint>, layers: List<List<Candidate>>, graph: RoadGraph): List<GpsPoint> {
        val out = ArrayList<GpsPoint>()
        var i = 0
        while (i < ms.size) {
            if (layers[i].isEmpty()) {
                var end = i
                while (end < ms.size && layers[end].isEmpty()) end++
                val isInteriorSpike = i > 0 && end < ms.size && (end - i) <= MAX_OUTLIER_RUN
                if (!isInteriorSpike) {
                    for (k in i until end) addPoint(out, ms[k])
                }
                i = end
            } else {
                i = decodeSegment(ms, layers, graph, i, out)
            }
        }
        return out
    }

    /**
     * Viterbi over consecutive layers starting at [start], until the trail ends, a layer is
     * off-road, or no candidate is reachable along the roads (HMM break). Emits the winning
     * candidate positions joined by their routed street vertices, and returns the index the next
     * stretch starts at.
     */
    private fun decodeSegment(
        ms: List<GpsPoint>,
        layers: List<List<Candidate>>,
        graph: RoadGraph,
        start: Int,
        out: ArrayList<GpsPoint>,
    ): Int {
        var costs = DoubleArray(layers[start].size) { layers[start][it].emissionCost() }
        val backs = ArrayList<IntArray>()          // backs[s][c] = winning previous candidate into layer start+s+1
        val paths = ArrayList<Array<List<Int>?>>() // paths[s][c] = routed node chain of that winning transition
        var end = start + 1
        while (end < ms.size && layers[end].isNotEmpty()) {
            val prev = layers[end - 1]
            val cur = layers[end]
            val gc = haversineMeters(
                ms[end - 1].latitude, ms[end - 1].longitude,
                ms[end].latitude, ms[end].longitude,
            )
            val bound = gc * MAX_DETOUR_FACTOR + DETOUR_SLACK_METERS
            // One bounded Dijkstra per distinct edge endpoint of the previous layer, shared across
            // all candidate pairs of this transition.
            val dijkstras = HashMap<Int, RoadGraph.ShortestPaths>()
            val nextCosts = DoubleArray(cur.size) { Double.MAX_VALUE }
            val nextBack = IntArray(cur.size) { -1 }
            val nextPath = arrayOfNulls<List<Int>>(cur.size)
            for (c in cur.indices) {
                for (pc in prev.indices) {
                    if (costs[pc] == Double.MAX_VALUE) continue
                    val route = graph.route(prev[pc], cur[c], bound, dijkstras) ?: continue
                    val cost = costs[pc] + abs(route.meters - gc) / TRANSITION_BETA_METERS
                    if (cost < nextCosts[c]) {
                        nextCosts[c] = cost
                        nextBack[c] = pc
                        nextPath[c] = route.nodeChain
                    }
                }
                if (nextCosts[c] != Double.MAX_VALUE) nextCosts[c] += cur[c].emissionCost()
            }
            if (nextCosts.all { it == Double.MAX_VALUE }) break // no plausible road path → honest chord
            costs = nextCosts
            backs.add(nextBack)
            paths.add(nextPath)
            end++
        }

        // Backtrack the winning chain, then emit forward as street geometry. Synthetic vertices
        // inherit the stretch's opening fix — these are drawn, never measured.
        val chosen = IntArray(backs.size + 1)
        var c = costs.indices.minBy { costs[it] }
        for (s in backs.size downTo 0) {
            chosen[s] = c
            if (s > 0) c = backs[s - 1][c]
        }
        val opening = ms[start]
        addCandidate(out, layers[start][chosen[0]], opening)
        for (s in backs.indices) {
            paths[s][chosen[s + 1]]?.forEach { node ->
                addPoint(out, opening.copy(latitude = graph.latOf(node), longitude = graph.lonOf(node)))
            }
            addCandidate(out, layers[start + s + 1][chosen[s + 1]], opening)
        }
        return end
    }

    private fun addPoint(out: ArrayList<GpsPoint>, p: GpsPoint) {
        val last = out.lastOrNull()
        if (last != null && last.latitude == p.latitude && last.longitude == p.longitude) return
        out.add(p)
    }

    private fun addCandidate(out: ArrayList<GpsPoint>, c: Candidate, opening: GpsPoint) {
        addPoint(out, opening.copy(latitude = c.lat, longitude = c.lon))
    }

    /** One possible on-street position for a fix: its projection onto one road edge. */
    class Candidate internal constructor(
        internal val edge: Int,
        internal val lat: Double,
        internal val lon: Double,
        internal val distMeters: Double,
        internal val alongFromU: Double,
    ) {
        internal fun emissionCost(): Double {
            val z = distMeters / EMISSION_SIGMA_METERS
            return 0.5 * z * z
        }
    }

    /**
     * The fetched ways as an undirected graph joined at shared OSM nodes (identical vertex
     * coordinates — Overpass emits the same node coords in every way that crosses it), with
     * explicit edges so candidates live ON an edge and routes start/end mid-edge. Pure and rebuilt
     * per snap call, which is debounced and off the main thread; a few thousand vertices build in
     * ~ms.
     */
    class RoadGraph private constructor(
        private val lats: DoubleArray,
        private val lons: DoubleArray,
        private val edgeU: IntArray,
        private val edgeV: IntArray,
        private val edgeLen: DoubleArray,
        private val adjacency: Array<IntArray>, // node → incident edge indices
    ) {
        val isEmpty: Boolean get() = edgeU.isEmpty()

        internal fun latOf(node: Int): Double = lats[node]
        internal fun lonOf(node: Int): Double = lons[node]

        /** Projections of [p] onto nearby edges: best per approach, deduped, nearest first. */
        internal fun candidatesFor(p: GpsPoint): List<Candidate> {
            // Local equirectangular scale: longitude degrees shrink by cos(lat) so planar
            // distances are ~isotropic over the small bbox of one trip.
            val cosLat = cos(p.latitude * PI / 180.0)
            val found = ArrayList<Candidate>()
            for (e in edgeU.indices) {
                val u = edgeU[e]
                val v = edgeV[e]
                val (lat, lon, t) = projectOntoSegment(
                    p.latitude, p.longitude,
                    lats[u], lons[u],
                    lats[v], lons[v],
                    cosLat,
                )
                val d = haversineMeters(p.latitude, p.longitude, lat, lon)
                if (d <= MAX_SNAP_METERS) {
                    found.add(Candidate(e, lat, lon, d, t * edgeLen[e]))
                }
            }
            found.sortBy { it.distMeters }
            val kept = ArrayList<Candidate>()
            for (cand in found) {
                if (kept.size == MAX_CANDIDATES_PER_POINT) break
                val duplicate = kept.any {
                    haversineMeters(it.lat, it.lon, cand.lat, cand.lon) < CANDIDATE_DEDUPE_METERS
                }
                if (!duplicate) kept.add(cand)
            }
            return kept
        }

        internal class RouteResult(val meters: Double, val nodeChain: List<Int>)

        /**
         * Shortest road path between two on-edge positions, or null when none exists within
         * [boundMeters]. The chain lists the graph nodes passed through (empty when both
         * candidates share an edge — the edge segment itself is straight).
         */
        internal fun route(
            from: Candidate,
            to: Candidate,
            boundMeters: Double,
            dijkstras: HashMap<Int, ShortestPaths>,
        ): RouteResult? {
            if (from.edge == to.edge) {
                return RouteResult(abs(from.alongFromU - to.alongFromU), emptyList())
            }
            var best: RouteResult? = null
            for (fromEnd in 0..1) {
                val fromNode = if (fromEnd == 0) edgeU[from.edge] else edgeV[from.edge]
                val fromDist = if (fromEnd == 0) from.alongFromU else edgeLen[from.edge] - from.alongFromU
                if (fromDist > boundMeters) continue
                val sp = dijkstras.getOrPut(fromNode) { boundedDijkstra(fromNode, boundMeters) }
                for (toEnd in 0..1) {
                    val toNode = if (toEnd == 0) edgeU[to.edge] else edgeV[to.edge]
                    val toDist = if (toEnd == 0) to.alongFromU else edgeLen[to.edge] - to.alongFromU
                    val mid = sp.dist[toNode] ?: continue
                    val total = fromDist + mid + toDist
                    if (total <= boundMeters && total < (best?.meters ?: Double.MAX_VALUE)) {
                        best = RouteResult(total, sp.pathTo(toNode))
                    }
                }
            }
            return best
        }

        internal class ShortestPaths(
            val dist: HashMap<Int, Double>,
            private val parent: HashMap<Int, Int>,
            private val source: Int,
        ) {
            fun pathTo(node: Int): List<Int> {
                val chain = ArrayList<Int>()
                var n = node
                while (true) {
                    chain.add(n)
                    if (n == source) break
                    n = parent[n] ?: break
                }
                chain.reverse()
                return chain
            }
        }

        private fun boundedDijkstra(source: Int, boundMeters: Double): ShortestPaths {
            val dist = HashMap<Int, Double>()
            val parent = HashMap<Int, Int>()
            val closed = HashSet<Int>()
            val heap = MinHeap(INITIAL_HEAP_CAPACITY)
            dist[source] = 0.0
            heap.push(source, 0.0)
            while (!heap.isEmpty()) {
                val node = heap.pop()
                if (!closed.add(node)) continue
                val d = dist[node] ?: continue
                for (e in adjacency[node]) {
                    val next = if (edgeU[e] == node) edgeV[e] else edgeU[e]
                    val nd = d + edgeLen[e]
                    if (nd <= boundMeters && nd < (dist[next] ?: Double.MAX_VALUE)) {
                        dist[next] = nd
                        parent[next] = node
                        heap.push(next, nd)
                    }
                }
            }
            return ShortestPaths(dist, parent, source)
        }

        companion object {
            // Node identity: coordinates rounded to ~1e-6° (≈0.1 m) — shared OSM nodes come
            // through Overpass with identical coords, so crossing ways join at intersections.
            private const val NODE_KEY_SCALE = 1_000_000.0
            private const val INITIAL_HEAP_CAPACITY = 64

            fun build(roads: List<RoadWay>): RoadGraph {
                val indexByKey = HashMap<Long, Int>()
                val lats = ArrayList<Double>()
                val lons = ArrayList<Double>()
                val incident = ArrayList<MutableList<Int>>()
                val edgeKeys = HashSet<Long>()
                val edgeU = ArrayList<Int>()
                val edgeV = ArrayList<Int>()
                val edgeLen = ArrayList<Double>()

                fun nodeOf(p: GpsPoint): Int {
                    // ±90e6 / ±180e6 µdeg both fit in 32 bits — pack lat high, lon low, collision-free.
                    val key = ((p.latitude * NODE_KEY_SCALE).roundToLong() shl 32) or
                        ((p.longitude * NODE_KEY_SCALE).roundToLong() and 0xFFFFFFFFL)
                    return indexByKey.getOrPut(key) {
                        lats.add(p.latitude)
                        lons.add(p.longitude)
                        incident.add(mutableListOf())
                        lats.size - 1
                    }
                }

                for (way in roads) {
                    for (i in 0 until way.points.size - 1) {
                        val a = nodeOf(way.points[i])
                        val b = nodeOf(way.points[i + 1])
                        if (a == b) continue
                        val key = (minOf(a, b).toLong() shl 32) or maxOf(a, b).toLong()
                        if (!edgeKeys.add(key)) continue // overlapping ways: one edge is enough
                        val e = edgeU.size
                        edgeU.add(a)
                        edgeV.add(b)
                        edgeLen.add(haversineMeters(lats[a], lons[a], lats[b], lons[b]))
                        incident[a].add(e)
                        incident[b].add(e)
                    }
                }
                return RoadGraph(
                    lats.toDoubleArray(),
                    lons.toDoubleArray(),
                    edgeU.toIntArray(),
                    edgeV.toIntArray(),
                    edgeLen.toDoubleArray(),
                    Array(incident.size) { incident[it].toIntArray() },
                )
            }
        }
    }

    /** Closest point on segment a→b to point p in the local planar frame: (lat, lon, t along a→b). */
    private fun projectOntoSegment(
        plat: Double, plon: Double,
        alat: Double, alon: Double,
        blat: Double, blon: Double,
        cosLat: Double,
    ): Triple<Double, Double, Double> {
        val ax = alon * cosLat; val ay = alat
        val bx = blon * cosLat; val by = blat
        val px = plon * cosLat; val py = plat
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
        val projX = ax + t * dx
        val projY = ay + t * dy
        return Triple(projY, projX / cosLat, t) // back to (lat, lon)
    }

    /** Minimal binary min-heap of (node, priority) — commonMain has no java.util.PriorityQueue. */
    private class MinHeap(capacity: Int) {
        private var nodes = IntArray(capacity)
        private var priorities = DoubleArray(capacity)
        private var size = 0

        fun isEmpty(): Boolean = size == 0

        fun push(node: Int, priority: Double) {
            if (size == nodes.size) {
                nodes = nodes.copyOf(size * 2)
                priorities = priorities.copyOf(size * 2)
            }
            var i = size++
            nodes[i] = node
            priorities[i] = priority
            while (i > 0) {
                val parent = (i - 1) / 2
                if (priorities[parent] <= priorities[i]) break
                swap(i, parent)
                i = parent
            }
        }

        fun pop(): Int {
            val top = nodes[0]
            size--
            if (size > 0) {
                nodes[0] = nodes[size]
                priorities[0] = priorities[size]
                var i = 0
                while (true) {
                    val l = 2 * i + 1
                    val r = 2 * i + 2
                    var smallest = i
                    if (l < size && priorities[l] < priorities[smallest]) smallest = l
                    if (r < size && priorities[r] < priorities[smallest]) smallest = r
                    if (smallest == i) break
                    swap(i, smallest)
                    i = smallest
                }
            }
            return top
        }

        private fun swap(a: Int, b: Int) {
            val n = nodes[a]; nodes[a] = nodes[b]; nodes[b] = n
            val p = priorities[a]; priorities[a] = priorities[b]; priorities[b] = p
        }
    }
}
