package com.rndeveloper.paparcar.domain.parkings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **La escalera: un hecho por superficie, el de mejor evidencia.**
 * [PARKINGS-A-LIVE-COUNT-OUTRANKS-A-TARIFF-001]
 *
 * ⛔ Lo que estos tests defienden no es el orden de la escalera: es que **nunca se diga de más**.
 * Un parking sin tarifa que dice «Gratuito», un cero que se lee como dato que falta, y sobre todo
 * **una cifra rancia presentada como si fuera de ahora** son las tres formas de romperlo, y las tres
 * tienen aquí su negativa.
 */
class ParkingHeadlineTest {

    private val now = 1_762_000_000_000L

    private fun parking(
        fee: FeeInfo = FeeInfo.UNKNOWN,
        availability: ParkingAvailability? = null,
    ) = PublicParking(
        id = "osm/way/1",
        name = "Prueba",
        operator = null,
        kind = PublicParkingKind.UNDERGROUND,
        latitude = 40.4168,
        longitude = -3.7038,
        fee = fee,
        availability = availability,
    )

    private fun live(
        free: Int,
        agedMinutes: Int = 0,
        stampedByUs: Boolean = false,
    ) = ParkingAvailability(
        free = free,
        updatedAtMs = now - agedMinutes * 60_000L,
        source = "madrid",
        isStampedByUs = stampedByUs,
    )

    // ── Escalón 1: la cuenta viva manda ─────────────────────────────────────

    @Test
    fun should_sayHowManyAreFree_when_theCountIsFresh() {
        val headline = parking(fee = FeeInfo.PAID, availability = live(free = 153)).headline(now)
        // ⛔ Gana a la tarifa aunque la tarifa exista: es el hecho más accionable y el más perecedero.
        assertEquals(ParkingHeadline.FreeSpaces(free = 153, agedMinutes = 0), headline)
    }

    @Test
    fun should_saySoldOut_when_thereAreZeroFree() {
        // ⛔ Su propio escalón, no un `FreeSpaces(0)`: «0 libres» se lee como un dato que FALTA.
        // `Parking Benavente: 0` es un caso real medido en Madrid el 03-11.
        val headline = parking(availability = live(free = 0)).headline(now)
        assertEquals(ParkingHeadline.Full(agedMinutes = 0), headline)
    }

    // ── El invariante nuevo: caduca y BAJA ──────────────────────────────────

    @Test
    fun should_dropToTheTariff_when_theCountHasGoneStale() {
        // ⛔ Un número viejo no se enseña viejo: se deja de enseñar. Si no, la ranura dejaría de ser
        // fiable — y es la misma ranura que F6a dejó sin cifra para no enseñar a desconfiar de ella.
        val stale = live(free = 153, agedMinutes = AVAILABILITY_FRESH_MINUTES + 1)
        val headline = parking(fee = FeeInfo.PAID, availability = stale).headline(now)
        assertEquals(ParkingHeadline.Tariff(FeeInfo.PAID), headline)
    }

    @Test
    fun should_fallAllTheWayToSilence_when_itIsStaleAndHasNoTariffEither() {
        // El caso mayoritario del mundo: ni cifra fresca ni tarifa declarada ⇒ no se dice NADA.
        val stale = live(free = 10, agedMinutes = AVAILABILITY_FRESH_MINUTES + 1)
        assertEquals(ParkingHeadline.Silent, parking(availability = stale).headline(now))
    }

    @Test
    fun should_expireSooner_when_theStampIsOursAndNotTheSources() {
        // ⛔ No es simetría mal entendida: si la fuente no sella, nuestra marca dice cuándo
        // PREGUNTAMOS, así que la cifra nace con una antigüedad desconocida encima. Darle los mismos
        // minutos sería sumarle diez más y presentarla igual de fresca.
        assertTrue(AVAILABILITY_FRESH_MINUTES_WHEN_WE_STAMPED_IT < AVAILABILITY_FRESH_MINUTES)

        val aged = AVAILABILITY_FRESH_MINUTES_WHEN_WE_STAMPED_IT + 1
        val ours = parking(fee = FeeInfo.FREE, availability = live(9, aged, stampedByUs = true))
        val theirs = parking(fee = FeeInfo.FREE, availability = live(9, aged, stampedByUs = false))

        assertEquals(ParkingHeadline.Tariff(FeeInfo.FREE), ours.headline(now), "la nuestra ya caducó")
        assertEquals(ParkingHeadline.FreeSpaces(9, aged), theirs.headline(now), "la suya aún vale")
    }

    @Test
    fun should_notGoNegative_when_theClockIsAhead() {
        // Un reloj del móvil adelantado no puede producir «hace -3 min», que es una frase imposible.
        val future = ParkingAvailability(free = 5, updatedAtMs = now + 600_000, source = "madrid")
        assertEquals(ParkingHeadline.FreeSpaces(5, 0), parking(availability = future).headline(now))
    }

    // ── Escalones 3 y 4: lo de siempre, sin tocar ───────────────────────────

    @Test
    fun should_sayTheTariff_when_thereIsNoLiveCount() {
        assertEquals(ParkingHeadline.Tariff(FeeInfo.PAID), parking(fee = FeeInfo.PAID).headline(now))
        assertEquals(ParkingHeadline.Tariff(FeeInfo.FREE), parking(fee = FeeInfo.FREE).headline(now))
    }

    @Test
    fun should_staySilent_when_nobodyHasPricedIt() {
        // ⛔ El caso NORMAL: el 73,6 % de los parkings de España no declara tarifa, medido sobre el
        // snapshot publicado. Inventar «gratis» mandaría a un conductor contra una barrera.
        assertEquals(ParkingHeadline.Silent, parking(fee = FeeInfo.UNKNOWN).headline(now))
    }

    @Test
    fun should_carryTheAge_when_thereIsSomethingToSayAboutIt() {
        // El peek dice «hace X min», y es lo único honesto cuando el dato empieza a envejecer.
        val headline = parking(availability = live(free = 67, agedMinutes = 3)).headline(now)
        assertEquals(3, (headline as ParkingHeadline.FreeSpaces).agedMinutes)
    }

    // ── El SELLO: tres estados, no dos ──────────────────────────────────────
    // [PARKINGS-SHOW-FEE-AND-OCCUPANCY-001] La escalera retira una cifra rancia, y al retirarla deja
    // «no medimos aquí» y «medimos y la última medida es vieja» indistinguibles. Estos cuatro tests
    // son la negativa a volver a fundirlos.

    @Test
    fun should_haveNoStamp_when_theSourceDoesNotPublishOccupancy() {
        // ⛔ Y no es `Expired` con la fecha a cero: no hay nada que fechar. La ficha calla.
        assertEquals(OccupancyStamp.None, parking(fee = FeeInfo.PAID).occupancyStamp(now))
    }

    @Test
    fun should_stampTheMeasurement_when_theCountStillRules() {
        val measured = live(free = 42, agedMinutes = 3)
        assertEquals(
            OccupancyStamp.Current(measured.updatedAtMs),
            parking(availability = measured).occupancyStamp(now),
        )
    }

    @Test
    fun should_keepTheStamp_when_theCountHasExpired() {
        // La cifra ya no se enseña; el sello SÍ, porque es lo único que dice que aquí se mide.
        val stale = live(free = 42, agedMinutes = AVAILABILITY_FRESH_MINUTES + 1)
        assertEquals(
            OccupancyStamp.Expired(stale.updatedAtMs),
            parking(fee = FeeInfo.PAID, availability = stale).occupancyStamp(now),
        )
    }

    @Test
    fun should_expireTheStampOnTheSameThresholdAsTheCount_when_weStampedItOurselves() {
        // ⛔ El criterio NO se vuelve a escribir en `occupancyStamp`: se lee de `headline`. Este test
        // es el testigo de eso — un dato sellado por nosotros caduca a los 4 min y no a los 10, y si
        // alguien copiase el umbral en el sello, los dos se separarían justo aquí.
        val ours = live(free = 9, agedMinutes = 6, stampedByUs = true)
        val theirs = live(free = 9, agedMinutes = 6, stampedByUs = false)

        assertEquals(OccupancyStamp.Expired(ours.updatedAtMs), parking(availability = ours).occupancyStamp(now))
        assertEquals(OccupancyStamp.Current(theirs.updatedAtMs), parking(availability = theirs).occupancyStamp(now))
    }

    @Test
    fun should_stampAFullCarPark_when_thereIsNoRoomLeft() {
        // ⛔ Un cero es un DATO: «Completo» manda, así que su sello es `Current`. Que un cero cayese
        // a `Expired` diría que no sabemos nada de un parking del que sabemos que está lleno.
        val none = live(free = 0, agedMinutes = 1)
        assertEquals(
            OccupancyStamp.Current(none.updatedAtMs),
            parking(availability = none).occupancyStamp(now),
        )
    }
}
