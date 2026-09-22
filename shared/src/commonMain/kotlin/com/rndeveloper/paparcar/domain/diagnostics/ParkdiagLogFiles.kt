package com.rndeveloper.paparcar.domain.diagnostics

/**
 * Cómo se llama el log local de detección y cuánto se guarda — **una sola definición para las dos
 * plataformas**. [IOS-A-DETECTION-THAT-FAILS-MUST-LEAVE-A-TRACE-001]
 *
 * Estos tres números los tenía Android dentro de `FileAntilog`, que es donde nacieron y donde
 * estaban bien mientras fueran de uno. En cuanto iOS escribe su propio `parkdiag`, el nombre del
 * fichero deja de ser un detalle del sumidero: es el **contrato entre quien escribe y quien lee**,
 * y lo leen dos sitios por plataforma (el antilog que rota y el snapshot que sube el informe) más
 * las instrucciones de `adb` que el equipo tiene memorizadas. Dos copias de `"parkdiag.log"` es la
 * forma exacta en que un día el informe sube un fichero que ya no es el que se escribe.
 *
 * ⛔ No lleva la RUTA: dónde vive el directorio privado de la app es lo único que sí es de cada
 * plataforma (`filesDir` en Android, `Library/Application Support` en iOS).
 */
object ParkdiagLogFiles {

    /** El fichero activo. Las rotaciones son `parkdiag.log.1` (la más reciente) … `.5`. */
    const val BASE_NAME: String = "parkdiag.log"

    /**
     * Generaciones que se conservan además de la activa. Cinco y no una:
     * [DET-PARKDIAG-KEEP-MORE-HISTORY-001] midió dos veces en una semana que la evidencia del
     * incidente ya se había caído por el final cuando el móvil se enchufó.
     */
    const val KEPT_ROTATIONS: Int = 5

    /** Tamaño a partir del cual la activa rota. 5 MB × 6 ficheros ≈ 150 h de tráfico PARKDIAG. */
    const val MAX_BYTES: Long = 5L * 1024 * 1024

    /**
     * Las dos generaciones que viajan en un informe de usuario, **del más viejo al más nuevo**: la
     * rotación más reciente y la activa. Un incidente puede caer justo en el corte, así que una sola
     * no basta; las seis sobrarían (≈30 MB) para contar un viaje.
     * [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001]
     */
    val REPORTED_GENERATIONS: List<String> = listOf("$BASE_NAME.1", BASE_NAME)
}
