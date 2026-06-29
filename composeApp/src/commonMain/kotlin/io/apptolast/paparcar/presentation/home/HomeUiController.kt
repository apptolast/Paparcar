package io.apptolast.paparcar.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.apptolast.paparcar.presentation.map.CameraTarget
import io.apptolast.paparcar.presentation.util.distanceMeters

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
     * (moveCamera / moveCameraToBounds / initial GPS centering). The Map
     * library fires onCameraMove at ~60 fps during the animation, which
     * would otherwise trigger the idle-drag glass effect — HomeContent
     * reads this flag to suppress the effect for synthetic frames.
     *
     * Set synchronously from [moveCamera] / [moveCameraToBounds] so it is
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
     */
    fun onUserMapGesture() {
        userMovedCameraManually = true
        followingDriver = false
    }

    fun clearProgrammaticMove() {
        isProgrammaticMove = false
    }

    private var centeredOnUser = false
    // True once the user pans/zooms by hand — disables every automatic re-frame thereafter. [FOCUS-002]
    private var userMovedCameraManually = false
    // True when the initial focus already framed the parked car, so [FOCUS-002] needn't re-fire.
    private var initialFocusWasParking = false
    private var refocusedOnParking = false

    fun moveCamera(lat: Double, lon: Double, zoom: Float? = null) {
        isProgrammaticMove = true
        cameraTarget = CameraTarget(lat, lon, zoom, token = (cameraTarget?.token ?: 0) + 1)
    }

    fun moveCameraToBounds(lat1: Double, lon1: Double, lat2: Double, lon2: Double) {
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
     */
    fun centerInitialFocus(
        parking: Pair<Double, Double>?,
        selectedSpot: Pair<Double, Double>?,
        user: Pair<Double, Double>,
    ) {
        if (centeredOnUser) return
        centeredOnUser = true
        initialFocusWasParking = parking != null
        when {
            parking != null -> frameParking(parking, user)
            selectedSpot != null -> moveCamera(selectedSpot.first, selectedSpot.second, zoom = FOCUS_SEARCH_ZOOM)
            else -> moveCamera(user.first, user.second, zoom = FOCUS_SEARCH_ZOOM)
        }
    }

    /**
     * Re-frame the parked car ONCE if its session arrived after the initial fix — but only while the
     * initial focus centred on the user (not already the car) and the user hasn't panned by hand. This
     * covers the common race where the GPS fix lands a beat before the parking session loads. [FOCUS-002]
     */
    fun refocusOnParkingArrival(parking: Pair<Double, Double>, user: Pair<Double, Double>?) {
        if (!centeredOnUser || userMovedCameraManually || initialFocusWasParking || refocusedOnParking) return
        refocusedOnParking = true
        frameParking(parking, user)
    }

    /** Frame the car alone (tight) or together with the user (bounds) when they're close. [FOCUS-001] */
    private fun frameParking(parking: Pair<Double, Double>, user: Pair<Double, Double>?) {
        val withinSpan = user != null &&
            distanceMeters(parking.first, parking.second, user.first, user.second) <= BOUNDS_MAX_SPAN_M
        if (withinSpan) {
            moveCameraToBounds(parking.first, parking.second, user!!.first, user.second)
        } else {
            moveCamera(parking.first, parking.second, zoom = FOCUS_PARKED_ZOOM)
        }
    }

    /**
     * Camera tracking of the live driving puck while a trip is detected. [FOLLOW-001]
     *
     *  - [setDriverFollowActive] toggles follow when a trip starts (true) or ends (false). Starting a
     *    trip re-arms follow even if the user had paused a previous one.
     *  - [followDriver] recentres on the puck WITHOUT changing zoom (the user's zoom is respected),
     *    but only while [followingDriver] is engaged.
     *  - [resumeDriverFollow] re-engages after the user paused it (the map's "resume" FAB), snapping
     *    the camera back onto the puck.
     */
    fun setDriverFollowActive(active: Boolean) {
        followingDriver = active
    }

    fun followDriver(lat: Double, lon: Double) {
        if (followingDriver) moveCamera(lat, lon)
    }

    fun resumeDriverFollow(lat: Double, lon: Double) {
        followingDriver = true
        moveCamera(lat, lon)
    }

    private companion object {
        const val FOCUS_PARKED_ZOOM = 17f  // tight: see the exact block of the parked car
        const val FOCUS_SEARCH_ZOOM = 16f  // a touch wider: reveal nearby free spots around the user
        const val BOUNDS_MAX_SPAN_M = 250f // within this, frame car + user together instead of just the car
    }
}

@Composable
fun rememberHomeUiController(): HomeUiController = remember { HomeUiController() }
