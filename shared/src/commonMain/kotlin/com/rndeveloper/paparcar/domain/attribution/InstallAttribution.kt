package com.rndeveloper.paparcar.domain.attribution

/**
 * De qué canal vino esta instalación. [MKT-INSTALL-ATTRIBUTION-CHANNEL-001]
 *
 * Vocabulario cerrado a propósito, igual que [com.rndeveloper.paparcar.domain.analytics.AdAttributionEvent]:
 * lo que no está aquí no se escribe.
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
    /** El CTA de un anuncio de TikTok — SIEMPRE a través de nuestro propio enlace
     *  (`/descargar/z-tiktok`), nunca del flujo nativo "direct to store" de TikTok Ads: ese exige
     *  conectar un MMP (AppsFlyer/Adjust/…) que hoy no tenemos, y sin él TikTok ni siquiera se
     *  atribuye sus propios anuncios. Decisión del user, 24-11-2026.
     *  `utm_source=tiktok&utm_medium=paid`. [MKT-TIKTOK-INSTALL-ATTRIBUTION-001] */
    TIKTOK_PAID("tiktok_paid"),
    /** Bio o descripción de un vídeo/post de TikTok, también a través de nuestro enlace propio
     *  (`/descargar/tiktok`) — a diferencia de Instagram/Facebook, el navegador in-app de TikTok NO
     *  etiqueta nada por su cuenta: sin un enlace nuestro ya tageado, Play no sabe que vino de ahí.
     *  `utm_source=tiktok&utm_medium=social`. [MKT-TIKTOK-INSTALL-ATTRIBUTION-001] */
    TIKTOK_ORGANIC("tiktok_organic"),
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

/** What [com.rndeveloper.paparcar.domain.usecase.attribution.ResolveInstallAttributionUseCase]
 *  read and classified from Play but could not publish to Firestore yet, because there was no
 *  signed-in session at that instant. [MKT-A-REFERRER-READ-BEFORE-LOGIN-MUST-SURVIVE-001]
 *
 *  The Install Referrer only answers usefully ONCE per installation, and `AppViewModel` — where
 *  the use case is triggered — is created above the Splash/Auth nav graph, so that one chance
 *  almost always lands before login. This record is what lets the already-classified answer wait
 *  for a session instead of being lost: it is cleared the moment the use case manages to publish
 *  it, which can happen on a later cold start or the instant a session appears in this same one. */
data class PendingInstallAttribution(
    val channel: String,
    val source: String?,
    val medium: String?,
    val campaign: String?,
    val metaCampaignId: String?,
    val metaAdsetId: String?,
    val metaAdId: String?,
    val rawReferrer: String?,
    val resolvedAtMs: Long,
)
