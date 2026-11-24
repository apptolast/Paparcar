package com.rndeveloper.paparcar.domain.attribution

/**
 * Lee el Google Play Install Referrer — una API de Android, no de Google Ads: cualquier app puede
 * leerla, sin permiso especial, y SOLO sirve datos útiles la primera vez que se conecta tras
 * instalar. [MKT-INSTALL-ATTRIBUTION-CHANNEL-001]
 *
 * ⛔ Android-only por construcción: la API (`com.android.installreferrer`) no existe fuera de Play
 * Store. En iOS no hay señal equivalente — [NoOpInstallReferrerPort] devuelve siempre `null`,
 * mismo patrón que [com.rndeveloper.paparcar.domain.detection.ports.CarAudioRouteSource] en Android
 * para el carril BT. [IOS-A-BT-LANE-NEEDS-A-REAL-SIGNAL-001]
 */
interface InstallReferrerPort {
    /**
     * Devuelve `null` si el servicio de Play no está disponible en este dispositivo (sin Play
     * Store, un ROM sin Play Services) — eso no es un error a reintentar, es una respuesta final.
     * Un fallo de CONEXIÓN al servicio (`SERVICE_DISCONNECTED`) es cosa del `Result.failure` del
     * caller, que decide si reintenta.
     */
    suspend fun read(): Result<InstallReferrerRaw?>
}

class NoOpInstallReferrerPort : InstallReferrerPort {
    override suspend fun read(): Result<InstallReferrerRaw?> = Result.success(null)
}
