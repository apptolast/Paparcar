@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rndeveloper.paparcar.logging

import com.rndeveloper.paparcar.domain.diagnostics.ParkdiagLogFiles
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSLocale
import platform.Foundation.NSLock
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.URLByAppendingPathComponent
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.closeFile
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.localeWithLocaleIdentifier
import platform.Foundation.seekToEndOfFile
import platform.Foundation.synchronizeFile
import platform.Foundation.writeData

/**
 * El `parkdiag` de iOS — el espejo de `FileAntilog` (Android), y por las mismas razones.
 * [IOS-A-DETECTION-THAT-FAILS-MUST-LEAVE-A-TRACE-001]
 *
 * Persiste toda línea de Napier cuya etiqueta empiece por [tagPrefix] (`PARKDIAG`) en
 * `Library/Application Support/parkdiag.log`, dentro del contenedor privado de la app. Rota igual:
 * al pasar de [ParkdiagLogFiles.MAX_BYTES] las generaciones bajan un escalón (`.1` es la más
 * reciente, `.5` la más vieja) y se empieza un fichero nuevo.
 *
 * ⛔ **Nace porque en iOS no había NINGÚN antilog instalado**, así que cada línea que escribía el
 * carril de detección se evaporaba: sin fichero, el botón «Informar de un problema» subía sólo
 * cabeceras y un viaje fallido no dejaba rastro en ninguna parte — ni en el móvil ni en Firestore,
 * cuyo gate hay que acordarse de encender ANTES. Issue #125.
 *
 * ## Decisiones que no son obvias
 *
 * - **`Library/Application Support`, no `Documents` ni `Caches`.** `Documents` es lo que ve el
 *   usuario si algún día se activa el uso compartido de ficheros, y `Caches` lo puede vaciar el
 *   sistema cuando aprieta el disco — justo el día del viaje que había que mirar. Se marca
 *   `isExcludedFromBackup`: son ~30 MB regenerables y no tienen por qué viajar a iCloud.
 * - **Un `NSFileHandle` abierto entre líneas, con `synchronizeFile` por entrada.** Es el equivalente
 *   exacto del `flush` de Android: entrega los bytes al sistema, así que el fichero sobrevive a que
 *   el proceso muera (que es el caso que este log existe para contar). Abrir y cerrar por línea
 *   sería una tormenta de syscalls durante todo el viaje.
 * - **Sin `synchronized`**: Kotlin/Native no lo tiene, y el contrato de Napier es que `performLog`
 *   puede llegar desde varios hilos. El acceso al handle se serializa con [NSLock].
 *
 * ## Sacar el log de un iPhone
 *
 * Lo normal es no sacarlo: Ajustes → «Informar de un problema» lo sube con el informe
 * ([com.rndeveloper.paparcar.logging.IosLocalDiagnosticsLog]). Con el cable, Xcode →
 * *Devices and Simulators* → la app → *Download Container* → «Mostrar contenido del paquete» →
 * `AppData/Library/Application Support/parkdiag.log*`.
 */
class IosFileAntilog(
    private val tagPrefix: String = "PARKDIAG",
    private val maxBytes: Long = ParkdiagLogFiles.MAX_BYTES,
    private val keptRotations: Int = ParkdiagLogFiles.KEPT_ROTATIONS,
) : Antilog() {

    private val fileManager = NSFileManager.defaultManager
    private val lock = NSLock()

    /** `Library/Application Support/paparcar-diagnostics`, creado si hace falta y excluido del
     *  backup. Null sólo si el sistema no da el directorio, en cuyo caso este antilog se convierte
     *  en un no-op silencioso: un log que no se puede escribir no es motivo para tumbar la app. */
    private val directory: String? = logDirectory()

    private val activePath: String? = directory?.let { "$it/${ParkdiagLogFiles.BASE_NAME}" }

    private val timestampFormat = NSDateFormatter().apply {
        dateFormat = "MM-dd HH:mm:ss.SSS"
        locale = NSLocale.localeWithLocaleIdentifier("en_US_POSIX")
    }

    /** Handle de escritura abierto, reusado entre líneas. */
    private var handle: NSFileHandle? = null

    /** Bytes del fichero activo, llevados a mano en vez de preguntarle al disco por línea. Se siembra
     *  del tamaño real para que un arranque de proceso continúe la generación en curso en lugar de
     *  reiniciar el reloj de la rotación. */
    private var activeFileBytes: Long = activePath?.let { fileSize(it) } ?: 0L

    override fun performLog(
        priority: LogLevel,
        tag: String?,
        throwable: Throwable?,
        message: String?,
    ) {
        if (tag?.startsWith(tagPrefix) != true) return
        val path = activePath ?: return
        val ts = timestampFormat.stringFromDate(NSDate())
        val level = priority.name.first()
        val line = buildString {
            append(ts).append(' ').append(level).append(' ').append(tag).append(": ")
            append(message ?: "").append('\n')
            throwable?.let { append("  ").append(it.stackTraceToString()).append('\n') }
        }
        lock.lock()
        try {
            runCatching { write(path, line) }
        } finally {
            lock.unlock()
        }
    }

    private fun write(path: String, line: String) {
        if (activeFileBytes > maxBytes) rotate(path)
        val data = (line as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val out = handle ?: openForAppend(path)?.also { handle = it } ?: return
        out.seekToEndOfFile()
        out.writeData(data)
        // El equivalente del flush: los bytes pasan al sistema, así que sobreviven a que el proceso
        // muera — que es exactamente el suceso que este fichero existe para contar.
        out.synchronizeFile()
        activeFileBytes += line.length.toLong()
    }

    /** `.4`→`.5`, …, activo→`.1`, y se empieza uno nuevo. Sólo se tira la generación más vieja. */
    private fun rotate(path: String) {
        val dir = directory ?: return
        // El handle apunta al fichero que va a cambiar de nombre: cerrarlo para que la siguiente
        // línea abra la generación nueva y no siga escribiendo en el inodo que se acaba de mover.
        runCatching { handle?.closeFile() }
        handle = null
        activeFileBytes = 0L
        fileManager.removeItemAtPath("$dir/${ParkdiagLogFiles.BASE_NAME}.$keptRotations", null)
        for (generation in keptRotations - 1 downTo 1) {
            val older = "$dir/${ParkdiagLogFiles.BASE_NAME}.$generation"
            if (fileManager.fileExistsAtPath(older)) {
                fileManager.moveItemAtPath(older, "$dir/${ParkdiagLogFiles.BASE_NAME}.${generation + 1}", null)
            }
        }
        fileManager.moveItemAtPath(path, "$dir/${ParkdiagLogFiles.BASE_NAME}.1", null)
    }

    private fun openForAppend(path: String): NSFileHandle? {
        if (!fileManager.fileExistsAtPath(path)) {
            fileManager.createFileAtPath(path, null, null)
        }
        return NSFileHandle.fileHandleForWritingAtPath(path)
    }

    private fun fileSize(path: String): Long =
        (fileManager.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? Number)?.toLong() ?: 0L

    companion object {
        /** Subcarpeta propia dentro de Application Support: el directorio lo comparten la base de
         *  datos y cualquier otro fichero de la app, y un `parkdiag.log.3` suelto ahí se lee como
         *  basura de alguien. */
        private const val DIR_NAME = "paparcar-diagnostics"

        /**
         * Dónde vive el `parkdiag` en iOS — **la única respuesta**, para que quien escribe
         * ([IosFileAntilog]) y quien lo sube ([IosLocalDiagnosticsLog]) no puedan discrepar sobre el
         * sitio. Crea el directorio si falta y lo marca excluido del backup: son ~30 MB
         * regenerables y no tienen por qué ocupar iCloud ni alargar cada copia.
         * [IOS-A-DETECTION-THAT-FAILS-MUST-LEAVE-A-TRACE-001]
         */
        fun logDirectory(): String? {
            val fileManager = NSFileManager.defaultManager
            val base = fileManager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask)
                .firstOrNull() as? NSURL ?: return null
            val dir = base.URLByAppendingPathComponent(DIR_NAME) ?: return null
            val path = dir.path ?: return null
            if (!fileManager.fileExistsAtPath(path)) {
                fileManager.createDirectoryAtPath(
                    path,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = null,
                )
            }
            runCatching { dir.setResourceValue(true, NSURLIsExcludedFromBackupKey, null) }
            return path
        }
    }
}
