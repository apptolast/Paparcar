package com.rndeveloper.paparcar.domain.push

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * El contrato de cable con `functions/`, leído sin ningún móvil delante.
 * [IOS-THE-GROUP-CANNOT-REACH-AN-IPHONE-001]
 *
 * Esto vivía dentro de un `FirebaseMessagingService`: una clase que ningún test puede instanciar y
 * que iOS no puede tener. Los casos de abajo son los que el servidor manda de verdad —copiados de
 * `index.ts` y `sharedCarFanout.ts`— y los que manda cuando algo le falta.
 */
class PushPayloadTest {

    private val now = 1_000_000L

    @Test
    fun should_readEveryField_when_theZonePushArrivesWhole() {
        val payload = pushPayloadFor(
            mapOf(
                "type" to "zone_spot",
                "spotId" to "spot-1",
                "zoneId" to "zone-1",
                "zoneName" to "Centro",
                "latitude" to "40.4",
                "longitude" to "-3.7",
                "provisional" to "true",
                "expiresAt" to "${now + 60_000}",
            ),
            nowMs = now,
        )

        val spot = assertIs<PushPayload.ZoneSpot>(payload)
        assertEquals("spot-1", spot.spotId)
        assertEquals("zone-1", spot.zoneId)
        assertEquals("Centro", spot.zoneName)
        assertEquals(40.4, spot.latitude)
        assertEquals(-3.7, spot.longitude)
        assertTrue(spot.isProvisional)
        assertEquals(now + 60_000, spot.expiresAt)
    }

    /** Un aviso sobre un hueco que ya no existe es peor que no avisar. */
    @Test
    fun should_ignoreTheSpot_when_itDiedOnTheWay() {
        val payload = pushPayloadFor(
            mapOf(
                "type" to "zone_spot",
                "spotId" to "spot-1",
                "zoneId" to "zone-1",
                "latitude" to "40.4",
                "longitude" to "-3.7",
                "expiresAt" to "${now - 1}",
            ),
            nowMs = now,
        )

        assertIs<PushPayload.Ignored>(payload)
    }

    /** `expiresAt = 0` es «no se sabe», no «caducada en 1970». */
    @Test
    fun should_keepTheSpot_when_noExpiryTravelled() {
        val payload = pushPayloadFor(
            mapOf(
                "type" to "zone_spot",
                "spotId" to "spot-1",
                "zoneId" to "zone-1",
                "latitude" to "40.4",
                "longitude" to "-3.7",
            ),
            nowMs = now,
        )

        assertEquals(0L, assertIs<PushPayload.ZoneSpot>(payload).expiresAt)
    }

    @Test
    fun should_ignoreTheSpot_when_thereIsNoPlaceToPointAt() {
        val payload = pushPayloadFor(
            mapOf("type" to "zone_spot", "spotId" to "spot-1", "zoneId" to "zone-1"),
            nowMs = now,
        )

        assertIs<PushPayload.Ignored>(payload)
    }

    @Test
    fun should_readEveryField_when_theGroupParkedTheCar() {
        val payload = pushPayloadFor(
            mapOf(
                "type" to "shared_car_parked",
                "carId" to "car-1",
                "sessionId" to "sess-1",
                "authorName" to "Ana",
                "addressLine" to "Calle Mayor",
                "latitude" to "40.4",
                "longitude" to "-3.7",
                "parkedAtMs" to "$now",
            ),
            nowMs = now,
        )

        val parked = assertIs<PushPayload.SharedCarParked>(payload)
        assertEquals("car-1", parked.carId)
        assertEquals("sess-1", parked.sessionId)
        assertEquals("Ana", parked.authorName)
        assertEquals("Calle Mayor", parked.addressLine)
        assertEquals(now, parked.parkedAtMs)
    }

    /**
     * El servidor manda la cadena vacía tal cual cuando el perfil no tiene nombre: inventar uno ahí
     * sería ponerle voz al servidor. Aquí se traduce a «no hay nombre», que es lo que la tarjeta
     * sabe decir.
     */
    @Test
    fun should_readABlankNameAsNoName_when_theProfileHasNone() {
        val payload = pushPayloadFor(
            mapOf("type" to "shared_car_left", "carId" to "car-1", "authorName" to ""),
            nowMs = now,
        )

        assertEquals(null, assertIs<PushPayload.SharedCarLeft>(payload).authorName)
    }

    /**
     * ⛔ Sigue siendo noticia del coche compartido. Que le falte la posición cuesta la tarjeta, y el
     * handler es quien decide eso — pero la valla se mueve igual, y por eso esto NO es `Ignored`.
     */
    @Test
    fun should_stillBeSharedCarNews_when_thePositionIsMissing() {
        val payload = pushPayloadFor(
            mapOf("type" to "shared_car_parked", "carId" to "car-1", "sessionId" to "sess-1"),
            nowMs = now,
        )

        assertIs<PushPayload.SharedCarNews>(payload)
        val parked = assertIs<PushPayload.SharedCarParked>(payload)
        assertEquals(null, parked.latitude)
        assertEquals(null, parked.longitude)
    }

    @Test
    fun should_ignoreTheMessage_when_theTypeIsNotOneOfOurs() {
        assertIs<PushPayload.Ignored>(pushPayloadFor(mapOf("type" to "whatever"), nowMs = now))
        assertIs<PushPayload.Ignored>(pushPayloadFor(emptyMap(), nowMs = now))
    }
}
