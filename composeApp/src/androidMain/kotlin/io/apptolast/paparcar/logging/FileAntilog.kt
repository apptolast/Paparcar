package io.apptolast.paparcar.logging

import android.content.Context
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent file-backed log sink for diagnostic captures.
 *
 * Writes every Napier log entry whose tag starts with [tagPrefix] (default `PARKDIAG`)
 * to `${context.filesDir}/parkdiag.log`. The file lives inside the app's private data
 * directory, so it survives process death, reboots, OS-level service kills (e.g. OPPO
 * ColorOS aggressive background management), and disconnections from `adb`. Rotation:
 * when the active file exceeds [maxBytes] (default 5 MB), the generations shift down
 * (`parkdiag.log.1` is the most recent rotation, `.5` the oldest) and a fresh file is
 * started. [keptRotations] generations are kept — see [rotate] for why five and not one.
 *
 * ⚠️ Builds before [DET-PARKDIAG-KEEP-MORE-HISTORY-001] wrote a single `parkdiag.log.old`.
 * A device upgraded in place may still hold one; it is never written or read again, so pull
 * it once if the window matters and then delete it with the rest.
 *
 * Register from `Application.onCreate` **after** `Napier.base(DebugAntilog())` so logs
 * still appear in Logcat alongside the file copy. Debug builds only — do **not** add
 * this sink to release builds.
 *
 * ───────────────────────────────────────────────────────────────────────────────────
 * USAGE (English)
 * ───────────────────────────────────────────────────────────────────────────────────
 *
 * Pull the WHOLE history, oldest first, as one chronological file (what you almost always
 * want — an incident can straddle a rotation boundary):
 *   adb shell run-as io.apptolast.paparcar sh -c \
 *     'cat files/parkdiag.log.5 files/parkdiag.log.4 files/parkdiag.log.3 \
 *          files/parkdiag.log.2 files/parkdiag.log.1 files/parkdiag.log 2>/dev/null' \
 *     > C:\temp\parkdiag-full.log
 *
 * Pull only the active file:
 *   adb shell run-as io.apptolast.paparcar cat files/parkdiag.log > C:\temp\parkdiag.log
 *
 * Clear before a fresh test (every generation):
 *   adb shell run-as io.apptolast.paparcar sh -c 'rm -f files/parkdiag.log*'
 *
 * Why this over a plain `adb logcat` capture:
 *   - Survives device reboots, ANRs, and process kills by aggressive OEM ROMs.
 *   - Independent of the logcat ring buffer (no risk of losing entries when noisy).
 *   - Effectively unbounded test duration — 5 MB × 6 files is ~150 h of PARKDIAG traffic.
 *   - No USB cable required during the test; pull whenever you reconnect.
 *
 * ───────────────────────────────────────────────────────────────────────────────────
 * USO (Español)
 * ───────────────────────────────────────────────────────────────────────────────────
 *
 * Pullear el HISTORIAL ENTERO, del más viejo al más nuevo, en un solo fichero cronológico
 * (lo que quieres casi siempre — un incidente puede caer justo en una rotación):
 *   adb shell run-as io.apptolast.paparcar sh -c \
 *     'cat files/parkdiag.log.5 files/parkdiag.log.4 files/parkdiag.log.3 \
 *          files/parkdiag.log.2 files/parkdiag.log.1 files/parkdiag.log 2>/dev/null' \
 *     > C:\temp\parkdiag-full.log
 *
 * Pullear sólo el fichero activo:
 *   adb shell run-as io.apptolast.paparcar cat files/parkdiag.log > C:\temp\parkdiag.log
 *
 * Limpiar antes de un test nuevo (todas las generaciones):
 *   adb shell run-as io.apptolast.paparcar sh -c 'rm -f files/parkdiag.log*'
 *
 * Por qué esto en lugar de un `adb logcat` normal:
 *   - Sobrevive a reinicios del dispositivo, ANRs y al asesino de procesos de ROMs
 *     agresivas (OPPO ColorOS, MIUI, Samsung One UI…).
 *   - Independiente del buffer del logcat — no se pierden líneas aunque haya ruido.
 *   - Sin límite práctico de duración del test — 5 MB × 6 ficheros son ~150 h de PARKDIAG.
 *   - No requiere cable USB durante el test; pulleas cuando reconectas.
 */
class FileAntilog(
    context: Context,
    private val tagPrefix: String = "PARKDIAG",
    private val maxBytes: Long = 5 * 1024 * 1024L,
    private val keptRotations: Int = KEPT_ROTATIONS,
) : Antilog() {

    private val dir: File = context.filesDir
    private val file: File = File(dir, BASE_NAME)
    private val timestampFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val lock = Any()

    override fun performLog(
        priority: LogLevel,
        tag: String?,
        throwable: Throwable?,
        message: String?,
    ) {
        if (tag?.startsWith(tagPrefix) != true) return
        val ts = timestampFormat.format(Date())
        val level = priority.name.first()
        val line = "$ts $level $tag: ${message ?: ""}\n"
        synchronized(lock) {
            runCatching {
                if (file.length() > maxBytes) rotate()
                file.appendText(line)
                throwable?.let { file.appendText("  ${it.stackTraceToString()}\n") }
            }
        }
    }

    /**
     * Shift the generations down by one and start a fresh active file: `.4`→`.5`, `.3`→`.4`, …,
     * active→`.1`. The oldest generation is the only thing discarded.
     *
     * [DET-PARKDIAG-KEEP-MORE-HISTORY-001] A single rotation was not a retention policy, it was a
     * coin flip: twice in one week (field 2026-08-24 and 2026-08-25) the evidence for the incident
     * under investigation had already fallen off the end by the time the phone was plugged in, and
     * one of those traces had to be reconstructed from Firestore instead — which does not carry
     * everything the device log does. Five generations at 5 MB is ~150 h of PARKDIAG traffic and
     * ~25 MB of private app storage, which is nothing next to losing the one trip that mattered.
     */
    private fun rotate() {
        File(dir, "$BASE_NAME.$keptRotations").delete()
        for (generation in keptRotations - 1 downTo 1) {
            val older = File(dir, "$BASE_NAME.$generation")
            if (older.exists()) older.renameTo(File(dir, "$BASE_NAME.${generation + 1}"))
        }
        file.renameTo(File(dir, "$BASE_NAME.1"))
    }

    private companion object {
        const val BASE_NAME = "parkdiag.log"
        const val KEPT_ROTATIONS = 5
    }
}
