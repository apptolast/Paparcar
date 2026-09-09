package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DET-A-BT-CONNECT-KNOWS-WHERE-THE-CAR-IS-001] The spatial twin of the freshness gate: does the
 * paired car's last CONNECT say this session's pin is talking about a place the car already left?
 *
 * Cada caso nombra su lado del error, porque los dos no cuestan lo mismo: un `true` de más cuesta
 * una plaza que no se publica (falso negativo); un `false` de más manda a un desconocido a una plaza
 * que se ocupó hace horas.
 */
class ParkedSessionIsContradictedTest {

    private val config = ParkingDetectionConfig()

    private fun fixAt(meters: Double, atMs: Long, accuracy: Float = 10f) = GpsPoint(
        latitude = BASE_LAT + meters / METERS_PER_DEGREE_LAT,
        longitude = BASE_LON,
        accuracy = accuracy,
        timestamp = atMs,
        speed = 0f,
    )

    private val parkedAtA = fixAt(meters = 0.0, atMs = PARKED_AT_MS)

    private fun contradicted(
        connectFix: GpsPoint?,
        connectedAtMs: Long?,
        session: GpsPoint = parkedAtA,
    ) = parkedSessionIsContradictedByBtConnect(session, connectFix, connectedAtMs, config)

    @Test
    fun should_contradictTheSession_when_theCarConnectedAWholeStreetAway() {
        // El caso del hermano: la sesión dice A, el coche lleva horas en B.
        assertTrue(
            contradicted(
                connectFix = fixAt(meters = 1_400.0, atMs = PARKED_AT_MS + 3 * 60 * 60_000L),
                connectedAtMs = PARKED_AT_MS + 3 * 60 * 60_000L,
            ),
        )
    }

    @Test
    fun should_notContradict_when_theConnectHappenedAtTheParkedCar() {
        // El caso NORMAL, y el que no se puede romper: vuelves a tu coche y te montas. El enlace
        // engancha desde la acera, así que unos metros de diferencia son el mismo sitio.
        assertFalse(
            contradicted(
                connectFix = fixAt(meters = 25.0, atMs = PARKED_AT_MS + 60 * 60_000L),
                connectedAtMs = PARKED_AT_MS + 60 * 60_000L,
            ),
        )
    }

    @Test
    fun should_notContradict_when_bothFixesAreCoarseEnoughToExplainTheGap() {
        // 150 m entre dos fixes de ±40 m no es una contradicción: es ruido. La tolerancia suma
        // ambas accuracies precisamente para esto.
        assertFalse(
            contradicted(
                connectFix = fixAt(meters = 150.0, atMs = PARKED_AT_MS + 60_000L, accuracy = 40f),
                connectedAtMs = PARKED_AT_MS + 60_000L,
                session = fixAt(meters = 0.0, atMs = PARKED_AT_MS, accuracy = 40f),
            ),
        )
    }

    @Test
    fun should_notContradict_when_theConnectPredatesThePark() {
        // Un connect ANTERIOR al aparcamiento describe el viaje que trajo el coche hasta aquí: dice
        // dónde estaba antes, no dónde está ahora. Misma regla que el gate de identidad de la red de
        // seguridad (`lastBtConnectedAtMs < sessionStartMs`, DET-BT-IDENTITY-GATE-001).
        assertFalse(
            contradicted(
                connectFix = fixAt(meters = 5_000.0, atMs = PARKED_AT_MS - 60 * 60_000L),
                connectedAtMs = PARKED_AT_MS - 60 * 60_000L,
            ),
        )
    }

    @Test
    fun should_notContradict_when_thereIsNothingToContradictWith() {
        // Falla CERRADO por triplicado: sin fix, sin instante, o sin ninguno de los dos, se publica
        // exactamente como antes de este guard. Un vehículo sin MAC emparejada vive aquí siempre.
        assertFalse(contradicted(connectFix = null, connectedAtMs = PARKED_AT_MS + 60_000L))
        assertFalse(contradicted(connectFix = fixAt(9_000.0, PARKED_AT_MS + 60_000L), connectedAtMs = null))
        assertFalse(contradicted(connectFix = null, connectedAtMs = null))
    }

    @Test
    fun should_sitTheThresholdWhereTheConfigSaysAndNotWhereItIsConvenient() {
        // Testigo del umbral: justo por debajo no contradice, justo por encima sí. Con accuracies de
        // 10 m + 10 m, la frontera está en `btConnectContradictsSessionMeters` + 20.
        val boundary = config.btConnectContradictsSessionMeters + 20f
        val connectAt = PARKED_AT_MS + 60_000L
        assertFalse(
            contradicted(fixAt(boundary - 5.0, connectAt), connectAt),
            "por debajo del umbral se sigue publicando",
        )
        assertTrue(
            contradicted(fixAt(boundary + 5.0, connectAt), connectAt),
            "por encima del umbral la sesión ha perdido el derecho a publicar",
        )
    }

    private companion object {
        const val BASE_LAT = 36.6024
        const val BASE_LON = -6.2766
        const val METERS_PER_DEGREE_LAT = 111_320.0
        const val PARKED_AT_MS = 1_756_000_000_000L
    }
}
