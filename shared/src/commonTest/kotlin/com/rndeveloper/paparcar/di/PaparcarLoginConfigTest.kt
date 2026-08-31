package com.rndeveloper.paparcar.di

import com.apptolast.baselogin.domain.model.IdentityProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the sign-in offer. [AUTH-PROVIDERS-EXPLICIT-001]
 *
 * The login library enables several methods by default — `phoneEnabled` among them — so an offer
 * built by omission is not the offer we mean. These tests fail the day someone drops the explicit
 * flags and lets a default put an unsupported button back on the login screen.
 */
class PaparcarLoginConfigTest {

    private companion object {
        const val WEB_CLIENT_ID = "test.apps.googleusercontent.com"
        const val IOS_CLIENT_ID = "test-ios.apps.googleusercontent.com"
    }

    @Test
    fun should_disablePhoneAuth_when_buildingTheConfig() {
        val config = paparcarLoginConfig(googleWebClientId = WEB_CLIENT_ID)

        assertFalse(config.isPhoneAuthEnabled, "Paparcar does not support SMS sign-in")
    }

    @Test
    fun should_notOfferPhone_when_listingProviders() {
        val providers = paparcarSocialProviders(paparcarLoginConfig(googleWebClientId = WEB_CLIENT_ID))

        assertFalse(IdentityProvider.Phone in providers, "an SMS button must never reach the screen")
    }

    @Test
    fun should_offerOnlyGoogle_when_webClientIdIsPresent() {
        val providers = paparcarSocialProviders(paparcarLoginConfig(googleWebClientId = WEB_CLIENT_ID))

        assertEquals(listOf(IdentityProvider.Google), providers)
    }

    @Test
    fun should_offerNothing_when_webClientIdIsMissing() {
        // Today's iOS: no client id, so no social buttons at all — better an empty row than a dead
        // button. See IOS-SOCIAL-LOGIN-001.
        assertTrue(paparcarSocialProviders(paparcarLoginConfig(googleWebClientId = null)).isEmpty())
        assertTrue(paparcarSocialProviders(paparcarLoginConfig(googleWebClientId = "")).isEmpty())
        assertTrue(paparcarSocialProviders(paparcarLoginConfig(googleWebClientId = "   ")).isEmpty())
    }

    @Test
    fun should_carryBothClientIds_when_iosIdIsGiven() {
        val google = paparcarLoginConfig(
            googleWebClientId = WEB_CLIENT_ID,
            googleIosClientId = IOS_CLIENT_ID,
        ).googleSignInConfig

        assertEquals(WEB_CLIENT_ID, google?.webClientId)
        assertEquals(IOS_CLIENT_ID, google?.iosClientId)
    }

    @Test
    fun should_notOfferAnyOtherProvider_when_usingTheDefaults() {
        val providers = paparcarSocialProviders(paparcarLoginConfig(googleWebClientId = WEB_CLIENT_ID))

        listOf(
            IdentityProvider.Apple,
            IdentityProvider.GitHub,
            IdentityProvider.Microsoft,
            IdentityProvider.MagicLink,
            IdentityProvider.Twitter,
            IdentityProvider.Facebook,
        ).forEach { assertFalse(it in providers, "${it.id} is not configured and must not be shown") }
    }
}
