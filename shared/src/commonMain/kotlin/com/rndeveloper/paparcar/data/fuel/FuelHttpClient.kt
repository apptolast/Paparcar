package com.rndeveloper.paparcar.data.fuel

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

/**
 * The one HTTP client every country adapter shares — with the ONE header that turned out to be
 * load-bearing. [FUEL-LAYER-FOLLOWS-THE-CAMERA-001]
 *
 * ⛔ **Overpass answers 406 to OkHttp's default User-Agent** (`okhttp/x.y.z` sits in its bot
 * filter). Measured on 09-09-2026 bisecting headers against the live endpoint: same request, 200
 * with curl's agent, 406 with okhttp's. This was also the unexplained device failure of
 * FUEL-STATIONS-LIST-001 — Spain never noticed because the Ministry answers first and does not
 * filter. Identifying the app is OSM ecosystem policy anyway, so the agent names us and where to
 * reach us, matching the Photon search's existing practice.
 *
 * A factory (not an inline `HttpClient()` in DI) so a test can drive it with `MockEngine` and
 * PROVE the header is on every request — an untested header vanishes in the next refactor.
 */
fun fuelHttpClient(engine: HttpClientEngine? = null): HttpClient {
    val config: HttpClientConfig<*>.() -> Unit = {
        install(DefaultRequest) {
            header(HttpHeaders.UserAgent, FUEL_HTTP_USER_AGENT)
        }
        // ⛔ Deadline invariant, same one `GEOCODE-DEADLINE-001` states for the search: a request
        // with no deadline is a request that can never fail — and a fuel ask that never returns
        // leaves the layer's `isLoading` pinned true, which silently kills every later ask. The
        // public Overpass instance goes slow often enough that this is a WHEN, not an IF: measured
        // on 09-09-2026 answering 504s and hanging past a minute.
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
        }
    }
    return if (engine != null) HttpClient(engine, config) else HttpClient(config)
}

internal const val FUEL_HTTP_USER_AGENT = "Paparcar/1.0 (+https://paparcar.com)"

/** Overpass' own server-side budget is 20 s, so the client waits a little past it and no more. */
private const val REQUEST_TIMEOUT_MS = 25_000L
private const val SOCKET_TIMEOUT_MS = 25_000L
private const val CONNECT_TIMEOUT_MS = 10_000L
