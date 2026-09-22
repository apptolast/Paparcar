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
import kotlin.test.assertTrue

/**
 * La invalidación de los `searchTimeMs` v1, ejercitada de verdad.
 * [STATS-EVERY-NUMBER-CITES-ITS-EVIDENCE-001]
 *
 * La fórmula retirada cerraba el reloj en el timestamp del ancla — un reloj de pared en los
 * caminos manual / «Sí» / timeout — así que todo valor v1 es latencia disfrazada de búsqueda y no
 * se puede recalcular (la polilínea no guarda relojes). La migración debe: anular la cifra, marcar
 * la fila `pendingSync` con `updatedAt` fresco (para que la anulación viaje al espejo remoto), y
 * NO tocar nada más de la fila ni las filas que ya estaban a NULL.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration19To20Test {

    // El driver de ANDROID, el que corre en producción — mismo criterio que Migration17To18Test.
    private val connection = AndroidSQLiteDriver().open(
        ApplicationProvider.getApplicationContext<Context>()
            .getDatabasePath("migration-19-20-witness.db").also { it.parentFile?.mkdirs() }
            .also { if (it.exists()) it.delete() }
            .absolutePath,
    )

    @AfterTest
    fun tearDown() = connection.close()

    @Test
    fun should_nullTheFigureAndFlagTheRowForSync_when_aV1SearchTimeExists() {
        connection.execSQL(V19_PARKING_SESSIONS)
        // El caso de campo del 22-09: pin manual con 387 s de «búsqueda» que eran latencia.
        connection.execSQL(
            "INSERT INTO `parking_sessions` (`id`, `userId`, `latitude`, `longitude`, `accuracy`, " +
                "`timestamp`, `detectionPath`, `routeDistanceMeters`, `searchTimeMs`, " +
                "`isActive`, `routeSnapped`, `publishedSpot`, `updatedAt`, `pendingSync`) VALUES " +
                "('s-1', 'u-1', 36.6082, -6.1927, 1.85, 1790029602173, 'user', " +
                "6379.9, 387236, 1, 0, 0, 1790029602999, 0)",
        )

        MIGRATION_19_20.migrate(connection)

        connection.prepare(
            "SELECT `searchTimeMs`, `pendingSync`, `updatedAt`, `routeDistanceMeters`, " +
                "`detectionPath` FROM `parking_sessions` WHERE `id` = 's-1'",
        ).use { stmt ->
            assertTrue(stmt.step())
            assertTrue(stmt.isNull(0), "la cifra v1 queda ANULADA, no corregida a ojo")
            assertEquals(1L, stmt.getLong(1), "la anulación viaja por el outbox")
            assertTrue(stmt.getLong(2) > 1790029602999L, "updatedAt fresco para ganar el reconcile")
            assertEquals(6379.9, stmt.getDouble(3), 0.01, "la distancia medida no se toca")
            assertEquals("user", stmt.getText(4), "la provenance no se toca")
        }
    }

    @Test
    fun should_leaveUnmeasuredRowsAlone() {
        connection.execSQL(V19_PARKING_SESSIONS)
        connection.execSQL(
            "INSERT INTO `parking_sessions` (`id`, `userId`, `latitude`, `longitude`, `accuracy`, " +
                "`timestamp`, `searchTimeMs`, `isActive`, `routeSnapped`, `publishedSpot`, " +
                "`updatedAt`, `pendingSync`) VALUES " +
                "('s-2', 'u-1', 36.6, -6.2, 5.0, 1789000000000, NULL, 0, 0, 1, 1789000000999, 0)",
        )

        MIGRATION_19_20.migrate(connection)

        connection.prepare(
            "SELECT `pendingSync`, `updatedAt` FROM `parking_sessions` WHERE `id` = 's-2'",
        ).use { stmt ->
            assertTrue(stmt.step())
            assertEquals(0L, stmt.getLong(0), "una fila sin cifra no tiene nada que sincronizar")
            assertEquals(1789000000999L, stmt.getLong(1), "y su updatedAt no se mueve")
        }
    }

    @Test
    fun should_surviveAnEmptyTable() {
        connection.execSQL(V19_PARKING_SESSIONS)
        MIGRATION_19_20.migrate(connection)
    }
}

/** El esquema REAL de la v19 — v18 sin `spotId` más `communitySpotId` (MIGRATION_18_19). */
private const val V19_PARKING_SESSIONS =
    "CREATE TABLE IF NOT EXISTS `parking_sessions` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `vehicleId` TEXT, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `accuracy` REAL NOT NULL, `timestamp` INTEGER NOT NULL, `geofenceId` TEXT, `isActive` INTEGER NOT NULL, `addressStreet` TEXT, `addressCity` TEXT, `addressRegion` TEXT, `addressCountry` TEXT, `addressCountryCode` TEXT, `addressProvince` TEXT, `addressPostalCode` TEXT, `placeInfoName` TEXT, `placeInfoCategory` TEXT, `detectionReliability` REAL, `sizeCategory` TEXT, `carbodyType` TEXT, `spotType` TEXT, `privateZoneId` TEXT, `tripMaxSpeedMps` REAL, `provisionalDepartureAtMs` INTEGER, `armEvidence` TEXT, `decisionEvidence` TEXT, `detectionPath` TEXT, `zoneRadiusMeters` REAL, `routePolyline` TEXT, `routeSnapped` INTEGER NOT NULL, `routeInferredSpans` TEXT, `routeInferredResolution` TEXT, `routeDistanceMeters` REAL, `searchTimeMs` INTEGER, `endedAtMs` INTEGER, `publishedSpot` INTEGER NOT NULL, `retractedAtMs` INTEGER, `updatedAt` INTEGER NOT NULL, `pendingSync` INTEGER NOT NULL, `communitySpotId` TEXT, PRIMARY KEY(`id`))"
