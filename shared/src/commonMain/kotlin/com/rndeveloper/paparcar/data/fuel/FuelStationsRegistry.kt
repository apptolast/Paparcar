package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.fuel.FuelStationsResult
import com.rndeveloper.paparcar.domain.fuel.FuelStationsSource

/**
 * Picks who answers for a country, and falls back when they cannot. [FUEL-STATIONS-LIST-001]
 *
 * It is itself a [FuelStationsSource], so nothing upstream has to know that "the source" is really a
 * shortlist — adding France is adding one entry to [national], not touching a use case.
 *
 * **The fallback is the whole point.** A national feed can be down, rate-limited, or simply unable
 * to place the user (Spain's is queried by province, so a region name it does not recognise stops it
 * dead). Dropping to OSM there turns "nothing here" into "here is where you can refuel, we just
 * cannot price it" — the honest degradation, and the one the user can act on.
 *
 * ⛔ It never degrades the other way. A national price is never replaced by, mixed with, or corrected
 * against a second opinion: the source that answers, answers whole.
 */
class FuelStationsRegistry(
    private val national: List<FuelStationsSource>,
    private val fallback: FuelStationsSource,
) : FuelStationsSource {

    /** Always: the fallback covers the rest of the world by construction. */
    override fun covers(countryCode: String): Boolean = true

    override suspend fun stationsNear(query: FuelStationsQuery): Result<FuelStationsResult> {
        // `claims`, no `covers`: una fuente puede reconocer el punto aunque nadie sepa el país —
        // que es justo lo que pasa cuando la búsqueda de dirección no contesta. El registro sigue
        // sin saber geografía: pregunta a cada fuente por la suya.
        // [FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001]
        val preferred = national.firstOrNull { it.claims(query) }
            ?: return fallback.stationsNear(query).map {
                // ⛔ TWO cases lived in this one line, and only one of them is a verdict we are
                // entitled to. [FUEL-AN-UNKNOWN-COUNTRY-IS-NOT-A-COUNTRY-WITHOUT-PRICES-001]
                //
                //  · A country we KNOW and do not cover — Denmark — genuinely publishes nothing we
                //    can read. The fallback IS the answer, and the lane may say so.
                //  · A country we could not NAME (the lookup was mute, so `countryCode` is empty)
                //    might be Spain, with the Ministry serving prices for that very street. The
                //    fallback is a SUBSTITUTE, and it is marked as one so the lane says "the
                //    official prices did not load" instead of pronouncing on a whole country.
                //
                // Measured: on a fresh Play Store install the platform geocoder is mute for the
                // first seconds, and Spain was told it does not publish fuel prices.
                if (query.countryCode.isBlank()) it.copy(didFallBack = true) else it
            }

        // ⛔ An EMPTY list from a national feed is a real answer — there is nothing within the radius
        // — so only a FAILURE hands over. Retrying a valid "none" against OSM would quietly turn a
        // priced country into an unpriced one, and the user would never see why the prices vanished.
        return preferred.stationsNear(query).recoverCatching {
            // ⛔ The fallback's answer is MARKED. Without this the lane could not tell «this country
            // publishes no prices» — true where no national feed exists — from «the national feed
            // failed and these are just the forecourts», and it said the first while the second was
            // happening: a verdict about a whole country that nobody verified.
            // [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
            // [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
            fallback.stationsNear(query).getOrThrow().copy(didFallBack = true)
        }
    }

    /**
     * Lo recordado por la fuente a la que esta consulta le TOCA — y si ésa no recuerda nada, lo que
     * recuerde el suelo mundial. [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001]
     *
     * ⛔ El mismo reparto que [stationsNear] y por `claims`, no por `covers`: si el recuerdo
     * saliera de otra fuente que la que contesta en vivo, la tarjeta del primer vistazo cotizaría un
     * precio que la lista de abajo nunca va a enseñar.
     *
     * ⛔ Y **no se marca `didFallBack`**: esa marca dice que el feed nacional *falló al preguntar*,
     * y aquí no se ha preguntado nada. Ponerla aquí haría que el carril acusara de un fallo que no
     * ha ocurrido. [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
     */
    override suspend fun remembered(query: FuelStationsQuery): FuelStationsResult? {
        val preferred = national.firstOrNull { it.claims(query) }
        return preferred?.remembered(query) ?: fallback.remembered(query)
    }
}
