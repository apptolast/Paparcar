package com.rndeveloper.paparcar.domain.parkings

import com.rndeveloper.paparcar.domain.util.haversineMeters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The grid, and the one promise it has to keep.
 * [PARKINGS-A-ZONE-IS-ASKED-ONCE-AND-PANNED-FOR-FREE-001]
 */
class ParkingZoneGridTest {

    private val solLat = 40.4168
    private val solLon = -3.7038

    @Test
    fun should_giveTheSameZone_when_askedTwiceAboutTheSamePoint() {
        assertEquals(ParkingZoneGrid.zoneOf(solLat, solLon), ParkingZoneGrid.zoneOf(solLat, solLon))
    }

    @Test
    fun should_giveTheSameZone_when_theCameraPansAFewHundredMetres() {
        // The whole point: a short pan must not produce a new query. At 750 m the OLD rule already
        // re-asked; the zone rule does not.
        val panned = ParkingZoneGrid.zoneOf(solLat + 0.006, solLon + 0.004) // ~670 m NE
        assertEquals(ParkingZoneGrid.zoneOf(solLat, solLon), panned)
    }

    @Test
    fun should_giveADifferentZone_when_theCameraLeavesIt() {
        // Far enough that no cell can hold both: 3 km cells, so 10 km apart is four cells away.
        assertFalse(ParkingZoneGrid.sameZone(solLat, solLon, solLat + 0.09, solLon))
    }

    /**
     * ⛔ **The promise.** A driver standing anywhere in the zone — corner included — must still be
     * covered out to the display radius by a disc drawn from the zone's CENTRE. If this fails, the
     * lane goes quietly short exactly for the people furthest from the centre, which is the hardest
     * kind of bug to notice: it looks like "there are no car parks here".
     */
    @Test
    fun should_reachTheDisplayRadius_forEverybodyInTheZone_includingTheCorners() {
        val zone = ParkingZoneGrid.zoneOf(solLat, solLon)

        // Sweep the cell densely enough to land on its corners, in both axes.
        val step = 0.0005
        var worstShortfall = 0.0
        var probes = 0
        var lat = zone.centreLatitude - 0.02
        while (lat <= zone.centreLatitude + 0.02) {
            var lon = zone.centreLongitude - 0.03
            while (lon <= zone.centreLongitude + 0.03) {
                if (ParkingZoneGrid.zoneOf(lat, lon) == zone) {
                    probes++
                    val fromCentre = haversineMeters(zone.centreLatitude, zone.centreLongitude, lat, lon)
                    val reachLeft = ParkingZoneGrid.ZONE_RADIUS_METERS - fromCentre
                    val shortfall = PublicParkingsQuery.DEFAULT_RADIUS_METERS - reachLeft
                    if (shortfall > worstShortfall) worstShortfall = shortfall
                }
                lon += step
            }
            lat += step
        }

        assertTrue(probes > 1_000, "the sweep must actually cover the cell; it probed $probes points")
        assertTrue(
            worstShortfall <= 0.0,
            "somebody in this zone is served ${worstShortfall.toInt()} m short of the display radius",
        )
    }

    @Test
    fun should_keepZonesRoughlySquare_when_theLatitudeChanges() {
        // ⛔ A degree of longitude is 85 km at Málaga and 62 km at Amsterdam. A grid in bare degrees
        // would make northern zones far narrower than southern ones, and the reach above is sized
        // off ONE cell size — so the cell has to stay put in metres, not in degrees.
        listOf(36.72 to -4.42, 40.42 to -3.70, 52.37 to 4.90).forEach { (lat, lon) ->
            val zone = ParkingZoneGrid.zoneOf(lat, lon)
            val widest = widestSpanOf(zone)
            assertTrue(
                widest in 2_500.0..3_500.0,
                "a zone at $lat is $widest m across; the grid is supposed to be 3 km everywhere",
            )
        }
    }

    /**
     * ⛔ Two neighbours must agree on the grid. The longitude step is derived from the SNAPPED
     * latitude and not from each point's own, precisely so that this holds — a step that varied
     * continuously would give two people on the same street slightly different grids, every cache
     * would miss, and nothing would look broken.
     */
    @Test
    fun should_agreeOnTheGrid_when_twoPointsShareABand() {
        val north = ParkingZoneGrid.zoneOf(solLat + 0.004, solLon)
        val south = ParkingZoneGrid.zoneOf(solLat - 0.004, solLon)
        if (north.centreLatitude == south.centreLatitude) {
            assertEquals(north.centreLongitude, south.centreLongitude)
        }
        // And across the whole band, every point snaps onto the same ladder of longitudes.
        val centres = (0..40).map { ParkingZoneGrid.zoneOf(solLat, solLon + it * 0.001).centreLongitude }
        val spacings = centres.distinct().sorted().zipWithNext { a, b -> b - a }
        assertTrue(
            spacings.all { kotlin.math.abs(it - spacings.first()) < 1e-9 },
            "the ladder must be evenly spaced, got $spacings",
        )
    }

    @Test
    fun should_notBlowUp_when_givenAnAbsurdLatitude() {
        // A broken fix must produce a nonsense-free zone rather than a cell wider than a continent.
        val polar = ParkingZoneGrid.zoneOf(89.9, 12.0)
        assertTrue(polar.centreLongitude.isFinite())
        assertTrue(widestSpanOf(polar) < 10_000.0, "a polar zone must still be bounded")
    }

    // ── El nombre de la celda y su vecindario [OSM-PRECOOKED-SNAPSHOT-001] ──────

    @Test
    fun should_comeBackTheSameZone_when_itIsNamedAndReadAgain() {
        val zone = ParkingZoneGrid.zoneOf(solLat, solLon)
        assertEquals(zone, ParkingZoneGrid.zoneOf(zone.id))
        // Un hemisferio sur y un oeste, que son los que llevan indices negativos en el nombre.
        val southWest = ParkingZoneGrid.zoneOf(-34.60, -58.38)
        assertEquals(southWest, ParkingZoneGrid.zoneOf(southWest.id))
        assertTrue(southWest.id.startsWith("-"), "un indice negativo se escribe, got ${southWest.id}")
    }

    @Test
    fun should_refuseAName_when_itIsNotOne() {
        // ⛔ Esto lee un NOMBRE DE FICHERO y una clave de CDN: lo que no se puede justificar tiene
        // que ser un fallo, no una suposicion. Un parse indulgente sirve los parkings de una celda
        // bajo el nombre de otra y nada aguas abajo podria notarlo.
        listOf("", "_", "1_", "_1", "1", "a_b", "1_2_", "01_2", "1_+2", " 1_2").forEach { bad ->
            assertEquals(null, ParkingZoneGrid.zoneOf(bad), "'$bad' no es un nombre de celda")
        }
    }

    /**
     * ⛔ **El testigo del bloque, y lo que comprueba no es su forma: es su COBERTURA.**
     * [OSM-PRECOOKED-SNAPSHOT-001]
     *
     * Para alguien plantado en cada una de las cuatro esquinas de su celda —que es donde la
     * cobertura es minima— TODO lo que el carril promete ensenarle tiene que caer en alguna celda
     * del bloque. 1.440 rumbos por esquina, sobre nueve puntos del mundo desde Kampala hasta el
     * paralelo 89.
     *
     * Falsificado en las dos direcciones: con `BLOCK_REACH_METERS` sin la media celda, o con las
     * columnas contadas en la banda del centro en vez de banda a banda, este test se pone rojo —
     * las dos formas se probaron y las dos dejaban puntos fuera.
     */
    @Test
    fun should_coverTheDisplayRadius_forSomebodyStandingInAnyCornerOfTheCell() {
        val places = listOf(
            40.4168 to -3.7038, 36.7213 to -4.4214, -34.60 to -58.38, 60.17 to 24.94,
            0.3 to 32.58, 78.22 to 15.63, 41.0 to 0.0, -0.1 to -0.1, 89.5 to 100.0,
        )
        places.forEach { (lat, lon) ->
            val zone = ParkingZoneGrid.zoneOf(lat, lon)
            val block = ParkingZoneGrid.blockAround(zone).toSet()
            // ⛔ El paso se saca EXACTO de dos centros contiguos, no caminando con `probeLonStep`:
            // ese avanza a saltos de 8,5 m y sobreestima el paso, asi que `0,49999 x paso` caia
            // FUERA de la celda y el test se acusaba a si mismo. Con los indices expuestos, la
            // resta de dos centros es el paso y no hay nada que estimar. [MEDIDO al abrirlo]
            val latStep = ParkingZoneGrid.Zone(zone.bandIndex + 1, zone.columnIndex).centreLatitude -
                zone.centreLatitude
            val lonStep = ParkingZoneGrid.Zone(zone.bandIndex, zone.columnIndex + 1).centreLongitude -
                zone.centreLongitude
            val radius = PublicParkingsQuery.DEFAULT_RADIUS_METERS.toDouble()
            listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1).forEach { (latSign, lonSign) ->
                // Un pelo DENTRO de la celda: la esquina exacta ya pertenece a la vecina.
                val cornerLat = zone.centreLatitude + latSign * latStep * CORNER
                val cornerLon = zone.centreLongitude + lonSign * lonStep * CORNER
                assertEquals(
                    zone, ParkingZoneGrid.zoneOf(cornerLat, cornerLon),
                    "la esquina ($latSign,$lonSign) de $zone tiene que ser de su propia celda",
                )
                val metresPerDegreeLon = 111_320.0 * kotlin.math.cos(
                    kotlin.math.abs(cornerLat) * kotlin.math.PI / 180.0,
                ).coerceAtLeast(0.17364818)
                (0 until BEARINGS).forEach { i ->
                    val bearing = i * (360.0 / BEARINGS) * kotlin.math.PI / 180.0
                    val probeLat = cornerLat + kotlin.math.cos(bearing) * radius / 111_320.0
                    val probeLon = cornerLon + kotlin.math.sin(bearing) * radius / metresPerDegreeLon
                    assertTrue(
                        ParkingZoneGrid.zoneOf(probeLat, probeLon) in block,
                        "en $lat,$lon: a ${radius.toInt()} m rumbo ${i * (360.0 / BEARINGS)} de la " +
                            "esquina ($latSign,$lonSign) se sale del bloque",
                    )
                }
            }
        }
    }

    @Test
    fun should_readEachCellOnce_andIncludeTheCellAskedAbout() {
        val zone = ParkingZoneGrid.zoneOf(solLat, solLon)
        val block = ParkingZoneGrid.blockAround(zone)
        assertEquals(block.size, block.distinct().size, "una celda no se lee dos veces")
        assertTrue(zone in block, "el bloque incluye la celda preguntada")
        // ⛔ No es un cuadrado de anillos, y por eso no se afirma que lo sea: 13 a 21 celdas segun
        // donde caiga, no 9. Lo que se afirma es que no se desmadra.
        assertTrue(block.size in 9..32, "un bloque de ${block.size} celdas no es un vecindario")
    }

    /** Cuanto dentro de su celda se planta la esquina de prueba: casi en el borde, no encima. */
    private val CORNER = 0.49999

    /** Rumbos por esquina. 1.440 = uno cada cuarto de grado. */
    private val BEARINGS = 1_440

    /** How tall the zone is in degrees, recovered by walking north until it changes. */
    private fun probeLatStep(zone: ParkingZoneGrid.Zone): Double {
        var lat = zone.centreLatitude
        while (ParkingZoneGrid.zoneOf(lat, zone.centreLongitude) == zone) lat += 0.00005
        return (lat - zone.centreLatitude) * 2
    }

    /** How wide the zone is in metres at its widest, which for a lat/lon cell is its lower edge. */
    private fun widestSpanOf(zone: ParkingZoneGrid.Zone): Double {
        val step = probeLonStep(zone)
        return haversineMeters(
            zone.centreLatitude, zone.centreLongitude - step / 2,
            zone.centreLatitude, zone.centreLongitude + step / 2,
        )
    }

    /** Recovers the grid's longitude step by walking east until the zone changes. */
    private fun probeLonStep(zone: ParkingZoneGrid.Zone): Double {
        var lon = zone.centreLongitude
        while (ParkingZoneGrid.zoneOf(zone.centreLatitude, lon) == zone) lon += 0.0001
        return (lon - zone.centreLongitude) * 2
    }
}
