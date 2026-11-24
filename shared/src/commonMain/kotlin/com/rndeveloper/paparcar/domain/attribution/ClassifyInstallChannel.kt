package com.rndeveloper.paparcar.domain.attribution

/**
 * Parsea la query del referrer de Play en pares clave-valor. Función pura: el referrer llega
 * URL-encoded una vez (`utm_source%3Dweb%26...`) y Play ya lo entrega decodificado una vez —
 * `installReferrer` de la API es la query cruda, sin el `?` inicial.
 */
fun parseReferrerParams(referrer: String?): Map<String, String> {
    if (referrer.isNullOrBlank()) return emptyMap()
    return referrer.split("&").mapNotNull { pair ->
        val idx = pair.indexOf('=')
        if (idx <= 0) return@mapNotNull null
        val key = pair.substring(0, idx)
        val value = decodeUrlComponent(pair.substring(idx + 1))
        key to value
    }.toMap()
}

/** `java.net.URLDecoder` no existe en commonMain — decodificación mínima de `%XX` y `+`, que es
 *  todo lo que un UTM necesita (nunca lleva Unicode fuera del ASCII imprimible). */
private fun decodeUrlComponent(value: String): String {
    val sb = StringBuilder(value.length)
    var i = 0
    while (i < value.length) {
        when (val c = value[i]) {
            '+' -> { sb.append(' '); i++ }
            '%' -> {
                val hex = value.getOrNull(i + 1)?.toString().orEmpty() + value.getOrNull(i + 2)?.toString().orEmpty()
                val code = hex.toIntOrNull(16)
                if (code != null) {
                    sb.append(code.toChar())
                    i += 3
                } else {
                    sb.append(c)
                    i++
                }
            }
            else -> { sb.append(c); i++ }
        }
    }
    return sb.toString()
}

/**
 * El vocabulario cerrado de [InstallChannel], como función pura. [MKT-INSTALL-ATTRIBUTION-CHANNEL-001]
 *
 * ⛔ El orden de los `when` IMPORTA: un bloque de Meta descifrado manda sobre cualquier UTM que el
 * mismo referrer lleve también (Meta puede adjuntar ambos). `metaIds` no nulo es la única entrada
 * que hace falta mirar antes de leer `source`/`medium`.
 */
fun classifyInstallChannel(
    params: Map<String, String>,
    metaIds: MetaInstallIds?,
): InstallChannel {
    if (metaIds != null && (metaIds.campaignId != null || metaIds.adsetId != null || metaIds.adId != null)) {
        return InstallChannel.META_PAID
    }
    val source = params[UTM_SOURCE]?.lowercase()
    val medium = params[UTM_MEDIUM]?.lowercase()
    return when {
        source == SOURCE_WEB && medium == MEDIUM_OWNED -> InstallChannel.OWNED_LINK
        // TikTok no etiqueta nada por su cuenta (a diferencia de Instagram/Facebook abajo): este
        // source/medium sólo puede venir de nuestro propio enlace, así que el medium decide solo
        // entre anuncio y publicación — no hace falta distinguir por campaign.
        // [MKT-TIKTOK-INSTALL-ATTRIBUTION-001]
        source == SOURCE_TIKTOK && medium == MEDIUM_PAID -> InstallChannel.TIKTOK_PAID
        source == SOURCE_TIKTOK -> InstallChannel.TIKTOK_ORGANIC
        source == SOURCE_INSTAGRAM || source == SOURCE_APPS_INSTAGRAM -> InstallChannel.META_ORGANIC_IG
        source == SOURCE_FACEBOOK || source == SOURCE_APPS_FACEBOOK -> InstallChannel.META_ORGANIC_FB
        source == SOURCE_GOOGLE_PLAY && medium == MEDIUM_ORGANIC -> InstallChannel.PLAY_ORGANIC
        params.isEmpty() -> InstallChannel.PLAY_ORGANIC
        else -> InstallChannel.UNKNOWN
    }
}

// ⛔ Mismo vocabulario que `web/lib/config.mjs` (INSTALL_SOURCE_SITE/INSTALL_MEDIUM_OWNED,
// DOWNLOAD_SHORTCUTS) y el que GA4 asigna solo al abrir Play desde dentro de la app de IG/FB
// (`apps.instagram.com`/`apps.facebook.com`). Sistemas distintos, mismas palabras — si uno cambia,
// el otro se queda ciego.
private const val UTM_SOURCE = "utm_source"
private const val UTM_MEDIUM = "utm_medium"
private const val SOURCE_WEB = "web"
private const val MEDIUM_OWNED = "owned"
private const val SOURCE_INSTAGRAM = "instagram"
private const val SOURCE_FACEBOOK = "facebook"
private const val SOURCE_APPS_INSTAGRAM = "apps.instagram.com"
private const val SOURCE_APPS_FACEBOOK = "apps.facebook.com"
private const val SOURCE_GOOGLE_PLAY = "google-play"
private const val SOURCE_TIKTOK = "tiktok"
private const val MEDIUM_ORGANIC = "organic"
private const val MEDIUM_PAID = "paid"
