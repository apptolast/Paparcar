package io.apptolast.paparcar.ios.preferences

import io.apptolast.paparcar.domain.preferences.AppPreferences
import platform.Foundation.NSUserDefaults

private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

/**
 * iOS implementation of [AppPreferences] backed by [NSUserDefaults].
 * Equivalent to Android's SharedPreferences.
 */
class IosAppPreferences : AppPreferences {

    private val userDefaults = NSUserDefaults.standardUserDefaults

    override val isOnboardingCompleted: Boolean
        get() = userDefaults.boolForKey(KEY_ONBOARDING_COMPLETED)

    override fun setOnboardingCompleted() {
        userDefaults.setBool(true, forKey = KEY_ONBOARDING_COMPLETED)
    }
}
