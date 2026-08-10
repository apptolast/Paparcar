package io.apptolast.paparcar.detection.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.apptolast.paparcar.domain.matching.TrailMapMatcher
import io.apptolast.paparcar.domain.places.RoadNetworkDataSource
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import io.apptolast.paparcar.domain.util.PolylineCodec
import kotlinx.coroutines.flow.catch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Post-park enrichment: geocoder address + POI, AND the one-time map-match of the driven route.
 *
 *  1. Address (local geocoder, instant) — written to DB immediately.
 *  2. Place info (network, best-effort) — written to DB if available.
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

        var addressSaved = false

        getAddressAndPlace(lat, lon)
            .catch { e -> PaparcarLogger.e(TAG, "getAddressAndPlace failed (attempt $runAttemptCount) lat=$lat lon=$lon", e) }
            .collect { info ->
                userParkingRepository
                    .updateParkingSessionAddressAndPlace(sessionId, info.address, info.placeInfo)
                    .onSuccess { addressSaved = true }
            }

        // Snap the driven route onto streets ONCE. true = done (snapped / nothing to snap); false =
        // roads couldn't be fetched (offline) → retryable. [DET-ROUTE-SNAP-STORE-001]
        val routeDone = runCatching { snapRoute(sessionId) }
            .getOrElse { e -> PaparcarLogger.w(TAG, "route snap failed (attempt $runAttemptCount)", e); false }

        return when {
            addressSaved && routeDone -> Result.success()
            runAttemptCount < MAX_RETRIES -> Result.retry()
            else -> {
                // Give up retrying: accept the raw route as final so history stops "recalculating".
                if (!routeDone) runCatching { acceptRawRouteAsFinal(sessionId) }
                Result.failure()
            }
        }
    }

    /**
     * Snaps the session's raw route onto streets and stores it as the final on-road line, or returns
     * false when the roads can't be fetched (offline → retry). Returns true when there is nothing to
     * do (already snapped, no route, or a trivial route) — the trivial/degenerate case is marked final
     * as-is so the UI stops waiting.
     */
    private suspend fun snapRoute(sessionId: String): Boolean {
        val session = userParkingRepository.getSessionById(sessionId) ?: return true
        if (session.routeSnapped) return true
        val encoded = session.routePolyline
        if (encoded.isNullOrEmpty()) return true // BT / legacy — no drive recorded
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
        val snapped = TrailMapMatcher.snap(raw, roads)
        userParkingRepository.updateParkingSessionRoute(sessionId, PolylineCodec.encode(snapped), snapped = true)
        PaparcarLogger.d(TAG, "route snapped for $sessionId: ${raw.size} raw → ${snapped.size} on-road pts")
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
