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
 * La columna `refuelStop`, ejercitada con el driver de Android.
 * [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001]
 *
 * Dos cosas que un `ALTER TABLE` leído a ojo no demuestra: que la fila vieja SOBREVIVE intacta con
 * la columna a NULL —«de ésta no se preguntó», que es la verdad— y que el `COALESCE` del cierre y
 * el `WHERE refuelStop IS NOT NULL` de la respuesta se comportan sobre la tabla real. Lo segundo
 * importa porque la respuesta del usuario y el cierre escriben la MISMA columna, y una de las dos
 * tiene prohibido pisar a la otra.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration20To21Test {

    private val connection = AndroidSQLiteDriver().open(
        ApplicationProvider.getApplicationContext<Context>()
            .getDatabasePath("migration-20-21-witness.db").also { it.parentFile?.mkdirs() }
            .also { if (it.exists()) it.delete() }
            .absolutePath,
    )

    @AfterTest
    fun tearDown() = connection.close()

    private fun seedRow(id: String) = connection.execSQL(
        "INSERT INTO `parking_sessions` (`id`, `userId`, `latitude`, `longitude`, `accuracy`, " +
            "`timestamp`, `isActive`, `routeSnapped`, `publishedSpot`, `endedAtMs`, " +
            "`updatedAt`, `pendingSync`) VALUES " +
            "('$id', 'u-1', 40.4301, -3.7038, 7.0, 1790000000000, 0, 0, 1, 1790000300000, " +
            "1790000300999, 0)",
    )

    private fun refuelStopOf(id: String): String? =
        connection.prepare("SELECT `refuelStop` FROM `parking_sessions` WHERE `id` = '$id'")
            .use { stmt ->
                assertTrue(stmt.step())
                if (stmt.isNull(0)) null else stmt.getText(0)
            }

    @Test
    fun should_leaveOldRowsUnasked_when_theColumnArrives() {
        connection.execSQL(V20_PARKING_SESSIONS)
        seedRow("s-old")

        MIGRATION_20_21.migrate(connection)

        assertEquals(null, refuelStopOf("s-old"), "sin backfill: de las viejas no se preguntó")
        connection.prepare(
            "SELECT `publishedSpot`, `updatedAt`, `pendingSync` FROM `parking_sessions` WHERE `id` = 's-old'",
        ).use { stmt ->
            assertTrue(stmt.step())
            assertEquals(1L, stmt.getLong(0), "la fila vieja no se toca")
            assertEquals(1790000300999L, stmt.getLong(1), "ni su updatedAt se mueve")
            assertEquals(0L, stmt.getLong(2), "ni se manda nada al espejo")
        }
    }

    @Test
    fun should_notReAskAnAnsweredStop_when_aPromotionClosesItAgain() {
        connection.execSQL(V20_PARKING_SESSIONS)
        seedRow("s-pump")
        MIGRATION_20_21.migrate(connection)

        // El cierre marca la parada, el usuario contesta, y la promoción de una salida deducida
        // vuelve a cerrar la misma fila horas después.
        connection.execSQL(CLEAR_ACTIVE.replace(":refuelStop", "'PENDING'").replace(":id", "s-pump"))
        connection.execSQL(ANSWER.replace(":answer", "'CONFIRMED'").replace(":id", "s-pump"))
        connection.execSQL(CLEAR_ACTIVE.replace(":refuelStop", "'PENDING'").replace(":id", "s-pump"))

        assertEquals("CONFIRMED", refuelStopOf("s-pump"), "el COALESCE protege la respuesta dada")
    }

    @Test
    fun should_refuseTheAnswer_when_theStopWasNeverAskedAbout() {
        connection.execSQL(V20_PARKING_SESSIONS)
        seedRow("s-plain")
        MIGRATION_20_21.migrate(connection)

        connection.execSQL(ANSWER.replace(":answer", "'CONFIRMED'").replace(":id", "s-plain"))

        assertEquals(null, refuelStopOf("s-plain"), "una respuesta no inventa un repostaje")
    }
}

/** El esquema REAL de la v20 — la v19 sin cambios de forma (MIGRATION_19_20 sólo anuló datos). */
private const val V20_PARKING_SESSIONS =
    "CREATE TABLE IF NOT EXISTS `parking_sessions` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `vehicleId` TEXT, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `accuracy` REAL NOT NULL, `timestamp` INTEGER NOT NULL, `geofenceId` TEXT, `isActive` INTEGER NOT NULL, `addressStreet` TEXT, `addressCity` TEXT, `addressRegion` TEXT, `addressCountry` TEXT, `addressCountryCode` TEXT, `addressProvince` TEXT, `addressPostalCode` TEXT, `placeInfoName` TEXT, `placeInfoCategory` TEXT, `detectionReliability` REAL, `sizeCategory` TEXT, `carbodyType` TEXT, `spotType` TEXT, `privateZoneId` TEXT, `tripMaxSpeedMps` REAL, `provisionalDepartureAtMs` INTEGER, `armEvidence` TEXT, `decisionEvidence` TEXT, `detectionPath` TEXT, `zoneRadiusMeters` REAL, `routePolyline` TEXT, `routeSnapped` INTEGER NOT NULL, `routeInferredSpans` TEXT, `routeInferredResolution` TEXT, `routeDistanceMeters` REAL, `searchTimeMs` INTEGER, `endedAtMs` INTEGER, `publishedSpot` INTEGER NOT NULL, `retractedAtMs` INTEGER, `updatedAt` INTEGER NOT NULL, `pendingSync` INTEGER NOT NULL, `communitySpotId` TEXT, PRIMARY KEY(`id`))"

/** El cierre tal y como lo escribe `UserParkingDao.clearActiveById`, en su parte de interés. */
private const val CLEAR_ACTIVE =
    "UPDATE parking_sessions SET isActive = 0, refuelStop = COALESCE(refuelStop, :refuelStop), " +
        "pendingSync = 1 WHERE id = ':id'"

/** La respuesta tal y como la escribe `UserParkingDao.answerRefuelStopById`. */
private const val ANSWER =
    "UPDATE parking_sessions SET refuelStop = :answer, pendingSync = 1 " +
        "WHERE id = ':id' AND refuelStop IS NOT NULL"
