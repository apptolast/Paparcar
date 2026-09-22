@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rndeveloper.paparcar.logging

import com.rndeveloper.paparcar.domain.diagnostics.LocalDiagnosticsLog
import com.rndeveloper.paparcar.domain.diagnostics.ParkdiagLogFiles
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_OK
import platform.zlib.ZLIB_VERSION
import platform.zlib.deflate
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2_
import platform.zlib.z_stream

/**
 * El snapshot que viaja en un informe de problema desde un iPhone: la rotación más reciente
 * (`parkdiag.log.1`) y el fichero activo, concatenados **del más viejo al más nuevo** para que quien
 * lo abra tenga una sola corriente cronológica, comprimidos en gzip.
 * [IOS-A-DETECTION-THAT-FAILS-MUST-LEAVE-A-TRACE-001] [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001]
 *
 * Mismas dos generaciones que en Android y por lo mismo: un incidente puede caer justo en el corte
 * de una rotación, y las seis serían ~30 MB para contar un viaje.
 *
 * ⛔ **gzip de verdad, no «zlib».** `compress2` produce el envoltorio de RFC 1950, y lo que hay al
 * otro lado —`SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001` y las herramientas del equipo— hace `gunzip`,
 * que es RFC 1952. La diferencia son los `+ 16` de [WINDOW_BITS_GZIP]: sin ellos el fichero sube
 * comprimido y **no se puede descomprimir con lo que ya usamos**, que es una forma silenciosa de no
 * tener log.
 */
class IosLocalDiagnosticsLog(
    private val directoryProvider: () -> String?,
) : LocalDiagnosticsLog {

    override suspend fun snapshotGzip(): ByteArray? = withContext(Dispatchers.Default) {
        val dir = directoryProvider() ?: return@withContext null
        val chronological = ParkdiagLogFiles.REPORTED_GENERATIONS
            .mapNotNull { name -> NSData.dataWithContentsOfFile("$dir/$name") }
            .filter { it.length.toInt() > 0 }
        if (chronological.isEmpty()) return@withContext null

        val raw = ByteArray(chronological.sumOf { it.length.toInt() })
        var offset = 0
        chronological.forEach { data ->
            val size = data.length.toInt()
            raw.usePinned { pinned -> memcpy(pinned.addressOf(offset), data.bytes, data.length) }
            offset += size
        }
        gzip(raw)
    }

    /**
     * Deflate con envoltorio gzip, por bloques: el log son megas y no se puede reservar un buffer de
     * salida «suficientemente grande» de una vez. Devuelve null ante cualquier fallo de zlib — un
     * informe sin log sigue siendo un informe, y es lo que ya hacía Android con su `runCatching`.
     */
    private fun gzip(input: ByteArray): ByteArray? = memScoped {
        if (input.isEmpty()) return null
        val stream = alloc<z_stream>()
        val started = deflateInit2_(
            stream.ptr,
            Z_DEFAULT_COMPRESSION,
            Z_DEFLATED,
            WINDOW_BITS_GZIP,
            MEM_LEVEL,
            Z_DEFAULT_STRATEGY,
            ZLIB_VERSION,
            sizeOf<z_stream>().toInt(),
        )
        if (started != Z_OK) return null
        try {
            val chunks = mutableListOf<ByteArray>()
            var total = 0
            input.usePinned { source ->
                stream.next_in = source.addressOf(0).reinterpret<UByteVar>()
                stream.avail_in = input.size.toUInt()
                val buffer = ByteArray(CHUNK_BYTES)
                var status = Z_OK
                do {
                    var produced = 0
                    buffer.usePinned { sink ->
                        stream.next_out = sink.addressOf(0).reinterpret<UByteVar>()
                        stream.avail_out = CHUNK_BYTES.toUInt()
                        status = deflate(stream.ptr, Z_FINISH)
                        produced = CHUNK_BYTES - stream.avail_out.toInt()
                    }
                    if (produced > 0) {
                        chunks += buffer.copyOf(produced)
                        total += produced
                    }
                    // Z_OK con Z_FINISH significa «queda salida»; Z_STREAM_END, que ya está todo.
                    // Cualquier otra cosa es un error y se abandona con lo que haya.
                } while (status == Z_OK)
                if (status < Z_OK) {
                    chunks.clear()
                    total = 0
                }
            }
            if (total == 0) return null
            val out = ByteArray(total)
            var at = 0
            chunks.forEach { chunk ->
                chunk.copyInto(out, at)
                at += chunk.size
            }
            out
        } finally {
            deflateEnd(stream.ptr)
        }
    }

    companion object {
        /**
         * El directorio por defecto: el mismo que escribe [IosFileAntilog]. Se pasa como lambda en
         * vez de recalcularse aquí para que quien lee y quien escribe no puedan discrepar sobre
         * dónde está el fichero — y para que un test pueda apuntar a otro sitio.
         */
        fun default(): IosLocalDiagnosticsLog = IosLocalDiagnosticsLog { IosFileAntilog.logDirectory() }

        /** 15 (ventana máxima) **+ 16** = envoltorio gzip en lugar de zlib. Ver el ⛔ de la clase. */
        private const val WINDOW_BITS_GZIP = 15 + 16

        /** El valor por defecto de zlib: memoria razonable, compresión razonable. */
        private const val MEM_LEVEL = 8

        /** 64 KB por vuelta. El log comprime ~10:1, así que un informe típico son pocas vueltas. */
        private const val CHUNK_BYTES = 64 * 1024
    }
}
