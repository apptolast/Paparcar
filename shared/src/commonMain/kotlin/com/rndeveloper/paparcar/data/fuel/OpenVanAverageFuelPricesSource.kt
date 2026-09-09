package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.AverageFuelPriceTable
import com.rndeveloper.paparcar.domain.fuel.AverageFuelPricesSource
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * National average fuel prices from OpenVan.camp. [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001]
 *
 * Data: OpenVan.camp, CC BY 4.0 — https://openvan.camp/
 *
 * One request for the whole world: the feed answers 168 countries in ~150 KB, so there is nothing
 * to page and no coordinate to send. That is the opposite shape from [MineturStationsSource], which
 * must ask per province because Spain alone is 12 MB — and the reason this one needs no query type.
 *
 * ⛔ **Nothing here is on the path of a calculation.** The spend figure reads whatever table is
 * already in memory; this only ever replaces it, later, and a failure leaves the previous answer
 * standing. That is what keeps the F5a invariant true: no rung of the price ladder needs the
 * network at the moment it is asked.
 */
class OpenVanAverageFuelPricesSource(
    private val client: HttpClient,
) : AverageFuelPricesSource {

    override suspend fun latest(): Result<AverageFuelPriceTable> = runCatching {
        val response = client.get(ENDPOINT)
        if (!response.status.isSuccess()) {
            throw IllegalStateException("OpenVan answered ${response.status.value}")
        }
        response.bodyAsText()
    }.mapCatching { body ->
        OpenVanFuelPricesParser.parse(body).getOrThrow()
    }

    private companion object {
        /**
         * ⛔ The `source` parameter is not decoration: OpenVan asks integrations to identify
         * themselves so it can credit them in its public reports, and it costs us nothing. The
         * licence (CC BY 4.0) is honoured on the Licences screen, not by this string.
         */
        const val ENDPOINT = "https://openvan.camp/api/fuel/prices?source=paparcar.com"
    }
}
