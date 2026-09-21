package com.rndeveloper.paparcar.domain.spot

import com.rndeveloper.paparcar.domain.model.GpsPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [SPOT-A-SPACE-THE-COMMUNITY-GAVE-YOU-IS-ONE-YOU-VOUCHED-FOR-001]
 *
 * ⛔ El invariante del fichero: **sólo cuenta lo que el propio usuario atestiguó**. Atribuirse una
 * plaza que el conductor encontró solo convierte la cifra en propaganda; no verla sólo la deja
 * pequeña, y ése es el error que se elige.
 */
class CommunityGivenSpotTest {

    private val spotLat = 40.41680
    private val spotLon = -3.70380

    private fun vouch(expiresAtMs: Long = 2_000L) = VouchedSpot(
        spotId = "spot-7",
        latitude = spotLat,
        longitude = spotLon,
        expiresAtMs = expiresAtMs,
    )

    /** Un punto a [metersNorth] metros al norte de la plaza. 1e-5 grados ≈ 1,11 m. */
    private fun parkedAt(metersNorth: Double, atMs: Long = 1_000L) = GpsPoint(
        latitude = spotLat + metersNorth / 111_320.0,
        longitude = spotLon,
        accuracy = 8f,
        timestamp = atMs,
        speed = 0f,
    )

    @Test
    fun should_creditTheCommunity_when_youVouchedForTheSpotAndParkedOnIt() {
        assertEquals("spot-7", communityGaveThisSpot(vouch(), parkedAt(metersNorth = 5.0)))
    }

    @Test
    fun should_creditNobody_when_youNeverVouchedForAnything() {
        // El caso NORMAL, y el que hace que esta cifra subcuente siempre. Es deliberado.
        assertNull(communityGaveThisSpot(vouch = null, parkedAt = parkedAt(metersNorth = 1.0)))
    }

    @Test
    fun should_creditNobody_when_youParkedFarFromTheSpotYouVouchedFor()  {
        // Diste fe de una plaza y aparcaste en otra calle: la plaza de la que diste fe se la quedó
        // otro, y ésta la encontraste tú.
        assertNull(communityGaveThisSpot(vouch(), parkedAt(metersNorth = 120.0)))
    }

    @Test
    fun should_useTheVotePolicyDistance_andNotANewOne() {
        // ⛔ EL testigo del diseño: el límite son los 50 m que la app YA usa para decidir que
        // alguien está EN una plaza — el mismo con el que un solo voto la retira para todos. Si
        // alguien introduce aquí un número propio, esto cae.
        assertEquals("spot-7", communityGaveThisSpot(vouch(), parkedAt(metersNorth = 49.0)))
        assertNull(communityGaveThisSpot(vouch(), parkedAt(metersNorth = 51.0)))
    }

    @Test
    fun should_creditNobody_when_theSpotHadAlreadyExpired() {
        // El reloj es el de la PLAZA, no uno inventado para esta cifra: pasada su caducidad no hay
        // plaza, así que no hay nada que nadie te haya dado.
        assertNull(communityGaveThisSpot(vouch(expiresAtMs = 900L), parkedAt(metersNorth = 2.0, atMs = 1_000L)))
        assertEquals(
            "spot-7",
            communityGaveThisSpot(vouch(expiresAtMs = 1_000L), parkedAt(metersNorth = 2.0, atMs = 1_000L)),
            "justo en el límite todavía existía",
        )
    }
}
