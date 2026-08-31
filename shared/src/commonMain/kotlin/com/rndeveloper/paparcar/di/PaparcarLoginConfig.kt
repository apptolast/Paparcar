package com.rndeveloper.paparcar.di

import com.apptolast.baselogin.config.GoogleSignInConfig
import com.apptolast.baselogin.di.LoginLibraryConfig
import com.apptolast.baselogin.domain.model.IdentityProvider

/**
 * Single source of truth for WHICH sign-in methods Paparcar offers. [AUTH-PROVIDERS-EXPLICIT-001]
 *
 * Every platform entry point builds its [LoginLibraryConfig] here — Android from
 * `PaparcarApp.onCreate`, iOS from `MainViewController` — so the offer cannot drift between
 * platforms, and neither can the previews and the Dev Catalog gallery, which read the same list
 * through [paparcarSocialProviders].
 *
 * The offer is declared EXPLICITLY, never inherited: several of the library's flags default to
 * enabled. In particular `phoneEnabled` defaults to `true`, which silently put an SMS button on
 * both the login and the register screens — a method Paparcar does not support (the Firebase phone
 * provider is not part of our setup, SMS is billed, and the library's default country code is
 * "+1"). Turning it off here is the whole point of this file: a method we do not support must not
 * be offered.
 *
 * @param googleWebClientId the Firebase web client id; blank or null disables Google sign-in
 *  (that is what happens on iOS today — see IOS-SOCIAL-LOGIN-001).
 * @param googleIosClientId the iOS client id, required by GoogleSignIn on iOS.
 */
fun paparcarLoginConfig(
    googleWebClientId: String?,
    googleIosClientId: String? = null,
): LoginLibraryConfig = LoginLibraryConfig(
    googleSignInConfig = googleWebClientId
        ?.takeIf { it.isNotBlank() }
        ?.let { GoogleSignInConfig(webClientId = it, iosClientId = googleIosClientId) },
    phoneEnabled = false,
)

/**
 * The social buttons a login/register screen shows for [config].
 *
 * Mirrors `AuthRepositoryImpl.getAvailableProviders()` of the login library — same conditions, same
 * order — because that is what decides the real screen at runtime and it needs a repository
 * instance we do not have in a preview. Kept next to [paparcarLoginConfig] on purpose: if the
 * library ever changes the mapping, both halves are read together.
 */
fun paparcarSocialProviders(config: LoginLibraryConfig): List<IdentityProvider> = buildList {
    if (config.googleSignInConfig != null) add(IdentityProvider.Google)
    if (config.appleSignInConfig != null) add(IdentityProvider.Apple)
    if (config.isGitHubAuthEnabled) add(IdentityProvider.GitHub)
    if (config.isMicrosoftAuthEnabled) add(IdentityProvider.Microsoft)
    if (config.magicLinkConfig != null) add(IdentityProvider.MagicLink)
    if (config.isPhoneAuthEnabled) add(IdentityProvider.Phone)
    if (config.isTwitterAuthEnabled) add(IdentityProvider.Twitter)
    if (config.isFacebookAuthEnabled) add(IdentityProvider.Facebook)
}
