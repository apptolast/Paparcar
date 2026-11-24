package com.rndeveloper.paparcar.domain.attribution

import kotlin.test.Test
import kotlin.test.assertEquals

/** [MKT-INSTALL-ATTRIBUTION-CHANNEL-001] */
class ClassifyInstallChannelTest {

    @Test
    fun should_parseEveryPair_when_theReferrerHasSeveralParams() {
        val params = parseReferrerParams("utm_source=web&utm_medium=owned&utm_campaign=home&utm_content=cta_hero")
        assertEquals(
            mapOf(
                "utm_source" to "web",
                "utm_medium" to "owned",
                "utm_campaign" to "home",
                "utm_content" to "cta_hero",
            ),
            params,
        )
    }

    @Test
    fun should_decodePercentEncoding_when_theReferrerCameFromAUrl() {
        // The real shape playUrl() produces: referrer=utm_source%3Dweb%26utm_medium%3Downed...
        // — but Play already hands the APP the decoded query string, so what the app sees is the
        // query AFTER one decode, still carrying %XX for anything the UTM value itself needed
        // escaped (a JSON blob in utm_content, for instance).
        val params = parseReferrerParams("utm_campaign=z%2Dmostoles&utm_content=%7B%22a%22%3A1%7D")
        assertEquals("z-mostoles", params["utm_campaign"])
        assertEquals("""{"a":1}""", params["utm_content"])
    }

    @Test
    fun should_returnEmptyMap_when_theReferrerIsNullOrBlank() {
        assertEquals(emptyMap(), parseReferrerParams(null))
        assertEquals(emptyMap(), parseReferrerParams(""))
    }

    @Test
    fun should_classifyAsMetaPaid_when_decryptedMetaIdsArePresent_regardlessOfUtm() {
        val params = mapOf("utm_source" to "web", "utm_medium" to "owned")
        val meta = MetaInstallIds(campaignId = "123", adsetId = "456", adId = "789")
        assertEquals(InstallChannel.META_PAID, classifyInstallChannel(params, meta))
    }

    @Test
    fun should_classifyAsMetaPaid_when_onlyOneMetaFieldIsPresent() {
        val meta = MetaInstallIds(campaignId = null, adsetId = null, adId = "789")
        assertEquals(InstallChannel.META_PAID, classifyInstallChannel(emptyMap(), meta))
    }

    @Test
    fun should_classifyAsOwnedLink_when_sourceIsWebAndMediumIsOwned() {
        val params = mapOf("utm_source" to "web", "utm_medium" to "owned", "utm_campaign" to "z-mostoles")
        assertEquals(InstallChannel.OWNED_LINK, classifyInstallChannel(params, metaIds = null))
    }

    @Test
    fun should_classifyAsTiktokPaid_when_sourceIsTiktokAndMediumIsPaid() {
        // El CTA del anuncio, apuntando a nuestro /descargar/z-tiktok — nunca al flujo nativo de
        // TikTok Ads. [MKT-TIKTOK-INSTALL-ATTRIBUTION-001]
        assertEquals(
            InstallChannel.TIKTOK_PAID,
            classifyInstallChannel(mapOf("utm_source" to "tiktok", "utm_medium" to "paid"), null),
        )
    }

    @Test
    fun should_classifyAsTiktokOrganic_when_sourceIsTiktokAndMediumIsNotPaid() {
        // Bio o descripción de un vídeo/post, siempre vía nuestro /descargar/tiktok.
        assertEquals(
            InstallChannel.TIKTOK_ORGANIC,
            classifyInstallChannel(mapOf("utm_source" to "tiktok", "utm_medium" to "social"), null),
        )
        // Sin medium reconocible, sigue siendo TikTok antes que UNKNOWN: el source ya lo dice todo
        // porque sólo nuestro propio enlace produce este valor.
        assertEquals(
            InstallChannel.TIKTOK_ORGANIC,
            classifyInstallChannel(mapOf("utm_source" to "tiktok"), null),
        )
    }

    @Test
    fun should_classifyAsMetaOrganicIg_when_sourceIsInstagram() {
        // Our own bio-link redirect: source=instagram&medium=social&campaign=bio
        assertEquals(
            InstallChannel.META_ORGANIC_IG,
            classifyInstallChannel(mapOf("utm_source" to "instagram", "utm_medium" to "social"), null),
        )
        // Google's own host-detected signal when Play opens from inside the Instagram app.
        assertEquals(
            InstallChannel.META_ORGANIC_IG,
            classifyInstallChannel(mapOf("utm_source" to "apps.instagram.com"), null),
        )
    }

    @Test
    fun should_classifyAsMetaOrganicFb_when_sourceIsFacebook() {
        assertEquals(
            InstallChannel.META_ORGANIC_FB,
            classifyInstallChannel(mapOf("utm_source" to "facebook"), null),
        )
        assertEquals(
            InstallChannel.META_ORGANIC_FB,
            classifyInstallChannel(mapOf("utm_source" to "apps.facebook.com"), null),
        )
    }

    @Test
    fun should_classifyAsPlayOrganic_when_sourceIsGooglePlayOrganic() {
        assertEquals(
            InstallChannel.PLAY_ORGANIC,
            classifyInstallChannel(mapOf("utm_source" to "google-play", "utm_medium" to "organic"), null),
        )
    }

    @Test
    fun should_classifyAsPlayOrganic_when_theReferrerIsEmpty() {
        assertEquals(InstallChannel.PLAY_ORGANIC, classifyInstallChannel(emptyMap(), null))
    }

    @Test
    fun should_classifyAsUnknown_when_theVocabularyDoesNotMatchAnything() {
        assertEquals(
            InstallChannel.UNKNOWN,
            classifyInstallChannel(mapOf("utm_source" to "newsletter", "utm_medium" to "email"), null),
        )
    }

    @Test
    fun should_beCaseInsensitive_when_readingSourceAndMedium() {
        assertEquals(
            InstallChannel.OWNED_LINK,
            classifyInstallChannel(mapOf("utm_source" to "WEB", "utm_medium" to "Owned"), null),
        )
    }
}
