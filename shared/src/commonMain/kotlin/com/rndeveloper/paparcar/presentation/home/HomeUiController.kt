package com.rndeveloper.paparcar.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rndeveloper.paparcar.presentation.map.CameraTarget
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
     */
    fun setPinPlacementActive(active: Boolean) {
        pinPlacementActive = active
    }

    fun clearProgrammaticMove() {
        isProgrammaticMove = false
    }

    private var centeredOnUser = false
    // True once the user pans/zooms by hand — disables every automatic re-frame thereafter. [FOCUS-002]
    private var userMovedCameraManually = false
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
    fun goToPlace(lat: Double, lon: Double, zoom: Float? = null) {
        followingDriver = false
        setTarget(lat, lon, zoom)
    }

    /** Deliberate two-point framing (the midpoint FAB: your car AND you). Outranks follow, as [goToPlace]. */
    fun framePlaces(lat1: Double, lon1: Double, lat2: Double, lon2: Double) {
        followingDriver = false
        setBoundsTarget(lat1, lon1, lat2, lon2)
    }

    private fun setTarget(lat: Double, lon: Double, zoom: Float? = null) {
        isProgrammaticMove = true
        cameraTarget = CameraTarget(lat, lon, zoom, token = (cameraTarget?.token ?: 0) + 1)
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
        if (centeredOnUser || userMovedCameraManually || pinPlacementActive) return
        centeredOnUser = true
        initialFocusWasParking = parking != null
        when {
            parking != null -> frameParking(parking, user)
            selectedSpot != null -> setTarget(selectedSpot.first, selectedSpot.second, zoom = FOCUS_SEARCH_ZOOM)
            else -> setTarget(user.first, user.second, zoom = FOCUS_SEARCH_ZOOM)
        }
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
        if (framedAskAtMs == shownAtMs || pinPlacementActive) return
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
        if (pinPlacementActive) return
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
    }

    fun followDriver(lat: Double, lon: Double) {
        if (followingDriver) setTarget(lat, lon)
    }

    fun resumeDriverFollow(lat: Double, lon: Double) {
        followingDriver = true
        setTarget(lat, lon)
    }

    private companion object {
        const val FOCUS_PARKED_ZOOM = 17f  // tight: see the exact block of the parked car
        const val FOCUS_SEARCH_ZOOM = 16f  // a touch wider: reveal nearby free spots around the user
        const val BOUNDS_MAX_SPAN_M = 250f // within this, frame car + user together instead of just the car
    }
}

@Composable
fun rememberHomeUiController(): HomeUiController = remember { HomeUiController() }
