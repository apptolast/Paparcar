package io.apptolast.paparcar.data.datasource.remote

import com.apptolast.customlogin.domain.AuthRepository
import dev.gitlive.firebase.firestore.FirebaseFirestore
import io.apptolast.paparcar.data.datasource.remote.dto.toDto
import io.apptolast.paparcar.domain.diagnostics.DeviceInfoProvider
import io.apptolast.paparcar.domain.diagnostics.UiLocationLogger
import io.apptolast.paparcar.domain.diagnostics.UiLocationSample
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.math.roundToInt

/**
 * Firestore-backed [UiLocationLogger] for verifying [UI-LOC-FOREGROUND-001] in the field.
 *
 * **Local sink (always on).** Every sample is mirrored to [PaparcarLogger] regardless of the remote
 * gate, so attaching logcat to the misbehaving device shows the fixes flow (cadence + accuracy) and
 * the SUBSCRIBED/STOPPED foreground scoping without any opt-in.
 *
 * **Remote sink (gated + throttled).** Mirrors samples to `diagnostics/{userId}/uiLocation/{autoId}`
 * behind the SAME opt-in flag as the detection log (`diagnostics_config/{userId}.enabled`), read once
 * and cached. Lifecycle samples (SUBSCRIBED/STOPPED) always go through; the high-rate FIX samples are
 * throttled to at most one per [REMOTE_FIX_MIN_INTERVAL_MS] so a map left open doesn't flood the
 * collection. Default (flag absent/unreadable) is disabled, so only opted-in devices emit.
 *
 * **Non-blocking contract.** [log] only mirrors to logcat and `trySend`s onto a buffered [Channel] —
 * it never throws and never touches the network on the caller's thread. A background consumer drains
 * the channel and writes off the hot path; on buffer saturation samples are dropped silently.
 *
 * The gate logic is intentionally duplicated from [FirestoreDetectionEventLogger] rather than shared,
 * to keep that field-critical detection class untouched by this diagnostics-only addition.
 */
class FirestoreUiLocationLogger(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val deviceInfo: DeviceInfoProvider,
    scope: CoroutineScope,
) : UiLocationLogger {

    private val channel = Channel<UiLocationSample>(capacity = BUFFER_CAPACITY)

    /** Cached gate: null until first successfully resolved, then the Firestore flag. @Volatile so the
     *  producer thread can short-circuit once the flag is known-disabled. */
    @Volatile private var gate: Boolean? = null

    /** Fix time of the last FIX sample forwarded to the remote channel; producer-side throttle. */
    @Volatile private var lastRemoteFixMs: Long = 0L

    init {
        scope.launch {
            for (sample in channel) {
                val userId = runCatching { authRepository.getCurrentSession()?.userId }.getOrNull()
                    ?: continue
                if (!isEnabled(userId)) continue
                runCatching { writeSample(userId, sample) }
                    .onFailure { e -> PaparcarLogger.w(TAG, "ui-location write failed: ${e.message}") }
            }
        }
    }

    override fun log(sample: UiLocationSample) {
        // LOCAL sink — always, gate-independent.
        PaparcarLogger.d(TAG, logLine(sample))
        // REMOTE sink — gated + FIX-throttled.
        if (gate == false) return
        if (sample.kind == UiLocationSample.Kind.FIX) {
            val prev = lastRemoteFixMs
            if (prev != 0L && sample.timestampMs - prev < REMOTE_FIX_MIN_INTERVAL_MS) return
            lastRemoteFixMs = sample.timestampMs
        }
        channel.trySend(sample)
    }

    private suspend fun isEnabled(userId: String): Boolean {
        gate?.let { return it }
        // Cache only a SUCCESSFUL read — a transient failure must not latch `false` for the process
        // and silently lose the whole field capture; leave the gate null so the next sample retries.
        return runCatching {
            firestore.collection(COLLECTION_CONFIG)
                .document(userId)
                .get()
                .get<Boolean?>(FIELD_ENABLED) ?: false
        }.fold(
            onSuccess = { value ->
                gate = value
                PaparcarLogger.d(TAG, "ui-location remote log enabled=$value")
                value
            },
            onFailure = { e ->
                PaparcarLogger.w(TAG, "ui-location gate read failed — will retry next sample: ${e.message}")
                false
            },
        )
    }

    private suspend fun writeSample(userId: String, sample: UiLocationSample) {
        firestore.collection(COLLECTION_DIAGNOSTICS)
            .document(userId)
            .collection(COLLECTION_UI_LOCATION)
            .add(sample.toDto(deviceInfo.deviceModel, deviceInfo.appVersion))
    }

    /** Compact one-line digest for logcat: "FIX fg=true HIGH_ACCURACY acc=8m gap=2100ms 40.41677,-3.70379". */
    private fun logLine(s: UiLocationSample): String = buildString {
        append(s.kind.name)
        append(" fg="); append(s.foreground)
        append(' '); append(s.priority)
        s.accuracy?.let { append(" acc="); append(it.roundToInt()); append('m') }
        s.sinceLastFixMs?.let { append(" gap="); append(it); append("ms") }
        s.speed?.let { append(" spd="); append(round1(it)); append("m/s") }
        if (s.latitude != null && s.longitude != null) {
            append(' '); append(round5(s.latitude)); append(','); append(round5(s.longitude))
        }
    }

    private companion object {
        const val TAG = "UiLocationLogger"
        const val COLLECTION_CONFIG = "diagnostics_config"
        const val FIELD_ENABLED = "enabled"
        const val COLLECTION_DIAGNOSTICS = "diagnostics"
        const val COLLECTION_UI_LOCATION = "uiLocation"
        const val BUFFER_CAPACITY = 64
        /** At most one FIX doc every 10 s remotely — enough to read cadence without flooding. */
        const val REMOTE_FIX_MIN_INTERVAL_MS = 10_000L
    }
}

/** Round to 1 decimal without `String.format` (unavailable in commonMain). */
private fun round1(v: Float): Double = (v * 10).roundToInt() / 10.0

/** Round a coordinate to 5 decimals (~1 m) for a compact, readable log line. */
private fun round5(v: Double): Double = (v * 100_000).roundToInt() / 100_000.0
