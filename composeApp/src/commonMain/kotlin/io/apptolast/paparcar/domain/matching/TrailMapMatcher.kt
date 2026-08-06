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
 * v1: each fix is snapped to the nearest point on the nearest road segment, so the polyline sits on
 * the street instead of cutting across blocks from raw GPS drift. Fixes farther than
 * [MAX_SNAP_METERS] from any road (e.g. GPS in a plaza / parking lot, or sparse rural roads) are
 * kept as-is rather than yanked onto a wrong road. [ROUTE-SNAP-001]
 *
 * v2: consecutive snapped points farther apart than [GAP_FILL_MIN_METERS] (the backdated
 * parking→first-fix origin chord, or an OEM GPS hole mid-trip) are FILLED by routing along the road
 * graph (A* over the fetched ways, joined at shared OSM nodes), so the drawn route follows the
 * actual streets instead of drawing a straight chord across the city. A path is only accepted when
 * it is plausibly a drive ([MAX_DETOUR_FACTOR] × the straight distance); a disconnected or
 * implausible graph keeps the honest straight chord. [DET-ROUTE-ORIGIN-001]
 *
 * v3: snapping is continuity-aware (a small Viterbi over per-way candidates) instead of
 * independently nearest-road per fix, so a noisy fix is pulled onto the street the trip is actually
 * following rather than jumping to a parallel road; and short off-road spikes (1–2 fixes with no
 * street nearby between on-road neighbours) are DROPPED so a single bad fix can't bend the line.
 * Long off-road runs (car park, rural track) and off-road trail ends are kept raw — the line stays
 * honest where there genuinely is no street. [ROUTE-LINE-CLEAN-001]
 */
object TrailMapMatcher {

    /** Beyond this distance to the nearest road a fix is off-road: no snap candidate. Urban
     *  multipath routinely pushes fixes 30–60 m off the street, so the radius covers that; the
     *  continuity term is what keeps a far candidate from grabbing a wrong parallel street.
     *  [ROUTE-LINE-CLEAN-001] */
    const val MAX_SNAP_METERS = 60.0

    /** Weight of the continuity term vs the fix→street distance: penalises snapped moves that
     *  stretch or shrink the MEASURED movement between fixes (a jump to a parallel street adds
     *  distance the GPS never travelled; following the real street through a corner doesn't). */
    const val TRANSITION_WEIGHT = 2.0

    /** At most this many candidate streets per fix feed the Viterbi (nearest first). */
    const val MAX_CANDIDATE_WAYS = 6

    /** Off-road runs up to this long between on-road neighbours are spikes → dropped. Longer runs
     *  are a real off-road stretch → kept raw. */
    const val MAX_OUTLIER_RUN = 2

    /** Consecutive snapped points farther apart than this get routed along the road graph. */
    const val GAP_FILL_MIN_METERS = 60.0

    /** A road path longer than this factor × the straight distance is not a plausible direct drive —
     *  keep the straight chord rather than draw a scenic detour we have no evidence for. */
    const val MAX_DETOUR_FACTOR = 3.0

    /** A gap endpoint must attach to a graph node within this distance for routing to run. */
    const val NODE_ATTACH_MAX_METERS = 80.0

    /** Safety valve for pathological graphs: give up the A* search beyond this many expansions. */
    const val MAX_ASTAR_EXPANSIONS = 20_000

    fun snap(points: List<GpsPoint>, roads: List<RoadWay>): List<GpsPoint> {
        if (roads.isEmpty() || points.size < 2) return points
        val snapped = snapWithContinuity(points, roads)
        val cleaned = dropShortOffRoadSpikes(points, snapped)
        return fillGapsAlongRoads(cleaned, roads)
    }

    // ── v3 — continuity-aware snapping + spike dropping [ROUTE-LINE-CLEAN-001] ────────────────────

    /** One possible on-street position for a fix: the best projection onto one way. */
    private class Candidate(val lat: Double, val lon: Double, val emissionMeters: Double)

    /**
     * Snapped position per input point, null = off-road (no street within [MAX_SNAP_METERS]).
     *
     * A tiny Viterbi: per point, the candidates are its best projection onto each nearby way; the
     * chosen path minimises Σ (fix→street distance + [TRANSITION_WEIGHT] × |snapped step − measured
     * step|). The second term is what a wrong parallel street can't fake — jumping sideways adds
     * travel the GPS never measured, while following the real street (even around a corner shared
     * by two ways) matches the measured step closely.
     */
    private fun snapWithContinuity(points: List<GpsPoint>, roads: List<RoadWay>): Array<GpsPoint?> {
        val candidates = points.map { candidatesFor(it, roads) }
        val result = arrayOfNulls<GpsPoint>(points.size)
        // The DP runs over the subsequence of points that have on-street candidates; off-road
        // points neither constrain nor break the chain.
        val onRoad = points.indices.filter { candidates[it].isNotEmpty() }
        if (onRoad.isEmpty()) return result

        val costs = Array(onRoad.size) { DoubleArray(candidates[onRoad[it]].size) }
        val back = Array(onRoad.size) { IntArray(candidates[onRoad[it]].size) { -1 } }
        for (c in candidates[onRoad[0]].indices) {
            costs[0][c] = candidates[onRoad[0]][c].emissionMeters
        }
        for (s in 1 until onRoad.size) {
            val i = onRoad[s]
            val j = onRoad[s - 1]
            val measuredStep = haversineMeters(
                points[j].latitude, points[j].longitude,
                points[i].latitude, points[i].longitude,
            )
            for (c in candidates[i].indices) {
                var bestCost = Double.MAX_VALUE
                var bestPrev = -1
                for (pc in candidates[j].indices) {
                    val snappedStep = haversineMeters(
                        candidates[j][pc].lat, candidates[j][pc].lon,
                        candidates[i][c].lat, candidates[i][c].lon,
                    )
                    val cost = costs[s - 1][pc] + TRANSITION_WEIGHT * abs(snappedStep - measuredStep)
                    if (cost < bestCost) {
                        bestCost = cost
                        bestPrev = pc
                    }
                }
                costs[s][c] = bestCost + candidates[i][c].emissionMeters
                back[s][c] = bestPrev
            }
        }

        var choice = costs.last().indices.minBy { costs.last()[it] }
        for (s in onRoad.size - 1 downTo 0) {
            val i = onRoad[s]
            val cand = candidates[i][choice]
            result[i] = points[i].copy(latitude = cand.lat, longitude = cand.lon)
            choice = back[s][choice]
        }
        return result
    }

    /** Best projection of [p] onto each way within [MAX_SNAP_METERS], nearest [MAX_CANDIDATE_WAYS]. */
    private fun candidatesFor(p: GpsPoint, roads: List<RoadWay>): List<Candidate> {
        // Local equirectangular scale: longitude degrees shrink by cos(lat) so planar distances are
        // ~isotropic over the small bbox of one trip. Roads are nearby, so one cosLat is enough.
        val cosLat = cos(p.latitude * PI / 180.0)
        val found = ArrayList<Candidate>()
        for (way in roads) {
            val v = way.points
            var best: Candidate? = null
            for (i in 0 until v.size - 1) {
                val (lat, lon) = projectOntoSegment(
                    p.latitude, p.longitude,
                    v[i].latitude, v[i].longitude,
                    v[i + 1].latitude, v[i + 1].longitude,
                    cosLat,
                )
                val d = haversineMeters(p.latitude, p.longitude, lat, lon)
                if (d <= MAX_SNAP_METERS && d < (best?.emissionMeters ?: Double.MAX_VALUE)) {
                    best = Candidate(lat, lon, d)
                }
            }
            best?.let(found::add)
        }
        found.sortBy { it.emissionMeters }
        return if (found.size > MAX_CANDIDATE_WAYS) found.subList(0, MAX_CANDIDATE_WAYS) else found
    }

    /**
     * Merges the snapped positions back into one trail, dropping short off-road runs
     * (≤ [MAX_OUTLIER_RUN]) BETWEEN on-road neighbours — those are GPS spikes, and the gap-fill
     * routes the resulting hole along streets when it's long. Off-road runs at the trail ends (the
     * backdated origin can sit in a car park) and longer runs are kept raw: there really is no
     * street there and inventing one would lie.
     */
    private fun dropShortOffRoadSpikes(raw: List<GpsPoint>, snapped: Array<GpsPoint?>): List<GpsPoint> {
        val out = ArrayList<GpsPoint>(raw.size)
        var i = 0
        while (i < raw.size) {
            val onRoad = snapped[i]
            if (onRoad != null) {
                out.add(onRoad)
                i++
                continue
            }
            var end = i
            while (end < raw.size && snapped[end] == null) end++
            val isInteriorSpike = i > 0 && end < raw.size && (end - i) <= MAX_OUTLIER_RUN
            if (!isInteriorSpike) {
                for (k in i until end) out.add(raw[k])
            }
            i = end
        }
        return out
    }

    /** Closest point (lat, lon) on segment a→b to point p, in the local planar frame. */
    private fun projectOntoSegment(
        plat: Double, plon: Double,
        alat: Double, alon: Double,
        blat: Double, blon: Double,
        cosLat: Double,
    ): Pair<Double, Double> {
        val ax = alon * cosLat; val ay = alat
        val bx = blon * cosLat; val by = blat
        val px = plon * cosLat; val py = plat
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
        val projX = ax + t * dx
        val projY = ay + t * dy
        return projY to (projX / cosLat) // back to (lat, lon)
    }

    // ── v2 — gap filling by routing along the road graph [DET-ROUTE-ORIGIN-001] ──────────────────

    private fun fillGapsAlongRoads(snapped: List<GpsPoint>, roads: List<RoadWay>): List<GpsPoint> {
        // Cheap pre-check: build the graph only when some pair actually needs routing.
        val needsFill = (0 until snapped.size - 1).any { i ->
            haversineMeters(
                snapped[i].latitude, snapped[i].longitude,
                snapped[i + 1].latitude, snapped[i + 1].longitude,
            ) > GAP_FILL_MIN_METERS
        }
        if (!needsFill) return snapped

        val graph = RoadGraph.build(roads)
        if (graph.isEmpty) return snapped

        val out = ArrayList<GpsPoint>(snapped.size)
        out.add(snapped.first())
        for (i in 0 until snapped.size - 1) {
            val a = snapped[i]
            val b = snapped[i + 1]
            val straight = haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
            if (straight > GAP_FILL_MIN_METERS) {
                val path = graph.route(a, b, maxLengthMeters = straight * MAX_DETOUR_FACTOR)
                // Insert the road vertices between the two endpoints; timestamps/accuracy inherit the
                // gap's opening point — these are drawn, never measured. [DET-ROUTE-ORIGIN-001]
                path?.forEach { (lat, lon) -> out.add(a.copy(latitude = lat, longitude = lon)) }
            }
            out.add(b)
        }
        return out
    }

    /**
     * The fetched ways as an undirected graph joined at shared OSM nodes (identical vertex
     * coordinates — Overpass emits the same node coords in every way that crosses it). Pure and
     * rebuilt per snap call, which is debounced and off the main thread; a few thousand vertices
     * build in ~ms.
     */
    private class RoadGraph private constructor(
        private val lats: DoubleArray,
        private val lons: DoubleArray,
        private val adjacency: Array<IntArray>,
    ) {
        val isEmpty: Boolean get() = lats.isEmpty()

        /**
         * Shortest road path between the graph nodes nearest to [from] and [to], as (lat, lon)
         * pairs EXCLUDING the endpoints themselves. Null when either endpoint is too far from the
         * graph, the graph is disconnected, or the best path exceeds [maxLengthMeters].
         */
        fun route(from: GpsPoint, to: GpsPoint, maxLengthMeters: Double): List<Pair<Double, Double>>? {
            val start = nearestNode(from.latitude, from.longitude) ?: return null
            val goal = nearestNode(to.latitude, to.longitude) ?: return null
            if (start == goal) return null

            // A* with a haversine heuristic (admissible: roads are never shorter than the crow flies).
            val g = DoubleArray(lats.size) { Double.MAX_VALUE }
            val cameFrom = IntArray(lats.size) { -1 }
            val closed = BooleanArray(lats.size)
            val open = MinHeap(lats.size)
            g[start] = 0.0
            open.push(start, heuristic(start, goal))
            var expansions = 0
            while (!open.isEmpty()) {
                val current = open.pop()
                if (closed[current]) continue
                closed[current] = true
                if (current == goal) break
                if (++expansions > MAX_ASTAR_EXPANSIONS) return null
                for (next in adjacency[current]) {
                    if (closed[next]) continue
                    val tentative = g[current] + edgeMeters(current, next)
                    if (tentative < g[next] && tentative <= maxLengthMeters) {
                        g[next] = tentative
                        cameFrom[next] = current
                        open.push(next, tentative + heuristic(next, goal))
                    }
                }
            }
            if (g[goal] == Double.MAX_VALUE || g[goal] > maxLengthMeters) return null

            val path = ArrayList<Pair<Double, Double>>()
            var node = goal
            while (node != -1) {
                path.add(lats[node] to lons[node])
                node = cameFrom[node]
            }
            path.reverse()
            return path
        }

        private fun nearestNode(lat: Double, lon: Double): Int? {
            var best = -1
            var bestDist = NODE_ATTACH_MAX_METERS
            for (i in lats.indices) {
                val d = haversineMeters(lat, lon, lats[i], lons[i])
                if (d < bestDist) {
                    bestDist = d
                    best = i
                }
            }
            return best.takeIf { it >= 0 }
        }

        private fun heuristic(node: Int, goal: Int): Double =
            haversineMeters(lats[node], lons[node], lats[goal], lons[goal])

        private fun edgeMeters(a: Int, b: Int): Double =
            haversineMeters(lats[a], lons[a], lats[b], lons[b])

        companion object {
            // Node identity: coordinates rounded to ~1e-6° (≈0.1 m) — shared OSM nodes come through
            // Overpass with identical coords, so crossing ways join at intersections.
            private const val NODE_KEY_SCALE = 1_000_000.0

            fun build(roads: List<RoadWay>): RoadGraph {
                val indexByKey = HashMap<Long, Int>()
                val lats = ArrayList<Double>()
                val lons = ArrayList<Double>()
                val edges = ArrayList<MutableSet<Int>>()

                fun nodeOf(p: GpsPoint): Int {
                    // ±90e6 / ±180e6 µdeg both fit in 32 bits — pack lat high, lon low, collision-free.
                    val key = ((p.latitude * NODE_KEY_SCALE).roundToLong() shl 32) or
                        ((p.longitude * NODE_KEY_SCALE).roundToLong() and 0xFFFFFFFFL)
                    return indexByKey.getOrPut(key) {
                        lats.add(p.latitude)
                        lons.add(p.longitude)
                        edges.add(mutableSetOf())
                        lats.size - 1
                    }
                }

                for (way in roads) {
                    for (i in 0 until way.points.size - 1) {
                        val a = nodeOf(way.points[i])
                        val b = nodeOf(way.points[i + 1])
                        if (a != b) {
                            edges[a].add(b)
                            edges[b].add(a)
                        }
                    }
                }
                return RoadGraph(
                    lats.toDoubleArray(),
                    lons.toDoubleArray(),
                    Array(edges.size) { edges[it].toIntArray() },
                )
            }
        }
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
