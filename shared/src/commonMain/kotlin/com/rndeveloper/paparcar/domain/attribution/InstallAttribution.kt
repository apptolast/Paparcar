package com.rndeveloper.paparcar.domain.attribution

/**
 * De qué canal vino esta instalación. [MKT-INSTALL-ATTRIBUTION-CHANNEL-001]
 *
 * Vocabulario cerrado a propósito, igual que [com.rndeveloper.paparcar.domain.analytics.AdAttributionEvent]:
 * lo que no está aquí no se escribe. Cinco respuestas, nunca un `if` libre repartido por el código.
 */
enum class InstallChannel(val wire: String) {
    /** El referrer de Play llevaba el bloque cifrado de Meta y se descifró con éxito: hay
     *  `campaignId`/`adsetId`/`adId` reales de una campaña de pago. */
    META_PAID("meta_paid"),
    /** Se abrió desde dentro de la app de Instagram (bio, post, historia) pero sin bloque de Meta
     *  descifrable — alcance orgánico del mismo contenido que puede llevar un anuncio detrás. */
    META_ORGANIC_IG("meta_organic_ig"),
    /** Misma idea que [META_ORGANIC_IG] para Facebook. */
    META_ORGANIC_FB("meta_organic_fb"),
    /** Un enlace NUESTRO (web, QR, redirect de `/descargar`) con sus propios UTM —
     *  `utm_source=web&utm_medium=owned`. [MKT-AN-INSTALL-MUST-SAY-WHERE-IT-CAME-FROM-001] */
    OWNED_LINK("owned_link"),
    /** Búsqueda o navegación directa en Google Play, sin referrer de campaña. */
    PLAY_ORGANIC("play_organic"),
    /** Referrer vacío, ilegible, o una combinación que el vocabulario de arriba no cubre. */
    UNKNOWN("unknown"),
}

/** Lo que Google Play entrega tal cual, antes de parsear nada. Un dato por campo de la API
 *  (`com.android.installreferrer.api.ReferrerDetails`), para que el parseo sea una función pura
 *  separada de la lectura con efectos. */
data class InstallReferrerRaw(
    val installReferrer: String?,
    val referrerClickTimestampSeconds: Long?,
    val installBeginTimestampSeconds: Long?,
    val googlePlayInstantParam: Boolean?,
)

/** Los tres identificadores que Meta mete en su bloque cifrado, cuando lo hay.
 *  ⚠️ Nomenclatura legacy de Meta: lo que hoy se llama Campaña/Conjunto/Anuncio en Ads Manager
 *  sale en el payload descifrado como `campaign_group_id`/`campaign_id`/`adgroup_id` — NO
 *  `campaign_id`/`adset_id`/`ad_id` como sugeriría la UI actual. Ver nota en
 *  `functions/src/installReferrer.ts`. */
data class MetaInstallIds(
    val campaignId: String?,
    val adsetId: String?,
    val adId: String?,
)

/** El resultado ya clasificado — lo que se escribe en Firestore y en la propiedad de usuario de
 *  GA4. `rawReferrer` viaja para poder re-clasificar en el futuro sin tener que leer Play otra
 *  vez (el referrer sólo se puede leer una vez por instalación). */
data class InstallAttribution(
    val channel: InstallChannel,
    val source: String?,
    val medium: String?,
    val campaign: String?,
    val meta: MetaInstallIds?,
    val rawReferrer: String?,
    val referrerClickTimestampSeconds: Long?,
    val installBeginTimestampSeconds: Long?,
)
