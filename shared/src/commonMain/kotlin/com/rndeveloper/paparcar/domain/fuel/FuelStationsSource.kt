package com.rndeveloper.paparcar.domain.fuel

/**
 * Where to look for stations, as one value. [FUEL-STATIONS-LIST-001]
 *
 * [countryCode] and [regionName] travel together because they come from the same place — the
 * address lookup the app already did — and because the sources need different halves of it: the
 * registry routes on the country, and Spain's feed is queried by province, so it cannot answer
 * without the region. Passing them as one value is what stopped the registry from having to guess a
 * country from coordinates it has no business geocoding.
 */
data class FuelStationsQuery(
    val latitude: Double,
    val longitude: Double,
    /**
     * ISO 3166-1 alpha-2. **Empty means we could not name the country**, which is a different thing
     * from a country nobody covers — see the registry, which marks the first as a substitute and
     * the second as the answer. [FUEL-AN-UNKNOWN-COUNTRY-IS-NOT-A-COUNTRY-WITHOUT-PRICES-001]
     *
     * ⛔ This line used to read *"Empty when the lookup failed — which routes straight to the
     * fallback"*, describing the symptom as if it were the design: it does route to the fallback,
     * and the whole bug was that nothing downstream could tell why.
     */
    val countryCode: String,
    /** Province/region as a geocoder wrote it. Null is normal; only some sources need it. */
    val regionName: String? = null,
    /**
     * TOWN as a geocoder wrote it. [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
     *
     * It travels beside [regionName] because it is the half that is actually THERE: a phone in El
     * Puerto de Santa María reported its region as «Andalucía» — the autonomous community — and
     * Spain's feed, which is keyed by province, could not be asked at all. The town is what the
     * peek prints under the street, so if there is an address there is a town.
     */
    val cityName: String? = null,
    /**
     * Código postal, cuando la dirección lo trajo. La clave MENOS frágil de las que sirven para
     * enrutar: en España sus dos primeros dígitos SON el id de provincia del ministerio, así que no
     * hay nombre que emparejar. [FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001]
     */
    val postalCode: String? = null,
    val radiusMeters: Int = DEFAULT_RADIUS_METERS,
) {
    companion object {
        /**
         * Walking to a forecourt is not the point — you drive there — but a list of everything
         * within half a province is not a list, it is a search result. Five km is the range at which
         * "on my way" is still true.
         */
        const val DEFAULT_RADIUS_METERS = 5_000
    }
}

/**
 * Whether a station serves a given fuel is answered by [FuelAvailability]; this is where the
 * stations themselves come from.
 *
 * **One source per country, and that source answers whole.** A national feed returns the complete
 * object — brand, coordinates, opening hours AND prices in the same response — so in Spain a single
 * call is the entire answer and there is nothing to join. The registry is `countryCode → source`,
 * not a stack of layers merged object by object.
 *
 * That matters because the obvious design is the wrong one here: "OSM for the world, prices layered
 * on top" means matching two catalogues by proximity, and two stations across a dual carriageway are
 * ~30 m apart. A mismatch there does not look like a bug — it looks like a price.
 *
 * The floor under the registry is OSM: 567.615 stations worldwide and **no prices at all**. So
 * outside a covered country the answer is honest and useful — here is where you can refuel — and the
 * price question is answered by the national average instead, which is a different sentence and is
 * said differently in the UI.
 *
 * ⚠️ Joining two sources will become necessary exactly once: France's official feed carries no
 * station name, and OSM names 100% of what it holds. That is a France problem, to be solved when
 * France ships, not a shape to impose on every country up front.
 */
interface FuelStationsSource {

    /** True when this source can answer for [countryCode] (ISO 3166-1 alpha-2, any case). */
    fun covers(countryCode: String): Boolean

    /**
     * True when this source claims this query — el país, y si no lo hay, lo que la propia fuente
     * sepa del punto. [FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001]
     *
     * Existe porque [covers] solo sabe de un código de país, y **el país es justo lo que falta**
     * cuando la búsqueda de dirección no contesta: ahí ninguna fuente nacional reclamaba nada y el
     * usuario se quedaba sin precios en un país que los publica.
     *
     * ⛔ Reclamar por coordenadas es un derecho de la FUENTE, no del registro: quien sabe hasta
     * dónde llega su país es quien lo sirve. El registro pregunta y no adivina — si esto viviera
     * ahí, la geografía de ocho países acabaría dentro de un `when`.
     *
     * Por defecto es exactamente [covers]: una fuente que no sepa nada de coordenadas no tiene que
     * escribir nada.
     */
    fun claims(query: FuelStationsQuery): Boolean = covers(query.countryCode)

    /**
     * Stations matching [query], nearest first.
     *
     * Returns a failure rather than an empty list when the network or the feed failed: "no stations
     * near you" and "we could not ask" are different answers and the UI must not say the first when
     * it means the second. That distinction is also what lets the registry know when to fall back.
     */
    suspend fun stationsNear(query: FuelStationsQuery): Result<FuelStationsResult>

    /**
     * Lo que esta fuente ya puede decir de [query] **sin preguntar a nadie**, o null cuando nunca
     * se ha registrado nada sobre ella. [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001]
     *
     * ⛔ **No es otra variante de [stationsNear] y no la sustituye.** Existe para que el primer
     * vistazo pueda decir algo del complemento que **nadie ha preguntado todavía**: al arrancar,
     * Home pregunta por el carril por defecto —plazas— y el de gasolineras no se pide hasta que el
     * usuario toca su pestaña. Sin esto, «Aquí y ahora» abre siempre con una sola tarjeta.
     *
     * ⛔ Y su valor **lleva su `rememberedAtMs`**, así que ninguna superficie puede confundirlo con
     * una respuesta fresca: eso es lo que permite enseñarlo sin mentir.
     *
     * Null es el valor honesto por defecto: una fuente que sólo sabe PREGUNTAR no recuerda nada.
     * Lo sobrescriben únicamente las capas que **guardan** algo — memoria y disco. Es el mismo
     * contrato, con la misma forma, que el lado de los parkings.
     * [PARKINGS-A-REMEMBERED-MAP-ANSWERS-BEFORE-THE-NETWORK-001]
     */
    suspend fun remembered(query: FuelStationsQuery): FuelStationsResult? = null
}

/**
 * Stations plus the hour their prices were reported.
 *
 * [reportedAt] travels WITH the stations rather than being fetched apart, because it is the
 * property that makes the list trustworthy — this feature exists because a proxy of the same data
 * ran twelve hours behind and disagreed with 30% of stations. A price with no hour beside it is a
 * price nobody can weigh, so the source that produces the price also produces its timestamp.
 *
 * Null when the source has no prices to date — OSM, for one, has stations and no prices.
 */
data class FuelStationsResult(
    val stations: List<FuelStation>,
    val reportedAt: String? = null,
    /**
     * Which feed answered. [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
     *
     * ⛔ Needed because [FuelStation.id] is only stable *within* its source and all three feeds land
     * in ONE cache table: without the source in the key, Spain's station `1234` would overwrite an
     * OSM station `1234` in another country. Only the source that answered knows its own name, which
     * is why this rides on the result rather than being guessed downstream.
     */
    val source: String = UNKNOWN_SOURCE,
    /**
     * True when the feed stopped at its own output cap, so this answer only speaks for the ground
     * out to its farthest station — not for the radius that was asked.
     *
     * Also something only the answering source can know: OSM caps at `out center 60`, France at
     * `limit=100`, and Spain does not cap at all because it filters a whole province on the phone.
     * A cache that guessed a single cap would either shrink Spain's honest coverage or let a
     * truncated OSM answer promise a ring it never looked at.
     */
    val isTruncated: Boolean = false,
    /**
     * True when a NATIONAL feed existed for this country and could not answer, so what follows came
     * from the worldwide fallback — forecourts, no prices.
     * [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
     *
     * ⛔ Not the same question as «are there prices?». A country with no national feed has no prices
     * and that IS the answer; a country with one that failed has prices we did not manage to fetch.
     * The lane said the first while the second was happening, which reads as the app calling Spain a
     * country without published fuel prices. Only the registry knows which of the two occurred, so
     * the fact rides out from there. [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
     */
    val didFallBack: Boolean = false,
    /**
     * True when this source's prices are near-real-time by construction — Germany's MTS-K obliges
     * stations to report changes within minutes. [FUEL-GERMANY-THE-FEED-ANSWERS-WHOLE-001]
     *
     * Each source states the freshness fact it actually owns, and they are three different
     * sentences: Spain stamps the RESPONSE ([reportedAt]), France stamps each PRICE
     * ([StationPrice.reportedAtMs]), Germany stamps nothing and instead guarantees recency by law.
     * Without this flag the German lane would be the first priced list with no freshness line at
     * all — silent exactly where its feed is strongest.
     *
     * ⛔ Only meaningful on a LIVE answer. A remembered list ([rememberedAtMs] set) reconstructs
     * from disk without it, and the UI's remembered sentence outranks this one anyway: "we could
     * not ask today" is the fact that matters there.
     */
    val pricesAreLive: Boolean = false,
    /**
     * Null when this was just fetched; otherwise when the app last actually managed to ask about
     * this area. [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
     *
     * ⛔ Distinct from [reportedAt], and conflating them would be the subtle lie: `reportedAt` is
     * the FEED's stamp on a response, this is OURS on the asking. A remembered answer has the
     * second and, by construction, no longer has the first.
     */
    val rememberedAtMs: Long? = null,
) {
    companion object {
        /** For sources that have not been taught to name themselves — never written to disk. */
        const val UNKNOWN_SOURCE = "unknown"
    }
}
