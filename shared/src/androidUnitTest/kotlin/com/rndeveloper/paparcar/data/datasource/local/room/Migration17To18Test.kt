package com.rndeveloper.paparcar.data.datasource.local.room

import android.content.Context
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * La migracion que retira `spotId`, **ejercitada de verdad** y no leida.
 * [DATA-A-DEAD-FIELD-THAT-LIES-IS-WORSE-THAN-A-DEAD-FIELD-001]
 *
 * (!) Existe porque esto NO es un `ALTER TABLE ADD COLUMN`: Android 8 trae SQLite 3.19, que no sabe
 * soltar columnas, asi que la tabla se **recrea** y se copian 40 columnas a mano. Una migracion que
 * copia columnas a mano y no tiene testigo es una forma elegante de perder el historial de todo el
 * mundo — y el builder de produccion lleva `fallbackToDestructiveMigration`, de modo que si esto
 * lanzara, la caida seria silenciosa y con forma de base de datos vacia.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration17To18Test {

    // (!) El driver de ANDROID, que es el que corre en produccion — no el bundled. Es justo la
    // diferencia que hace falta probar: el SQLite del sistema es el que no sabe soltar columnas.
    private val connection = AndroidSQLiteDriver().open(
        ApplicationProvider.getApplicationContext<Context>()
            .getDatabasePath("migration-17-18-witness.db").also { it.parentFile?.mkdirs() }
            .also { if (it.exists()) it.delete() }
            .absolutePath,
    )

    @AfterTest
    fun tearDown() = connection.close()

    private fun columnsOf(table: String): List<String> = buildList {
        connection.prepare("PRAGMA table_info(`" + table + "`)").use { stmt ->
            while (stmt.step()) add(stmt.getText(1))
        }
    }

    @Test
    fun should_keepEveryRowAndEveryValue_when_theTableIsRecreatedWithoutTheDeadColumn() {
        connection.execSQL(V17_PARKING_SESSIONS)
        // Valores DISTINGUIBLES en los extremos y en el medio: si el INSERT copiara las columnas
        // corridas, un valor acabaria en la casilla de al lado y esto lo veria.
        connection.execSQL(
            "INSERT INTO `parking_sessions` (`id`, `userId`, `latitude`, `longitude`, `accuracy`, " +
                "`timestamp`, `spotId`, `detectionPath`, `routeDistanceMeters`, `searchTimeMs`, " +
                "`isActive`, `routeSnapped`, `publishedSpot`, `updatedAt`, `pendingSync`) VALUES " +
                "('s-1', 'u-1', 40.4168, -3.7038, 8.0, 1789906123005, 'muere-aqui', 'steps+egress', " +
                "11839.5, 209054, 1, 1, 1, 1789906123999, 0)",
        )

        MIGRATION_17_18.migrate(connection)

        val columns = columnsOf("parking_sessions")
        assertFalse("spotId" in columns, "la columna muerta tiene que haber desaparecido")
        assertEquals(40, columns.size, "y no puede haberse llevado ninguna otra por delante")

        connection.prepare(
            "SELECT `id`, `userId`, `detectionPath`, `routeDistanceMeters`, `searchTimeMs`, " +
                "`publishedSpot`, `updatedAt` FROM `parking_sessions`",
        ).use { stmt ->
            assertTrue(stmt.step(), "la fila tiene que seguir ahi")
            assertEquals("s-1", stmt.getText(0))
            assertEquals("u-1", stmt.getText(1))
            assertEquals("steps+egress", stmt.getText(2))
            assertEquals(11839.5, stmt.getDouble(3))
            assertEquals(209054L, stmt.getLong(4))
            assertEquals(1L, stmt.getLong(5))
            assertEquals(1789906123999L, stmt.getLong(6))
            assertFalse(stmt.step(), "y ser la unica")
        }
    }

    @Test
    fun should_leaveNoScaffolding_when_itFinishes() {
        // (!) Una `parking_sessions_new` olvidada haria que la siguiente migracion que recreara la
        // tabla se encontrara con que ya existe.
        connection.execSQL(V17_PARKING_SESSIONS)

        MIGRATION_17_18.migrate(connection)

        val tables = buildList {
            connection.prepare("SELECT name FROM sqlite_master WHERE type='table'").use { stmt ->
                while (stmt.step()) add(stmt.getText(0))
            }
        }
        assertFalse("parking_sessions_new" in tables, "el andamio se retira")
        assertTrue("parking_sessions" in tables)
    }

    @Test
    fun should_surviveAnEmptyTable() {
        // El caso de quien nunca aparco: copiar cero filas no puede fallar.
        connection.execSQL(V17_PARKING_SESSIONS)

        MIGRATION_17_18.migrate(connection)

        assertEquals(40, columnsOf("parking_sessions").size)
    }
}

/** El esquema REAL de la v17, copiado de `17.json` — el que hay en los moviles ahora mismo. */
private const val V17_PARKING_SESSIONS =
    "CREATE TABLE IF NOT EXISTS `parking_sessions` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `vehicleId` TEXT, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `accuracy` REAL NOT NULL, `timestamp` INTEGER NOT NULL, `spotId` TEXT, `geofenceId` TEXT, `isActive` INTEGER NOT NULL, `addressStreet` TEXT, `addressCity` TEXT, `addressRegion` TEXT, `addressCountry` TEXT, `addressCountryCode` TEXT, `addressProvince` TEXT, `addressPostalCode` TEXT, `placeInfoName` TEXT, `placeInfoCategory` TEXT, `detectionReliability` REAL, `sizeCategory` TEXT, `carbodyType` TEXT, `spotType` TEXT, `privateZoneId` TEXT, `tripMaxSpeedMps` REAL, `provisionalDepartureAtMs` INTEGER, `armEvidence` TEXT, `decisionEvidence` TEXT, `detectionPath` TEXT, `zoneRadiusMeters` REAL, `routePolyline` TEXT, `routeSnapped` INTEGER NOT NULL, `routeInferredSpans` TEXT, `routeInferredResolution` TEXT, `routeDistanceMeters` REAL, `searchTimeMs` INTEGER, `endedAtMs` INTEGER, `publishedSpot` INTEGER NOT NULL, `retractedAtMs` INTEGER, `updatedAt` INTEGER NOT NULL, `pendingSync` INTEGER NOT NULL, PRIMARY KEY(`id`))"
