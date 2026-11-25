package com.rndeveloper.paparcar.presentation.settings

import com.rndeveloper.paparcar.domain.attribution.InstallChannel
import com.rndeveloper.paparcar.domain.attribution.classifyInstallChannel
import com.rndeveloper.paparcar.domain.attribution.parseReferrerParams
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FriendReferralLinkTest {
    @Test
    fun should_supplyARecognizablePlayReferrer_when_invitationLinkIsOpened() {
        val link = friendReferralPlayLink("test-account")
        val uri = URI(link)
        val referrer = uri.query.substringAfter("&referrer=")
        val params = parseReferrerParams(referrer)

        assertEquals("https", uri.scheme)
        assertEquals("play.google.com", uri.host)
        assertEquals("id=com.rndeveloper.paparcar", uri.query.substringBefore("&referrer="))
        assertEquals(InstallChannel.FRIEND_REFERRAL, classifyInstallChannel(params, metaIds = null))
        assertTrue(uri.rawQuery.contains("utm_source%3Dfriend%26utm_medium%3Dreferral"))
        assertFalse(link.contains("test-account"))
    }

    @Test
    fun should_generateADistinctStableLink_when_accountsDiffer() {
        assertEquals(friendReferralPlayLink("account-a"), friendReferralPlayLink("account-a"))
        assertNotEquals(friendReferralPlayLink("account-a"), friendReferralPlayLink("account-b"))
    }
}
