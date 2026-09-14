package com.rndeveloper.paparcar.detection.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.rndeveloper.paparcar.domain.detection.DetectionPath
import com.rndeveloper.paparcar.domain.geocoder.enrichParkingSession
import com.rndeveloper.paparcar.domain.matching.InferredRoute
import com.rndeveloper.paparcar.domain.matching.TrailMapMatcher
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.places.RoadNetworkDataSource
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.domain.util.PolylineCodec
import com.rndeveloper.paparcar.domain.util.haversineMeters
import kotlinx.coroutines.flow.catch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Post-park enrichment: geocoder address + POI, AND the one-time map-match of the driven route.
 *
 *  1. Address (local geocoder, instant) — written to DB immediately.
 *  2. Place info (network) — written to DB when a place is found. **Best-effort does not mean
 *     one shot**: a lookup that could not reach Overpass is a [Result.retry], and only a lookup
 *     that ANSWERED (place, or a measured "nothing within 40 m") completes this half. The two
 *     used to be the same null. [POI-A-FAILED-LOOKUP-IS-NOT-AN-ANSWER-001]
 *  3. **Route snap** — the raw route stored at confirm is snapped onto OSM streets ONCE here and
 *     written back as the final on-road line (`routeSnapped = true`), so the history draws it without
 *     re-computing. Until then the session is `routeSnapped = false` → the UI shows "recalculating".
 *     [DET-ROUTE-SNAP-STORE-001]
 *
 * No network constraint: the geocoder works offline. The places lookup + route snap are best-effort;
 * a snap that can't fetch roads (offline) retries with the worker's backoff, and after [MAX_RETRIES]
 * the raw route is accepted as final so history never stalls on "recalculating".
 *
 * Input data: [KEY_SESSION_ID], [KEY_LAT], [KEY_LON].
 * Backoff: EXPONENTIAL starting at 30 s, up to [MAX_RETRIES] attempts.
 */
class EnrichParkingSessionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val getAddressAndPlace: GetAddressAndPlaceUseCase by inject()
    private val userParkingRepository: UserParkingRepository by inject()
    private val roadNetworkDataSource: RoadNetworkDataSource by inject()

    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return Result.failure()
        val lat = inputData.getDouble(KEY_LAT, Double.NaN).takeIf { !it.isNaN() } ?: return Result.failure()
        val lon = inputData.getDouble(KEY_LON, Double.NaN).takeIf { !it.isNaN() } ?: return Result.failure()

        // The stamping and the "is it finished?" verdict live in commonMain, shared with the iOS
        // drainer — two copies of this loop is what let the same bug run on both platforms.
        // [POI-A-FAILED-LOOKUP-IS-NOT-AN-ANSWER-001]
        val geocodeDone = enrichParkingSession(
            sessionId = sessionId,
            // persisting: this name goes into the session and stays there. A cell that says "no
            // place here" — possibly sealed seconds ago by the user's own position, metres away —
            // does not get to answer for the pin. [POI-A-FAILED-LOOKUP-IS-NOT-AN-ANSWER-001]
            answers = getAddressAndPlace(lat, lon, persisting = true)
                .catch { e -> PaparcarLogger.e(TAG, "getAddressAndPlace failed (attempt $runAttemptCount) lat=$lat lon=$lon", e) },
            repository = userParkingRepository,
        )

        // Snap the driven route onto streets ONCE. true = done (snapped / nothing to snap); false =
        // roads couldn't be fetched (offline) → retryable. [DET-ROUTE-SNAP-STORE-001]
        val routeDone = runCatching { snapRoute(sessionId) }
            .getOrElse { e -> PaparcarLogger.w(TAG, "route snap failed (attempt $runAttemptCount)", e); false }

        return when {
            geocodeDone && routeDone -> Result.success()
            runAttemptCount < MAX_RETRIES -> Result.retry()
            else -> {
                // Give up retrying: accept the raw route as final so history stops "recalculating".
                if (!routeDone) runCatching { acceptRawRouteAsFinal(sessionId) }
                Result.failure()
            }
        }
    }

    /**
     * Snaps the session's raw route onto streets and stores it as the final on-road line — with the
     * provenance of any road-INFERRED stretches (data holes reconstructed along the streets) so the
     * UI can dim them and ask [ROUTE-GAP-HONEST-001] — or returns false when the roads can't be
     * fetched (offline → retry). Returns true when there is nothing to do (already snapped, no
     * route, or a trivial route) — the trivial/degenerate case is marked final as-is so the UI
     * stops waiting. A routeless pin whose placement is trusted (safety-net backfill or user
     * ground truth) gets a fully-inferred pin-to-pin reconstruction instead of no route at all.
     */
    private suspend fun snapRoute(sessionId: String): Boolean {
        val session = userParkingRepository.getSessionById(sessionId) ?: return true
        if (session.routeSnapped) return true
        val encoded = session.routePolyline
        if (encoded.isNullOrEmpty()) return inferPinToPinRoute(session) // BT/legacy no-op; trusted pins reconstruct
        val raw = PolylineCodec.decode(encoded)
        if (raw.size < 2) {
            userParkingRepository.updateParkingSessionRoute(sessionId, encoded, snapped = true)
            return true
        }
        val lats = raw.map { it.latitude }
        val lons = raw.map { it.longitude }
        val roads = roadNetworkDataSource.getRoads(
            minLat = lats.min() - ROADS_BBOX_MARGIN_DEG,
            minLon = lons.min() - ROADS_BBOX_MARGIN_DEG,
            maxLat = lats.max() + ROADS_BBOX_MARGIN_DEG,
            maxLon = lons.max() + ROADS_BBOX_MARGIN_DEG,
        ).getOrNull().orEmpty()
        if (roads.isEmpty()) return false // couldn't fetch → retry with backoff
        val matched = TrailMapMatcher.match(raw, roads)
        val spans = InferredRoute.encode(matched.inferredSpans, matched.cuts)
        userParkingRepository.updateParkingSessionRoute(
            sessionId,
            PolylineCodec.encode(matched.points),
            snapped = true,
            inferredSpans = spans,
        )
        PaparcarLogger.d(
            TAG,
            "route snapped for $sessionId: ${raw.size} raw → ${matched.points.size} on-road pts" +
                (spans?.let { ", provenance=$it" } ?: ""),
        )
        return true
    }

    /**
     * A pin with NO recorded drive but a TRUSTED placement — the safety net's reconciled backfill,
     * or the user's own hand ("manual" / "user" prompt / "nudge" answer, reliability 1.0) —
     * gets the trip reconstructed pin-to-pin along the roads instead of staying routeless, stored
     * FULLY inferred and pending the user's "did you drive this way?" verdict (REJECTED erases it).
     * Deliberately NOT eligible: "bt" (BT drives should get a MEASURED route — separate gap),
     * unattended/approximate pins (a guessed route to a guessed pin stacks doubt on doubt), and
     * measured coordinator paths (they carry a real route; a missing one means a trivial hop that
     * the lower bound below rejects anyway). Skipped (true, no route) when there is no plausible
     * previous pin or the hop is trivial/too large; false only when the road fetch failed (retry).
     * [ROUTE-GAP-HONEST-001][ROUTE-MANUAL-PIN-INFERRED-001]
     */
    private suspend fun inferPinToPinRoute(session: UserParking): Boolean {
        if (!isPinToPinEligible(session.detectionPath)) return true
        val vehicleId = session.vehicleId ?: return true
        val previous = userParkingRepository.getPreviousSession(vehicleId, session.location.timestamp)
            ?: return true
        val hopMeters = haversineMeters(
            previous.location.latitude, previous.location.longitude,
            session.location.latitude, session.location.longitude,
        )
        if (hopMeters <= TrailMapMatcher.MAX_MEASURED_STEP_METERS ||
            hopMeters > TrailMapMatcher.GAP_BRIDGE_CEILING_METERS
        ) return true
        // The plausible road route can bow sideways well past the two pins' bbox — widen the fetch
        // with the hop scale.
        val margin = ROADS_BBOX_MARGIN_DEG + (hopMeters / 2.0) / METERS_PER_DEGREE_LAT
        val roads = roadNetworkDataSource.getRoads(
            minLat = minOf(previous.location.latitude, session.location.latitude) - margin,
            minLon = minOf(previous.location.longitude, session.location.longitude) - margin,
            maxLat = maxOf(previous.location.latitude, session.location.latitude) + margin,
            maxLon = maxOf(previous.location.longitude, session.location.longitude) + margin,
        ).getOrNull().orEmpty()
        if (roads.isEmpty()) return false // couldn't fetch → retry with backoff
        val matched = TrailMapMatcher.match(listOf(previous.location, session.location), roads)
        if (matched.inferredSpans.isEmpty()) return true // no plausible bridge — honestly no route
        userParkingRepository.updateParkingSessionRoute(
            session.id,
            PolylineCodec.encode(matched.points),
            snapped = true,
            inferredSpans = InferredRoute.encode(matched.inferredSpans, matched.cuts),
        )
        PaparcarLogger.d(
            TAG,
            "backfill route inferred pin-to-pin for ${session.id}: ${hopMeters.toInt()}m hop → ${matched.points.size} pts (pending user verdict)",
        )
        return true
    }

    /** Last resort after retries are exhausted: mark the raw route final so the UI draws it instead
     *  of showing "recalculating" forever (offline for the whole retry window). */
    private suspend fun acceptRawRouteAsFinal(sessionId: String) {
        val session = userParkingRepository.getSessionById(sessionId) ?: return
        if (!session.routeSnapped && !session.routePolyline.isNullOrEmpty()) {
            userParkingRepository.updateParkingSessionRoute(sessionId, session.routePolyline, snapped = true)
            PaparcarLogger.w(TAG, "route snap gave up for $sessionId — accepting raw route as final")
        }
    }

    companion object {
        const val TAG = "EnrichParkingSessionWorker"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_SECONDS = 30L

        // Pad the OSM road fetch ~400 m around the route bbox so the matcher has the streets just
        // outside the trace to snap onto. Mirrors the live fetch margin. [ROUTE-SNAP-001]
        private const val ROADS_BBOX_MARGIN_DEG = 0.004

        /**
         * Confirmation PATHs whose routeless pins get the pin-to-pin reconstruction (marked
         * inferred, user verdict pending): the safety net's reconciled backfill plus the three
         * user-ground-truth placements.
         * [ROUTE-GAP-HONEST-001][ROUTE-MANUAL-PIN-INFERRED-001]
         *
         * [DET-A-NUDGE-MUST-SAY-WHO-ASKED-001] PARSES the label instead of comparing it against a
         * hand-kept set of strings. The set was equality-only, so the day `nudge` learned to carry
         * who asked (`nudge_bt_no_move`), every nudge pin would have dropped silently out of
         * eligibility and stayed routeless — the failure would have been a missing route nobody
         * reported, not a crash. `DetectionPath.ofLabel` already fails closed on anything unknown,
         * and the `when` is exhaustive, so a new path has to be classified rather than defaulted.
         */
        internal fun isPinToPinEligible(label: String?): Boolean =
            when (DetectionPath.ofLabel(label)) {
                is DetectionPath.SafetyNetBackfill,
                is DetectionPath.ManualPin,
                is DetectionPath.UserAnswered,
                is DetectionPath.Nudge,
                -> true

                // Deliberately NOT eligible, per the doc above: "bt" (BT drives deserve a MEASURED
                // route — separate gap), unattended/approximate pins (a guessed route to a guessed
                // pin stacks doubt on doubt), the measured coordinator paths (they carry a real
                // route), a user DRAG (the session already has its own route), and an unrecognised
                // or absent label.
                is DetectionPath.Bt, is DetectionPath.BtTimeout,
                is DetectionPath.UnattendedTimeout, is DetectionPath.UnattendedZone,
                is DetectionPath.ClosedApproximatePin, is DetectionPath.ClosedApproximateZone,
                is DetectionPath.StepsEgress, is DetectionPath.KinematicEgress,
                is DetectionPath.VehicleExitWindow, is DetectionPath.UserMovedPin,
                null,
                -> false
            }

        // Meters per degree of latitude — scales the pin-to-pin road fetch margin with the hop.
        private const val METERS_PER_DEGREE_LAT = 111_000.0

        fun buildRequest(sessionId: String, lat: Double, lon: Double): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<EnrichParkingSessionWorker>()
                .setInputData(
                    workDataOf(
                        KEY_SESSION_ID to sessionId,
                        KEY_LAT to lat,
                        KEY_LON to lon,
                    )
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, INITIAL_BACKOFF_SECONDS, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()
    }
}
