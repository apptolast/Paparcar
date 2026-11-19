package com.rndeveloper.paparcar.harness

import com.rndeveloper.paparcar.domain.matching.TrailMapMatcher
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.places.RoadWay
import com.rndeveloper.paparcar.domain.util.haversineMeters
import java.io.File
import kotlin.math.abs
import kotlin.test.Test

/**
 * ⛔ BANCO DE MEDIDA, NO UN TEST: su salida es un informe para leer, no una asercion. Lee trazas
 * GPS reales de un corpus que vive FUERA del repo (decision del user, 03-09) y se SALTA solo si no
 * esta en disco, para no romperle la suite a quien no lo tenga.
 *
 * Existe porque hasta ahora la calidad del matcher no se podia medir, solo opinar. Lo que contesta,
 * por sesion: cuantos fixes se quedan sin via a <=120 m (y cuanto terreno se tiraria con ellos),
 * cuantos huecos reconstruye, cuanto se desvia la distancia de la linea dibujada respecto a la
 * medida, y donde quedan picos — con su enlace a OSM para ir a mirarlos.
 *
 * Corre el matcher REAL sobre los fixes crudos de las sesiones de campo con las MISMAS vias de OSM
 * que pediria la app, y mide de donde salen los huecos: si son del dato o los fabrica el matcher al
 * descartar fixes que no sabe poner en una calle.
 */
class RouteMatchHarness {

    private val corpus = File("C:/Users/rndev/Documents/AndroidProjects/route-audit/raw")

    @Test
    fun report() {
        // El corpus vive FUERA del repo a proposito (trazas GPS reales, decision del user 03-09).
        // Sin el, este banco no tiene nada que medir y se salta: asi puede estar versionado sin
        // romperle la suite a nadie que no tenga el corpus en disco.
        val sessionsDir = File(corpus, "sessions")
        if (!sessionsDir.isDirectory) {
            println("RouteMatchHarness: sin corpus en $corpus — nada que medir, saltando.")
            return
        }
        val sessions = sessionsDir.listFiles()!!.sortedBy { it.name }
        println("=".repeat(120))
        println("sesion       fixes  admit  >120m  terreno_tirado  salida  spans  cuts   km_admit  km_salida   delta%  picos")
        println("=".repeat(120))
        for (f in sessions) {
            val sid = f.nameWithoutExtension
            val roadsFile = File(corpus, "roads/$sid.json")
            if (!roadsFile.exists()) { println("$sid: sin vias"); continue }
            val fixes = readFixes(f)
            val roads = readRoads(roadsFile)
            if (fixes.size < 2) { println("$sid: sin fixes"); continue }

            val admitted = admit(fixes)
            val matched = TrailMapMatcher.match(admitted, roads)

            // La MISMA prueba que usa el matcher: hay candidato si alguna via pasa a <= 120 m,
            // medido punto-SEGMENTO (no al vertice).
            val offRoadIdx = admitted.indices.filter { i ->
                val r = if (i == 0) TrailMapMatcher.ORIGIN_SNAP_METERS else TrailMapMatcher.MAX_SNAP_METERS
                roads.none { w -> distanceToWay(admitted[i], w) <= r }
            }
            // Terreno que vive SOLO en los fixes descartados: los tramos internos de cada racha.
            var droppedGround = 0.0
            var k = 0
            while (k < offRoadIdx.size) {
                var j = k
                while (j + 1 < offRoadIdx.size && offRoadIdx[j + 1] == offRoadIdx[j] + 1) j++
                val from = (offRoadIdx[k] - 1).coerceAtLeast(0)
                val to = (offRoadIdx[j] + 1).coerceAtMost(admitted.lastIndex)
                droppedGround += len(admitted.subList(from, to + 1))
                k = j + 1
            }

            val kmIn = len(admitted) / 1000
            val kmOut = len(matched.points) / 1000
            println(
                "%-12s %5d %6d %6d %15.0f %7d %6d %5d %10.2f %10.2f %8.1f %6d".format(
                    sid.takeLast(10), fixes.size, admitted.size, offRoadIdx.size, droppedGround,
                    matched.points.size, matched.inferredSpans.size, matched.cuts.size,
                    kmIn, kmOut, 100 * (kmOut - kmIn) / kmIn, countSpikes(matched.points),
                )
            )
            spikeDetails(matched.points, roads).takeIf { it.isNotEmpty() }?.forEach { println("      $it") }
            if (offRoadIdx.isNotEmpty()) {
                println("      fixes sin via a <=120 m: " + offRoadIdx.joinToString(",").take(160))
            }
            if (!matched.covers(admitted)) {
                println("      la linea no llega al final: %.0f m corta".format(matched.shortfallMeters(admitted)))
            }
        }
    }

    /** Distancia punto-segmento a una via, en metros (proyeccion local plana). */
    private fun distanceToWay(p: GpsPoint, w: com.rndeveloper.paparcar.domain.places.RoadWay): Double {
        var best = Double.MAX_VALUE
        for (i in 1 until w.points.size) {
            best = minOf(best, distanceToSegment(p, w.points[i - 1], w.points[i]))
        }
        return best
    }

    private fun distanceToSegment(p: GpsPoint, a: GpsPoint, b: GpsPoint): Double {
        val mx = 111_320.0 * Math.cos(Math.toRadians(a.latitude))
        val my = 110_540.0
        val px = (p.longitude - a.longitude) * mx; val py = (p.latitude - a.latitude) * my
        val bx = (b.longitude - a.longitude) * mx; val by = (b.latitude - a.latitude) * my
        val n2 = bx * bx + by * by
        if (n2 == 0.0) return Math.hypot(px, py)
        val t = ((px * bx + py * by) / n2).coerceIn(0.0, 1.0)
        return Math.hypot(px - t * bx, py - t * by)
    }

    /** Para cada pico: su tamano, lo que se separan los vecinos, y a que distancia de una via
     *  queda el vertice del pico. Un vertice MUY cerca de una via distinta de la de sus vecinos es
     *  un salto de via del matcher; uno lejos de todo es otra cosa. */
    private fun spikeDetails(
        pts: List<GpsPoint>,
        roads: List<com.rndeveloper.paparcar.domain.places.RoadWay>,
    ): List<String> {
        val out = ArrayList<String>()
        for (i in 1 until pts.size - 1) {
            val l1 = haversineMeters(pts[i-1].latitude, pts[i-1].longitude, pts[i].latitude, pts[i].longitude)
            val l2 = haversineMeters(pts[i].latitude, pts[i].longitude, pts[i+1].latitude, pts[i+1].longitude)
            if (l1 < 1 || l2 < 1) continue
            val b1 = bearing(pts[i-1], pts[i]); val b2 = bearing(pts[i], pts[i+1])
            var d = abs(b1 - b2) % 360.0; if (d > 180) d = 360 - d
            if (d < 150.0 || minOf(l1, l2) > 25.0) continue
            val neighbours = haversineMeters(
                pts[i-1].latitude, pts[i-1].longitude, pts[i+1].latitude, pts[i+1].longitude)
            val toRoad = roads.minOfOrNull { distanceToWay(pts[i], it) } ?: -1.0
            out.add(
                "pico i=%d  saliente %.0f m  vecinos a %.0f m  el vertice esta a %.1f m de una via  https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f#map=19/%.6f/%.6f"
                    .format(i, minOf(l1, l2), neighbours, toRoad,
                        pts[i].latitude, pts[i].longitude, pts[i].latitude, pts[i].longitude)
            )
        }
        return out
    }

    private fun spikeSizesUnused(pts: List<GpsPoint>): List<Double> {
        val out = ArrayList<Double>()
        for (i in 1 until pts.size - 1) {
            val l1 = haversineMeters(pts[i-1].latitude, pts[i-1].longitude, pts[i].latitude, pts[i].longitude)
            val l2 = haversineMeters(pts[i].latitude, pts[i].longitude, pts[i+1].latitude, pts[i+1].longitude)
            if (l1 < 1 || l2 < 1) continue
            val b1 = bearing(pts[i-1], pts[i]); val b2 = bearing(pts[i], pts[i+1])
            var d = abs(b1 - b2) % 360.0; if (d > 180) d = 360 - d
            if (d >= 150.0 && minOf(l1, l2) <= 25.0) out.add(minOf(l1, l2))
        }
        return out
    }

    // --- la MISMA regla de admision que el store [DrivingRoute] ---
    private fun admit(fixes: List<GpsPoint>): List<GpsPoint> {
        val kept = ArrayList<GpsPoint>()
        for (f in fixes) {
            if (f.accuracy > 100f) continue
            val last = kept.lastOrNull()
            if (last == null) { kept.add(f); continue }
            if (f.timestamp - last.timestamp > 20 * 60_000L) { kept.clear(); kept.add(f); continue }
            if (haversineMeters(last.latitude, last.longitude, f.latitude, f.longitude) < 12.0) continue
            kept.add(f)
        }
        return kept
    }

    private fun near(p: GpsPoint, way: RoadWay, meters: Double): Boolean =
        way.points.any { haversineMeters(p.latitude, p.longitude, it.latitude, it.longitude) <= meters }

    private fun len(pts: List<GpsPoint>): Double =
        (1 until pts.size).sumOf {
            haversineMeters(pts[it-1].latitude, pts[it-1].longitude, pts[it].latitude, pts[it].longitude)
        }

    private fun countSpikes(pts: List<GpsPoint>): Int {
        var n = 0
        for (i in 1 until pts.size - 1) {
            val l1 = haversineMeters(pts[i-1].latitude, pts[i-1].longitude, pts[i].latitude, pts[i].longitude)
            val l2 = haversineMeters(pts[i].latitude, pts[i].longitude, pts[i+1].latitude, pts[i+1].longitude)
            if (l1 < 1 || l2 < 1) continue
            val b1 = bearing(pts[i-1], pts[i]); val b2 = bearing(pts[i], pts[i+1])
            var d = abs(b1 - b2) % 360.0; if (d > 180) d = 360 - d
            if (d >= 150.0 && minOf(l1, l2) <= 25.0) n++
        }
        return n
    }

    private fun bearing(a: GpsPoint, b: GpsPoint): Double {
        val p1 = Math.toRadians(a.latitude); val p2 = Math.toRadians(b.latitude)
        val dl = Math.toRadians(b.longitude - a.longitude)
        val y = Math.sin(dl) * Math.cos(p2)
        val x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl)
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360
    }

    private fun readFixes(f: File): List<GpsPoint> {
        val re = Regex(
            """"type":\s*"LOCATION_FIX".*?"timestampMs":\s*(\d+)""", RegexOption.DOT_MATCHES_ALL)
        // El JSON lo escribio nuestro parser con un objeto por evento; se lee campo a campo sin
        // dependencias nuevas: este banco no se commitea.
        val text = f.readText()
        val out = ArrayList<GpsPoint>()
        Regex("""\{[^{}]*?"type":\s*"LOCATION_FIX"[^{}]*?\}""", RegexOption.DOT_MATCHES_ALL)
            .findAll(text).forEach { m ->
                val o = m.value
                val lat = num(o, "lat") ?: return@forEach
                val lon = num(o, "lon") ?: return@forEach
                val ts = num(o, "timestampMs")?.toLong() ?: return@forEach
                out.add(GpsPoint(lat, lon, (num(o, "accuracy") ?: 0.0).toFloat(), ts,
                    (num(o, "speed") ?: 0.0).toFloat()))
            }
        if (re.containsMatchIn(text) && out.isEmpty()) error("no se pudieron leer los fixes de ${f.name}")
        return out.sortedBy { it.timestamp }
    }

    private fun num(o: String, key: String): Double? =
        Regex("\"" + key + "\"" + """:\s*(-?[0-9.eE+]+)""").find(o)?.groupValues?.get(1)?.toDoubleOrNull()

    private fun readRoads(f: File): List<RoadWay> {
        val text = f.readText()
        return Regex("""\{\s*"points":\s*\[(.*?)\]\s*,\s*"isMinor":\s*(true|false)\s*\}""", RegexOption.DOT_MATCHES_ALL)
            .findAll(text).map { m ->
                val pts = Regex("""\[\s*(-?[0-9.]+)\s*,\s*(-?[0-9.]+)\s*\]""").findAll(m.groupValues[1]).map {
                    GpsPoint(it.groupValues[1].toDouble(), it.groupValues[2].toDouble(), 0f, 0L, 0f)
                }.toList()
                RoadWay(pts, m.groupValues[2] == "true")
            }.filter { it.points.size >= 2 }.toList()
    }
}
