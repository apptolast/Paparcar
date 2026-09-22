package com.rndeveloper.paparcar.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rndeveloper.paparcar.presentation.map.CameraTarget
import com.rndeveloper.paparcar.presentation.map.ZoomIntent
import com.rndeveloper.paparcar.presentation.util.distanceMeters

@Stable
class HomeUiController {

    var cameraTarget: CameraTarget? by mutableStateOf(null)
        private set

    /** Actual center reported by the map on every camera move (drag or animation). */
    var cameraLat: Double? by mutableStateOf(null)
        private set
    var cameraLon: Double? by mutableStateOf(null)
        private set

    /**
     * True while the map is running a programmatic camera animation
     * (a place the user asked for, driver-follow tracking, initial GPS centering). The Map
     * library fires onCameraMove at ~60 fps during the animation, which
     * would otherwise trigger the idle-drag glass effect — HomeContent
     * reads this flag to suppress the effect for synthetic frames.
     *
     * Set synchronously by every camera door on the way to a [CameraTarget], so it is
     * true before the first animation frame arrives, and cleared by
     * HomeContent once the animation has settled.
     */
    var isProgrammaticMove: Boolean by mutableStateOf(false)
        private set

    /**
     * True while the camera is actively following the live driving puck during a detected trip.
     * Engaged when a trip starts; disengaged the moment the user pans by hand (their gesture wins) or
     * the trip ends. Observable so the map can show a "resume follow" affordance while paused. [FOLLOW-001]
     */
    var followingDriver: Boolean by mutableStateOf(false)
        private set

    fun onCameraMoved(lat: Double, lon: Double) {
        cameraLat = lat
        cameraLon = lon
    }

    /**
     * A genuine user gesture on the map (touch / drag), reported by a pointer observer over the map.
     * We can't infer this from camera frames: programmatic follow + centre moves emit identical frames,
     * so the old `!isProgrammaticMove` heuristic raced (the guard cleared between the ~700ms follow
     * moves) and dropped follow after a single step. A real touch wins — it stops auto re-framing
     * ([FOCUS-002]) and pauses driver-follow ([FOLLOW-001]); the map then shows a resume FAB.
     *
     * It is no longer the ONLY thing that releases follow — [goToPlace] does too, which is what makes
     * requests coming from off the map tile (sheet rows, search, the FAB column) work at all.
     * [UI-MAP-A-TAPPED-PLACE-OUTRANKS-THE-FOLLOWED-CAR-001]
     */
    fun onUserMapGesture() {
        userMovedCameraManually = true
        followingDriver = false
    }

    /**
     * Devuelve la cámara al sitio que el usuario estaba mirando cuando dejó Home.
     * [HOME-WHAT-IS-REMEMBERED-IS-THE-PLACE-YOU-CHOSE-001]
     *
     * ⛔ **Y vuelve a armar [userMovedCameraManually]**, que es la mitad que importa. El carril
     * sobrevive a la navegación porque vive en el ViewModel; esta clase no, y con ella se perdía
     * también el «no me reencuadres»: al volver, el primer fix mandaba la cámara a tu ubicación y
     * la lista seguía hablando de la otra ciudad. Recordar el sitio sin recordar que lo elegiste tú
     * habría durado exactamente un fix.
     *
     * ⚠️ No es [goToPlace]: aquel es una petición NUEVA —suelta el follow y sube el zoom si hace
     * falta— y esto es una restauración. Restaurar no es ir.
     */
    fun restoreChosenView(lat: Double, lon: Double) {
        userMovedCameraManually = true
        setTarget(lat, lon)
    }

    /**
     * [PARK-A-DRAGGED-PIN-MUST-OUTRANK-AN-AUTOMATIC-CAMERA-001] Whether a pin is being POSITIONED
     * right now (Reporting · AddingZone · AddingParking), reported by the host from its own
     * `isPinningMode`.
     *
     * In those modes the camera centre is not a viewport, it is the ANSWER the user is giving: the
     * confirm reads `pinCameraLat/Lon`, which is fed by camera frames. So an automatic re-frame does
     * not merely move the map, it moves the pin — the first GPS fix used to fly the camera onto the
     * user and plant the parking there, however far the user had dragged.
     *
     * Only the AUTOMATIC entries below honour it. A deliberate one ([goToPlace], [framePlaces],
     * [resumeDriverFollow]) still flies: the user pressing "my location" while placing a pin is
     * asking to go there.
     *
     * ⛔ Y solo lo honran cuando hay pin QUE PROTEGER — ver [pinToProtect]. Este guard nació para que
     * el primer fix no arrastrase un pin ya colocado; aplicado a una cámara que nadie ha posado, lo
     * que hacía era dejarla clavada en el Golfo de Guinea justo en el único modo donde su centro es
     * la respuesta del usuario. [HOME-AN-UNSEEDED-CAMERA-IS-NOT-A-PLACE-001]
     */
    fun setPinPlacementActive(active: Boolean) {
        pinPlacementActive = active
    }

    /**
     * ¿Hay un pin que defender de un reencuadre automático?
     *
     * Solo si además de estar colocándose un pin, la cámara está POSADA en algún sitio: [cameraLat]
     * lo escribe únicamente [onCameraMoved], y el mapa dejó de reportar su centro mientras sigue en
     * la semilla de emergencia — así que `cameraLat == null` significa exactamente «nadie ha posado
     * esta cámara». Un pin que no está en ninguna parte no supera a nada: el primer sitio real que
     * aparezca (tu fix, tu coche, una zona) manda. [HOME-AN-UNSEEDED-CAMERA-IS-NOT-A-PLACE-001]
     */
    private val pinToProtect: Boolean get() = pinPlacementActive && cameraLat != null

    fun clearProgrammaticMove() {
        isProgrammaticMove = false
    }

    private var centeredOnUser = false
    // True once the user pans/zooms by hand — disables every automatic re-frame thereafter. [FOCUS-002]
    private var userMovedCameraManually = false

    /** El seguimiento acaba de arrancar y todavía no ha encuadrado. Ver [followDriver].
     *  [HOME-A-LIVE-TRIP-DESERVES-THE-ZOOM-IT-NEEDS-001] */
    private var firstFollowPending = false
    // True while a pin is being positioned — see [setPinPlacementActive].
    // [PARK-A-DRAGGED-PIN-MUST-OUTRANK-AN-AUTOMATIC-CAMERA-001]
    private var pinPlacementActive = false
    // True when the initial focus already framed the parked car, so [FOCUS-002] needn't re-fire.
    // The ASK does not set it: framing a question is not framing a session, which is what lets the
    // session born from a "Yes" still re-frame. [UI-THE-ASK-IS-A-CAMERA-SUBJECT-001]
    private var initialFocusWasParking = false
    private var refocusedOnParking = false
    // Identity (post timestamp) of the question whose place is already framed. [UI-THE-ASK-IS-A-CAMERA-SUBJECT-001]
    private var framedAskAtMs: Long? = null

    /**
     * A place the USER asked to see — a spot row, a search result, a zone chip, a marker, a camera
     * FAB. It outranks driver-follow: the camera flies there and STAYS there.
     * [UI-MAP-A-TAPPED-PLACE-OUTRANKS-THE-FOLLOWED-CAR-001]
     *
     * Following the car is a default, not a lock. Before this, follow was only released by a finger
     * landing on the map tile (`onUserMapGesture`), so every request coming from a surface that is
     * NOT the map — the sheet, the search header, the FAB column — was silently dropped by the map's
     * follow branch: no camera move, no error. Rank cannot be inferred from where the finger was, so
     * it is declared at the door instead, and the low-level setters are private: a name that doesn't
     * state its rank is exactly what lets the next call site be born broken.
     */
    fun goToPlace(
        lat: Double,
        lon: Double,
        zoom: Float? = null,
        /** Por defecto un SUELO: ir a un sitio no puede costarle al usuario volver a acercar.
         *  [MAP-A-CAMERA-MOVE-MUST-NOT-COST-A-PINCH-001] */
        zoomIntent: ZoomIntent = ZoomIntent.AtLeast,
    ) {
        followingDriver = false
        setTarget(lat, lon, zoom, zoomIntent)
    }

    /** Deliberate two-point framing (the midpoint FAB: your car AND you). Outranks follow, as [goToPlace]. */
    fun framePlaces(lat1: Double, lon1: Double, lat2: Double, lon2: Double) {
        followingDriver = false
        setBoundsTarget(lat1, lon1, lat2, lon2)
    }

    private fun setTarget(
        lat: Double,
        lon: Double,
        zoom: Float? = null,
        zoomIntent: ZoomIntent = ZoomIntent.AtLeast,
    ) {
        isProgrammaticMove = true
        cameraTarget = CameraTarget(
            lat, lon, zoom,
            token = (cameraTarget?.token ?: 0) + 1,
            zoomIntent = zoomIntent,
        )
    }

    private fun setBoundsTarget(lat1: Double, lon1: Double, lat2: Double, lon2: Double) {
        isProgrammaticMove = true
        cameraTarget = CameraTarget(
            lat = lat1,
            lon = lon1,
            boundsLat2 = lat2,
            boundsLon2 = lon2,
            token = (cameraTarget?.token ?: 0) + 1,
        )
    }

    /**
     * One-shot initial focus on the first GPS fix, framed by the user's current state so Home always
     * opens on something meaningful — never on an empty patch of map. [FOCUS-001]
     *
     *  - **Parked**: frame the car. If the user is within [BOUNDS_MAX_SPAN_M] of it, fit BOTH via
     *    bounds so "my car + me" read together; otherwise centre tight on the car ([FOCUS_PARKED_ZOOM]).
     *  - **Not parked**: centre on the user at a slightly wider [FOCUS_SEARCH_ZOOM] so nearby free
     *    spots reveal around them. A [selectedSpot] (e.g. deep-link) wins the centre when present.
     *
     * Idempotent: only the first call moves the camera; later GPS frames are ignored so the map is
     * never yanked out from under a user who has started panning. [FOCUS-002] extends this to re-fire
     * once if a parking session arrives after the first fix but before any manual pan.
     *
     * ⚠️ That "never yanked out from under a user who has started panning" was, for a long time, only
     * TRUE AFTER the first fix: `centeredOnUser` answers "did I already centre once", not "did the
     * user already pan". A cold start whose first fix lands LATE — the normal case — left this
     * one-shot armed while the user was already dragging the map, and then flew the camera onto them.
     * With the pin being the camera centre in pin modes, that did not just move the map: it planted
     * the parking on the user. Hence the two guards below.
     * [PARK-A-DRAGGED-PIN-MUST-OUTRANK-AN-AUTOMATIC-CAMERA-001]
     *
     * An open "did you park?" question consumes this one-shot before the first fix even arrives, so
     * Home opens on the place being asked about rather than on the user — there is no `ask` parameter
     * here because that framing already happened. [UI-THE-ASK-IS-A-CAMERA-SUBJECT-001]
     *
     * AUTOMATIC framing, so it goes through the private setter and stays neutral on driver-follow:
     * opening the app mid-trip centres on the user, which IS centring on the car being driven
     * ([DET-READY-TRIP-OVER-PARKED-001]) — revoking follow here would make the app un-follow itself on
     * launch. The rank is "what the user asked for", not "anything that moves the camera".
     * [UI-MAP-A-TAPPED-PLACE-OUTRANKS-THE-FOLLOWED-CAR-001]
     */
    fun centerInitialFocus(
        parking: Pair<Double, Double>?,
        selectedSpot: Pair<Double, Double>?,
        user: Pair<Double, Double>,
    ) {
        if (centeredOnUser || userMovedCameraManually || pinToProtect) return
        centeredOnUser = true
        initialFocusWasParking = parking != null
        when {
            parking != null -> frameParking(parking, user)
            selectedSpot != null -> setTarget(selectedSpot.first, selectedSpot.second, zoom = FOCUS_SEARCH_ZOOM)
            else -> setTarget(user.first, user.second, zoom = FOCUS_SEARCH_ZOOM)
        }
    }

    /**
     * The initial focus for a Home that has NO position to wait for — location permission denied or
     * GPS off — while a car is parked: fly to the car. [HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001]
     *
     * [centerInitialFocus] needs the first fix, and without location it never comes, so Home used to
     * open on whatever default the map had while the user's own car sat somewhere off screen. The car
     * is the one place Home knows without a fix. Tight on it, like the parked branch of the initial
     * focus, and it consumes the same one-shot: if location arrives later the camera stays on the car
     * instead of jumping, and [refocusOnParkingArrival] has nothing left to do. Same guards as the
     * initial focus: a pan or a pin being placed win.
     */
    fun centerOnParkingWithoutLocation(parking: Pair<Double, Double>) {
        if (centeredOnUser || userMovedCameraManually || pinToProtect) return
        centeredOnUser = true
        initialFocusWasParking = true
        setTarget(parking.first, parking.second, zoom = FOCUS_PARKED_ZOOM)
    }

    /**
     * Y cuando NO hay coche aparcado tampoco: una ZONA guardada. [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001]
     *
     * ⛔ **Sin esto Home abre en (0, 0), el Golfo de Guinea.** La semilla del mapa es
     * `cameraTarget ?: initialCamera ?: userLocation ?: Coordinates(0,0)` y Home nunca pasa
     * `initialCamera`, así que sin fix y sin sesión no queda nadie que mueva la cámara: el usuario
     * abre la app en mitad del Atlántico, y con ella todo lo que cuelga de la cámara — plazas,
     * parkings y gasolineras se preguntan por el sitio que miras, y ese sitio no era ninguno.
     *
     * Una zona es lo más parecido a «donde vivo» que la app tiene sin medir nada: la declaró el
     * usuario. Consume el mismo one-shot que las otras dos entradas, así que si el fix llega después
     * la cámara ya no salta. Sin zonas no hay nada honesto que inventar — ahí manda el buscador, y
     * los tres carriles dicen que todavía no han podido preguntar.
     */
    fun centerOnZoneWithoutLocation(zone: Pair<Double, Double>) {
        if (centeredOnUser || userMovedCameraManually || pinToProtect) return
        centeredOnUser = true
        setTarget(zone.first, zone.second, zoom = FOCUS_SEARCH_ZOOM)
    }

    /**
     * [UI-THE-ASK-IS-A-CAMERA-SUBJECT-001] The place an open "did you park?" question is about, framed
     * as a camera subject of its own. Once per QUESTION, keyed by its post timestamp — the same
     * identity and the same "once" the sheet's auto-open already uses, so the sheet and the map can
     * never be open on two different places.
     *
     * Before this, the question had a marker and a tap target but no camera: the sheet opened by
     * itself on a place the camera was not looking at. On the case the whole slot exists for — park,
     * walk off, open the app — Home framed YOU, because during a question there IS no session and
     * [centerInitialFocus] therefore always took its "not parked" branch.
     *
     * Framed TIGHT on the place, deliberately not in bounds with the user the way [frameParking] does.
     * The parked car is framed with you because you are walking back to it and the two positions read
     * together; a question asks "is the car THERE?", and answering it needs that block, not an average
     * of that block and wherever you happen to be standing. Not needing the user's position is also
     * what frees this from waiting on the first GPS fix — it fires on a cold open while the fix is
     * still pending.
     *
     * It CONSUMES the one-shot initial focus ([centeredOnUser]): otherwise [centerInitialFocus] would
     * arrive with that first fix and drag the camera straight back onto the user. That is also what
     * ranks an open question above a genuinely parked car at open time — the question is the one thing
     * with a deadline, and the sheet is already on it.
     *
     * No [userMovedCameraManually] guard, on purpose: a new question is a new event, and the sheet
     * already opens itself for it without asking. A camera that obeyed the pan guard while the sheet
     * did not would put the two surfaces back on different places, which is the bug this fixes.
     *
     * It DOES stand down for a pin being placed, which is not the same thing as a pan: there the
     * camera centre is the user's pending answer, and this framing would overwrite it. The question
     * loses its framing in that case (the effect is keyed per question and does not retry) — accepted,
     * because confirming a parking by hand cancels the in-flight detection, so the pin answers the
     * question in fact. [PARK-A-DRAGGED-PIN-MUST-OUTRANK-AN-AUTOMATIC-CAMERA-001]
     *
     * AUTOMATIC framing, so it goes through the private setter and stays neutral on driver-follow, as
     * the rest of the focus machinery does. [UI-MAP-A-TAPPED-PLACE-OUTRANKS-THE-FOLLOWED-CAR-001]
     */
    fun frameTheAsk(shownAtMs: Long, candidate: Pair<Double, Double>) {
        if (framedAskAtMs == shownAtMs || pinToProtect) return
        framedAskAtMs = shownAtMs
        centeredOnUser = true
        setTarget(candidate.first, candidate.second, zoom = FOCUS_PARKED_ZOOM)
    }

    /**
     * Re-frame the parked car ONCE if its session arrived after the initial fix — but only while the
     * initial focus centred on the user (not already the car) and the user hasn't panned by hand. This
     * covers the common race where the GPS fix lands a beat before the parking session loads. [FOCUS-002]
     *
     * It is also the path that answers a question: "Yes" creates the session, and the camera moves off
     * the asked place onto the pin that was actually planted. [UI-THE-ASK-IS-A-CAMERA-SUBJECT-001]
     *
     * Stands down while a pin is being placed too: a session born from a hand-placed pin is already
     * under the camera — the pin WAS its centre — so there was never anything to re-frame.
     * [PARK-A-DRAGGED-PIN-MUST-OUTRANK-AN-AUTOMATIC-CAMERA-001]
     */
    fun refocusOnParkingArrival(parking: Pair<Double, Double>, user: Pair<Double, Double>?) {
        if (!centeredOnUser || userMovedCameraManually || initialFocusWasParking || refocusedOnParking) return
        if (pinToProtect) return
        refocusedOnParking = true
        frameParking(parking, user)
    }

    /** Frame the car alone (tight) or together with the user (bounds) when they're close. [FOCUS-001] */
    private fun frameParking(parking: Pair<Double, Double>, user: Pair<Double, Double>?) {
        val withinSpan = user != null &&
            distanceMeters(parking.first, parking.second, user.first, user.second) <= BOUNDS_MAX_SPAN_M
        if (withinSpan) {
            setBoundsTarget(parking.first, parking.second, user.first, user.second)
        } else {
            setTarget(parking.first, parking.second, zoom = FOCUS_PARKED_ZOOM)
        }
    }

    /**
     * Camera tracking of the live driving puck while a trip is detected. [FOLLOW-001]
     *
     *  - [setDriverFollowActive] toggles follow when a trip starts (true) or ends (false). Starting a
     *    trip re-arms follow even if the user had paused a previous one.
     *  - [followDriver] recentres on the puck WITHOUT changing zoom (the user's zoom is respected),
     *    but only while [followingDriver] is engaged. It goes through the private setter: revoking
     *    follow here would kill the very thing it serves, one GPS fix in.
     *  - [resumeDriverFollow] re-engages after the user paused it or asked for another place (the
     *    map's "resume" FAB), snapping the camera back onto the puck. Together with a trip starting,
     *    these are the ONLY two ways follow comes back.
     *    [UI-MAP-A-TAPPED-PLACE-OUTRANKS-THE-FOLLOWED-CAR-001]
     */
    fun setDriverFollowActive(active: Boolean) {
        followingDriver = active
        // Empezar a seguir un viaje ARMA el encuadre de abajo. Ver [followDriver].
        if (active) firstFollowPending = true
    }

    /**
     * ⛔ **El PRIMER seguimiento de un viaje sí pone zoom; los siguientes no.**
     * [HOME-A-LIVE-TRIP-DESERVES-THE-ZOOM-IT-NEEDS-001]
     *
     * «Recentra sin tocar el zoom, que el del usuario manda» es correcto **mientras se sigue**: nadie
     * quiere que la cámara le deshaga el pellizco cada vez que llega un fix. Pero en el instante en
     * que el seguimiento ARRANCA no hay zoom del usuario que respetar — hay el que tuviera el mapa,
     * que al abrir la app en mitad de un viaje es el de por defecto. Resultado, reportado en la
     * issue #66: *«al estar en estado driving y abrir la app, la cámara no me hace caso y no va a la
     * localización ni se acerca»*. Iba al coche; lo que no hacía era acercarse.
     *
     * Y es el único camino que queda en ese escenario: el enfoque inicial de [FOCUS-001] necesita un
     * `userGpsPoint`, que durante un viaje puede no llegar nunca a Home —la detección es quien tiene
     * el GPS—, así que su `else` nunca corre y `centeredOnUser` se queda en `false`.
     *
     * Por eso el zoom entra como **[ZoomIntent.AtLeast]**, igual que [goToPlace]: es un SUELO. Si el
     * usuario ya estaba más cerca que [FOCUS_DRIVING_ZOOM], no se le aleja — acercar a quien mira de
     * lejos y respetar a quien ya mira de cerca son la misma regla, no dos.
     */
    fun followDriver(lat: Double, lon: Double) {
        if (!followingDriver) return
        if (firstFollowPending) {
            firstFollowPending = false
            setTarget(lat, lon, zoom = FOCUS_DRIVING_ZOOM, zoomIntent = ZoomIntent.AtLeast)
        } else {
            setTarget(lat, lon)
        }
    }

    /**
     * ⛔ **Reanudar es ENCUADRAR, no sólo recentrar.** [HOME-A-LOCATE-ME-TAP-IS-A-FRAMING-001]
     *
     * Esto lo llama el FAB de localización, y nada más. Un fix de GPS no lo pide nadie —por eso
     * [followDriver] no toca el zoom— pero una PULSACIÓN sí: el usuario ha dicho «llévame a mi
     * localización», y llevarle sin acercarle es media respuesta. En ruta el botón recentraba y se
     * quedaba tan lejos como estuviera, mientras el mismo botón, parado, sí acercaba: dos
     * comportamientos para un icono, decididos por la rama que atendía la pulsación.
     *
     * El mismo suelo ([ZoomIntent.AtLeast]) y el mismo número ([FOCUS_DRIVING_ZOOM]) que el primer
     * seguimiento de un viaje: es la misma pregunta —a qué distancia se mira un coche en marcha— y
     * ya estaba respondida. A quien ya mira de cerca no se le aleja.
     * [HOME-A-LIVE-TRIP-DESERVES-THE-ZOOM-IT-NEEDS-001] [MAP-A-CAMERA-MOVE-MUST-NOT-COST-A-PINCH-001]
     */
    fun resumeDriverFollow(lat: Double, lon: Double) {
        followingDriver = true
        // Esto YA es el encuadre, así que consume el que [setDriverFollowActive] dejara armado: si
        // no, el siguiente fix volvería a aplicar el suelo, y entre la pulsación y ese fix el
        // usuario puede haber abierto el mapa a propósito.
        firstFollowPending = false
        setTarget(lat, lon, zoom = FOCUS_DRIVING_ZOOM, zoomIntent = ZoomIntent.AtLeast)
    }

    /** ⛔ `internal` y no `private`: son decisiones de producto —a qué distancia se mira un coche
     *  aparcado, y a cuál uno en marcha— y un test que las comprueba tiene que poder NOMBRARLAS.
     *  Repetir el 16 suelto en el test convierte el número en dos números.
     *  [HOME-A-LIVE-TRIP-DESERVES-THE-ZOOM-IT-NEEDS-001] */
    internal companion object {
        /** Al que se acerca la cámara al empezar a seguir un viaje. Un paso más lejos que el
         *  del coche aparcado (17): aparcado se mira la manzana exacta, conduciendo hace falta
         *  ver por dónde se va. [HOME-A-LIVE-TRIP-DESERVES-THE-ZOOM-IT-NEEDS-001] */
        const val FOCUS_DRIVING_ZOOM = 16f
        const val FOCUS_PARKED_ZOOM = 17f  // tight: see the exact block of the parked car
        const val FOCUS_SEARCH_ZOOM = 16f  // a touch wider: reveal nearby free spots around the user
        const val BOUNDS_MAX_SPAN_M = 250f // within this, frame car + user together instead of just the car
    }
}

@Composable
fun rememberHomeUiController(): HomeUiController = remember { HomeUiController() }
