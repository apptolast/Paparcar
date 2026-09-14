package com.rndeveloper.paparcar.data.repository

import com.rndeveloper.paparcar.data.overpass.OverpassWalkPatience
import com.rndeveloper.paparcar.data.overpass.OverpassQuota
import com.rndeveloper.paparcar.domain.geocoder.GeocoderDataSource
import com.rndeveloper.paparcar.domain.geocoder.LocalAddressAndPlaceDataSource
import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.places.PlacesDataSource
import com.rndeveloper.paparcar.domain.repository.AddressAndPlaceRepository
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "AddressAndPlaceRepo"

/**
 * Two-phase reverse geocoding with a Room cache in front.
 *
 * Invariants: [GEOCODE-DEADLINE-001]
 * - **Every remote lookup has a deadline.** The API-33 `Geocoder` listener can
 *   simply never call back (GmsCore), and an unbounded await here froze the
 *   whole address/POI pipeline (stuck shimmer at app open). Phase 2's HTTP
 *   timeouts don't cover DNS stalls, so it gets a belt too.
 * - **Only real answers reach the cache.** A timeout/failure emits a fallback
 *   for display but writes nothing: cells live for 30 days, so caching an
 *   empty address (or sealing `poiChecked=true` after a network failure) used
 *   to poison the user's own street for a month. `success(null)` from Places
 *   IS a real answer ("no POI here") and does seal the cell.
 */
class AddressAndPlaceRepositoryImpl(
    private val local: LocalAddressAndPlaceDataSource,
    private val geocoder: GeocoderDataSource,
    private val places: PlacesDataSource,
) : AddressAndPlaceRepository {

    private var evictDone = false

    override fun getAddressAndPlace(
        lat: Double,
        lon: Double,
        persisting: Boolean,
    ): Flow<AddressAndPlace> = flow {
        if (!evictDone) {
            evictDone = true
            local.evictExpired()
        }

        // Local is the single source of truth. Cache hit → emit and done.
        //
        // ⛔ …unless this answer is going to be STORED and the cell says "no place here". That
        // sentence was measured from one point of an 11 × 8 m cell, and 14 m against the 40 m
        // naming radius is exactly the margin that flips it — so for a caller that will keep the
        // answer, the cell is re-measured at ITS coordinates. A cached NAME is served to everyone:
        // being 14 m off about "Supersol is here" costs nothing.
        //
        // This is also what settles the race: walking away from a just-parked car, the user's own
        // position geocodes metres from the pin and seconds before the enrichment worker, so the
        // caller that was going to discard its answer on the next pan used to decide the parking's
        // name for 30 days. [POI-A-FAILED-LOOKUP-IS-NOT-AN-ANSWER-001]
        val cached = local.get(lat, lon)
        if (cached != null && !(persisting && cached.placeInfo == null)) {
            emit(cached)
            return@flow
        }

        // Cache miss — fetch from remote sources, write to local, emit.

        // Phase 1: address (platform geocoder). Not cached yet — local.get() only
        // serves sealed cells, so an unsealed write would be dead weight AND could
        // race a concurrent lookup of the same cell (user + camera at app open),
        // overwriting its freshly sealed row. The single write point is the seal.
        val fetchedAddress: AddressInfo? = withTimeoutOrNull(PHASE1_TIMEOUT_MS) {
            geocoder.getAddress(lat, lon).getOrNull()
        }
        // Phase 1 failed (offline / GMS mute): borrow the nearest cached street as a
        // display-only approximation instead of an empty address. The answer carries
        // approximate=true so persisting consumers skip it, and it is never written back
        // to the cache — the seal below still requires a real Phase-1 answer.
        // [GEO-CACHE-ANSWERS-NEARBY-001]
        //
        // A borrowed cell lends its STREET and never its POI: the neighbour may sit up to 250 m away
        // (RoomLocalAddressAndPlaceDataSource.MAX_NEAREST_DISTANCE_METERS), and while a street that
        // far is still an honest "you are around here", a PLACE that far is exactly the claim
        // [POI-A-PLACE-IS-NAMED-ONLY-IF-YOU-ARE-AT-IT-001] forbids. Phase 2 below is the only thing
        // allowed to name a place, and it measures.
        val nearest = if (fetchedAddress == null) local.getNearest(lat, lon) else null
        val approximate = nearest != null
        // Argumentos con NOMBRE: `AddressInfo(null, null, null, null)` eran cuatro posicionales para una
        // clase de seis campos, que es exactamente la forma del bug que este ticket persigue — hoy
        // inofensiva porque todo es null, mañana no. [FUEL-A-PARKING-WITHOUT-A-COUNTRY-HAS-NO-PRICE-001]
        val address = fetchedAddress ?: nearest?.address
            ?: AddressInfo(street = null, city = null, region = null, country = null)
        emit(AddressAndPlace(address = address, placeInfo = null, approximate = approximate))

        // Phase 2: POI (network, best-effort). Seals the entry with poiChecked=true
        // so subsequent visits get a full cache hit without hitting Overpass again.
        PaparcarLogger.d(TAG, "Phase 2 start — querying Overpass for ($lat, $lon)")
        val placeResult = withTimeoutOrNull(PHASE2_TIMEOUT_MS) {
            runCatching { places.getNearbyPlace(lat, lon) }.getOrElse { e -> Result.failure(e) }
        }
        val placeInfo = placeResult?.getOrNull()
        // Overpass ANSWERED — with a place or with a measured nothing. A timeout (null result) and a
        // failure are the other thing entirely, and the difference leaves this flow on the emission
        // below. [POI-A-FAILED-LOOKUP-IS-NOT-AN-ANSWER-001]
        val poiAnswered = placeResult?.isSuccess == true
        PaparcarLogger.d(TAG, "Phase 2 result — placeInfo=$placeInfo answered=$poiAnswered")
        if (fetchedAddress != null && poiAnswered) {
            local.put(lat, lon, AddressAndPlace(address = address, placeInfo = placeInfo), poiChecked = true)
        }
        // ⛔ Emit on ANSWERED, not on `placeInfo != null`. The no-POI answer used to end this flow in
        // silence, identical to a dead mirror, so a consumer that persists could not tell "there is
        // nothing here" from "nobody replied" — and `EnrichParkingSessionWorker` called both done.
        // A real POI upgrade keeps the address's provenance: if the line under it is still a
        // borrowed neighbour, the emission stays approximate.
        if (poiAnswered) {
            emit(
                AddressAndPlace(
                    address = address,
                    placeInfo = placeInfo,
                    approximate = approximate,
                    poiChecked = true,
                ),
            )
        }
    }

    /**
     * ⛔ `internal` y no `private`: [PHASE2_TIMEOUT_MS] se deriva de dos constantes de otro paquete y
     * la única forma de que esa derivación tenga testigo es que el test pueda leerla. Es el mismo
     * motivo por el que `OverpassWalkPatience` y `OsmPublicParkingParser.MAX_RESULTS` son internal:
     * un número que nadie puede citar es un número que nadie puede comprobar.
     * [OVERPASS-THE-QUOTAS-WAIT-IS-PART-OF-THE-WALK-001]
     */
    internal companion object {
        /** Phase 1 is a local/GMS lookup — healthy answers arrive in well under a second. */
        const val PHASE1_TIMEOUT_MS = 5_000L
        /**
         * Safety net ABOVE the whole Overpass ask, not one request.
         * [OVERPASS-A-SINGLE-MIRROR-HAS-NO-FAILOVER-001]
         * [OVERPASS-THE-QUOTAS-WAIT-IS-PART-OF-THE-WALK-001]
         *
         * ⛔ **Derivado, no carvado** — y esta es la segunda vez que hace falta decirlo en este
         * carril. Valía 20 s cuando la vía hablaba con UN espejo, y cortaba el failover a mitad del
         * segundo. Se subió a 80 s "por encima de `OverpassEndpoints.ALL.size` × 25 s (3 × 25)", que
         * el 14-09-2026 dejó de describir el código por **tres** motivos a la vez: los espejos
         * pasaron a 2, la caminata dejó de costar N × 25 s para estar acotada por
         * [OverpassWalkPatience.WALK_CEILING_MS], y apareció un término que ese cálculo no tenía —
         * la espera del cupo. El número seguía siendo seguro por casualidad, no por diseño.
         *
         * Ahora se suma de sus dos piezas reales, así que mover cualquiera de ellas mueve esto:
         *
         *     esperar turno  +  la caminata entera  +  holgura
         *
         * Que sea largo no lo paga nadie mirando: la fase 1 ya emitió, y esto es enriquecimiento
         * de fondo (un worker con reintentos) cuyo fallo se limita a no sellar la celda.
         */
        val PHASE2_TIMEOUT_MS: Long =
            OverpassQuota.WAIT_CEILING_MS + OverpassWalkPatience.WALK_CEILING_MS + PHASE2_SLACK_MS

        /**
         * Lo que se le concede al resto: parsear, y el salto entre hilos al volver.
         *
         * ⛔ No es margen para que Overpass tarde más — eso ya lo acotan las dos piezas de arriba.
         * Es para que un timeout jamás se dispare por un retraso NUESTRO, que se leería como "el
         * servidor no contestó" y mandaría a alguien a mirar la red.
         */
        private const val PHASE2_SLACK_MS = 10_000L
    }
}
