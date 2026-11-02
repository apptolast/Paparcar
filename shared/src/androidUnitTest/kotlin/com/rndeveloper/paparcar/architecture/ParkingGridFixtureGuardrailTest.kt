package com.rndeveloper.paparcar.architecture

import com.rndeveloper.paparcar.domain.parkings.ParkingZoneGrid
import com.rndeveloper.paparcar.tools.parkings.SnapshotToolMain
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * **La tabla de fixtures de la rejilla dice lo que la rejilla hace.**
 * [OSM-PRECOOKED-SNAPSHOT-001]
 *
 * `tools/parkings/grid-fixtures.json` no lo lee la app: existe para que **otro lenguaje** pueda
 * comprobarse contra la rejilla sin reimplementarla a ciegas. El backend del ticket 2 va en
 * TypeScript y necesita `blockAround` para leer el vecindario de la celda que le piden; sin esta
 * tabla, su puerto no tendría contra qué medirse.
 *
 * ⛔ **Y un fichero generado que nadie vigila es un fichero que miente en cuanto se toca el
 * generador.** Este guardarraíl es el que lo impide: si alguien mueve `CELL_METERS`,
 * `DEFAULT_RADIUS_METERS` o la forma del bloque y no regenera la tabla, se pone rojo con el comando
 * exacto que hay que correr.
 *
 * Se regenera con:
 * `./gradlew :shared:parkingSnapshotTool --args="--grid-fixtures tools/parkings/grid-fixtures.json"`
 */
class ParkingGridFixtureGuardrailTest {

    @Test
    fun should_matchTheLiveGrid_forEverySampleInTheTable() {
        val fixtures = readFixtures()
        assertEquals(
            SAMPLE_COUNT, fixtures.samples.size,
            "la tabla tiene que cubrir los $SAMPLE_COUNT puntos del testigo de cobertura",
        )

        fixtures.samples.forEach { sample ->
            val zone = ParkingZoneGrid.zoneOf(sample.lat, sample.lon)
            assertEquals(sample.cell, zone.id, "${sample.lat},${sample.lon}: otra celda. $REGENERATE")
            assertEquals(
                zone, ParkingZoneGrid.zoneOf(sample.cell),
                "el nombre '${sample.cell}' tiene que volver a ser su celda",
            )
            assertEquals(
                sample.centreLat, zone.centreLatitude, TOLERANCE_DEGREES,
                "${sample.cell}: otro centro en latitud. $REGENERATE",
            )
            assertEquals(
                sample.centreLon, zone.centreLongitude, TOLERANCE_DEGREES,
                "${sample.cell}: otro centro en longitud. $REGENERATE",
            )
            assertEquals(
                sample.block, ParkingZoneGrid.blockAround(zone).map { it.id },
                "${sample.cell}: otro bloque — y el orden también cuenta, porque es el que el " +
                    "otro lenguaje va a reproducir. $REGENERATE",
            )
        }
    }

    private fun readFixtures(): SnapshotToolMain.GridFixtures {
        val file = File(repoRoot, FIXTURES_RELATIVE)
        if (!file.isFile) fail("no está desde ${repoRoot.path}: $FIXTURES_RELATIVE. $REGENERATE")
        return json.decodeFromString(file.readText())
    }

    private companion object {
        /** Una sola instancia: `allWarningsAsErrors` convierte en error crear un `Json` por uso.
         *  [BUILD-ZERO-WARNINGS-IS-ENFORCED-001] */
        val json = Json { ignoreUnknownKeys = true }

        const val FIXTURES_RELATIVE = "tools/parkings/grid-fixtures.json"

        /** 1e-9 grados son ~0,1 mm: la tabla guarda el `Double` entero, así que esto sólo absorbe
         *  la diferencia de impresión, no una rejilla distinta. */
        const val TOLERANCE_DEGREES = 1e-9

        /** Los diez de `SnapshotToolMain.SAMPLE_POINTS`. Escrito aquí para que quitar un punto de
         *  la tabla sea rojo, en vez de pasar como «todos los que hay cuadran». */
        const val SAMPLE_COUNT = 10

        const val REGENERATE =
            "Regenerar: ./gradlew :shared:parkingSnapshotTool " +
                "--args=\"--grid-fixtures tools/parkings/grid-fixtures.json\""

        val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("repo root not found from ${File("").absolutePath}")
    }
}
