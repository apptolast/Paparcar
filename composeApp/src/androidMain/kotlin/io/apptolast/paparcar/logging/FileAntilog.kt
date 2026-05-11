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
 * when the active file exceeds [maxBytes] (default 5 MB), it is renamed to
 * `parkdiag.log.old` and a fresh file is started. Only one rotation is kept; older
 * snapshots are discarded.
 *
 * Register from `Application.onCreate` **after** `Napier.base(DebugAntilog())` so logs
 * still appear in Logcat alongside the file copy. Debug builds only — do **not** add
 * this sink to release builds.
 *
 * ───────────────────────────────────────────────────────────────────────────────────
 * USAGE (English)
 * ───────────────────────────────────────────────────────────────────────────────────
 *
 * Pull the active log to your PC:
 *   adb shell run-as io.apptolast.paparcar cat files/parkdiag.log > C:\temp\parkdiag.log
 *
 * Pull the previous rotation, if any:
 *   adb shell run-as io.apptolast.paparcar cat files/parkdiag.log.old > C:\temp\parkdiag.log.old
 *
 * Clear before a fresh test:
 *   adb shell run-as io.apptolast.paparcar rm files/parkdiag.log
 *   adb shell run-as io.apptolast.paparcar rm files/parkdiag.log.old
 *
 * Why this over a plain `adb logcat` capture:
 *   - Survives device reboots, ANRs, and process kills by aggressive OEM ROMs.
 *   - Independent of the logcat ring buffer (no risk of losing entries when noisy).
 *   - Effectively unbounded test duration — 5 MB is ~30+ hours of PARKDIAG traffic.
 *   - No USB cable required during the test; pull whenever you reconnect.
 *
 * ───────────────────────────────────────────────────────────────────────────────────
 * USO (Español)
 * ───────────────────────────────────────────────────────────────────────────────────
 *
 * Pullear el log activo al PC:
 *   adb shell run-as io.apptolast.paparcar cat files/parkdiag.log > C:\temp\parkdiag.log
 *
 * Pullear el archivo rotado anterior (si existe):
 *   adb shell run-as io.apptolast.paparcar cat files/parkdiag.log.old > C:\temp\parkdiag.log.old
 *
 * Limpiar antes de un test nuevo:
 *   adb shell run-as io.apptolast.paparcar rm files/parkdiag.log
 *   adb shell run-as io.apptolast.paparcar rm files/parkdiag.log.old
 *
 * Por qué esto en lugar de un `adb logcat` normal:
 *   - Sobrevive a reinicios del dispositivo, ANRs y al asesino de procesos de ROMs
 *     agresivas (OPPO ColorOS, MIUI, Samsung One UI…).
 *   - Independiente del buffer del logcat — no se pierden líneas aunque haya ruido.
 *   - Sin límite práctico de duración del test — 5 MB son ~30+ horas de PARKDIAG.
 *   - No requiere cable USB durante el test; pulleas cuando reconectas.
 */
class FileAntilog(
    context: Context,
    private val tagPrefix: String = "PARKDIAG",
    private val maxBytes: Long = 5 * 1024 * 1024L,
) : Antilog() {

    private val file: File = File(context.filesDir, "parkdiag.log")
    private val rotatedFile: File = File(context.filesDir, "parkdiag.log.old")
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
                if (file.length() > maxBytes) {
                    if (rotatedFile.exists()) rotatedFile.delete()
                    file.renameTo(rotatedFile)
                }
                file.appendText(line)
                throwable?.let { file.appendText("  ${it.stackTraceToString()}\n") }
            }
        }
    }
}
