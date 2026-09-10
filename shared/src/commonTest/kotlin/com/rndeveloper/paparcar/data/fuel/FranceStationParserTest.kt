package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelAvailability
import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.model.FuelType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The fixture is a **verbatim** slice of the live feed (six real stations pulled on 09/10-09-2026),
 * picked so that every measured trap is in it: the ×100.000 coordinate strings including the ones
 * with a decimal point inside, the ~110 m quantised `geom`, both kinds of rupture, total silence,
 * and an SP95-only forecourt. A parser tested against a tidied-up sample is a parser tested against
 * a format that does not exist. [FUEL-FRANCE-A-PRICE-CARRIES-ITS-OWN-HOUR-001]
 */
class FranceStationParserTest {

    private val fixture = """
        {
          "total_count": 6,
          "results": [
            {
              "id": 31200022,
              "geom": { "lon": 1.421, "lat": 43.616 },
              "latitude": "4361600",
              "longitude": "142100",
              "adresse": "55 BOULEVARD DE SUISSE",
              "ville": "Toulouse",
              "horaires_jour": "Lundi07.00-22.00, Mardi07.00-22.00, Samedi08.00-22.00",
              "horaires_automate_24_24": "Non",
              "gazole_prix": 2.25,
              "gazole_maj": "2026-09-10T00:01:00+00:00",
              "gazole_rupture_type": null,
              "sp95_prix": null, "sp95_maj": null, "sp95_rupture_type": "definitive",
              "sp98_prix": null, "sp98_maj": null, "sp98_rupture_type": "temporaire",
              "e10_prix": 1.99,
              "e10_maj": "2026-09-10T00:01:00+00:00",
              "e10_rupture_type": null,
              "e85_prix": null, "e85_maj": null, "e85_rupture_type": "definitive",
              "gplc_prix": null, "gplc_maj": null, "gplc_rupture_type": "definitive"
            },
            {
              "id": 31150010,
              "geom": { "lon": 1.404265747, "lat": 43.687548 },
              "latitude": "4368754.8",
              "longitude": "140426.5747",
              "adresse": "RUE DES USINESCC TOULOUSE NORD",
              "ville": "Fenouillet",
              "horaires_jour": null,
              "horaires_automate_24_24": "Non",
              "gazole_prix": 2.25,
              "gazole_maj": "2026-09-04T10:30:00+00:00",
              "gazole_rupture_type": null,
              "sp95_prix": 2.127,
              "sp95_maj": "2026-09-04T10:30:00+00:00",
              "sp95_rupture_type": null,
              "sp98_prix": null, "sp98_maj": null, "sp98_rupture_type": "definitive",
              "e10_prix": 2.089,
              "e10_maj": "2026-09-07T10:00:00+00:00",
              "e10_rupture_type": null,
              "e85_prix": null, "e85_maj": null, "e85_rupture_type": "definitive",
              "gplc_prix": 0.979,
              "gplc_maj": "2026-09-04T10:30:00+00:00",
              "gplc_rupture_type": null
            },
            {
              "id": 31330005,
              "geom": { "lon": 1.295984410997, "lat": 43.768739365359004 },
              "latitude": "4376873.9365359",
              "longitude": "129598.4410997",
              "adresse": "18 Av. Lazare Carnot",
              "ville": "Grenade",
              "horaires_jour": "Automate-24-24",
              "horaires_automate_24_24": "Oui",
              "gazole_prix": 2.36,
              "gazole_maj": "2026-09-09T12:17:18+00:00",
              "gazole_rupture_type": null,
              "sp95_prix": 2.29,
              "sp95_maj": "2026-09-09T12:17:18+00:00",
              "sp95_rupture_type": null,
              "sp98_prix": 2.36,
              "sp98_maj": "2026-09-09T12:17:18+00:00",
              "sp98_rupture_type": null,
              "e10_prix": null, "e10_maj": null, "e10_rupture_type": "definitive",
              "e85_prix": null, "e85_maj": null, "e85_rupture_type": "definitive",
              "gplc_prix": null, "gplc_maj": null, "gplc_rupture_type": "definitive"
            },
            {
              "id": 31500007,
              "geom": { "lon": 1.459, "lat": 43.626 },
              "latitude": "4362600",
              "longitude": "145900",
              "adresse": "231 FAUBOURG BONNEFOY",
              "ville": "Toulouse",
              "horaires_jour": null,
              "horaires_automate_24_24": "Non",
              "gazole_prix": 2.27,
              "gazole_maj": "2026-09-09T08:06:00+00:00",
              "gazole_rupture_type": null,
              "sp95_prix": null, "sp95_maj": null, "sp95_rupture_type": null,
              "sp98_prix": 2.208,
              "sp98_maj": "2026-09-09T08:06:00+00:00",
              "sp98_rupture_type": null,
              "e10_prix": 2.121,
              "e10_maj": "2026-09-09T08:06:00+00:00",
              "e10_rupture_type": null,
              "e85_prix": null, "e85_maj": null, "e85_rupture_type": null,
              "gplc_prix": null, "gplc_maj": null, "gplc_rupture_type": null
            },
            {
              "id": 29770002,
              "geom": { "lon": -4.55726292638, "lat": 48.028807028900005 },
              "latitude": "4802880.70289",
              "longitude": "-455726.292638",
              "adresse": "kerivoas",
              "ville": "Audierne",
              "horaires_jour": null,
              "horaires_automate_24_24": "Non",
              "gazole_prix": 2.249,
              "gazole_maj": "2026-09-09T09:05:13+00:00",
              "gazole_rupture_type": null,
              "sp95_prix": 2.199,
              "sp95_maj": "2026-09-09T09:05:14+00:00",
              "sp95_rupture_type": null,
              "sp98_prix": 2.225,
              "sp98_maj": "2026-09-09T09:05:14+00:00",
              "sp98_rupture_type": null,
              "e10_prix": 2.169,
              "e10_maj": "2026-09-09T09:05:14+00:00",
              "e10_rupture_type": null,
              "e85_prix": null, "e85_maj": null, "e85_rupture_type": "definitive",
              "gplc_prix": null, "gplc_maj": null, "gplc_rupture_type": "definitive"
            },
            {
              "id": 58240003,
              "geom": { "lon": 3.1406912128899998, "lat": 46.7535171497 },
              "latitude": "4675351.71497",
              "longitude": "314069.121289",
              "adresse": "ROUTE NATIONALE 7 - LES BOULAISES",
              "ville": "Chantenay-Saint-Imbert",
              "horaires_jour": null,
              "horaires_automate_24_24": "Non",
              "gazole_prix": null, "gazole_maj": null,
              "gazole_rupture_type": "temporaire",
              "sp95_prix": null, "sp95_maj": null, "sp95_rupture_type": "definitive",
              "sp98_prix": null, "sp98_maj": null, "sp98_rupture_type": "temporaire",
              "e10_prix": null, "e10_maj": null, "e10_rupture_type": "temporaire",
              "e85_prix": null, "e85_maj": null, "e85_rupture_type": "temporaire",
              "gplc_prix": null, "gplc_maj": null, "gplc_rupture_type": "definitive"
            }
          ]
        }
    """.trimIndent()

    private val stations: List<FuelStation> = FranceStationParser.parse(fixture)

    private fun station(id: String): FuelStation = stations.single { it.id == id }

    @Test
    fun `should_readCoordinatesFromGeom_when_theScaledStringsCarryADecimalPoint`() {
        // ⛔ The trap: `latitude` is a string scaled ×100.000, and 15 of 100 sampled records put a
        // decimal point INSIDE it ('4802880.70289'). An integer parse of that throws, so this is not
        // "geom is tidier" — it is the only field that can be read at all.
        val audierne = station("29770002")
        assertEquals(48.028807028900005, audierne.latitude)
        assertEquals(-4.55726292638, audierne.longitude)

        // And the quantised case: same station shape, 3-decimal grid (74% of the feed).
        val toulouse = station("31200022")
        assertEquals(43.616, toulouse.latitude)
        assertEquals(1.421, toulouse.longitude)
    }

    @Test
    fun `should_stampEachPriceWithItsOwnHour_when_theStationUpdatedThemApart`() {
        // The whole reason the hour moved inside the price: within ONE station, petrol and diesel
        // were reported three days apart.
        val fenouillet = station("31150010")
        assertEquals(
            1788775200000L, // 2026-09-07T10:00:00Z — the E10 reading
            fenouillet.prices.getValue(FuelType.GASOLINE).reportedAtMs,
        )
        assertEquals(
            1788517800000L, // 2026-09-04T10:30:00Z — the diesel reading, 71 h older
            fenouillet.prices.getValue(FuelType.DIESEL).reportedAtMs,
        )
    }

    @Test
    fun `should_preferE10OverSp95_when_bothArePriced`() {
        // Measured: E10 alone covers 7.285 stations of 9.805 and is the grade France actually sells.
        val fenouillet = station("31150010")
        assertEquals(2.089, fenouillet.prices.getValue(FuelType.GASOLINE).perUnit.amount)
    }

    @Test
    fun `should_fallBackToSp95_when_e10IsInDefinitiveRupture`() {
        // The 1.734 stations that would go mute under an E10-only mapping: SP95 is still a 95 grade
        // and still the petrol a driver buys there.
        val grenade = station("31330005")
        assertEquals(2.29, grenade.prices.getValue(FuelType.GASOLINE).perUnit.amount)
        assertEquals(1788956238000L, grenade.prices.getValue(FuelType.GASOLINE).reportedAtMs)
    }

    @Test
    fun `should_neverPriceAFuelInRupture_when_theFeedReportsOneAsZeroOrMissing`() {
        // ⛔ The rupture trap: no price at all, and above all NOT a 0,000 € that would rank this
        // forecourt as the cheapest in France.
        val outOfDiesel = station("58240003")
        assertNull(outOfDiesel.prices[FuelType.DIESEL])
        assertNull(outOfDiesel.prices[FuelType.GASOLINE])
    }

    @Test
    fun `should_keepStationVisible_when_theRuptureIsTemporary`() {
        // It sells diesel; today it has none. Hiding it would be a false statement tomorrow, so
        // `SOLD` — the station stays on the map, with no price claimed.
        val outOfDiesel = station("58240003")
        assertEquals(FuelAvailability.SOLD, outOfDiesel.availabilityOf(FuelType.DIESEL))
        assertFalse(outOfDiesel.isKnownNotToSell(FuelType.DIESEL))
    }

    @Test
    fun `should_hideOnlyOnDefinitiveRupture_when_theFeedDeclaresTheNegative`() {
        // France declares "we stopped selling this" as a definitive rupture — 6.644 stations say so
        // of LPG, some since 2019. That is the ONE state that may hide a station.
        val toulouse = station("31200022")
        assertEquals(FuelAvailability.NOT_SOLD, toulouse.availabilityOf(FuelType.LPG_CNG))
        assertTrue(toulouse.isKnownNotToSell(FuelType.LPG_CNG))
    }

    @Test
    fun `should_readSilenceAsUnknown_when_thereIsNeitherPriceNorRupture`() {
        // ⛔ Not a negative, even though France usually declares its negatives: 1.149 stations are
        // simply silent about LPG, and hiding them on an assumption is what the three-state model
        // exists to prevent.
        val bonnefoy = station("31500007")
        assertEquals(FuelAvailability.UNKNOWN, bonnefoy.availabilityOf(FuelType.LPG_CNG))
        assertFalse(bonnefoy.isKnownNotToSell(FuelType.LPG_CNG))
    }

    @Test
    fun `should_notCallPetrolUnsold_when_onlyOneOfItsTwoGradesStopped`() {
        // Grenade stopped E10 but sells SP95 — with two grades behind one FuelType the negative has
        // to be unanimous, or a priced forecourt would vanish from a petrol driver's list.
        val grenade = station("31330005")
        assertEquals(FuelAvailability.SOLD, grenade.availabilityOf(FuelType.GASOLINE))
    }

    @Test
    fun `should_carryNoName_when_theFeedHasNoNameField`() {
        // The feed has no `nom`/`marque`/`enseigne` at all — the reason OsmStationNames exists. The
        // address is what the row shows meanwhile.
        val toulouse = station("31200022")
        assertNull(toulouse.name)
        assertEquals("55 BOULEVARD DE SUISSE", toulouse.address)
        assertEquals("Toulouse", toulouse.town)
    }

    @Test
    fun `should_sayRoundTheClock_when_theAutomatFlagIsSet`() {
        // A list of day names says nothing to a driver at 3 a.m.; the 24/24 flag does.
        assertEquals("24/24", station("31330005").schedule)
        assertEquals(
            "Lundi07.00-22.00, Mardi07.00-22.00, Samedi08.00-22.00",
            station("31200022").schedule,
        )
        assertNull(station("31150010").schedule)
    }

    @Test
    fun `should_readEveryStation_when_theResponseIsTheRealShape`() {
        assertEquals(6, stations.size)
        // Ids are numbers on the wire and the join key for names — they must survive as text.
        assertTrue(stations.any { it.id == "58240003" })
    }
}
