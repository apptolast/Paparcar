package com.rndeveloper.paparcar.domain.model

/**
 * Represents the user's own active or past parking session.
 *
 * Distinct from [Spot], which represents spots shared with other users.
 * Only one session should be active ([isActive] = true) at a time.
 *
 * [address] and [placeInfo] are enriched asynchronously after the session is saved
 * (via Overpass + geocoder). Both may be null for legacy records or if the network
 * was unavailable at parking time.
 *
 * Snapshot of the vehicle taken at confirmation time:
 * - [spotType]       – how the parking was detected / confirmed.
 * - [sizeCategory]   – length-based size of the vehicle (null = unknown legacy row).
 * - [carbodyType]    – body-shape of the vehicle (null for non-CAR or legacy rows).
 *
 * Both [sizeCategory] and [carbodyType] are propagated to the released [Spot]
 * on departure so the community fit indicator can match against other users.
 */
data class UserParking(
    val id: String,
    val userId: String = "",
    val vehicleId: String? = null,
    val location: GpsPoint,
    val geofenceId: String? = null,
    val isActive: Boolean = true,
    val address: AddressInfo? = null,
    val placeInfo: PlaceInfo? = null,
    /** Probability [0.0, 1.0] that this is a genuine parking event.
     *  1.0 = user manually confirmed; ~0.90 = vehicle-exit signal observed;
     *  ~0.75 = slow-path auto-detection only. */
    val detectionReliability: Float? = null,
    /** How the parking was detected / confirmed. Propagated to the released [Spot]. */
    val spotType: SpotType = SpotType.AUTO_DETECTED,
    /** Snapshot of the vehicle's length category at park time. Propagated to the released [Spot]. */
    val sizeCategory: VehicleSize? = null,
    /** Snapshot of the vehicle's body shape at park time. Null for motorcycles / scooters / bikes. */
    val carbodyType: CarbodyType? = null,
    /** Non-null when the session was parked inside a private zone — spot publication is suppressed on departure. */
    val privateZoneId: String? = null,
    /** Max GPS speed (m/s) observed during the detection session that confirmed this park.
     *  Local-only (never synced) — feeds the repark-plausibility guard: a replacement park whose
     *  session never reached driving speed is suspect. [DET-SOLID-001] */
    val tripMaxSpeedMps: Float? = null,
    /** [DET-HANDOFF-NOT-MANUAL-001 §B] Epoch-ms when a DEDUCED departure published this session's
     *  spot PROVISIONALLY and deliberately did NOT release the session (see
     *  [com.rndeveloper.paparcar.domain.detection.DepartureProof]). The session stays alive — the car
     *  is not given up on a deduction — and this marks that a promotion (drive proven → full TTL +
     *  release) or an expiry (short TTL runs out) is outstanding. Local-only: it coordinates two
     *  moments on THIS device, nothing remote reads it. Null when no deduced departure is pending. */
    val provisionalDepartureAtMs: Long? = null,
    /** Arm-evidence label of the confirming session ("speed", "vehicle_enter", "unverified",
     *  "manual") — the ARM trigger. Synced to Firestore for remote diagnostics. [DET-SOLID-001] */
    val armEvidence: String? = null,
    /**
     * **Qué miró la rama que DECIDIÓ**, en una línea. [DET-A-WALK-IS-NOT-AN-EGRESS-001]
     *
     * `detectionPath` dice QUÉ camino colocó el pin y [armEvidence] qué lo armó; faltaba el tercer
     * cuarto de la pregunta: **con qué números** decidió ese camino. Sin él, el falso positivo de la
     * issue #72 —una zona de 250 m a quien salió ANDANDO de su casa— sólo se podía diagnosticar
     * adivinando cuál de las dos ramas de `vehicularSignal` se había cumplido, porque la traza que
     * lo sabía vivía en logcat y esa cuenta no tenía diagnósticos.
     *
     * ⛔ **UN campo, no diez** (petición expresa del user): sólo las entradas que esa rama miró para
     * decidir, ya formateadas por quien decidió. Llenar cada sesión de columnas «por si acaso» es
     * cómo se acaba con un modelo que nadie sabe leer.
     */
    val decisionEvidence: String? = null,
    /** Confirmation PATH that placed this pin — the answer to "which trigger put this parking".
     *  Pairs with [armEvidence] for full provenance; synced to Firestore. Null for legacy rows.
     *  [DET-PIN-PROVENANCE-001]
     *
     *  ⛔ **The set of values lives in `DetectionPath`, not in this comment.** This KDoc used to
     *  enumerate them and had drifted: it listed `"vehicle-exit"`, which production has never
     *  written — the real label is `"vehicleExit+window+egress"` — and that spelling had spread to
     *  the repository fake and the preview data. A list of strings kept in prose is a list that
     *  goes stale in silence. [DET-DETECTION-PATH-IS-A-TYPE-001] */
    val detectionPath: String? = null,
    /** Non-null when this session is an APPROXIMATE ZONE, not an exact point: the honest-close
     *  ladder proved the car drove away from its last pin but had no pin-grade anchor at the new
     *  spot, so the artifact is an AREA of this radius (meters) — rendered as a circle, never a
     *  deceptively precise pin. Null = exact point (the normal case). Local-only, like an
     *  unrefined "please confirm" mark: it stays on the device that detected it until the user
     *  refines it to an exact point (which then syncs normally). [DET-HONEST-CLOSE-001] */
    val zoneRadiusMeters: Float? = null,
    /** The driven route that led to THIS parking — the trip from the previous park to here — as a
     *  Google-encoded polyline (lat/lon, [com.rndeveloper.paparcar.domain.util.PolylineCodec]). One
     *  compact string (~hundreds of bytes for a whole trip) so per-parking route storage grows the
     *  DB linearly and cheaply. Synced to Firestore (local + remote). Null/blank = no route recorded
     *  (legacy rows, or a drive no recorder covered). ⚠️ A BT park is NOT routeless by nature since
     *  [TRIP-THE-TRACKER-RIDES-THE-SENTINEL-001] — a BT connect starts the same recorder. Rendered
     *  as a polyline in the history detail. [DET-ROUTE-TRACK-001] */
    val routePolyline: String? = null,
    /** Whether [routePolyline] is the FINAL on-road line (map-matched, ready to draw) rather than the
     *  raw fixes still awaiting the one-time snap. false + a non-null [routePolyline] = "recalculating"
     *  (the post-park worker hasn't snapped it onto streets yet); true = draw it as-is. Legacy / BT
     *  rows (null polyline) stay false and simply have no route. Snapped ONCE by the enrichment worker,
     *  never re-computed on display. [DET-ROUTE-SNAP-STORE-001] */
    val routeSnapped: Boolean = false,
    /** Provenance of [routePolyline]'s stretches when the matcher reconstructed data holes along
     *  the roads: encoded spans/cuts ([com.rndeveloper.paparcar.domain.matching.InferredRoute]).
     *  Null = fully measured route (the common case). Synced. [ROUTE-GAP-HONEST-001] */
    val routeInferredSpans: String? = null,
    /** The user's verdict on the inferred stretches — null while the "did you drive this way?"
     *  question is pending. Only meaningful when [routeInferredSpans] is non-null. Synced.
     *  [ROUTE-GAP-HONEST-001] */
    val routeInferredResolution: RouteInferenceResolution? = null,
    /** Haversine length (meters) of [routePolyline] — stamped by the repository on every route
     *  write (raw store, snap, accept-raw, pin-to-pin) so the stats never decode polylines in the
     *  hot path. Null when there is no route (BT/legacy) — a missing distance is "unknown", never
     *  0. Synced. [VEH-STATS-SAY-SOMETHING-USEFUL-001] */
    val routeDistanceMeters: Float? = null,
    /**
     * La plaza de la comunidad que ESTA sesión ocupó, o null — que es lo normal.
     * [SPOT-A-SPACE-THE-COMMUNITY-GAVE-YOU-IS-ONE-YOU-VOUCHED-FOR-001]
     *
     * ⛔ **Esto es lo que el difunto `spotId` fingía ser** [DATA-A-DEAD-FIELD-THAT-LIES-IS-WORSE-THAN-A-DEAD-FIELD-001],
     * y por eso NO se llama igual: aquél no contenía nada y su nombre hacía creer que sí.
     *
     * Sólo se rellena cuando el propio usuario dio fe de estar en la plaza («sigue libre», con el
     * filtro de proximidad ya pasado) y aparcó en ella antes de que caducara. Subcuenta siempre, a
     * propósito: no ver una plaza que te dieron sólo deja la cifra pequeña, mientras que atribuirse
     * una que encontraste tú la convierte en propaganda.
     */
    val communitySpotId: String? = null,

    /** Time this trip spent LOOKING for a spot (ms) — measured at confirm from the recorded
     *  route's clocks (the stored polyline has none, so the figure is measurable exactly once) by
     *  [com.rndeveloper.paparcar.domain.detection.ParkingSearchTime]. Three-valued
     *  [STATS-EVERY-NUMBER-CITES-ITS-EVIDENCE-001]: null = unmeasurable (no fresh route attached,
     *  arrival not witnessed, short hop, legacy); 0 = a MEASURED direct arrival; >0 = demonstrated
     *  circling, timed to the measured stop — never to the confirmation clock. Synced, but the
     *  local row's verdict is authoritative on reconcile (v1 figures were invalidated by
     *  MIGRATION_19_20 and must not resurrect from the mirror).
     *  [TRIP-THE-SEARCH-FOR-A-SPOT-IS-MEASURED-001] */
    val searchTimeMs: Long? = null,
    /** Epoch-ms when this session ENDED (isActive flipped false) — the moment the departure was
     *  committed, or the real deduced-departure instant when a promotion finalizes one. Null while
     *  active and on legacy rows closed before this field existed. Synced.
     *  [VEH-STATS-SAY-SOMETHING-USEFUL-001] */
    val endedAtMs: Long? = null,
    /** True when closing this session PUBLISHED a community spot (tu aparcamiento liberó una
     *  plaza). Stays false for private-zone departures, kept-private releases, reverts and
     *  supersedes. Synced. [VEH-STATS-SAY-SOMETHING-USEFUL-001] */
    val publishedSpot: Boolean = false,
    /**
     * [PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001] Epoch-ms when this parking was WITHDRAWN — the app
     * concluded it never happened, or the user said so by reverting it. Null for every ordinary
     * session, active or ended. Synced.
     *
     * ⛔ **A withdrawal is a state, not a delete.** Same choice the community spot faced and wrote
     * down in [SpotStatus]: *a deleted document just stops arriving, taking the explanation with
     * it.* The row survives for diagnostics — it is precisely the phantom a field report is trying
     * to explain — and it leaves the history through [isRetracted].
     *
     * ⚠️ **And not an enum, deliberately.** `isActive` already answers a different question ("is
     * this the car's current parking"), and a session is withdrawn only after it is closed. Folding
     * the two into one `status` would either duplicate `isActive` — which five Room queries and the
     * Firestore close both read — or migrate all of them, in a ticket whose scope is one row
     * leaving one list. One nullable instant answers *whether* and *when* with no second source of
     * truth.
     */
    val retractedAtMs: Long? = null,
) {
    /** True when this route carries road-inferred stretches the user has not judged yet — the
     *  detail screen asks. [ROUTE-GAP-HONEST-001] */
    val hasPendingInferredRoute: Boolean
        get() = routeSnapped && !routeInferredSpans.isNullOrBlank() && routeInferredResolution == null

    /** True when this session is an approximate AREA rather than an exact point — the single
     *  source of truth is [zoneRadiusMeters] (an area intrinsically has a radius; a boolean
     *  alongside it could contradict it). [DET-HONEST-CLOSE-001] */
    val isApproximate: Boolean get() = zoneRadiusMeters != null

    /** True when this parking has been withdrawn — it is kept for diagnostics and must not appear
     *  in the user's history, stats or chart. [PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001] */
    val isRetracted: Boolean get() = retractedAtMs != null
}
